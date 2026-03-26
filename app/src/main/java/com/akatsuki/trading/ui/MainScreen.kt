package com.akatsuki.trading.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private enum class Tab(val icon: String, val label: String) {
    SCALPER("⚡", "Scalper"),
    POSITIONS("◎", "Positions"),
    ORDERS("≡", "Orders"),
    FUNDS("₹", "Funds"),
    PROFILE("●", "Profile"),
}

@Composable
fun MainScreen(vm: AppViewModel, st: AppUiState) {
    var activeTab by remember { mutableStateOf(Tab.SCALPER) }

    Scaffold(
        containerColor = Bg,
        topBar = { AkatsukiHeader(st) },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0E0E0E),
                contentColor = TxtSec,
                tonalElevation = 0.dp,
                modifier = Modifier.height(60.dp),
            ) {
                Tab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { activeTab = tab },
                        icon = {
                            Text(tab.icon, fontSize = 18.sp,
                                color = if (selected) Txt else TxtMuted)
                        },
                        label = {
                            Text(tab.label, fontSize = 9.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) Txt else TxtMuted)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIndicatorColor = Color(0xFF1E1E1E),
                            indicatorColor = Color(0xFF1E1E1E),
                        ),
                    )
                }
            }
        },
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            when (activeTab) {
                Tab.SCALPER   -> ScalperScreen(vm, st)
                Tab.POSITIONS -> PositionsScreen(vm, st)
                Tab.ORDERS    -> OrdersScreen(vm, st)
                Tab.FUNDS     -> FundsScreen(vm, st)
                Tab.PROFILE   -> ProfileScreen(vm, st)
            }
        }
    }
}

// ── Header — matches Go's hdr: [⚡ AKATSUKI] ... [clock] [user] [● Live/Offline] ──
@Composable
private fun AkatsukiHeader(st: AppUiState) {
    // Live clock — ticks every second matching Go's clockIv
    var clock by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")
        while (true) {
            clock = LocalTime.now().format(fmt)
            delay(1000)
        }
    }

    Row(
        Modifier.fillMaxWidth()
            .background(Color(0xFF111111))
            .border(width = 1.dp, color = Border,
                shape = RoundedCornerShape(0.dp))
            .padding(horizontal = 14.dp)
            .height(44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: ⚡ AKATSUKI
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⚡", fontSize = 18.sp)
            Text("AKATSUKI",
                color = Blue, fontSize = 14.sp,
                fontFamily = Mono, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp)
        }

        // Right: clock + greeting + status pill
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (clock.isNotEmpty()) {
                Text(clock, color = TxtMuted, fontSize = 11.sp, fontFamily = Mono)
            }
            if (st.greetingName.isNotEmpty()) {
                Text(st.greetingName.take(12), color = TxtSec,
                    fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            // HSM status pill: Live (green) / Offline (red)
            Row(
                Modifier.clip(RoundedCornerShape(99.dp))
                    .background(if (st.hsmConnected) GreenDim else RedDim)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier.size(6.dp).clip(RoundedCornerShape(50))
                        .background(if (st.hsmConnected) Green else Red)
                )
                Text(
                    if (st.hsmConnected) "Live" else "Offline",
                    color = if (st.hsmConnected) Green else Red,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
