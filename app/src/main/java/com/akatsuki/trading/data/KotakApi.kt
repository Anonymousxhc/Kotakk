package com.akatsuki.trading.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val LOGIN_URL = "https://mis.kotaksecurities.com/login/1.0/tradeApiLogin"
private const val VALIDATE_URL = "https://mis.kotaksecurities.com/login/1.0/tradeApiValidate"

private val gson = Gson()
private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

private suspend fun doPost(url: String, headers: Map<String, String>, body: okhttp3.RequestBody): JsonObject =
    withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
            post(body)
        }.build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: "{}"
            try { gson.fromJson(text, JsonObject::class.java) } catch (_: Exception) { JsonObject() }
        }
    }

private suspend fun doGet(url: String, headers: Map<String, String>): JsonObject =
    withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
            get()
        }.build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: "{}"
            try { gson.fromJson(text, JsonObject::class.java) } catch (_: Exception) { JsonObject() }
        }
    }

private fun postHeaders(s: KotakSession) = mapOf(
    "accept" to "application/json",
    "Auth" to s.sessionToken,
    "Sid" to s.sessionSid,
    "neo-fin-key" to "neotradeapi",
    "Content-Type" to "application/x-www-form-urlencoded",
)

private fun getHeaders(s: KotakSession) = postHeaders(s) + mapOf("Content-Type" to "application/json")

private fun jstr(obj: JsonObject, key: String) = obj.get(key)?.asString ?: ""
private fun jnum(obj: JsonObject, key: String) = obj.get(key)?.asDouble ?: 0.0

suspend fun kotakLoginTOTP(
    accessToken: String, mobileNumber: String, ucc: String, totp: String
): LoginResult {
    val body = FormBody.Builder()
        .add("jData", gson.toJson(mapOf(
            "ucc" to ucc, "uid" to mobileNumber, "pwd" to totp,
            "token" to accessToken, "appId" to "", "source" to "WEB"
        )))
        .add("jKey", accessToken)
        .build()
    val headers = mapOf(
        "accept" to "application/json",
        "Authorization" to accessToken,
        "Content-Type" to "application/x-www-form-urlencoded",
    )
    return try {
        val obj = doPost(LOGIN_URL, headers, body)
        val stat = (obj.get("stat")?.asString ?: obj.get("status")?.asString ?: "").lowercase()
        if (stat == "ok" || stat == "success") {
            val data = obj.get("data")?.asJsonObject ?: obj
            LoginResult(
                status = "success",
                viewToken = jstr(data, "token").ifEmpty { jstr(obj, "token") },
                viewSid = jstr(data, "sid").ifEmpty { jstr(obj, "sid") },
            )
        } else {
            LoginResult(status = "error", message = obj.get("emsg")?.asString ?: obj.get("message")?.asString ?: "Login failed")
        }
    } catch (e: Exception) {
        LoginResult(status = "error", message = e.message ?: "Network error")
    }
}

suspend fun kotakValidateMpin(
    accessToken: String, mpin: String, viewToken: String, viewSid: String
): ValidateResult {
    val body = FormBody.Builder()
        .add("jData", gson.toJson(mapOf("mpin" to mpin)))
        .add("jKey", accessToken)
        .build()
    val headers = mapOf(
        "accept" to "application/json",
        "Authorization" to accessToken,
        "Auth" to viewToken,
        "Sid" to viewSid,
        "Content-Type" to "application/x-www-form-urlencoded",
    )
    return try {
        val obj = doPost(VALIDATE_URL, headers, body)
        val stat = (obj.get("stat")?.asString ?: obj.get("status")?.asString ?: "").lowercase()
        if (stat == "ok" || stat == "success") {
            val data = obj.get("data")?.asJsonObject ?: obj
            val dc = jstr(data, "dc").ifEmpty { jstr(obj, "dc") }
            val srvr = jstr(data, "srvr").ifEmpty { jstr(obj, "srvr") }
            val dcSuf = if (dc.isNotEmpty()) dc else ""
            val baseURL = "https://$srvr"
            ValidateResult(
                status = "success",
                sessionToken = jstr(data, "token").ifEmpty { jstr(obj, "token") },
                sessionSid = jstr(data, "sid").ifEmpty { jstr(obj, "sid") },
                baseURL = baseURL,
                dataCenter = dc,
                greetingName = jstr(data, "greetingName").ifEmpty { jstr(obj, "greetingName") },
            )
        } else {
            ValidateResult(status = "error", message = obj.get("emsg")?.asString ?: "MPIN failed")
        }
    } catch (e: Exception) {
        ValidateResult(status = "error", message = e.message ?: "Network error")
    }
}

suspend fun kotakGetSpot(s: KotakSession, index: String): Double {
    val symMap = mapOf(
        "NIFTY" to "Nifty 50", "BANKNIFTY" to "Nifty Bank",
        "SENSEX" to "SENSEX", "FINNIFTY" to "Nifty Fin Service",
        "MIDCPNIFTY" to "NIFTY MID SELECT", "BANKEX" to "BSE Bankex",
    )
    val sym = symMap[index.uppercase()] ?: "Nifty 50"
    return try {
        val url = "${s.baseURL}/market-data/1.0/spot?exchange=NSE&symbol=${sym.replace(" ", "%20")}"
        val obj = doGet(url, getHeaders(s))
        val data = obj.get("data")?.asJsonObject ?: obj
        jnum(data, "ltp").takeIf { it > 0 } ?: jnum(obj, "ltp")
    } catch (_: Exception) { 0.0 }
}

suspend fun kotakGetPositions(s: KotakSession): JsonObject {
    return try {
        val url = "${s.baseURL}/portfolio/1.0/portfolio/positions"
        val body = FormBody.Builder().build()
        doPost(url, postHeaders(s), body)
    } catch (_: Exception) { JsonObject() }
}

suspend fun kotakGetOrderbook(s: KotakSession): JsonObject {
    return try {
        val url = "${s.baseURL}/trade/1.0/trade/order/execution"
        val body = FormBody.Builder().build()
        doPost(url, postHeaders(s), body)
    } catch (_: Exception) { JsonObject() }
}

suspend fun kotakGetLimits(s: KotakSession): JsonObject {
    return try {
        val url = "${s.baseURL}/portfolio/1.0/portfolio/limits"
        val body = FormBody.Builder().add("jData", gson.toJson(mapOf("seg" to "ALL", "exch" to "ALL", "prod" to "ALL"))).build()
        doPost(url, postHeaders(s), body)
    } catch (_: Exception) { JsonObject() }
}

suspend fun kotakFetchScripPaths(s: KotakSession): List<String> {
    return try {
        val url = "${s.baseURL}/scripmaster/1.0/scripmaster/file/path"
        val obj = doGet(url, getHeaders(s))
        val arr = obj.get("data")?.asJsonArray ?: return emptyList()
        arr.map { it.asString }
    } catch (_: Exception) { emptyList() }
}

suspend fun kotakPlaceOrder(
    s: KotakSession, seg: String, ts: String, side: String, qty: Int, prod: String = "MIS"
): PlaceOrderResult {
    return try {
        val url = "${s.baseURL}/trade/1.0/trade/order/regular"
        val jData = gson.toJson(mapOf(
            "am" to "NO", "dq" to "0", "es" to seg, "mp" to "0",
            "pc" to prod, "pf" to "N", "pr" to "0", "pt" to "MKT",
            "qt" to qty.toString(), "rt" to "DAY", "tp" to "0",
            "ts" to ts, "tt" to side,
        ))
        val body = FormBody.Builder().add("jData", jData).build()
        val obj = doPost(url, postHeaders(s), body)
        val stat = (obj.get("stat")?.asString ?: "").lowercase()
        if (stat == "ok") {
            PlaceOrderResult(ok = true, orderId = obj.get("nOrdNo")?.asString ?: "")
        } else {
            PlaceOrderResult(ok = false, message = obj.get("emsg")?.asString ?: obj.get("message")?.asString ?: "Order failed")
        }
    } catch (e: Exception) {
        PlaceOrderResult(ok = false, message = e.message ?: "Network error")
    }
}

suspend fun kotakCancelOrder(s: KotakSession, orderNo: String): Boolean {
    return try {
        val url = "${s.baseURL}/trade/1.0/trade/order/cancel"
        val body = FormBody.Builder().add("jData", gson.toJson(mapOf("on" to orderNo, "am" to "NO"))).build()
        val obj = doPost(url, postHeaders(s), body)
        (obj.get("stat")?.asString ?: "").lowercase() == "ok"
    } catch (_: Exception) { false }
}

suspend fun kotakGetLtp(s: KotakSession, seg: String, tok: String): Double {
    return try {
        val url = "${s.baseURL}/script-details/1.0/quotes/neosymbol/$seg|$tok/ltp"
        val obj = doGet(url, getHeaders(s))
        val arr = obj.get("data")?.asJsonArray ?: return 0.0
        arr.firstOrNull()?.asJsonObject?.get("ltp")?.asDouble ?: 0.0
    } catch (_: Exception) { 0.0 }
}

fun parsePositions(obj: JsonObject): List<Position> {
    if ((obj.get("stat")?.asString ?: "").lowercase() != "ok") return emptyList()
    val arr = obj.get("data")?.asJsonArray ?: return emptyList()
    return arr.mapNotNull { el ->
        try {
            val o = el.asJsonObject
            fun s(k: String) = o.get(k)?.asString ?: ""
            fun d(vararg keys: String) = keys.firstNotNullOfOrNull { o.get(it)?.asDouble.takeIf { v -> v != null && v != 0.0 } } ?: 0.0
            fun i(k: String) = o.get(k)?.asInt ?: 0
            val ts = s("trdSym").ifEmpty { s("ts") }
            if (ts.isEmpty()) return@mapNotNull null
            val bq = d("flBuyQty", "cfBuyQty", "buyQty").toInt()
            val sq = d("flSellQty", "cfSellQty", "sellQty").toInt()
            val netQty = o.get("netQty")?.asInt ?: (bq - sq)
            Position(
                ts = ts,
                seg = s("seg").ifEmpty { s("exSeg") }.ifEmpty { "nse_fo" },
                netQty = netQty,
                buyAmt = d("buyAmt", "cfBuyAmt"),
                sellAmt = d("sellAmt", "cfSellAmt"),
                ltp = d("lp", "ltp", "lastPrice"),
                prod = s("prod").ifEmpty { s("pc") }.ifEmpty { "MIS" },
                tok = s("tok").ifEmpty { s("token") },
            )
        } catch (_: Exception) { null }
    }
}

fun parseOrders(obj: JsonObject): List<Order> {
    if ((obj.get("stat")?.asString ?: "").lowercase() != "ok") return emptyList()
    val arr = obj.get("data")?.asJsonArray ?: return emptyList()
    return arr.mapNotNull { el ->
        try {
            val o = el.asJsonObject
            fun s(vararg keys: String) = keys.firstNotNullOfOrNull { o.get(it)?.asString?.takeIf { v -> v.isNotEmpty() } } ?: ""
            val no = s("nOrdNo")
            if (no.isEmpty()) return@mapNotNull null
            Order(
                nOrdNo = no,
                ts = s("trdSym", "ts"),
                ordSt = s("ordSt"),
                tt = s("tt"),
                qty = s("qty", "qt"),
                pr = s("pr"),
                pt = s("pt"),
                pc = s("pc"),
                time = s("ordDtTm", "time"),
            )
        } catch (_: Exception) { null }
    }
}

fun parseFunds(obj: JsonObject): FundsData? {
    if ((obj.get("stat")?.asString ?: "").lowercase() != "ok") return null
    val data = obj.get("data")?.asJsonObject ?: return null
    fun d(vararg keys: String) = keys.firstNotNullOfOrNull { data.get(it)?.asDouble.takeIf { v -> v != null } } ?: 0.0
    return FundsData(
        availableCash = d("Net", "net", "availablecash"),
        utilised = d("utilisedAmount", "Utilised", "utilised"),
        totalMargin = d("grossAvailableMargin", "Total", "total"),
    )
}
