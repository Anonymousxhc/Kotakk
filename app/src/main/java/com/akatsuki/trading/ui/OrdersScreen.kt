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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akatsuki.trading.data.Order
import com.akatsuki.trading.ui.theme.*
import com.akatsuki.trading.viewmodel.AppUiState
import com.akatsuki.trading.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun OrdersScreen(vm: AppViewModel, st: AppUiState) {
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().background(Bg2).padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ORDERS", color = Txt, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(BlueDim).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("${st.orders.size}", color = Blue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            IconButton(onClick = { vm.refreshOrders() }, modifier = Modifier.size(28.dp)) {
                Text("↻", color = TxtSec, fontSize = 16.sp)
            }
        }
        HorizontalDivider(color = Border)

        if (st.orders.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("○", fontSize = 32.sp, color = TxtMuted)
                    Text("No orders today", color = TxtSec, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn {
                items(st.orders) { order ->
                    OrderCard(order) {
                        if (order.ordSt.uppercase() in listOf("OPN", "OPEN", "PENDING")) {
                            scope.launch { vm.cancelOrder(order.nOrdNo) }
                        }
                    }
                    HorizontalDivider(color = Border, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun OrderCard(order: Order, onCancel: () -> Unit) {
    val status = order.ordSt.uppercase()
    val isBuy = order.tt.uppercase() == "B"
    val isOpen = status in listOf("OPN", "OPEN", "PENDING", "TRIGGER PENDING")
    val statusColor = when {
        status in listOf("COMPLETE", "COMPLETED", "FIL") -> Green
        status in listOf("REJECTED", "REJ") -> Red
        isOpen -> Amber
        else -> TxtSec
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.clip(RoundedCornerShape(4.dp))
                    .background(if (isBuy) GreenDim else RedDim)
                    .padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text(if (isBuy) "BUY" else "SELL", color = if (isBuy) Green else Red,
                        fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                Text(order.ts, color = Txt, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LabelVal2("QTY", order.qty)
                LabelVal2("TYPE", order.pt)
                if (order.pr.isNotEmpty() && order.pr != "0") LabelVal2("PR", order.pr)
            }
            if (order.time.isNotEmpty()) {
                Text(order.time, color = TxtMuted, fontSize = 9.sp, fontFamily = Mono, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.clip(RoundedCornerShape(6.dp))
                .background(statusColor.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(status, color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            if (isOpen) {
                Box(Modifier.clip(RoundedCornerShape(6.dp))
                    .background(RedDim)
                    .border(1.dp, Red.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)) {
                    TextButton(onClick = onCancel, contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(IntrinsicSize.Min)) {
                        Text("CANCEL", color = Red, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelVal2(label: String, value: String) {
    Column {
        Text(label, color = TxtMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text(value, color = TxtSec, fontSize = 11.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
    }
}
