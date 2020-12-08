package com.example.flirfinal

import android.os.Environment
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

object textHandler {
    const val Data_Path = "/SIPL_FlirOne/data"
    const val Data_txt = "/data"
    const val Log_Path = "/SIPL_FlirOne/log"
    const val Log_txt = "/log"
    const val Fusion_Mode_Line = "FUSION_MODE"

    // todo: make the hole class run on a fread
    fun append_txt(
        Path: String,
        name: String,
        append_line: String,
        append_data: String
    ) {
        val FullPath = "$Path$name.txt"
        // stream
        var inputStream: InputStream? = null
        var data = ""

        // create path
        val filepath = Environment.getExternalStorageDirectory()
        val file = File(filepath.absolutePath + FullPath)

        // read the txt
        try {
            inputStream = FileInputStream(file)
            val isr = InputStreamReader(inputStream)
            val br = BufferedReader(isr)
            val sb = StringBuffer()
            var text = ""
            while (br.readLine().also { text = it } != null) {
                if (text.split(":").toTypedArray()[0] == "DATE") {
                    val timeStamp =
                        SimpleDateFormat("dd-MM-yyyy__HH-mm-ss-SSS")
                            .format(Date())
                    text = "DATE:$timeStamp"
                }
                sb.append(text).append("\n")
            }

            //append data
            text = "$append_line:$append_data"
            sb.append(text).append("\n")
            data = sb.toString()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            // close stream
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

        // save the data that was change
        if (data != "") {
            save_txt(Path, name, data)
        }
    }

    fun clear_txt(Path: String, name: String) {
        save_txt(Path, name, "")
    }

    fun save_txt(Path: String, name: String, data: String) {
        // stream
        var outputStream: OutputStream? = null

        // create path
        val filepath = Environment.getExternalStorageDirectory()
        val dir = File(filepath.absolutePath + Path)
        dir.mkdir()

        // create files path
        val file = File(dir, "$name.txt")

        // save the text
        try {
            outputStream = FileOutputStream(file)
            outputStream.write(data.toByteArray())
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            // close stream
            if (outputStream != null) {
                try {
                    outputStream.flush()
                    outputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun load_txt(Path: String): String {
        // stream
        var inputStream: FileInputStream? = null
        var data = ""

        // create path
        val filepath = Environment.getExternalStorageDirectory()
        val file = File(filepath.absolutePath + Path)

        // read the txt
        try {
            inputStream = FileInputStream(file)
            val isr = InputStreamReader(inputStream)
            val br = BufferedReader(isr)
            val sb = StringBuffer()
            var text: String?
            while (br.readLine().also { text = it } != null) {
                sb.append(text).append("\n")
            }
            data = sb.toString()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            // close stream
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

        //return the txt that was read
        return data
    }

    fun update_line_txt(
        Path: String,
        FileName: String,
        line_name: String,
        rewrite: String
    ) {
        val FullPath = "$Path$FileName.txt"
        // stream
        var inputStream: InputStream? = null
        var data = ""
        var rewrite_flag = false

        // create path
        val filepath = Environment.getExternalStorageDirectory()
        val file = File(filepath.absolutePath + FullPath)

        // read the txt
        try {
            inputStream = FileInputStream(file)
            val isr = InputStreamReader(inputStream)
            val br = BufferedReader(isr)
            val sb = StringBuffer()
            var text = ""
            while (br.readLine().also { text = it } != null) {
                if (text.split(":").toTypedArray()[0] == "DATE") {
                    val timeStamp =
                        SimpleDateFormat("dd-MM-yyyy__HH-mm-ss-SSS")
                            .format(Date())
                    text = "DATE:$timeStamp"
                } else if (text.split(":").toTypedArray()[0] == line_name) {
                    text = "$line_name:$rewrite"
                    rewrite_flag = true
                }
                sb.append(text).append("\n")
            }

            // if there is no line with this name
            if (!rewrite_flag) {
                text = "$line_name:$rewrite"
                sb.append(text).append("\n")
            }
            data = sb.toString()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            // close stream
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

        // save the data that was change
        if (data != "") {
            save_txt(Path, FileName, data)
        }
    }

    fun find_line_txt(
        Path: String,
        FileName: String,
        line_name: String
    ): String? {
        val FullPath = "$Path$FileName.txt"
        // stream
        var inputStream: InputStream? = null
        var data: String? = null

        // create path
        val filepath = Environment.getExternalStorageDirectory()
        val file = File(filepath.absolutePath + FullPath)

        // read the txt
        try {
            inputStream = FileInputStream(file)
            val isr = InputStreamReader(inputStream)
            val br = BufferedReader(isr)
            var text = ""

            // find the line
            while (br.readLine().also { text = it } != null) {
                if (text.split(":").toTypedArray()[0] == line_name) {
                    data = text.split(":").toTypedArray()[1]
                }
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            // close stream
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        return data
    }
}
