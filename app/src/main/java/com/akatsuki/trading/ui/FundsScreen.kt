package com.akatsuki.trading.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akatsuki.trading.ui.theme.*
import com.akatsuki.trading.viewmodel.AppUiState
import com.akatsuki.trading.viewmodel.AppViewModel

@Composable
fun FundsScreen(vm: AppViewModel, st: AppUiState) {
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().background(Bg2).padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("FUNDS", color = Txt, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            IconButton(onClick = { vm.refreshFunds() }, modifier = Modifier.size(28.dp)) {
                Text("↻", color = TxtSec, fontSize = 16.sp)
            }
        }
        HorizontalDivider(color = Border)

        if (st.funds == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Amber, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    Text("Loading funds...", color = TxtSec, fontSize = 13.sp)
                }
            }
        } else {
            val funds = st.funds
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FundCard("Available Cash", funds.availableCash, Green)
                FundCard("Utilised Margin", funds.utilised, Red)
                FundCard("Total Margin", funds.totalMargin, Blue)

                // PnL card
                HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 4.dp))
                val pnl = st.dayPnl
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (pnl >= 0) GreenDim else RedDim)
                        .border(1.dp, if (pnl >= 0) Green.copy(0.3f) else Red.copy(0.3f), RoundedCornerShape(14.dp))
                        .padding(20.dp),
                ) {
                    Column {
                        Text("Day P&L", color = TxtSec, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        Text(
                            "${if (pnl >= 0) "+" else ""}₹${"%.2f".format(Math.abs(pnl))}",
                            color = if (pnl >= 0) Green else Red,
                            fontSize = 28.sp, fontFamily = Mono, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FundCard(label: String, value: Double, color: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bg2)
            .border(1.dp, Border, RoundedCornerShape(12.dp)).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TxtSec, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text("₹${"%.2f".format(value)}", color = color, fontSize = 16.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
    }
}
