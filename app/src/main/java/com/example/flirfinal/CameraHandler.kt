package com.example.flirfinal

import android.graphics.Bitmap
import android.util.Log
import com.flir.thermalsdk.androidsdk.image.BitmapAndroid
import com.flir.thermalsdk.image.ThermalImage
import com.flir.thermalsdk.image.fusion.FusionMode
import com.flir.thermalsdk.live.Camera
import com.flir.thermalsdk.live.CommunicationInterface
import com.flir.thermalsdk.live.ConnectParameters
import com.flir.thermalsdk.live.Identity
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener
import com.flir.thermalsdk.live.discovery.DiscoveryFactory
import com.flir.thermalsdk.live.streaming.ThermalImageStreamListener
import java.io.IOException
import java.util.*

class CameraHandler {


    private val TAG = "CameraHandler"

    private var streamDataListener: StreamDataListener? = null

    interface StreamDataListener {
        fun images(dataHolder: FrameDataHolder?)
        fun images(
            dcBitmap: Bitmap?,
            fusBitmap: Bitmap?
        )
    }

    //Discovered FLIR cameras
    var foundCameraIdentities =
        LinkedList<Identity>()

    //A FLIR Camera
    private lateinit var camera:Camera


    interface DiscoveryStatus {
        fun started()
        fun stopped()
    }

    fun CameraHandler() {}

    /**
     * Start discovery of USB and Emulators
     */
    fun startDiscovery(
        cameraDiscoveryListener: DiscoveryEventListener?,
        discoveryStatus: DiscoveryStatus
    ) {
        DiscoveryFactory.getInstance().scan(
            cameraDiscoveryListener!!,
            CommunicationInterface.EMULATOR,
            CommunicationInterface.USB
        )
        discoveryStatus.started()
    }

    /**
     * Stop discovery of USB and Emulators
     */
    fun stopDiscovery(discoveryStatus: DiscoveryStatus) {
        DiscoveryFactory.getInstance()
            .stop(CommunicationInterface.EMULATOR, CommunicationInterface.USB)
        discoveryStatus.stopped()
    }

    @Throws(IOException::class)
    public fun connect(
        identity: Identity,
        connectionStatusListener: ConnectionStatusListener
    ) {
        camera = Camera()
        camera.connect(identity,connectionStatusListener,null)

    }

    fun disconnect() {
        if (camera == null) {
            return
        }
        if (camera!!.isGrabbing) {
            camera!!.unsubscribeAllStreams()
        }
        camera!!.disconnect()
    }


    /**
     * Start a stream of [ThermalImage]s images from a FLIR ONE or emulator
     */
    fun startStream(listener: StreamDataListener?) {
        streamDataListener = listener
        camera!!.subscribeStream(thermalImageStreamListener)
    }

    /**
     * Stop a stream of [ThermalImage]s images from a FLIR ONE or emulator
     */
    fun stopStream(listener: ThermalImageStreamListener?) {
        camera!!.unsubscribeStream(listener)
    }

    /**
     * Add a found camera to the list of known cameras
     */
    fun add(identity: Identity) {
        foundCameraIdentities.add(identity)
    }

    operator fun get(i: Int): Identity? {
        return foundCameraIdentities[i]
    }

    /**
     * Get a read only list of all found cameras
     */
    fun getCameraList(): List<Identity?>? {
        return Collections.unmodifiableList(foundCameraIdentities)
    }

    /**
     * Clear all known network cameras
     */
    fun clear() {
        foundCameraIdentities.clear()
    }

    fun getCppEmulator(): Identity? {
        for (foundCameraIdentity in foundCameraIdentities) {
            if (foundCameraIdentity.deviceId.contains("C++ Emulator")) {
                return foundCameraIdentity
            }
        }
        return null
    }

    fun getFlirOneEmulator(): Identity? {
        for (foundCameraIdentity in foundCameraIdentities) {
            if (foundCameraIdentity.deviceId.contains("EMULATED FLIR ONE")) {
                return foundCameraIdentity
            }
        }
        return null
    }

    fun getFlirOne(): Identity? {
        for (foundCameraIdentity in foundCameraIdentities) {
            val isFlirOneEmulator =
                foundCameraIdentity.deviceId.contains("EMULATED FLIR ONE")
            val isCppEmulator =
                foundCameraIdentity.deviceId.contains("C++ Emulator")
            if (!isFlirOneEmulator && !isCppEmulator) {
                return foundCameraIdentity
            }
        }
        return null
    }

    private fun withImage(
        listener: ThermalImageStreamListener,
        functionToRun: Camera.Consumer<ThermalImage>
    ) {
        camera!!.withImage(listener, functionToRun)
    }


    /**
     * Called whenever there is a new Thermal Image available, should be used in conjunction with [Camera.Consumer]
     */
    private val thermalImageStreamListener: ThermalImageStreamListener =
        object : ThermalImageStreamListener {
            override fun onImageReceived() {
                //Will be called on a non-ui thread
                Log.d(TAG, "onImageReceived(), we got another ThermalImage")
                withImage(this, handleIncomingImage)
            }
        }

    /**
     * Function to process a Thermal Image and update UI
     */
    private val handleIncomingImage: Camera.Consumer<ThermalImage> =
        object : Camera.Consumer<ThermalImage> {
            override fun accept(thermalImage: ThermalImage) {
                Log.d(
                    TAG,
                    "accept() called with: thermalImage = [" + thermalImage.description + "]"
                )
                //Will be called on a non-ui thread,
                // extract information on the background thread and send the specific information to the UI thread

                //Get a bitmap with the fusion mode selected
                var fusBitmap: Bitmap
                run {
                    when (textHandler.find_line_txt(
                        textHandler.Data_Path,
                        textHandler.Data_txt,
                        textHandler.Fusion_Mode_Line
                    )) {
                        "Thermal Only" -> thermalImage.fusion!!.setFusionMode(FusionMode.THERMAL_ONLY)
                        "Visual Only" -> thermalImage.fusion!!.setFusionMode(FusionMode.VISUAL_ONLY)
                        "Msx" -> thermalImage.fusion!!.setFusionMode(FusionMode.MSX)
                        "Fusion" -> thermalImage.fusion!!.setFusionMode(FusionMode.THERMAL_FUSION)
                        "Blending" -> thermalImage.fusion!!.setFusionMode(FusionMode.BLENDING)
                        "Picture In Picture" -> thermalImage.fusion!!.setFusionMode(FusionMode.PICTURE_IN_PICTURE)
                        "Color Night Vision" -> thermalImage.fusion!!.setFusionMode(FusionMode.COLOR_NIGHT_VISION)
                        else -> {
                            thermalImage.fusion!!.setFusionMode(FusionMode.VISUAL_ONLY)
                            Log.d(
                                TAG,
                                "Selected mode is not defined, set default to VISUAL"
                            )
                        }
                    }
                    val tmp = thermalImage.image
                    val anBitmap = BitmapAndroid.createBitmap(tmp)
                    fusBitmap = anBitmap.bitMap
                }

                //Get a bitmap with only DC
                //Get a bitmap with the visual image, it might have different dimensions then the bitmap from THERMAL_ONLY
                var dcBitmap: Bitmap?
                run {
                    thermalImage.fusion!!.setFusionMode(FusionMode.VISUAL_ONLY)
                    dcBitmap =
                        BitmapAndroid.createBitmap(thermalImage.fusion!!.photo!!).bitMap
                }
                Log.d(TAG, "adding images to cache")
                streamDataListener!!.images(dcBitmap, fusBitmap)
            }
        }


}


