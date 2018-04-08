package com.nut.kiosk.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle

import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.AppCompatSpinner
import android.support.v7.widget.SwitchCompat
import android.text.method.ScrollingMovementMethod
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.content.edit
import com.afollestad.materialdialogs.MaterialDialog
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.nut.kiosk.BuildConfig
import com.nut.kiosk.R
import com.nut.kiosk.api.KioskApi
import com.nut.kiosk.service.*
import com.nut.kiosk.model.Page
import com.nut.kiosk.room.AppDatabase
import com.nut.kiosk.room.Utils
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
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.TimeUnit

@SuppressLint("ByteOrderMark")
class MainActivity : AppCompatActivity(), View.OnTouchListener {
//
//    companion object {
//        const val REFRESH_OPT_DISABLED = "DISABLED"
//        const val REFRESH_OPT_DEFAULT = "15"
//
//        const val PREF_SELECTED_PAGE_ID = "SELECTED_PAGE_ID"
//        const val PREF_REFRESH_OPTION = "REFRESH_OPTION"
//        const val PREF_SHOW_LOG_VIEW = "SHOW_LOG_VIEW"
//    }

    // detect 5 tap
    private var numberOfTaps = 0
    private var lastTapTimeMs: Long = 0
    private var touchDownMs: Long = 0

    private lateinit var gson: Gson
    private lateinit var httpLoggingInterceptor: HttpLoggingInterceptor
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var retrofit: Retrofit
    private lateinit var kioskApi: KioskApi
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable

    private val compositeDisposable = CompositeDisposable()

    private lateinit var usbService: UsbService
    private lateinit var usbHandler: UsbHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
        usbHandler = UsbHandler(this)

        initViews()
        initModules()
        loadPages()
        updateViews()
    }

    private fun initViews() {
        setContentView(R.layout.activity_main)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.hide()
        tvLogs.movementMethod = ScrollingMovementMethod()
        webView.setOnTouchListener(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.displayZoomControls = false
        WebView.setWebContentsDebuggingEnabled(true)
        webView.addJavascriptInterface(KioskJsInterface(this@MainActivity, tvLogs), "Native")
        webView.webChromeClient = WebChromeClient()
        hideSystemUi()
    }


    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_IMMERSIVE)
    }

    private fun showSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    private fun updateViews() {
        tvLogs.visibility = if (sharedPreferences.getBoolean(Setting.PREF_SHOW_LOG_VIEW, false)) {
            View.VISIBLE
        } else {
            View.GONE
        }
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

        kioskApi = retrofit.create(KioskApi::class.java)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        handler = Handler()
        runnable = Runnable {
            Timber.d("Runnable runs")

            loadPages()

            // re-register
            val refreshOption = sharedPreferences.getString(Setting.PREF_REFRESH_OPTION, Setting.REFRESH_OPT_DEFAULT)
            if (refreshOption != Setting.REFRESH_OPT_DISABLED) {
                handler.removeCallbacks(runnable)
                handler.postDelayed(runnable, refreshOption.toLong() * 1000)// * 60)
            }
        }


        val refreshOption = sharedPreferences.getString(Setting.PREF_REFRESH_OPTION, Setting.REFRESH_OPT_DEFAULT)
        if (sharedPreferences.getString(Setting.PREF_SELECTED_PAGE_ID, "").isNullOrEmpty() && refreshOption != Setting.REFRESH_OPT_DISABLED) {
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
                        val file = File(filesDir, page.path)
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
        Timber.d("downloadPage() version=$page.version")

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
        val selectedPageId = sharedPreferences.getString(Setting.PREF_SELECTED_PAGE_ID, "")
        Timber.d("showSelectedPage() - selectedPageId=" + selectedPageId)

        val selectedPage = if (selectedPageId.isNullOrEmpty()) {
            AppDatabase.getInstance(this@MainActivity)?.pageDao()?.findLatest()
        } else {
            AppDatabase.getInstance(this@MainActivity)?.pageDao()?.findById(selectedPageId)
        }

        if (selectedPage == null) {
            webView.loadUrl("file:///android_asset/loading.html")
        } else {
            Timber.d("selectedPage=" + selectedPage.id)
            try {
                val file = File(filesDir, selectedPage.path)
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
        val dialogView = View.inflate(this, R.layout.dialog_settings, null)

        val list = ArrayList<String>()
        list.add("Latest")
        pageList?.mapTo(list) { "version $it.version" }

        val dataAdapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, list)
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogView.findViewById<AppCompatSpinner>(R.id.spVersion).adapter = dataAdapter

        val selectedPageId = sharedPreferences.getString(Setting.PREF_SELECTED_PAGE_ID, "")
        if (selectedPageId.isNullOrEmpty()) {
            dialogView.findViewById<AppCompatSpinner>(R.id.spVersion).setSelection(0)
        } else {
            val storedPageList = AppDatabase.getInstance(this@MainActivity)?.pageDao()?.findAllStoredPage()
            for ((index, page) in storedPageList!!.withIndex()) {
                if (page.id == selectedPageId) {
                    dialogView.findViewById<AppCompatSpinner>(R.id.spVersion).setSelection(index + 1)
                    break
                }
            }
        }

        val refreshOption = sharedPreferences.getString(Setting.PREF_REFRESH_OPTION, Setting.REFRESH_OPT_DEFAULT)
        Timber.d("refreshOption=$refreshOption")
        if (refreshOption == Setting.REFRESH_OPT_DISABLED) {
            dialogView.findViewById<AppCompatSpinner>(R.id.spRefreshOption).setSelection(0)
        } else {
            var i = 0
            var selectedRefreshOptPos = 0
            while (i < dialogView.findViewById<AppCompatSpinner>(R.id.spRefreshOption).adapter.count) {
                val value = dialogView.findViewById<AppCompatSpinner>(R.id.spRefreshOption).adapter.getItem(i).toString()
                Timber.d("value=$value")
                if (value.startsWith(refreshOption)) {
                    selectedRefreshOptPos = i
                    break
                }
                i++
            }
            dialogView.findViewById<AppCompatSpinner>(R.id.spRefreshOption).setSelection(selectedRefreshOptPos)
        }
        val switchLogView = dialogView.findViewById<SwitchCompat>(R.id.scLogView)
        switchLogView?.isChecked = sharedPreferences.getBoolean(Setting.PREF_SHOW_LOG_VIEW, false)
        val switchBill = dialogView.findViewById<SwitchCompat>(R.id.switchBill)
        switchBill?.isChecked = sharedPreferences.getBoolean(Setting.BILL_ON, false)
        val switchCoin = dialogView.findViewById<SwitchCompat>(R.id.switchCoin)
        switchCoin?.isChecked = sharedPreferences.getBoolean(Setting.COIN_ON, false)
        val switchCash = dialogView.findViewById<SwitchCompat>(R.id.switchCash)
        switchCash?.isChecked = sharedPreferences.getBoolean(Setting.CASH_OPERATION_ENABLE, false)

        switchBill.setOnCheckedChangeListener{dialogView, isChecked ->
            if (usbService != null) { // if UsbService was correctly binded, Send data
                usbService.write(if (isChecked) BillOn.cmd() else BillOff.cmd())
                Toast.makeText(this@MainActivity, "Bill ${if (isChecked) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
            }
        }
        switchCoin.setOnCheckedChangeListener{dialogView, isChecked ->
            if (usbService != null) { // if UsbService was correctly binded, Send data
                usbService.write(if (isChecked) CoinOn.cmd() else CoinOff.cmd())
                Toast.makeText(this@MainActivity, "Coin ${if (isChecked) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
            }
        }

        switchCash.setOnCheckedChangeListener{dialogView, isChecked ->
            if (usbService != null) { // if UsbService was correctly binded, Send data
                usbService.write(if (isChecked) CashEnable.cmd() else CashDisable.cmd())
                Toast.makeText(this@MainActivity, "Cash Operation ${if (isChecked) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
            }
        }

        val settingDialogBuilder = MaterialDialog.Builder(this)
        settingDialogBuilder.title("Settings")
        settingDialogBuilder.customView(dialogView, false)
        settingDialogBuilder.cancelable(false)
        settingDialogBuilder.neutralText("Turn off app")
        settingDialogBuilder.onNeutral { dialog, which -> finish() }
        settingDialogBuilder.negativeText("Cancel")
        settingDialogBuilder.onNegative { dialog, which -> hideSystemUi() }
        settingDialogBuilder.positiveText("Save")
        settingDialogBuilder.onPositive { dialog, which ->
            val selectedIdx = dialog.customView?.findViewById<AppCompatSpinner>(R.id.spVersion)?.selectedItemPosition
            Timber.d("selectedIdx=$selectedIdx")
            if (selectedIdx == 0) {
                sharedPreferences.edit {
                    putString(Setting.PREF_SELECTED_PAGE_ID, "")
                }
            } else {
                val version = dialog.customView?.findViewById<AppCompatSpinner>(R.id.spVersion)?.selectedItem.toString().split(" ")[1].toLong()
                val page = AppDatabase.getInstance(this@MainActivity)?.pageDao()?.findByVersion(version)
                sharedPreferences.edit {
                    putString(Setting.PREF_SELECTED_PAGE_ID, page?.id)
                    putString(Setting.PREF_REFRESH_OPTION, Setting.REFRESH_OPT_DISABLED)
                }
            }
            showSelectedPage()

            // refresh option works only when version is latest
            if (selectedIdx == 0) {
                val oldRefreshOpt = sharedPreferences.getString(Setting.PREF_REFRESH_OPTION, Setting.REFRESH_OPT_DEFAULT)

                val newRefreshOpt = if (dialog.customView?.findViewById<AppCompatSpinner>(R.id.spRefreshOption)?.selectedItemPosition == 0) {
                    Setting.REFRESH_OPT_DISABLED
                } else {
                    dialog.customView?.findViewById<AppCompatSpinner>(R.id.spRefreshOption)?.selectedItem.toString().split(" ")[0]
                }

                if (oldRefreshOpt != newRefreshOpt) {
                    sharedPreferences.edit {
                        putString(Setting.PREF_REFRESH_OPTION, newRefreshOpt)
                    }
                    handler.removeCallbacks(runnable)
                    handler.post(runnable)
                }
            }

            sharedPreferences.edit {
                putBoolean(Setting.PREF_SHOW_LOG_VIEW, switchLogView.isChecked!!)
                putBoolean(Setting.BILL_ON, switchBill.isChecked!!)
                putBoolean(Setting.COIN_ON, switchCoin.isChecked!!)
                putBoolean(Setting.CASH_OPERATION_ENABLE, switchCash.isChecked!!)
            }
            updateViews()
        }
        settingDialogBuilder.show()
    }

    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        val tapTimeOut = (ViewConfiguration.getDoubleTapTimeout() * 2.5).toLong()

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
                        return true
                    }
                }
            }
        }

        return false
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        handler.removeCallbacks(runnable)

        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        setFilters()
        startService(UsbService::class.java, usbConnection, null)
    }

    override fun onPause(){
        super.onPause()
        unregisterReceiver(mUsbReceiver)
        unbindService(usbConnection)
    }

    // **************** USB service section *********************//
    private val mUsbReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbService.ACTION_USB_PERMISSION_GRANTED // USB PERMISSION GRANTED
                -> Toast.makeText(context, "USB Ready", Toast.LENGTH_SHORT).show()
                UsbService.ACTION_USB_PERMISSION_NOT_GRANTED // USB PERMISSION NOT GRANTED
                -> Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show()
                UsbService.ACTION_NO_USB // NO USB CONNECTED
                -> Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show()
                UsbService.ACTION_USB_DISCONNECTED // USB DISCONNECTED
                -> Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show()
                UsbService.ACTION_USB_NOT_SUPPORTED // USB NOT SUPPORTED
                -> Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val usbConnection = object:ServiceConnection {
        override fun onServiceConnected(arg0:ComponentName, binder: IBinder) {
            usbService = (binder as UsbService.UsbBinder).service
            usbService.setHandler(usbHandler)
        }
        override fun onServiceDisconnected(arg0:ComponentName) {
//            usbService = null
        }
    }

    private fun setFilters() {
        val filter = IntentFilter()
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED)
        filter.addAction(UsbService.ACTION_NO_USB)
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED)
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED)
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED)
        registerReceiver(mUsbReceiver, filter)
    }

    private fun startService(service:Class<*>, serviceConnection:ServiceConnection, extras:Bundle?) {
        if (!UsbService.SERVICE_CONNECTED)
        {
            val startService = Intent(this, service)
            if (extras != null && !extras.isEmpty)
            {
                for (key in extras.keySet())
                {
                    val extra = extras.getString(key)
                    startService.putExtra(key, extra)
                }
            }
            startService(startService)
        }
        val bindingIntent = Intent(this, service)
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private class UsbHandler(activity:MainActivity):Handler() {
        private val mActivity:WeakReference<MainActivity>

        init {
            mActivity = WeakReference(activity)
        }

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                UsbService.MESSAGE_FROM_SERIAL_PORT -> {
                    val data = msg.obj as String
                    mActivity.get()!!.tvLogs.append(Utils.logFormat(data))
                }
                UsbService.CTS_CHANGE -> Toast.makeText(mActivity.get(), "CTS_CHANGE", Toast.LENGTH_LONG).show()
                UsbService.DSR_CHANGE -> Toast.makeText(mActivity.get(), "DSR_CHANGE", Toast.LENGTH_LONG).show()
            }
        }
    }
}
