package com.akatsuki.trading.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson

private const val PREFS_FILE = "akatsuki_secure"
private const val KEY_CREDENTIALS = "credentials"
private const val KEY_SESSION = "session"

private val gson = Gson()

private fun getPrefs(ctx: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(ctx)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        ctx, PREFS_FILE, masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}

fun saveCredentials(ctx: Context, creds: KotakCredentials) {
    getPrefs(ctx).edit().putString(KEY_CREDENTIALS, gson.toJson(creds)).apply()
}

fun loadCredentials(ctx: Context): KotakCredentials? = try {
    val raw = getPrefs(ctx).getString(KEY_CREDENTIALS, null) ?: return null
    gson.fromJson(raw, KotakCredentials::class.java)
} catch (_: Exception) { null }

fun saveSession(ctx: Context, session: KotakSession) {
    getPrefs(ctx).edit().putString(KEY_SESSION, gson.toJson(session)).apply()
}

fun loadSession(ctx: Context): KotakSession? = try {
    val raw = getPrefs(ctx).getString(KEY_SESSION, null) ?: return null
    val s = gson.fromJson(raw, KotakSession::class.java)
    val today = java.time.LocalDate.now().toString()
    if (s.loginDate != today || s.sessionToken.isEmpty()) null else s
} catch (_: Exception) { null }

fun clearSession(ctx: Context) {
    getPrefs(ctx).edit().remove(KEY_SESSION).apply()
}

fun clearAll(ctx: Context) {
    getPrefs(ctx).edit().clear().apply()
}
