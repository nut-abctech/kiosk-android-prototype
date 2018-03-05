package com.nut.kiosk.ui

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast
import timber.log.Timber

class KioskJsInterface(var context: Context) {

    @JavascriptInterface
    fun toast(message: String) {
        Timber.d("toast() - message=" + message)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

}