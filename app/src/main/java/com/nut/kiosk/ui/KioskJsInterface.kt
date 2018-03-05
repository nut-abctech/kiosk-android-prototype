package com.nut.kiosk.ui

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast

public class KioskJsInterface(var context: Context) {

    @JavascriptInterface
    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

}