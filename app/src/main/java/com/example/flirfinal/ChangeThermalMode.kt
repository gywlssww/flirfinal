package com.example.flirfinal

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class ChangeThermalMode : AppCompatActivity() {
    private val TAG = "ChangeModeActivity"
    private var ThermalMode: String = ""
    private lateinit var modeStatus: TextView

    // todo: clear instend here and in the main, from transform data
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_thermal_mode)
        // get data
        val intent = intent
        ThermalMode = intent.getStringExtra("ThermalMode").toString()

        // crete list
        val list =
            findViewById<View>(R.id.ModeList) as ListView
        Log.d(TAG, "onCreate: Started")

        // create list of fusion mode
        val fusion_mode = FusionModesList()
        val modes = ArrayList(fusion_mode.List)
        val adapter: ArrayAdapter<String> = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, modes)
        list.adapter = adapter

        //crate text
        modeStatus = findViewById(R.id.ModeStatus)
        modeStatus.setText("SelectedMode: $ThermalMode")

        // onclick list
        list.onItemClickListener =
            OnItemClickListener { adapterView, view, i, l ->
                Log.d(TAG, "onItemClick: Mode - " + modes[i])
                ThermalMode = modes[i]
                modeStatus.setText("SelectedMode: $ThermalMode")
                textHandler.update_line_txt(
                    textHandler.Data_Path,
                    textHandler.Data_txt,
                    textHandler.Fusion_Mode_Line,
                    ThermalMode
                )
            }
    }

    fun finish(v: View?) {
        val changeIntent = Intent()
        changeIntent.putExtra("ThermalMode", ThermalMode)
        setResult(Activity.RESULT_OK, changeIntent)
        finish()
    }
}