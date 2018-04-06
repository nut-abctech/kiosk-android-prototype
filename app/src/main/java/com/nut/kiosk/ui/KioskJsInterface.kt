package com.nut.kiosk.ui

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.TextView
import android.widget.Toast
import com.nut.kiosk.room.Utils
import timber.log.Timber


class KioskJsInterface(var context: Context, var tvLogs: TextView) {

    @JavascriptInterface
    fun toast(message: String) {
        Timber.d("toast() - message=" + message)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun log(message: String) {
        Timber.d("log() - message=" + message)

        tvLogs.append(Utils.logFormat(message))
        // svLogs.post(Runnable { svLogs.fullScroll(View.FOCUS_DOWN) })

        val scrollDelta = (tvLogs.layout.getLineBottom(tvLogs.lineCount - 1) - tvLogs.scrollY - tvLogs.height)
        Timber.d("scrollDelta=" + scrollDelta)
        if (scrollDelta > 0)
            tvLogs.scrollBy(0, scrollDelta)
    }

}