package com.example.flirfinal

import android.content.Context
import java.io.File

class FileHandler {
    private var filesDir: File? = null

    fun FileHandler(applicationContext: Context) {
        filesDir = applicationContext.filesDir
    }

    fun getImageStoragePathStr(): String? {
        return filesDir!!.absolutePath
    }

    fun getImageStoragePath(): File? {
        return filesDir
    }

}
