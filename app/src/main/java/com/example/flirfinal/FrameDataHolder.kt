package com.example.flirfinal

import android.graphics.Bitmap
import java.util.*

class FrameDataHolder(fusBitmap: Bitmap?, dcBitmap: Bitmap?) {
    //public final Bitmap msxBitmap;
    public lateinit var dcBitmap: Bitmap ;
    public lateinit var fusBitmap: Bitmap;

    fun FrameDataHolder( /*Bitmap msxBitmap,*/
        dcBitmap: Bitmap, fusBitmap: Bitmap
    ) {
        //this.msxBitmap = msxBitmap;
        this.dcBitmap = dcBitmap
        this.fusBitmap = fusBitmap
    }
}

// if you adding here a mode, add it also to the "CameraHandler" save image func. in the switch case
internal class FusionModesList {
    val List = ArrayList<String>()

    init {
        List.add("Thermal Only")
        List.add("Visual Only")
        List.add("Msx")
        List.add("Thermal Fusion")
        List.add("Blending")
        List.add("Picture In Picture")
        List.add("Color Night Vision")
    }
}
