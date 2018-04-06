package com.nut.kiosk.room

import java.text.SimpleDateFormat
import java.util.*
const val DF_SIMPLE_STRING = "yyyy-MM-dd HH:mm:ss"

/**
 * Created by nut-abctech on 3/9/18.
 */
object Utils {
    fun logFormat(text: String): String {
        val now = Date()
        val dateFormat = SimpleDateFormat(DF_SIMPLE_STRING)
        dateFormat.timeZone = TimeZone.getTimeZone("Asia/Bangkok")
        return "%s:- %s \n".format(dateFormat.format(now), text)
    }
}
