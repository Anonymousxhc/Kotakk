package com.akatsuki.trading.data

data class KotakCredentials(
    val accessToken: String,
    val mobileNumber: String,
    val ucc: String,
    val mpin: String,
)

data class KotakSession(
    val accessToken: String,
    val mobileNumber: String,
    val ucc: String,
    val mpin: String,
    val viewToken: String,
    val viewSid: String,
    val sessionToken: String,
    val sessionSid: String,
    val baseURL: String,
    val dataCenter: String,
    val greetingName: String,
    val loginDate: String,
)

data class LoginResult(
    val status: String,
    val viewToken: String = "",
    val viewSid: String = "",
    val message: String = "",
)

data class ValidateResult(
    val status: String,
    val sessionToken: String = "",
    val sessionSid: String = "",
    val baseURL: String = "",
    val dataCenter: String = "",
    val greetingName: String = "",
    val message: String = "",
)

data class PlaceOrderResult(
    val ok: Boolean,
    val orderId: String = "",
    val message: String = "",
)

data class Position(
    val ts: String,
    val seg: String,
    val netQty: Int,
    val buyAmt: Double,
    val sellAmt: Double,
    val ltp: Double,
    val prod: String,
    val tok: String = "",
) {
    val pnl: Double get() = if (netQty != 0) {
        ltp * netQty - (buyAmt - sellAmt)
    } else {
        sellAmt - buyAmt
    }
}

data class Order(
    val nOrdNo: String,
    val ts: String,
    val ordSt: String,
    val tt: String,
    val qty: String,
    val pr: String,
    val pt: String,
    val pc: String,
    val time: String = "",
)

data class FundsData(
    val availableCash: Double,
    val utilised: Double,
    val totalMargin: Double,
)

data class ChainRow(
    val strike: Double,
    val isAtm: Boolean,
    val ceTs: String,
    val ceSym: String,
    val ceSeg: String,
    val ceLot: Int,
    val peTs: String,
    val peSym: String,
    val peSeg: String,
    val peLot: Int,
)

data class ExpiryInfo(
    val label: String,
    val isNearest: Boolean,
)

data class OptionInfo(
    val ts: String,
    val symbol: String,
    val seg: String,
    val lot: Int,
)

enum class AuthStep { RESTORE, SETUP, TOTP, MPIN, TERMINAL }
enum class InstrumentStatus { IDLE, LOADING, READY, ERROR }
