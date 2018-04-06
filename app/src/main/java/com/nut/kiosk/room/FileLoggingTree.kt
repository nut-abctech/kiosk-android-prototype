package com.nut.kiosk.room

import android.content.Context
import android.os.Environment
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.security.AccessControlContext
import java.text.SimpleDateFormat
import java.util.*


class FileLoggingTree() : Timber.DebugTree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {

        try {
            val direct = File("${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}/kiosk-client-logs")
            if (!direct.exists() && !direct?.mkdir()) {
                Log.i(TAG, "Created log dir")
            }

            val fileNameTimeStamp = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
            val logTimeStamp = SimpleDateFormat("E MMM dd yyyy 'at' hh:mm:ss:SSS aaa", Locale.getDefault()).format(Date())

            val fileName = "$fileNameTimeStamp.html"

            val file = File("${direct.absolutePath}${File.separator}$fileName")
            Log.i(TAG, file.absolutePath)
            if (!file.exists() && !file?.createNewFile()) {
                Log.i(TAG, "Created log file")
            }
            val fileOutputStream = FileOutputStream(file, true)
            fileOutputStream.write("<p style=\"background:lightgray;\"><strong style=\"background:lightblue;\">&nbsp&nbsp$logTimeStamp :&nbsp&nbsp</strong>&nbsp&nbsp$message</p>".toByteArray())
            fileOutputStream.close()

        } catch (e: Exception) {
            Log.e(TAG, "Error while logging into file : $e")
        }

    }


    companion object {
        private val TAG = FileLoggingTree::class.java.simpleName
    }
}
