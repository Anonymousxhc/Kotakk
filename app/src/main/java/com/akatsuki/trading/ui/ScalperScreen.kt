package com.akatsuki.trading.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akatsuki.trading.data.ChainRow
import com.akatsuki.trading.data.InstrumentStatus
import com.akatsuki.trading.ui.theme.*
import com.akatsuki.trading.viewmodel.AppUiState
import com.akatsuki.trading.viewmodel.AppViewModel
import kotlinx.coroutines.launch

private val INDICES = listOf("NIFTY", "BANKNIFTY", "SENSEX", "FINNIFTY", "MIDCPNIFTY")
private val NUM_STRIKES_OPTIONS = listOf(5, 10, 15, 20)

// Go version colors: buy = linear-gradient(135deg, #10b981, #059669)
//                   sell = linear-gradient(135deg, #ef4444, #dc2626)
private val BuyGradient = Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))
private val SellGradient = Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626)))

@Composable
fun ScalperScreen(vm: AppViewModel, st: AppUiState) {
    val scope = rememberCoroutineScope()
    val chainState = rememberLazyListState()
    var orderMsg by remember { mutableStateOf("") }
    var orderBusy by remember { mutableStateOf(false) }

    LaunchedEffect(st.chain.size, st.selectedRowIdx) {
        if (st.selectedRowIdx >= 0 && st.chain.isNotEmpty()) {
            chainState.animateScrollToItem(st.selectedRowIdx)
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {

        // ── Index strip (scrollable chips with spot prices)
        Row(
            Modifier.fillMaxWidth().background(Bg2)
                .padding(vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // HSM connection dot
            Box(
                Modifier.padding(start = 8.dp, end = 4.dp).size(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (st.hsmConnected) Green else Color(0xFF444444))
            )
            INDICES.forEach { idx ->
                val active = st.currentIndex == idx
                val spot = st.spotPrices[idx]
                Box(
                    Modifier.padding(horizontal = 3.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) AmberDim else Bg3)
                        .border(1.dp, if (active) Amber else Border, RoundedCornerShape(6.dp))
                        .clickable { vm.setCurrentIndex(idx) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(idx, color = if (active) Amber else TxtMuted,
                            fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        if (spot != null && spot > 0) {
                            Text("%.0f".format(spot), color = if (active) Amber else TxtSec,
                                fontSize = 11.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── Expiry chips row + numStrikes chips
        Row(
            Modifier.fillMaxWidth().background(Bg)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Expiry chips (scrollable)
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (st.expiries.isEmpty()) {
                    Text("Loading expiries...", color = TxtMuted, fontSize = 9.sp)
                }
                st.expiries.forEach { exp ->
                    val active = st.selectedExpiry == exp.label
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (active) BlueDim else Bg3)
                            .border(1.dp, if (active) Blue else Border, RoundedCornerShape(12.dp))
                            .clickable { vm.setSelectedExpiry(exp.label) }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (exp.isNearest) {
                                Box(Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(Green))
                            }
                            Text(exp.label.take(9), color = if (active) Blue else TxtMuted,
                                fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // numStrikes chips (5/10/15/20) — right side
            Spacer(Modifier.width(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                NUM_STRIKES_OPTIONS.forEach { n ->
                    val active = st.numStrikes == n
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(if (active) BlueDim else Bg3)
                            .border(1.dp, if (active) Blue else Border, RoundedCornerShape(6.dp))
                            .clickable { vm.setNumStrikes(n) }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        Text("$n", color = if (active) Blue else TxtMuted,
                            fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        HorizontalDivider(color = Border, thickness = 1.dp)

        // ── Chain header
        Row(
            Modifier.fillMaxWidth().background(Bg2)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text("CALL", Modifier.weight(2f), textAlign = TextAlign.Center,
                color = Green, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("Strike", Modifier.weight(1.4f), textAlign = TextAlign.Center,
                color = Amber, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("PUT", Modifier.weight(2f), textAlign = TextAlign.Center,
                color = Red, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        HorizontalDivider(color = Border, thickness = 1.dp)

        // ── Chain list (takes all remaining space)
        Box(Modifier.weight(1f)) {
            when (st.instrumentStatus) {
                InstrumentStatus.LOADING -> Column(Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Amber, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(st.instrumentMsg, color = TxtSec, fontSize = 12.sp)
                }
                InstrumentStatus.ERROR -> Column(Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠", fontSize = 24.sp, color = Red)
                    Spacer(Modifier.height(6.dp))
                    Text(st.instrumentMsg, color = Red, fontSize = 12.sp)
                }
                InstrumentStatus.READY -> {
                    if (st.chainLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = TxtSec,
                                modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    } else if (st.chain.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Select index and expiry", color = TxtSec, fontSize = 12.sp)
                        }
                    } else {
                        LazyColumn(state = chainState) {
                            itemsIndexed(st.chain) { i, row ->
                                ChainRowItem(row, i == st.selectedRowIdx, st.liveLtps) {
                                    vm.setSelectedRow(i)
                                }
                                HorizontalDivider(color = Border, thickness = 0.5.dp)
                            }
                        }
                    }
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading instruments...", color = TxtSec, fontSize = 12.sp)
                }
            }
        }

        // ── Mobile bar — matches Go's mb-top + mb-btns exactly
        Column(
            Modifier.fillMaxWidth().background(Bg2)
                .border(width = 1.dp, color = Border, shape = RoundedCornerShape(0.dp))
        ) {
            val selRow = if (st.selectedRowIdx >= 0 && st.selectedRowIdx < st.chain.size)
                st.chain[st.selectedRowIdx] else null
            val ceLtp = selRow?.let { st.liveLtps[it.ceTs] } ?: 0.0
            val peLtp = selRow?.let { st.liveLtps[it.peTs] } ?: 0.0
            val hasCe = selRow != null && selRow.ceTs.isNotEmpty()
            val hasPe = selRow != null && selRow.peTs.isNotEmpty()
            val locked = st.safetyLock
            val pnl = st.dayPnl

            // ── mb-top: [strike + P&L] | [safety btn] | [lots control]
            Row(
                Modifier.fillMaxWidth()
                    .border(width = 0.dp, color = Border, shape = RoundedCornerShape(0.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: strike number + P&L inline (Go: mb-strike + mb-pnl)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selRow != null) {
                        Text(
                            "◉ %.0f".format(selRow.strike),
                            color = Amber, fontSize = 13.sp,
                            fontFamily = Mono, fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    } else {
                        Text("No strike", color = TxtMuted,
                            fontSize = 10.sp, fontStyle = FontStyle.Italic)
                    }
                    Text(
                        "P&L ${if (pnl >= 0) "+" else ""}₹${"%.0f".format(pnl)}",
                        color = when {
                            pnl > 0 -> Green
                            pnl < 0 -> Red
                            else -> TxtMuted
                        },
                        fontSize = 10.sp, fontFamily = Mono, fontWeight = FontWeight.Bold,
                    )
                }

                // Center: safety btn (Go: .safety-btn.live / .safety-btn.locked)
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (locked) RedDim else GreenDim)
                        .border(1.dp,
                            if (locked) Color(0x4DEF4444) else Color(0x4010B981),
                            RoundedCornerShape(6.dp))
                        .clickable { vm.toggleSafety() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (locked) "🔒 LOCKED" else "🔓 LIVE",
                        color = if (locked) Red else Green,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    )
                }

                // Right: lots control (Go: mb-lots-group)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text("LOTS", color = TxtMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Box(
                        Modifier.size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Bg3)
                            .border(1.dp, Border, RoundedCornerShape(4.dp))
                            .clickable { vm.setLots(st.lots - 1) },
                        contentAlignment = Alignment.Center,
                    ) { Text("−", color = Txt, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    Text("${st.lots}", color = Txt, fontSize = 14.sp,
                        fontFamily = Mono, fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(min = 20.dp), textAlign = TextAlign.Center)
                    Box(
                        Modifier.size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Bg3)
                            .border(1.dp, Border, RoundedCornerShape(4.dp))
                            .clickable { vm.setLots(st.lots + 1) },
                        contentAlignment = Alignment.Center,
                    ) { Text("+", color = Txt, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }

            HorizontalDivider(color = Border, thickness = 1.dp)

            // Order feedback toast
            if (orderMsg.isNotEmpty()) {
                Text(orderMsg,
                    color = if (orderMsg.startsWith("✓")) Green else Red,
                    fontSize = 11.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp))
            }

            // ── mb-btns: 2×2 grid matching Go's grid-template-columns:1fr 1fr
            // Row 1: BUY CE | SELL CE
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MbActionBtn("BUY CE", isBuy = true, enabled = hasCe && !locked && !orderBusy,
                    modifier = Modifier.weight(1f)) {
                    scope.launch {
                        orderBusy = true; orderMsg = ""
                        val r = vm.placeOrder("B", "CE")
                        orderMsg = if (r.ok) "✓ BUY CE placed" else "✗ ${r.message}"
                        orderBusy = false
                    }
                }
                MbActionBtn("SELL CE", isBuy = false, enabled = hasCe && !locked && !orderBusy,
                    modifier = Modifier.weight(1f)) {
                    scope.launch {
                        orderBusy = true; orderMsg = ""
                        val r = vm.placeOrder("S", "CE")
                        orderMsg = if (r.ok) "✓ SELL CE placed" else "✗ ${r.message}"
                        orderBusy = false
                    }
                }
            }

            // Row 2: BUY PE | SELL PE
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 10.dp, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MbActionBtn("BUY PE", isBuy = true, enabled = hasPe && !locked && !orderBusy,
                    modifier = Modifier.weight(1f)) {
                    scope.launch {
                        orderBusy = true; orderMsg = ""
                        val r = vm.placeOrder("B", "PE")
                        orderMsg = if (r.ok) "✓ BUY PE placed" else "✗ ${r.message}"
                        orderBusy = false
                    }
                }
                MbActionBtn("SELL PE", isBuy = false, enabled = hasPe && !locked && !orderBusy,
                    modifier = Modifier.weight(1f)) {
                    scope.launch {
                        orderBusy = true; orderMsg = ""
                        val r = vm.placeOrder("S", "PE")
                        orderMsg = if (r.ok) "✓ SELL PE placed" else "✗ ${r.message}"
                        orderBusy = false
                    }
                }
            }

            HorizontalDivider(color = Border, thickness = 1.dp)

            // ── Close All + Day P&L (bonus strip — useful for quick position exit)
            val openPos = st.positions.filter { it.netQty != 0 }
            Row(
                Modifier.fillMaxWidth().background(Bg3)
                    .clickable { scope.launch { vm.closeAll() } }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("✕", color = if (openPos.isNotEmpty()) Red else TxtMuted, fontSize = 12.sp)
                    Text("CLOSE ALL (${openPos.size})",
                        color = if (openPos.isNotEmpty()) Red else TxtMuted,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
                Text("Day P&L  ${if (pnl >= 0) "+" else ""}₹${"%.0f".format(pnl)}",
                    color = when { pnl > 0 -> Green; pnl < 0 -> Red; else -> TxtMuted },
                    fontSize = 11.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Chain row item — CE (green tint) | Strike | PE (red tint)
@Composable
private fun ChainRowItem(
    row: ChainRow,
    selected: Boolean,
    ltps: Map<String, Double>,
    onClick: () -> Unit,
) {
    val ceLtp = ltps[row.ceTs] ?: 0.0
    val peLtp = ltps[row.peTs] ?: 0.0
    val bg = when {
        selected -> AmberDim
        row.isAtm -> Color(0xFF0D1117)
        else -> Color.Transparent
    }
    Row(
        Modifier.fillMaxWidth().background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // CE (green tint bg)
        Box(
            Modifier.weight(2f).clip(RoundedCornerShape(4.dp))
                .background(GreenDim).padding(vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (ceLtp > 0) "%.2f".format(ceLtp) else "—",
                color = if (ceLtp > 0) Green else TxtMuted,
                fontSize = 13.sp, fontFamily = Mono, fontWeight = FontWeight.Bold,
            )
        }

        // Strike
        Column(Modifier.weight(1.4f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (row.isAtm) Text("ATM", color = Blue, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            Text("%.0f".format(row.strike),
                color = if (row.isAtm) Amber else TxtSec,
                fontSize = if (row.isAtm) 13.sp else 12.sp,
                fontFamily = Mono, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center)
        }

        // PE (red tint bg)
        Box(
            Modifier.weight(2f).clip(RoundedCornerShape(4.dp))
                .background(RedDim).padding(vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (peLtp > 0) "%.2f".format(peLtp) else "—",
                color = if (peLtp > 0) Red else TxtMuted,
                fontSize = 13.sp, fontFamily = Mono, fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── Mobile action button — matches Go's .mb-action-btn style
@Composable
private fun MbActionBtn(
    label: String,
    isBuy: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isBuy) BuyGradient else SellGradient)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 15.sp, fontFamily = Mono, fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
        if (!enabled) Box(Modifier.matchParentSize().clip(RoundedCornerShape(12.dp))
            .background(Color(0x82000000)))
    }
}
