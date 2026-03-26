package com.akatsuki.trading.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

private val dlClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .build()

private val MONTH_MAP = mapOf(
    "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6,
    "JUL" to 7, "AUG" to 8, "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12,
)
private val MONTH_NAMES = listOf("JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC")

private fun todayStr() = LocalDate.now().toString()

private fun formatExpiryLabel(d: LocalDate): String {
    val day = d.dayOfMonth.toString().padStart(2, '0')
    val mon = MONTH_NAMES[d.monthValue - 1]
    return "$day-$mon-${d.year}"
}

// Index → Expiry → Strike → OptType → OptionInfo
private val optDB: MutableMap<String, MutableMap<String, MutableMap<Double, MutableMap<String, OptionInfo>>>> = mutableMapOf()
// Index → sorted list of future expiries
private val expiryLists: MutableMap<String, MutableList<Pair<String, LocalDate>>> = mutableMapOf()

fun isInstrumentsLoaded(index: String) = optDB.containsKey(index.uppercase())

fun getExpiries(index: String): List<ExpiryInfo> {
    val key = index.uppercase()
    val list = expiryLists[key] ?: return emptyList()
    val nearest = list.firstOrNull()?.first ?: ""
    return list.map { (label, _) -> ExpiryInfo(label, label == nearest) }
}

fun buildChain(index: String, spot: Double, numStrikes: Int, expiryLabel: String): List<ChainRow>? {
    val key = index.uppercase()
    val expiryDb = optDB[key]?.get(expiryLabel) ?: return null
    val stepMap = mapOf("NIFTY" to 50.0, "BANKNIFTY" to 100.0, "SENSEX" to 100.0, "FINNIFTY" to 50.0)
    val step = stepMap[key] ?: 50.0
    val strikes = expiryDb.keys.sorted()
    if (strikes.isEmpty()) return null

    var atm = strikes[0]; var minDiff = Math.abs(strikes[0] - spot)
    for (s in strikes) { val d = Math.abs(s - spot); if (d < minDiff) { minDiff = d; atm = s } }
    val atmIdx = strikes.indexOf(atm)
    val start = maxOf(0, atmIdx - numStrikes)
    val end = minOf(strikes.size, atmIdx + numStrikes + 1)

    return strikes.subList(start, end).map { strike ->
        val sdata = expiryDb[strike] ?: emptyMap()
        val ce = sdata["CE"]
        val pe = sdata["PE"]
        ChainRow(
            strike = strike, isAtm = Math.abs(strike - atm) < step / 2,
            ceTs = ce?.ts ?: "", ceSym = ce?.symbol ?: "", ceSeg = ce?.seg ?: "", ceLot = ce?.lot ?: 1,
            peTs = pe?.ts ?: "", peSym = pe?.symbol ?: "", peSeg = pe?.seg ?: "", peLot = pe?.lot ?: 1,
        )
    }
}

suspend fun downloadAndParseInstruments(
    ctx: Context, session: KotakSession, onProgress: (String) -> Unit
) = withContext(Dispatchers.IO) {
    val targets = listOf(
        "nse_fo" to listOf("NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY"),
        "bse_fo" to listOf("SENSEX", "BANKEX"),
    )
    val cacheDir = ctx.filesDir

    for ((key, indices) in targets) {
        val cacheFile = File(cacheDir, "${key}_${todayStr()}.csv")

        // Clean old cache files
        cacheDir.listFiles { f -> f.name.startsWith("${key}_") && f.name != cacheFile.name }
            ?.forEach { it.delete() }

        val text: String
        if (cacheFile.exists() && cacheFile.length() > 1024) {
            onProgress("Loading $key from cache...")
            text = cacheFile.readText()
        } else {
            onProgress("Fetching $key paths...")
            val paths = kotakFetchScripPaths(session)
            val url = paths.find { it.lowercase().contains(key) }
            if (url == null) { onProgress("$key not found"); continue }

            onProgress("Downloading $key...")
            val req = Request.Builder().url(url).get().build()
            text = dlClient.newCall(req).execute().use { resp ->
                resp.body?.string() ?: ""
            }
            if (text.length > 1024) cacheFile.writeText(text)
        }

        onProgress("Parsing $key...")
        parseCSV(text, indices)
    }
    onProgress("Instruments ready")
}

private fun parseCSV(text: String, indices: List<String>) {
    val lines = text.split("\n")
    if (lines.size < 2) return

    val header = lines[0].replace(";", "").split(",")
    val colIdx = header.mapIndexed { i, col -> col.trim() to i }.toMap()

    fun row(cells: List<String>, name: String): String {
        val i = colIdx[name] ?: return ""
        return if (i < cells.size) cells[i].trim() else ""
    }

    val today = LocalDate.now()

    for (line in lines.drop(1)) {
        if (line.isBlank()) continue
        val cells = line.split(",")

        val symName = row(cells, "pSymbolName").uppercase()
        if (symName !in indices) continue

        val optType = row(cells, "pOptionType").trim()
        if (optType != "CE" && optType != "PE") continue

        if (symName in listOf("NIFTY", "BANKNIFTY", "FINNIFTY")) {
            val instType = row(cells, "pInstType").uppercase().trim()
            if (instType != "OPTIDX") continue
        }

        val ts = row(cells, "pTrdSymbol").uppercase()
        val strikePriceRaw = row(cells, "dStrikePrice").toDoubleOrNull() ?: continue
        if (strikePriceRaw <= 0) continue
        val strike = strikePriceRaw / 100.0

        val scripRef = row(cells, "pScripRefKey").uppercase()
        if (!scripRef.startsWith(symName) || scripRef.length <= symName.length + 6) continue

        val datePart = scripRef.substring(symName.length, symName.length + 7)
        val day = datePart.slice(0..1).toIntOrNull() ?: continue
        val month = MONTH_MAP[datePart.slice(2..4)] ?: continue
        val yr = 2000 + (datePart.slice(5..6).toIntOrNull() ?: continue)
        if (yr < 2025 || yr > 2030) continue

        val expDate = try { LocalDate.of(yr, month, day) } catch (_: Exception) { continue }
        if (expDate.isBefore(today)) continue

        val label = formatExpiryLabel(expDate)
        val lot = row(cells, "lLotSize").toIntOrNull() ?: 1
        val symbol = row(cells, "pSymbol")
        val seg = row(cells, "pExchSeg")

        val info = OptionInfo(ts = ts, symbol = symbol, seg = seg, lot = lot)

        optDB.getOrPut(symName) { mutableMapOf() }
            .getOrPut(label) { mutableMapOf() }
            .getOrPut(strike) { mutableMapOf() }[optType] = info

        val list = expiryLists.getOrPut(symName) { mutableListOf() }
        if (list.none { it.first == label }) list.add(label to expDate)
    }

    for ((key, list) in expiryLists) {
        list.sortBy { it.second }
        expiryLists[key] = list
    }
}
