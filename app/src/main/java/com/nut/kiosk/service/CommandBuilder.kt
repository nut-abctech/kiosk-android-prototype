package com.nut.kiosk.service
import com.felhr.utils.HexData
import timber.log.Timber
import kotlin.experimental.xor


interface MiddlewareBoardCommand{
    fun cmd(): ByteArray
}

private class CommandBuilder(val cmd:String, val len:String=LEN_0, val data:String="") {
    companion object {
        const val STX = "0x02"
        const val RECEIVE_ID = "0x4D"
        const val ETX = "0x03"

        const val COIN_OFF = "0xA9"
        const val COIN_ON = "0xAA"
        const val BILL_OFF = "0xAB"
        const val BILL_ON = "0xAC"

        const val CASH_OPERATION = "0xB9"
        const val CASH_CANCEL = "0xB1"

        const val LEN_1 = "0x01"
        const val LEN_0 = "0x00"
    }

    private fun bcc(): String {
        val dpackage = "$RECEIVE_ID$cmd$len$data"
        var bcc:Byte = 0x00
        for (b in HexData.stringTobytes(dpackage).asIterable()) {
            bcc = bcc.xor(b)
        }
        return HexData.hexToString(byteArrayOf(bcc)).trim()
    }

    fun buildCmd():ByteArray {
        val hex = "$STX$RECEIVE_ID$cmd$LEN_0$len$data${bcc()}$ETX"
        Timber.i("build cmd: $hex")
        return HexData.stringTobytes(hex)
    }
}

object BillOn: MiddlewareBoardCommand {
    private var builder:CommandBuilder = CommandBuilder(CommandBuilder.BILL_ON)
    override fun cmd() = builder.buildCmd()
}

object BillOff: MiddlewareBoardCommand {
    private var builder:CommandBuilder = CommandBuilder(CommandBuilder.BILL_OFF)
    override fun cmd() = builder.buildCmd()
}

object CoinOn: MiddlewareBoardCommand {
    private var builder:CommandBuilder = CommandBuilder(CommandBuilder.COIN_ON)
    override fun cmd() = builder.buildCmd()
}

object CoinOff: MiddlewareBoardCommand {
    private var builder:CommandBuilder = CommandBuilder(CommandBuilder.COIN_OFF)
    override fun cmd() = builder.buildCmd()
}

object CashEnable: MiddlewareBoardCommand {
    private var builder:CommandBuilder = CommandBuilder(CommandBuilder.CASH_OPERATION)
    override fun cmd() = builder.buildCmd()
}

object CashDisable: MiddlewareBoardCommand {
    private var builder:CommandBuilder = CommandBuilder(CommandBuilder.CASH_CANCEL)
    override fun cmd() = builder.buildCmd()
}
//4DB900013C