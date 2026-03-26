package com.akatsuki.trading

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akatsuki.trading.data.AuthStep
import com.akatsuki.trading.ui.LoginScreen
import com.akatsuki.trading.ui.MainScreen
import com.akatsuki.trading.ui.theme.AkatsukiTheme
import com.akatsuki.trading.ui.theme.Bg
import com.akatsuki.trading.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AkatsukiTheme {
                val vm: AppViewModel = viewModel()
                val st by vm.ui.collectAsState()

                val modifier = Modifier
                    .fillMaxSize()
                    .background(Bg)
                    .statusBarsPadding()
                    .navigationBarsPadding()

                if (st.authStep == AuthStep.TERMINAL) {
                    MainScreen(vm, st)
                } else {
                    LoginScreen(
                        vm = vm,
                        step = st.authStep,
                        error = st.loginError,
                        isLoading = st.loadingMsg.isNotEmpty(),
                    )
                }
            }
        }
    }
}
