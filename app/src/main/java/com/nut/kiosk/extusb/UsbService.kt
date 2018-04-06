package com.nut.kiosk.extusb

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.os.Handler
import android.hardware.usb.UsbManager
import android.os.Binder
import android.os.IBinder
import com.felhr.usbserial.CDCSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import com.felhr.usbserial.UsbSerialDevice
import timber.log.Timber
import java.io.UnsupportedEncodingException


class UsbService : Service() {
    companion object {
        const val ACTION_USB_READY = "com.nut.kiosk.USB_READY"
        const val ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED"
        const val ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED"
        const val ACTION_USB_NOT_SUPPORTED = "com.nut.kiosk.USB_NOT_SUPPORTED"
        const val ACTION_NO_USB = "com.nut.kiosk.NO_USB"
        const val ACTION_USB_PERMISSION_GRANTED = "com.nut.kiosk.USB_PERMISSION_GRANTED"
        const val ACTION_USB_PERMISSION_NOT_GRANTED = "com.nut.kiosk.USB_PERMISSION_NOT_GRANTED"
        const val ACTION_USB_DISCONNECTED = "com.nut.kiosk.USB_DISCONNECTED"
        const val ACTION_CDC_DRIVER_NOT_WORKING = "com.nut.kiosk.ACTION_CDC_DRIVER_NOT_WORKING"
        const val ACTION_USB_DEVICE_NOT_WORKING = "com.nut.kiosk.ACTION_USB_DEVICE_NOT_WORKING"
        const val MESSAGE_FROM_SERIAL_PORT = 0
        const val CTS_CHANGE = 1
        const val DSR_CHANGE = 2
        private const val ACTION_USB_PERMISSION = "com.nut.kiosk.USB_PERMISSION"
        private const val BAUD_RATE = 9600 // BaudRate. Change this value if you need
        var SERVICE_CONNECTED = false
    }
    private val binder = UsbBinder()

    private lateinit var context: Context
    private lateinit var mHandler: Handler
    private lateinit var usbManager: UsbManager
    private lateinit var device: UsbDevice
    private lateinit var connection: UsbDeviceConnection
    private lateinit var serialPort: UsbSerialDevice

    private var serialPortConnected: Boolean = false
    /*
     *  Data received from serial port will be received here. Just populate onReceivedData with your code
     *  In this particular example. byte stream is converted to String and send to UI thread to
     *  be treated there.
     */
    private val mCallback = UsbSerialInterface.UsbReadCallback { msg ->
        try {
            val data = String(msg, charset("UTF-8"))
            Timber.i("Receive msg: $data")
            mHandler.obtainMessage(MESSAGE_FROM_SERIAL_PORT, data).sendToTarget()
        } catch (e: UnsupportedEncodingException) {
            Timber.e(e)
        }
    }

    /*
     * State changes in the CTS line will be received here
     */
    private val ctsCallback = UsbSerialInterface.UsbCTSCallback {
        mHandler.obtainMessage(CTS_CHANGE).sendToTarget()
    }

    /*
     * State changes in the DSR line will be received here
     */
    private val dsrCallback = UsbSerialInterface.UsbDSRCallback {
        mHandler.obtainMessage(DSR_CHANGE).sendToTarget()
    }
    /*
     * Different notifications from OS will be received here (USB attached, detached, permission responses...)
     * About BroadcastReceiver: http://developer.android.com/reference/android/content/BroadcastReceiver.html
     */
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == ACTION_USB_PERMISSION) {
                val granted = intent.extras.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
                if (granted)
                // User accepted our USB connection. Try to open the device as a serial port
                {
                    ctx.sendBroadcast(Intent(ACTION_USB_PERMISSION_GRANTED))
                    connection = usbManager.openDevice(device)
                    ConnectionThread().start()
                } else
                // User not accepted our USB connection. Send an Intent to the Main Activity
                {
                    ctx.sendBroadcast(Intent(ACTION_USB_PERMISSION_NOT_GRANTED))
                }
            } else if (intent.action == ACTION_USB_ATTACHED) {
                if (!serialPortConnected)
                    findSerialPortDevice() // A USB device has been attached. Try to open it as a Serial port
            } else if (intent.action == ACTION_USB_DETACHED) {
                // Usb device was disconnected. send an intent to the Main Activity
                ctx.sendBroadcast(Intent(ACTION_USB_DISCONNECTED))
                if (serialPortConnected) {
                    serialPort.close()
                }
                serialPortConnected = false
            }
        }
    }

    /*
     * onCreate will be executed when service is started. It configures an IntentFilter to listen for
     * incoming Intents (USB ATTACHED, USB DETACHED...) and it tries to open a serial port.
     */
    override fun onCreate() {
        this.context = this
        serialPortConnected = false
        UsbService.SERVICE_CONNECTED = true
        setFilter()
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        findSerialPortDevice()
    }

    /* MUST READ about services
     * http://developer.android.com/guide/components/services.html
     * http://developer.android.com/guide/components/bound-services.html
     */
    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        UsbService.SERVICE_CONNECTED = false
    }

    /*
     * This function will be called from MainActivity to write data through Serial Port
     */
    fun write(data: ByteArray) {
        serialPort.write(data)
    }

    fun setHandler(mHandler: Handler) {
        this.mHandler = mHandler
    }

    private fun findSerialPortDevice() {
        // This snippet will try to open the first encountered usb device connected, excluding usb root hubs
        val usbDevices = usbManager.deviceList
        if (!usbDevices.isEmpty()) {
            var keep = true
            for (entry in usbDevices.entries) {
                device = entry.value
                val deviceVID = device.vendorId
                val devicePID = device.productId

                if (deviceVID != 0x1d6b && devicePID != 0x0001 && devicePID != 0x0002 && devicePID != 0x0003 && deviceVID != 0x5c6 && devicePID != 0x904c) {
                    // There is a device connected to our Android device. Try to open it as a Serial Port.
                    requestUserPermission()
                    keep = false
                }

                if (!keep)
                    break
            }
            if (!keep) {
                // There is no USB devices connected (but usb host were listed). Send an intent to MainActivity.
                sendBroadcast(Intent(ACTION_NO_USB))
            }
        } else {
            // There is no USB devices connected. Send an intent to MainActivity
            sendBroadcast(Intent(ACTION_NO_USB))
        }
    }

    private fun setFilter() {
        val filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(ACTION_USB_DETACHED)
        filter.addAction(ACTION_USB_ATTACHED)
        registerReceiver(usbReceiver, filter)
    }

    /*
     * Request user permission. The response will be received in the BroadcastReceiver
     */
    private fun requestUserPermission() {
        val mPendingIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
        usbManager.requestPermission(device, mPendingIntent)
    }

    inner class UsbBinder : Binder() {
        val service: UsbService
            get() = this@UsbService
    }

    /*
     * A simple thread to open a serial port.
     * Although it should be a fast operation. moving usb operations away from UI thread is a good thing.
     */
    private inner class ConnectionThread : Thread() {
        override fun run() {
            serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection)
            if (serialPort != null) {
                if (serialPort.open()) {
                    serialPortConnected = true
                    serialPort.setBaudRate(BAUD_RATE)
                    serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8)
                    serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1)
                    serialPort.setParity(UsbSerialInterface.PARITY_NONE)
                    /**
                     * Current flow control Options:
                     * UsbSerialInterface.FLOW_CONTROL_OFF
                     * UsbSerialInterface.FLOW_CONTROL_RTS_CTS only for CP2102 and FT232
                     * UsbSerialInterface.FLOW_CONTROL_DSR_DTR only for CP2102 and FT232
                     */
                    serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)
                    serialPort.read(mCallback)
                    serialPort.getCTS(ctsCallback)
                    serialPort.getDSR(dsrCallback)

                    //
                    // Some Arduinos would need some sleep because firmware wait some time to know whether a new sketch is going
                    // to be uploaded or not
                    //Thread.sleep(2000); // sleep some. YMMV with different chips.

                    // Everything went as expected. Send an intent to MainActivity
                    context.sendBroadcast(Intent(ACTION_USB_READY))
                } else {
                    // Serial port could not be opened, maybe an I/O error or if CDC driver was chosen, it does not really fit
                    // Send an Intent to Main Activity
                    if (serialPort is CDCSerialDevice) {
                        context.sendBroadcast(Intent(ACTION_CDC_DRIVER_NOT_WORKING))
                    } else {
                        context.sendBroadcast(Intent(ACTION_USB_DEVICE_NOT_WORKING))
                    }
                }
            } else {
                // No driver for given device, even generic CDC driver could not be loaded
                context.sendBroadcast(Intent(ACTION_USB_NOT_SUPPORTED))
            }
        }
    }


}