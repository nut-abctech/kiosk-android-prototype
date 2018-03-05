package com.nut.kiosk.ui

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.AppCompatSpinner
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.content.edit
import com.afollestad.materialdialogs.MaterialDialog
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.nut.kiosk.BuildConfig
import com.nut.kiosk.R
import com.nut.kiosk.api.KioskApi
import com.nut.kiosk.model.Page
import com.nut.kiosk.room.AppDatabase
import io.reactivex.Completable
import io.reactivex.CompletableObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.annotations.NonNull
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit


@SuppressLint("ByteOrderMark")
class MainActivity : AppCompatActivity(), View.OnTouchListener {

    companion object {
        const val REFRESH_OPT_DISABLED = "DISABLED"
        const val REFRESH_OPT_DEFAULT = "15"

        const val PREF_SELECTED_PAGE_ID = "SELECTED_PAGE_ID"
        const val PREF_REFRESH_OPTION = "REFRESH_OPTION"

        const val ACTION_USB_PERMISSION = "com.nut.kiosk.USB_PERMISSION"
    }

    // detect 5 tap
    var numberOfTaps = 0
    var lastTapTimeMs: Long = 0
    var touchDownMs: Long = 0

    private lateinit var gson: Gson
    private lateinit var httpLoggingInterceptor: HttpLoggingInterceptor
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var retrofit: Retrofit
    private lateinit var kioskApi: KioskApi
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable

    private val compositeDisposable = CompositeDisposable()

    private lateinit var usbPermissionIntent: PendingIntent
    private lateinit var usbManager: UsbManager

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val usbDevice = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    usbManager.requestPermission(usbDevice, usbPermissionIntent)

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (usbDevice != null) {
                            //call method to set up device communication
                            Timber.d("device id %s", "****" + usbDevice.deviceId)
                            Timber.d("product id %s", "****" + usbDevice.productId)

                        } else {
                            Timber.d("device id %s", "No USB device")
                        }

                    } else {
                        Timber.d("shiv %s", "permission denied for device ")
                    }
                }
            }
            if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                // Device removed
                synchronized (this) {
                    // Check to see if usbDevice is yours and cleanup ...
                }
            }
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action) {
                // Device attached
                synchronized (this) {
                    // Qualify the new device to suit your needs and request permission
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.hide()

        webView.setOnTouchListener(this)
        webView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LOW_PROFILE or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        usbPermissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
        val filter = IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
        registerReceiver(usbReceiver, filter)

        initModules()
        loadPages()
    }

    private fun initModules() {
        gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").create()

        httpLoggingInterceptor = HttpLoggingInterceptor().setLevel(if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE)

        okHttpClient = OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(httpLoggingInterceptor).build()

        retrofit = Retrofit.Builder()
                .baseUrl(KioskApi.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .client(okHttpClient)
                .build()

        kioskApi = retrofit?.create(KioskApi::class.java)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        handler = Handler()
        runnable = Runnable {
            Timber.d("Runnable runs")

            loadPages()

            // re-register
            var refreshOption = sharedPreferences.getString(PREF_REFRESH_OPTION, REFRESH_OPT_DEFAULT)
            if (refreshOption != REFRESH_OPT_DISABLED) {
                handler.removeCallbacks(runnable)
                handler.postDelayed(runnable, refreshOption.toLong() * 1000)// * 60)
            }
        }


        var refreshOption = sharedPreferences.getString(PREF_REFRESH_OPTION, REFRESH_OPT_DEFAULT)
        if (sharedPreferences.getString(PREF_SELECTED_PAGE_ID, "").isNullOrEmpty() && refreshOption != REFRESH_OPT_DISABLED) {
            handler.removeCallbacks(runnable)
            handler.postDelayed(runnable, refreshOption.toLong() * 1000) // * 60)
            loadPages()
        }
    }

    private fun loadPages() {
        val disposable = kioskApi.getPageList()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ pageList ->

                    Timber.d("loadPages() count=" + pageList.size)
                    for (page in pageList) {
                        var file = File(filesDir, page.path)
                        page.downloaded = file.exists()

                        if (!page.downloaded) {
                            downloadPage(page)
                        }
                    }
                    AppDatabase.getInstance(this@MainActivity)?.pageDao()?.insert(pageList)

                    showSelectedPage()
                }, { t: Throwable? ->
                    t!!.printStackTrace()
                    showSelectedPage()
                })

        compositeDisposable.add(disposable!!)
    }

    private fun downloadPage(page: Page) {
        Timber.d("downloadPage() version=" + page.version)

        val disposable = kioskApi.getPage(page.path)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ responseBody ->
                    try {
                        openFileOutput(page.path, Context.MODE_PRIVATE).use {
                            it.write(responseBody.bytes())
                        }
                        page.downloaded = true

                        Completable.fromAction { AppDatabase.getInstance(this)?.pageDao()?.insert(page) }
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(object : CompletableObserver {
                                    override fun onSubscribe(@NonNull d: Disposable) {
                                        compositeDisposable.add(d)
                                        showSelectedPage()
                                    }

                                    override fun onComplete() {
                                        Timber.e("DataSource has been Populated")
                                        showSelectedPage()
                                    }

                                    override fun onError(@NonNull e: Throwable) {
                                        e.printStackTrace()
                                        Timber.e("DataSource hasn't been Populated")
                                        showSelectedPage()
                                    }
                                })

                        Timber.d("page version " + page.version + "has saved")
                    } catch (e: IOException) {
                        Timber.e(e)
                        Toast.makeText(this, "Failed to save content file", Toast.LENGTH_SHORT).show()
                        showSelectedPage()
                    }
                }, { t: Throwable? -> t!!.printStackTrace() })

        compositeDisposable.add(disposable!!)
    }

    private fun showSelectedPage() {
        var selectedPageId = sharedPreferences.getString(PREF_SELECTED_PAGE_ID, "")
        Timber.d("showSelectedPage() - selectedPageId=" + selectedPageId)

        var selectedPage = if (selectedPageId.isNullOrEmpty()) {
            AppDatabase.getInstance(this@MainActivity)?.pageDao()?.findLatest()
        } else {
            AppDatabase.getInstance(this@MainActivity)?.pageDao()?.findById(selectedPageId)
        }

        if (selectedPage == null) {
            webView.loadUrl("file:///android_asset/loading.html")
        } else {
            Timber.d("selectedPage=" + selectedPage.id)
            try {
                var file = File(filesDir, selectedPage.path)
                webView.loadUrl("file:///" + file)
            } catch (e: Exception) {
                Timber.e(e)
                Toast.makeText(this, "Failed to read content file\n" + e.localizedMessage, Toast.LENGTH_SHORT).show()
                webView.loadUrl("file:///android_asset/error.html")
            }
        }
    }

    private fun showSettingDialog() {
        val pageList = AppDatabase.getInstance(this@MainActivity)?.pageDao()?.findAllStoredPage()
        var dialogView = View.inflate(this, R.layout.dialog_settings, null)

        var list = ArrayList<String>()
        list.add("Latest")
        pageList?.mapTo(list) { "version " + it.version }

        var dataAdapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, list)
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogView.findViewById<AppCompatSpinner>(R.id.spVersion).adapter = dataAdapter

        var selectedPageId = sharedPreferences.getString(PREF_SELECTED_PAGE_ID, "")
        if (selectedPageId.isNullOrEmpty()) {
            dialogView.findViewById<AppCompatSpinner>(R.id.spVersion).setSelection(0)
        } else {
            var storedPageList = AppDatabase.getInstance(this@MainActivity)?.pageDao()?.findAllStoredPage()
            for ((index, page) in storedPageList!!.withIndex()) {
                if (page.id == selectedPageId) {
                    dialogView.findViewById<AppCompatSpinner>(R.id.spVersion).setSelection(index + 1)
                    break
                }
            }
        }

        var refreshOption = sharedPreferences.getString(PREF_REFRESH_OPTION, REFRESH_OPT_DEFAULT)
        Timber.d("refreshOption=" + refreshOption)
        if (refreshOption == REFRESH_OPT_DISABLED) {
            dialogView.findViewById<AppCompatSpinner>(R.id.spRefreshOption).setSelection(0)
        } else {
            var i = 0
            var selectedRefreshOptPos = 0
            while (i < dialogView.findViewById<AppCompatSpinner>(R.id.spRefreshOption).adapter.count) {
                var value = dialogView.findViewById<AppCompatSpinner>(R.id.spRefreshOption).adapter.getItem(i).toString()
                Timber.d("value=" + value)
                if (value.startsWith(refreshOption)) {
                    selectedRefreshOptPos = i
                    break
                }
                i++
            }
            dialogView.findViewById<AppCompatSpinner>(R.id.spRefreshOption).setSelection(selectedRefreshOptPos)
        }

        var alertDialogBuilder = MaterialDialog.Builder(this)
        alertDialogBuilder.title("Settings")
        alertDialogBuilder.customView(dialogView, false)
        alertDialogBuilder.cancelable(true)
        alertDialogBuilder.negativeText("Turn off app")
        alertDialogBuilder.onNegative { dialog, which -> finish() }
        alertDialogBuilder.positiveText("Save")
        alertDialogBuilder.onPositive { dialog, which ->
            var selectedIdx = dialog.customView?.findViewById<AppCompatSpinner>(R.id.spVersion)?.selectedItemPosition
            Timber.d("selectedIdx=" + selectedIdx)
            if (selectedIdx == 0) {
                sharedPreferences.edit {
                    putString(PREF_SELECTED_PAGE_ID, "")
                }
            } else {
                var version = dialog.customView?.findViewById<AppCompatSpinner>(R.id.spVersion)?.selectedItem.toString().split(" ")[1].toLong()
                var page = AppDatabase.getInstance(this@MainActivity)?.pageDao()?.findByVersion(version)
                sharedPreferences.edit {
                    putString(PREF_SELECTED_PAGE_ID, page?.id)
                    putString(PREF_REFRESH_OPTION, REFRESH_OPT_DISABLED)
                }
            }
            showSelectedPage()

            // refresh option works only when version is latest
            if (selectedIdx == 0) {
                var oldRefreshOpt = sharedPreferences.getString(PREF_REFRESH_OPTION, REFRESH_OPT_DEFAULT)

                var newRefreshOpt = if (dialog.customView?.findViewById<AppCompatSpinner>(R.id.spRefreshOption)?.selectedItemPosition == 0) {
                    REFRESH_OPT_DISABLED
                } else {
                    dialog.customView?.findViewById<AppCompatSpinner>(R.id.spRefreshOption)?.selectedItem.toString().split(" ")[0]
                }

                if (oldRefreshOpt != newRefreshOpt) {
                    sharedPreferences.edit {
                        putString(PREF_REFRESH_OPTION, newRefreshOpt)
                    }
                    handler.removeCallbacks(runnable)
                    handler.post(runnable)
                }
            }
        }
        alertDialogBuilder.show()
    }

    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        var tapTimeOut = (ViewConfiguration.getDoubleTapTimeout() * 2.5).toLong()

        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownMs = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                Timber.d("numberOfTaps=" + numberOfTaps)

                if ((System.currentTimeMillis() - touchDownMs) > tapTimeOut) {
                    numberOfTaps = 0
                    lastTapTimeMs = 0
                } else {
                    if (numberOfTaps > 0 && (System.currentTimeMillis() - lastTapTimeMs) < tapTimeOut) {
                        numberOfTaps += 1
                    } else {
                        numberOfTaps = 1
                    }

                    lastTapTimeMs = System.currentTimeMillis()

                    if (numberOfTaps == 5) {
                        showSettingDialog()
                        numberOfTaps = 0
                    }
                }
            }
        }

        return true
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        handler.removeCallbacks(runnable)

        super.onDestroy()
    }

}
