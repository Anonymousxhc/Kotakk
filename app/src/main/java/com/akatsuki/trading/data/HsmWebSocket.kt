package com.akatsuki.trading.data

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "HsmWS"
private const val HSM_URL = "wss://mlhsm.kotaksecurities.com"
private const val HEARTBEAT_MS = 30_000L
private const val RECONNECT_BASE_MS = 5_000L
private const val RECONNECT_MAX_MS = 60_000L

class HsmWebSocket(
    private val session: KotakSession,
    private val onLtp: (token: String, ltp: Double) -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var ws: WebSocket? = null
    @Volatile private var connected = false
    @Volatile private var destroyed = false
    private var retries = 0

    private var heartbeatJob: Job? = null
    private var pendingScrips: String = ""

    fun connect() {
        if (destroyed) return
        val req = Request.Builder().url(HSM_URL).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                connected = true
                retries = 0
                val connectMsg = buildString {
                    append("{")
                    append("\"type\":\"cn\",")
                    append("\"Authorization\":\"${session.sessionToken}\",")
                    append("\"Sid\":\"${session.sessionSid}\"")
                    if (session.dataCenter.isNotEmpty()) append(",\"dataCenter\":\"${session.dataCenter}\"")
                    append("}")
                }
                ws.send(connectMsg)
                Log.i(TAG, "Connected to HSM (dc=${session.dataCenter})")
                startHeartbeat(ws)
                onConnected()
                // Re-subscribe if we had scrips before (e.g. on reconnect)
                val scrips = pendingScrips
                if (scrips.isNotEmpty()) {
                    ws.send(buildSubscribeMsg(scrips))
                    Log.i(TAG, "Re-subscribed: ${scrips.take(120)}")
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val j = JSONObject(text)
                    val tok = j.optString("tk").takeIf { it.isNotEmpty() }
                        ?: j.optString("TK").takeIf { it.isNotEmpty() }
                        ?: return
                    val ltpRaw = j.opt("ltp") ?: j.opt("LTP") ?: return
                    val ltp = when (ltpRaw) {
                        is Number -> ltpRaw.toDouble()
                        is String -> ltpRaw.toDoubleOrNull() ?: return
                        else -> return
                    }
                    if (ltp > 0) onLtp(tok, ltp)
                } catch (_: Exception) {}
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                // Some frames are binary — decode as UTF-8 and try JSON
                onMessage(ws, bytes.utf8())
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WS failure: ${t.message}")
                handleClose()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WS closed: $code $reason")
                handleClose()
            }
        })
    }

    fun subscribe(scrips: String) {
        pendingScrips = scrips
        val localWs = ws
        if (connected && localWs != null) {
            localWs.send(buildSubscribeMsg(scrips))
            Log.i(TAG, "Subscribed: ${scrips.take(120)}")
        }
    }

    fun disconnect() {
        destroyed = true
        heartbeatJob?.cancel()
        ws?.close(1000, "logout")
        ws = null
        connected = false
        scope.cancel()
    }

    private fun handleClose() {
        heartbeatJob?.cancel()
        connected = false
        if (!destroyed) {
            onDisconnected()
            scheduleReconnect()
        }
    }

    private fun startHeartbeat(activeWs: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            val hb = "{\"type\":\"ti\",\"scrips\":\"\"}"
            while (isActive && !destroyed) {
                delay(HEARTBEAT_MS)
                if (!destroyed && connected) {
                    activeWs.send(hb)
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (destroyed) return
        retries++
        if (retries > 10) {
            Log.w(TAG, "HSM: giving up after 10 retries")
            return
        }
        val wait = minOf(RECONNECT_BASE_MS * retries, RECONNECT_MAX_MS)
        Log.i(TAG, "HSM: reconnecting in ${wait}ms (retry $retries)")
        scope.launch {
            delay(wait)
            if (!destroyed) connect()
        }
    }

    private fun buildSubscribeMsg(scrips: String) =
        "{\"type\":\"mws\",\"scrips\":\"$scrips\",\"channelnum\":1}"
}
