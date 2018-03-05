package com.nut.kiosk.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import com.nut.kiosk.ui.MainActivity

class BootUpReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val intent = Intent(context, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)
        }
    }
}