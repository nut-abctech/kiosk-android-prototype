/**
 * Copyright 2017 Erik Jhordan Rey.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nut.kiosk

import android.Manifest
import android.app.Application
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.os.Build
import android.support.v4.content.ContextCompat
import android.util.Log
import com.nut.kiosk.room.FileLoggingTree
import timber.log.BuildConfig
import timber.log.Timber
import timber.log.Timber.DebugTree



class KioskApp : Application() {

    override fun onCreate() {
        super.onCreate()
//        if (BuildConfig.DEBUG) {
//            Timber.plant(DebugTree())
//        }
        Timber.plant(FileLoggingTree())
    }

    /** A tree which logs important information for crash reporting. */
    //  private static class CrashReportingTree extends Timber.Tree {
    //    @Override protected void log(int priority, String tag, @NonNull String message, Throwable t) {
    //      if (priority == Log.VERBOSE || priority == Log.DEBUG) {
    //        return;
    //      }
    //
    //      FakeCrashLibrary.log(priority, tag, message);
    //
    //      if (t != null) {
    //        if (priority == Log.ERROR) {
    //          FakeCrashLibrary.logError(t);
    //        } else if (priority == Log.WARN) {
    //          FakeCrashLibrary.logWarning(t);
    //        }
    //      }
    //    }

}

