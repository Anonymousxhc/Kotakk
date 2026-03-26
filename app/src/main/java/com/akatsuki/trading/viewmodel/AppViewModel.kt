package com.akatsuki.trading.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akatsuki.trading.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppUiState(
    val authStep: AuthStep = AuthStep.RESTORE,
    val loginError: String = "",
    val loadingMsg: String = "",

    val credentials: KotakCredentials? = null,
    val session: KotakSession? = null,
    val greetingName: String = "",

    val currentIndex: String = "NIFTY",
    val selectedExpiry: String = "",
    val expiries: List<ExpiryInfo> = emptyList(),
    val chain: List<ChainRow> = emptyList(),
    val spotPrices: Map<String, Double> = emptyMap(),
    val selectedRowIdx: Int = -1,
    val lots: Int = 1,
    val safetyLock: Boolean = false,
    val numStrikes: Int = 10,
    val instrumentStatus: InstrumentStatus = InstrumentStatus.IDLE,
    val instrumentMsg: String = "",
    val chainLoading: Boolean = false,

    val positions: List<Position> = emptyList(),
    val orders: List<Order> = emptyList(),
    val funds: FundsData? = null,
    val liveLtps: Map<String, Double> = emptyMap(),
    val dayPnl: Double = 0.0,
    val hsmConnected: Boolean = false,
)

private val INDICES = listOf("NIFTY", "BANKNIFTY", "SENSEX", "FINNIFTY", "MIDCPNIFTY")
private val NUM_STRIKES_OPTIONS = listOf(5, 10, 15, 20)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val _ui = MutableStateFlow(AppUiState())
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

    private var viewTokenTemp: Pair<String, String>? = null
    private var posJob: Job? = null
    private var spotJob: Job? = null
    private var hsmWs: HsmWebSocket? = null

    init {
        viewModelScope.launch { tryRestoreSession() }
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    private suspend fun tryRestoreSession() {
        _ui.update { it.copy(authStep = AuthStep.RESTORE, loadingMsg = "Restoring session...") }
        val session = loadSession(ctx)
        val creds = loadCredentials(ctx)
        if (session != null && creds != null) {
            _ui.update { it.copy(session = session, credentials = creds, greetingName = session.greetingName) }
            startTerminal(session)
            return
        }
        val step = if (creds != null) AuthStep.TOTP else AuthStep.SETUP
        _ui.update { it.copy(authStep = step, credentials = creds, loadingMsg = "") }
    }

    fun saveCreds(creds: KotakCredentials) {
        saveCredentials(ctx, creds)
        _ui.update { it.copy(credentials = creds, authStep = AuthStep.TOTP, loginError = "") }
    }

    fun doTotpLogin(totp: String) {
        val creds = _ui.value.credentials ?: return
        _ui.update { it.copy(loadingMsg = "Verifying TOTP...", loginError = "") }
        viewModelScope.launch {
            val r = kotakLoginTOTP(creds.accessToken, creds.mobileNumber, creds.ucc, totp)
            if (r.status == "success") {
                viewTokenTemp = r.viewToken to r.viewSid
                _ui.update { it.copy(authStep = AuthStep.MPIN, loadingMsg = "") }
            } else {
                _ui.update { it.copy(loadingMsg = "", loginError = r.message) }
            }
        }
    }

    fun doMpinValidate() {
        val creds = _ui.value.credentials ?: return
        val (vt, vs) = viewTokenTemp ?: return
        _ui.update { it.copy(loadingMsg = "Validating MPIN...", loginError = "") }
        viewModelScope.launch {
            val r = kotakValidateMpin(creds.accessToken, creds.mpin, vt, vs)
            if (r.status == "success") {
                val session = KotakSession(
                    accessToken = creds.accessToken, mobileNumber = creds.mobileNumber,
                    ucc = creds.ucc, mpin = creds.mpin,
                    viewToken = vt, viewSid = vs,
                    sessionToken = r.sessionToken, sessionSid = r.sessionSid,
                    baseURL = r.baseURL, dataCenter = r.dataCenter,
                    greetingName = r.greetingName,
                    loginDate = java.time.LocalDate.now().toString(),
                )
                saveSession(ctx, session)
                _ui.update { it.copy(session = session, greetingName = r.greetingName, loadingMsg = "") }
                startTerminal(session)
            } else {
                _ui.update { it.copy(loadingMsg = "", loginError = r.message) }
            }
        }
    }

    fun logout() {
        stopAll()
        clearSession(ctx)
        _ui.value = AppUiState(
            authStep = AuthStep.TOTP,
            credentials = _ui.value.credentials,
        )
    }

    // ── Terminal startup ──────────────────────────────────────────────────────

    private fun startTerminal(session: KotakSession) {
        _ui.update { it.copy(authStep = AuthStep.TERMINAL, loadingMsg = "", instrumentStatus = InstrumentStatus.LOADING) }
        viewModelScope.launch {
            refreshPositions()
            refreshOrders()
            refreshFunds()
            refreshAllSpots()
        }
        startPolling(session)
        connectHsm(session)
        viewModelScope.launch { loadInstruments(session) }
    }

    private fun startPolling(session: KotakSession) {
        stopPolling()
        posJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                _refreshPositions()
                _refreshOrders()
            }
        }
        spotJob = viewModelScope.launch {
            while (isActive) { delay(5_000); refreshAllSpots() }
        }
    }

    private fun stopPolling() {
        posJob?.cancel(); posJob = null
        spotJob?.cancel(); spotJob = null
    }

    // ── HSM WebSocket ─────────────────────────────────────────────────────────

    private fun connectHsm(session: KotakSession) {
        hsmWs?.disconnect()
        hsmWs = HsmWebSocket(
            session = session,
            onLtp = { token, ltp -> applyLtpTick(token, ltp) },
            onConnected = {
                _ui.update { it.copy(hsmConnected = true) }
                // Immediately re-subscribe current chain + positions on connect/reconnect
                viewModelScope.launch { resubscribeAll() }
            },
            onDisconnected = { _ui.update { it.copy(hsmConnected = false) } },
        )
        hsmWs?.connect()
    }

    private fun applyLtpTick(token: String, ltp: Double) {
        _ui.update { st ->
            val newLtps = st.liveLtps + (token to ltp)
            val newPos = st.positions.map { p ->
                if (p.ts == token) p.copy(ltp = ltp) else p
            }
            st.copy(liveLtps = newLtps, positions = newPos, dayPnl = newPos.sumOf { it.pnl })
        }
    }

    private fun resubscribeAll() {
        val st = _ui.value
        val scrips = buildScripsString(st.chain, st.positions)
        if (scrips.isNotEmpty()) hsmWs?.subscribe(scrips)
    }

    private fun buildScripsString(chain: List<ChainRow>, positions: List<Position>): String {
        val tokens = linkedSetOf<String>()
        chain.forEach { row ->
            if (row.ceTs.isNotEmpty() && row.ceSeg.isNotEmpty()) tokens.add("${row.ceSeg}|${row.ceTs}")
            if (row.peTs.isNotEmpty() && row.peSeg.isNotEmpty()) tokens.add("${row.peSeg}|${row.peTs}")
        }
        positions.filter { it.netQty != 0 }.forEach { p ->
            if (p.ts.isNotEmpty() && p.seg.isNotEmpty()) tokens.add("${p.seg}|${p.ts}")
        }
        return tokens.joinToString("+")
    }

    private fun stopAll() {
        stopPolling()
        hsmWs?.disconnect()
        hsmWs = null
    }

    // ── Instruments ──────────────────────────────────────────────────────────

    private suspend fun loadInstruments(session: KotakSession) {
        _ui.update { it.copy(instrumentStatus = InstrumentStatus.LOADING, instrumentMsg = "Loading instruments...") }
        try {
            downloadAndParseInstruments(ctx, session) { msg ->
                _ui.update { it.copy(instrumentMsg = msg) }
            }
            val exp = getExpiries("NIFTY")
            val nearest = exp.find { it.isNearest }?.label ?: exp.firstOrNull()?.label ?: ""
            _ui.update {
                it.copy(
                    instrumentStatus = InstrumentStatus.READY,
                    instrumentMsg = "",
                    expiries = exp,
                    selectedExpiry = nearest,
                )
            }
            if (nearest.isNotEmpty()) loadChain("NIFTY", nearest)
        } catch (e: Exception) {
            _ui.update { it.copy(instrumentStatus = InstrumentStatus.ERROR, instrumentMsg = e.message ?: "Failed") }
        }
    }

    // ── Index / Expiry / Chain ────────────────────────────────────────────────

    fun setCurrentIndex(idx: String) {
        val exp = getExpiries(idx)
        val nearest = exp.find { it.isNearest }?.label ?: exp.firstOrNull()?.label ?: ""
        _ui.update { it.copy(currentIndex = idx, expiries = exp, selectedExpiry = nearest, chain = emptyList(), selectedRowIdx = -1) }
        if (nearest.isNotEmpty()) viewModelScope.launch { loadChain(idx, nearest) }
    }

    fun setSelectedExpiry(exp: String) {
        _ui.update { it.copy(selectedExpiry = exp) }
        viewModelScope.launch { loadChain(_ui.value.currentIndex, exp) }
    }

    fun setNumStrikes(n: Int) {
        _ui.update { it.copy(numStrikes = n) }
        val st = _ui.value
        if (st.selectedExpiry.isNotEmpty()) {
            viewModelScope.launch { loadChain(st.currentIndex, st.selectedExpiry) }
        }
    }

    private suspend fun loadChain(idx: String, expiry: String) {
        _ui.update { it.copy(chainLoading = true) }
        val s = _ui.value.session ?: return
        var spot = _ui.value.spotPrices[idx] ?: 0.0
        if (spot <= 0.0) {
            spot = kotakGetSpot(s, idx)
            if (spot > 0.0) _ui.update { it.copy(spotPrices = it.spotPrices + (idx to spot)) }
        }
        if (spot <= 0.0) { _ui.update { it.copy(chainLoading = false) }; return }
        val numStrikes = _ui.value.numStrikes
        val rows = buildChain(idx, spot, numStrikes, expiry) ?: emptyList()
        val atmIdx = rows.indexOfFirst { it.isAtm }.takeIf { it >= 0 } ?: (rows.size / 2)
        _ui.update { it.copy(chain = rows, selectedRowIdx = atmIdx, chainLoading = false) }
        // Subscribe chain tokens immediately
        val positions = _ui.value.positions
        val scrips = buildScripsString(rows, positions)
        if (scrips.isNotEmpty()) hsmWs?.subscribe(scrips)
    }

    fun setSelectedRow(idx: Int) { _ui.update { it.copy(selectedRowIdx = idx) } }
    fun setLots(n: Int) { _ui.update { it.copy(lots = maxOf(1, minOf(50, n))) } }
    fun toggleSafety() { _ui.update { it.copy(safetyLock = !it.safetyLock) } }

    // ── Market data ──────────────────────────────────────────────────────────

    fun refreshPositions() { viewModelScope.launch { _refreshPositions() } }
    private suspend fun _refreshPositions() {
        val s = _ui.value.session ?: return
        val obj = kotakGetPositions(s)
        val pos = parsePositions(obj)
        val ltps = _ui.value.liveLtps
        val posWithLtp = pos.map { p -> val ltp = ltps[p.ts] ?: p.ltp; p.copy(ltp = ltp) }
        val pnl = posWithLtp.sumOf { it.pnl }
        _ui.update { it.copy(positions = posWithLtp, dayPnl = pnl) }
        // Subscribe position tokens to get real-time P&L
        val chain = _ui.value.chain
        val scrips = buildScripsString(chain, posWithLtp)
        if (scrips.isNotEmpty()) hsmWs?.subscribe(scrips)
    }

    fun refreshOrders() { viewModelScope.launch { _refreshOrders() } }
    private suspend fun _refreshOrders() {
        val s = _ui.value.session ?: return
        val obj = kotakGetOrderbook(s)
        _ui.update { it.copy(orders = parseOrders(obj)) }
    }

    fun refreshFunds() { viewModelScope.launch { _refreshFunds() } }
    private suspend fun _refreshFunds() {
        val s = _ui.value.session ?: return
        val obj = kotakGetLimits(s)
        parseFunds(obj)?.let { funds -> _ui.update { it.copy(funds = funds) } }
    }

    private suspend fun refreshAllSpots() {
        val s = _ui.value.session ?: return
        val prices = INDICES.map { idx ->
            viewModelScope.async { idx to kotakGetSpot(s, idx) }
        }.awaitAll().filter { it.second > 0 }.toMap()
        if (prices.isNotEmpty()) _ui.update { it.copy(spotPrices = it.spotPrices + prices) }
    }

    // ── Orders ───────────────────────────────────────────────────────────────

    suspend fun placeOrder(tt: String, ot: String): PlaceOrderResult {
        val s = _ui.value.session ?: return PlaceOrderResult(false, message = "Not logged in")
        val st = _ui.value
        if (st.safetyLock) return PlaceOrderResult(false, message = "Safety lock is ON — toggle it off to trade")
        val idx = st.selectedRowIdx
        if (idx < 0 || idx >= st.chain.size) return PlaceOrderResult(false, message = "No strike selected")
        val row = st.chain[idx]
        val ts = if (ot == "CE") row.ceTs else row.peTs
        val seg = if (ot == "CE") row.ceSeg else row.peSeg
        val lot = if (ot == "CE") row.ceLot else row.peLot
        if (ts.isEmpty() || seg.isEmpty()) return PlaceOrderResult(false, message = "No instrument data")
        val qty = lot * st.lots
        val result = kotakPlaceOrder(s, seg, ts, tt, qty)
        delay(500)
        _refreshPositions(); _refreshOrders()
        return result
    }

    suspend fun cancelOrder(orderNo: String) {
        val s = _ui.value.session ?: return
        kotakCancelOrder(s, orderNo)
        delay(300); _refreshOrders()
    }

    suspend fun closeAll() {
        val s = _ui.value.session ?: return
        _ui.value.positions.filter { it.netQty != 0 }.forEach { p ->
            val side = if (p.netQty > 0) "S" else "B"
            kotakPlaceOrder(s, p.seg, p.ts, side, Math.abs(p.netQty), p.prod)
        }
        delay(600); _refreshPositions(); _refreshOrders()
    }

    override fun onCleared() {
        super.onCleared()
        stopAll()
    }
}
