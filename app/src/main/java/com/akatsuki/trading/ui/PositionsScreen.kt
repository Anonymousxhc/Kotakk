package com.akatsuki.trading.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akatsuki.trading.data.Position
import com.akatsuki.trading.ui.theme.*
import com.akatsuki.trading.viewmodel.AppUiState
import com.akatsuki.trading.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun PositionsScreen(vm: AppViewModel, st: AppUiState) {
    val scope = rememberCoroutineScope()
    val openPositions = st.positions.filter { it.netQty != 0 }
    val totalPnl = st.dayPnl

    Column(Modifier.fillMaxSize().background(Bg)) {
        // Header
        Row(
            Modifier.fillMaxWidth().background(Bg2).padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("POSITIONS", color = Txt, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(BlueDim).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("${openPositions.size}", color = Blue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val pnlColor = if (totalPnl >= 0) Green else Red
                Text(
                    "${if (totalPnl >= 0) "+" else ""}₹${"%.0f".format(Math.abs(totalPnl))}",
                    color = pnlColor, fontSize = 14.sp, fontFamily = Mono, fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = { vm.refreshPositions() }, modifier = Modifier.size(28.dp)) {
                    Text("↻", color = TxtSec, fontSize = 16.sp)
                }
            }
        }
        HorizontalDivider(color = Border)

        if (openPositions.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("○", fontSize = 32.sp, color = TxtMuted)
                    Text("No open positions", color = TxtSec, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(openPositions) { pos ->
                    PositionCard(pos, st.liveLtps)
                    HorizontalDivider(color = Border, thickness = 0.5.dp)
                }
            }
        }

        // Close All
        if (openPositions.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().padding(12.dp)) {
                Button(
                    onClick = { scope.launch { vm.closeAll() } },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("✕  CLOSE ALL POSITIONS", color = Txt,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun PositionCard(pos: Position, ltps: Map<String, Double>) {
    val ltp = ltps[pos.ts] ?: pos.ltp
    val pnl = if (pos.netQty != 0) ltp * pos.netQty - (pos.buyAmt - pos.sellAmt) else pos.sellAmt - pos.buyAmt
    val pnlColor = if (pnl >= 0) Green else Red
    val sideColor = if (pos.netQty > 0) Green else Red
    val sideLabel = if (pos.netQty > 0) "LONG" else "SHORT"

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.clip(RoundedCornerShape(4.dp))
                    .background(if (pos.netQty > 0) GreenDim else RedDim)
                    .padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text(sideLabel, color = sideColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                Text(pos.ts, color = Txt, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LabelVal("QTY", "${Math.abs(pos.netQty)}")
                LabelVal("LTP", if (ltp > 0) "%.2f".format(ltp) else "—")
                LabelVal("PROD", pos.prod)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${if (pnl >= 0) "+" else ""}₹${"%.0f".format(Math.abs(pnl))}",
                color = pnlColor, fontSize = 14.sp, fontFamily = Mono, fontWeight = FontWeight.Bold,
            )
            val avg = if (pos.netQty != 0) (pos.buyAmt - pos.sellAmt) / Math.abs(pos.netQty) else 0.0
            if (avg > 0) Text("avg %.2f".format(avg), color = TxtSec, fontSize = 10.sp, fontFamily = Mono)
        }
    }
}

@Composable
private fun LabelVal(label: String, value: String) {
    Column {
        Text(label, color = TxtMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text(value, color = TxtSec, fontSize = 11.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
    }
}
