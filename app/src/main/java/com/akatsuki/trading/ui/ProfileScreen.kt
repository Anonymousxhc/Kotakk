package com.akatsuki.trading.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akatsuki.trading.ui.theme.*
import com.akatsuki.trading.viewmodel.AppUiState
import com.akatsuki.trading.viewmodel.AppViewModel

@Composable
fun ProfileScreen(vm: AppViewModel, st: AppUiState) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout?", color = Txt) },
            text = { Text("Session will be cleared. You'll need to TOTP login again tomorrow.", color = TxtSec) },
            confirmButton = {
                TextButton(onClick = { vm.logout(); showLogoutDialog = false }) {
                    Text("LOGOUT", color = Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = TxtSec)
                }
            },
            containerColor = Bg2,
        )
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().background(Bg2).padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text("PROFILE", color = Txt, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        HorizontalDivider(color = Border)

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // User avatar + info
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Bg2)
                    .border(1.dp, Border, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(AmberDim)
                        .border(2.dp, Amber, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    val initials = st.greetingName.take(2).uppercase().ifEmpty { "A" }
                    Text(initials, color = Amber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(st.greetingName.ifEmpty { "User" }, color = Txt, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(st.credentials?.ucc ?: "—", color = TxtSec, fontSize = 12.sp, fontFamily = Mono)
                    Text(st.credentials?.mobileNumber ?: "—", color = TxtMuted, fontSize = 11.sp)
                }
            }

            // Session info
            val session = st.session
            if (session != null) {
                InfoRow("Session Date", session.loginDate)
                InfoRow("Data Center", session.dataCenter.ifEmpty { "—" })
                InfoRow("Server", session.baseURL.removePrefix("https://"))
            }

            // Stats
            HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 4.dp))
            val pnl = st.dayPnl
            InfoRow("Day P&L", "${if (pnl >= 0) "+" else ""}₹${"%.2f".format(Math.abs(pnl))}",
                if (pnl >= 0) Green else Red)
            InfoRow("Open Positions", "${st.positions.count { it.netQty != 0 }}")
            InfoRow("Total Orders", "${st.orders.size}")

            // Logout
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedDim),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Red.copy(alpha = 0.4f)),
                ),
            ) {
                Text("LOGOUT", color = Red, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = TxtSec) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Bg2)
            .border(1.dp, Border, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TxtMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(value, color = valueColor, fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
    }
}
