package com.akatsuki.trading.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akatsuki.trading.data.AuthStep
import com.akatsuki.trading.data.KotakCredentials
import com.akatsuki.trading.ui.theme.*
import com.akatsuki.trading.viewmodel.AppViewModel

@Composable
fun LoginScreen(vm: AppViewModel, step: AuthStep, error: String, isLoading: Boolean) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Logo
            Text("⚡", fontSize = 36.sp)
            Text(
                "AKATSUKI",
                fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Txt,
                letterSpacing = 6.sp, modifier = Modifier.padding(top = 6.dp),
            )
            Text("Options Scalping Terminal", color = TxtSec, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 40.dp))

            when (step) {
                AuthStep.RESTORE -> {
                    CircularProgressIndicator(color = Amber, modifier = Modifier.size(32.dp))
                    Text("Restoring session...", color = TxtSec, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
                }
                AuthStep.SETUP -> SetupForm(vm, error, isLoading)
                AuthStep.TOTP -> TotpForm(vm, error, isLoading)
                AuthStep.MPIN -> MpinConfirm(vm, error, isLoading)
                AuthStep.TERMINAL -> {}
            }
        }
    }
}

@Composable
private fun SetupForm(vm: AppViewModel, error: String, isLoading: Boolean) {
    var accessToken by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var ucc by remember { mutableStateOf("") }
    var mpin by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Bg2),
        shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Kotak Neo Setup", color = Txt, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Credentials stored securely on-device", color = TxtSec, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))

            SecureField("Access Token", accessToken, { accessToken = it }, secure = true, mono = true)
            SecureField("Mobile Number", mobile, { mobile = it }, keyType = KeyboardType.Phone)
            SecureField("UCC", ucc, { ucc = it })
            SecureField("MPIN", mpin, { mpin = it }, secure = true, keyType = KeyboardType.NumberPassword)

            if (error.isNotEmpty()) Text(error, color = Red, fontSize = 12.sp)

            Button(
                onClick = {
                    if (accessToken.isNotBlank() && mobile.isNotBlank() && ucc.isNotBlank() && mpin.isNotBlank()) {
                        vm.saveCreds(KotakCredentials(accessToken.trim(), mobile.trim(), ucc.trim(), mpin.trim()))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
            ) { Text("Save & Continue", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun TotpForm(vm: AppViewModel, error: String, isLoading: Boolean) {
    var totp by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }

    LaunchedEffect(Unit) { fr.requestFocus() }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Bg2),
        shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Enter TOTP", color = Txt, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("6-digit code from your authenticator app", color = TxtSec, fontSize = 12.sp)

            OutlinedTextField(
                value = totp,
                onValueChange = { if (it.length <= 6) totp = it },
                modifier = Modifier.fillMaxWidth().focusRequester(fr),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = Mono, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, letterSpacing = 10.sp, color = Txt,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (totp.length == 6) vm.doTotpLogin(totp) }),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Amber, unfocusedBorderColor = Border2,
                    focusedContainerColor = Bg3, unfocusedContainerColor = Bg3,
                ),
            )

            if (error.isNotEmpty()) Text(error, color = Red, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())

            Button(
                onClick = { if (totp.length == 6) vm.doTotpLogin(totp) },
                enabled = totp.length == 6 && !isLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                else Text("Verify TOTP", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MpinConfirm(vm: AppViewModel, error: String, isLoading: Boolean) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Bg2),
        shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Confirm Identity", color = Txt, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Verifying MPIN with Kotak Neo", color = TxtSec, fontSize = 12.sp)

            if (error.isNotEmpty()) Text(error, color = Red, fontSize = 12.sp)

            Button(
                onClick = { vm.doMpinValidate() },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                else Text("Confirm MPIN", fontWeight = FontWeight.Bold)
            }

            TextButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) {
                Text("← Back to TOTP", color = TxtSec, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SecureField(
    label: String, value: String, onChange: (String) -> Unit,
    secure: Boolean = false, mono: Boolean = false,
    keyType: KeyboardType = KeyboardType.Text,
) {
    var visible by remember { mutableStateOf(!secure) }
    Column {
        Text(label.uppercase(), color = TxtMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(
                fontFamily = if (mono) Mono else null, fontSize = if (mono) 11.sp else 14.sp, color = Txt,
            ),
            singleLine = true,
            visualTransformation = if (secure && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyType),
            trailingIcon = if (secure) ({
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null, tint = TxtSec)
                }
            }) else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Amber, unfocusedBorderColor = Border,
                focusedContainerColor = Bg3, unfocusedContainerColor = Bg3,
            ),
        )
    }
}
