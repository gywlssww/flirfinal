package com.example.flirfinal

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.flir.thermalsdk.ErrorCode
import com.flir.thermalsdk.androidsdk.BuildConfig
import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid
import com.flir.thermalsdk.androidsdk.live.connectivity.UsbPermissionHandler
import com.flir.thermalsdk.androidsdk.live.connectivity.UsbPermissionHandler.UsbPermissionListener
import com.flir.thermalsdk.live.CommunicationInterface
import com.flir.thermalsdk.live.Identity
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener
import com.flir.thermalsdk.log.ThermalLog.LogLevel
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

/**
 * Sample application for scanning a FLIR ONE or a built in emulator
 *
 *
 * See the [CameraHandler] for how to preform discovery of a FLIR ONE camera, connecting to it and start streaming images
 *
 *
 * The MainActivity is primarily focused to "glue" different helper classes together and updating the UI components
 *
 *
 * Please note, this is **NOT** production quality code, error handling has been kept to a minimum to keep the code as clear and concise as possible
 */
public class MainActivity : AppCompatActivity() {
    // saving stream
    var outputStream: OutputStream? = null

    //Handles network camera operations
    private lateinit var cameraHandler: CameraHandler
    private var connectedIdentity: Identity? =null
    private lateinit var connectionStatus: TextView
    private lateinit var discoveryStatus: TextView
    private lateinit var fusImage: ImageView
    private lateinit var dcImage: ImageView
    private var fusImageBitmap: Bitmap? =null
    private var dcImageBitmap: Bitmap? =null

    // saved image DC
    private lateinit var photoImageSaved: ImageView
    private var framesBuffer: LinkedBlockingQueue<FrameDataHolder> = LinkedBlockingQueue(21)
    private var usbPermissionHandler = UsbPermissionHandler()

    // what mode to use
    private var ThermalMode: String = "Thermal Only"
    private lateinit var thermalModeText: TextView

    /**
     * Show message on the screen
     */
    interface ShowMessage {
        fun show(message: String?)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val enableLoggingInDebug = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.NONE

        //ThermalSdkAndroid has to be initiated from a Activity with the Application Context to prevent leaking Context,
        // and before ANY using any ThermalSdkAndroid functions
        //ThermalLog will show log from the Thermal SDK in standards android log framework
        ThermalSdkAndroid.init(applicationContext, enableLoggingInDebug)
        cameraHandler = CameraHandler()

        // build txt file for data use
        setDataFile()
        dataReadThermalMode()

        // to update the page
        setupViews()
        showSDKversion(ThermalSdkAndroid.getVersion())
    }

    private fun dataReadThermalMode() {
        // read ThermalMode
        val temp: String? = textHandler.find_line_txt(textHandler.Data_Path, textHandler.Data_txt, textHandler.Fusion_Mode_Line)
        if (temp != null) {
            ThermalMode = temp
        } else {
            ThermalMode = "Thermal Only"
            Log.d(TAG, "fail to load data from txt file")
        }
    }

    private fun setDataFile() {
        // check if file is not already exist
        val filepath =applicationContext.getExternalFilesDir(null)
        val file = File(filepath!!.absolutePath + textHandler.Data_Path + textHandler.Data_txt.toString() + ".txt")
        if (file == null || !file.exists()) {
            val timeStamp = SimpleDateFormat("dd-MM-yyyy__HH-mm-ss-SSS").format(Date())
            val fusionMode: String = textHandler.Fusion_Mode_Line.toString() + ":Thermal Only"
            val text = "DATE:$timeStamp\n$fusionMode"
            textHandler.save_txt(textHandler.Data_Path, textHandler.Data_txt, text)
        }
    }

    fun startDiscovery(view: View?) {
        startDiscovery()

        // delay before connect to the camera
        val handler = Handler()
        handler.postDelayed({ connect(cameraHandler!!.getFlirOne()) }, 1000)
    }

    fun stopDiscovery(view: View?) {
        disconnect()

        // delay before disconnect from the camera
        val handler = Handler()
        handler.postDelayed({ stopDiscovery() }, 500)
    }

    /**
     * Connect to a Camera
     */
    private fun connect(identity: Identity?) {
        //We don't have to stop a discovery but it's nice to do if we have found the camera that we are looking for
        cameraHandler!!.stopDiscovery(discoveryStatusListener)
        if (connectedIdentity != null) {
            Log.d(TAG, "connect(), in *this* code sample we only support one camera connection at the time")
            showMessage.show("connect(), in *this* code sample we only support one camera connection at the time")
            return
        }
        if (identity == null) {
            Log.d(TAG, "connect(), can't connect, no camera available")
            showMessage.show("connect(), can't connect, no camera available")
            return
        }
        connectedIdentity = identity
        updateConnectionText(identity, "CONNECTING")
        //IF your using "USB_DEVICE_ATTACHED" and "usb-device vendor-id" in the Android Manifest
        // you don't need to request permission, see documentation for more information
        if (UsbPermissionHandler.isFlirOne(identity)) {
            usbPermissionHandler.requestFlirOnePermisson(identity, this, permissionListener)
        } else {
            doConnect(identity)
        }
    }

    private val permissionListener: UsbPermissionListener = object : UsbPermissionListener {
        override fun permissionGranted(identity: Identity) {
            doConnect(identity)
        }

        override fun permissionDenied(identity: Identity) {
            showMessage.show("Permission was denied for identity ")
        }

        override fun error(errorType: UsbPermissionListener.ErrorType, identity: Identity) {
            showMessage.show("Error when asking for permission for FLIR ONE, error:$errorType identity:$identity")
        }
    }

    private fun doConnect(identity: Identity) {
        Thread(Runnable {
            try {
                cameraHandler!!.connect(identity, connectionStatusListener)
                runOnUiThread {
                    updateConnectionText(identity, "CONNECTED")
                    cameraHandler!!.startStream(streamDataListener)
                }
            } catch (e: IOException) {
                runOnUiThread {
                    Log.d(TAG, "Could not connect: $e")
                    updateConnectionText(identity, "DISCONNECTED")
                }
            }
        }).start()
    }

    /**
     * Disconnect to a camera
     */
    private fun disconnect() {
        updateConnectionText(connectedIdentity, "DISCONNECTING")
        connectedIdentity = null
        Log.d(TAG, "disconnect() called with: connectedIdentity = [$connectedIdentity]")
        Thread(Runnable {
            cameraHandler!!.disconnect()
            runOnUiThread { updateConnectionText(null, "DISCONNECTED") }
        }).start()
    }

    /**
     * Update the UI text for connection status
     */
    private fun updateConnectionText(identity: Identity?, status: String) {
        val deviceId = identity?.deviceId ?: ""
        connectionStatus!!.text = getString(R.string.connection_status_text, "$deviceId $status")
    }

    /**
     * Start camera discovery
     */
    private fun startDiscovery() {
        cameraHandler!!.startDiscovery(cameraDiscoveryListener, discoveryStatusListener)
    }

    /**
     * Stop camera discovery
     */
    private fun stopDiscovery() {
        cameraHandler!!.stopDiscovery(discoveryStatusListener)
    }

    /**
     * Callback for discovery status, using it to update UI
     */
    private val discoveryStatusListener: CameraHandler.DiscoveryStatus = object : CameraHandler.DiscoveryStatus {
        override fun started() {
            discoveryStatus!!.text = getString(R.string.connection_status_text, "discovering")
        }

        override fun stopped() {
            discoveryStatus!!.text = getString(R.string.connection_status_text, "not discovering")
        }
    }

    /**
     * Camera connecting state thermalImageStreamListener, keeps track of if the camera is connected or not
     *
     *
     * Note that callbacks are received on a non-ui thread so have to eg use [.runOnUiThread] to interact view UI components
     */
    private val connectionStatusListener = ConnectionStatusListener { errorCode ->
        Log.d(TAG, "onDisconnected errorCode:$errorCode")
        runOnUiThread { updateConnectionText(connectedIdentity, "DISCONNECTED") }
    }
    private val streamDataListener: CameraHandler.StreamDataListener = object : CameraHandler.StreamDataListener {


        override fun images(dataHolder: FrameDataHolder?) {
            runOnUiThread {
                fusImage!!.setImageBitmap(dataHolder!!.fusBitmap)
                dcImage!!.setImageBitmap(dataHolder.dcBitmap)
                fusImageBitmap = dataHolder.fusBitmap
                dcImageBitmap = dataHolder.dcBitmap
            }
        }

        override fun images(dcBitmap: Bitmap?, fusBitmap: Bitmap?) {
            try {
                framesBuffer.put(FrameDataHolder(dcBitmap, fusBitmap))
            } catch (e: InterruptedException) {
                //if interrupted while waiting for adding a new item in the queue
                Log.e(TAG, "images(), unable to add incoming images to frames buffer, exception:$e")
            }
            runOnUiThread {
                Log.d(TAG, "framebuffer size:" + framesBuffer.size)
                val poll: FrameDataHolder? = framesBuffer.poll()
                fusImage!!.setImageBitmap(poll!!.fusBitmap)
                dcImage!!.setImageBitmap(poll!!.dcBitmap)
                fusImageBitmap = poll.fusBitmap
                dcImageBitmap = poll.dcBitmap
            }
        }
    }

    /**
     * Camera Discovery thermalImageStreamListener, is notified if a new camera was found during a active discovery phase
     *
     *
     * Note that callbacks are received on a non-ui thread so have to eg use [.runOnUiThread] to interact view UI components
     */
    private val cameraDiscoveryListener: DiscoveryEventListener = object : DiscoveryEventListener {
        override fun onCameraFound(identity: Identity) {
            Log.d(TAG, "onCameraFound identity:$identity")
            runOnUiThread { cameraHandler.add(identity) }
        }

        override fun onDiscoveryError(communicationInterface: CommunicationInterface, errorCode: ErrorCode) {
            Log.d(TAG, "onDiscoveryError communicationInterface:$communicationInterface errorCode:$errorCode")
            runOnUiThread {
                stopDiscovery()
                showMessage.show("onDiscoveryError communicationInterface:$communicationInterface errorCode:$errorCode")
            }
        }
    }
    private val showMessage: ShowMessage = object : ShowMessage {
        override fun show(message: String?) {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSDKversion(version: String) {
        val sdkVersionTextView = findViewById<TextView>(R.id.sdk_version)
        val sdkVersionText = getString(R.string.sdk_version_text, version)
        sdkVersionTextView.text = sdkVersionText
    }

    private fun setupViews() {
        connectionStatus = findViewById(R.id.connection_status_text)
        discoveryStatus = findViewById(R.id.discovery_status)
        fusImage = findViewById(R.id.msx_image)
        dcImage = findViewById(R.id.photo_image)
        photoImageSaved = findViewById(R.id.photo_image_saved)

        // button mode
        thermalModeText = findViewById(R.id.change_mode)
        thermalModeText.setText(ThermalMode)
    }

    // todo: make this function run on a fread
    fun saveImage(view: View?) {
        // get image
        if (dcImageBitmap == null || fusImageBitmap == null) {
            Toast.makeText(applicationContext, "no image exist yet", Toast.LENGTH_LONG).show()
            return
        }
        val dcSave: Bitmap = dcImageBitmap as Bitmap
        val fusSave: Bitmap = fusImageBitmap as Bitmap
        photoImageSaved!!.setImageBitmap(dcSave) // update the user "save" photo

        // load path
        val filepath = Environment.getExternalStorageDirectory()
        // create main dir
        val dir = File(filepath.absolutePath + "/SIPL_FlirOne/")
        dir.mkdir()
        // create sub dirs
        val dir_dc = File(filepath.absolutePath + "/SIPL_FlirOne/dc")
        dir_dc.mkdir()
        val dir_fus = File(filepath.absolutePath + "/SIPL_FlirOne/fus")
        dir_fus.mkdir()
        // create files path
        val timeStamp = SimpleDateFormat("dd-MM-yyyy__HH-mm-ss-SSS").format(Date())
        val file_dc = File(dir_dc, "DC_$timeStamp.png")
        val file_fus = File(dir_fus, ThermalMode + "_" + timeStamp + ".png")

        // todo: change PDF to - PNG, BMP, TIFF. DONE!
        // create stream to the path and save the image
        try {
            // create the stream
            outputStream = FileOutputStream(file_dc)
            // save image
            dcSave.compress(Bitmap.CompressFormat.PNG, 100, outputStream)

            // for fus image
            outputStream = FileOutputStream(file_fus)
            fusSave.compress(Bitmap.CompressFormat.PNG, 100, outputStream)


            // success saving all the image
            Toast.makeText(applicationContext, "save in - $dir", Toast.LENGTH_SHORT).show()
            // Toast.makeText(getApplicationContext(), "bitmap size:" + dcSave.getByteCount(), Toast.LENGTH_SHORT).show();
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            Toast.makeText(applicationContext, "can't access file stream", Toast.LENGTH_LONG).show()
        } finally {
            // close stream
            if (outputStream != null) {
                try {
                    outputStream!!.flush()
                    outputStream!!.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun open_activity_changeMode(v: View?) {
        val intent = Intent(this, ChangeThermalMode::class.java)
        intent.putExtra("ThermalMode", ThermalMode)
        startActivityForResult(intent, 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK) {
                ThermalMode = data!!.getStringExtra("ThermalMode").toString()
                thermalModeText!!.text = ThermalMode
            }
        }
    }

    companion object {
        //todo: creat video
        //todo: find a way to get raw thermal image
        //todo: keep main activity running when we open a new activity
        //todo: make flir-one auto-connect
        private const val TAG = "MainActivity"
    }
}