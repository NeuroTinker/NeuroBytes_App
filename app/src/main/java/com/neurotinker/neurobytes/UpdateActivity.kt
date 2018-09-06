package com.neurotinker.neurobytes

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.Gravity
import android.widget.PopupWindow
import com.neurotinker.neurobytes.R.layout.flashing_popup

import kotlinx.android.synthetic.main.activity_update.*
import kotlinx.android.synthetic.main.content_update.*
import kotlinx.android.synthetic.main.flashing_popup.*

const val UPDATE_INTENT = "com.neurontinker.neurobytes.UPDATE"

class UpdateActivity : AppCompatActivity() {

    val flashService = UsbFlashService(this, 0x6018, 0x1d50)
    val gdbController = GdbController(flashService!!)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update)
        setSupportActionBar(toolbar)

        // check NID firmware version then enable neurobytes firmware updates
        // update stored firmware versions from online sources
        Firmware.updatePath(filesDir.path)
        Firmware.UpdateFirmwareAsyncTask().execute()

        connectToGdb()

        flashButton.setOnClickListener{
            if (gdbController.state == GdbController.State.INITIALIZED) {
                gdbController.startFlash(flashStatus, fingerprintStatus)
                flashStatus.setText("initializing now...")
            } else {
                flashStatus.setText("not initialized yet")
                connectToGdb()
            }
            setSupportActionBar(toolbar)
        }

        cancelButton.setOnClickListener {
            gdbController.stopFlash()
        }

        updateFirmware.setOnClickListener{
            Firmware.UpdateFirmwareAsyncTask().execute()
        }
    }

    fun connectToGdb() {
        val connected = gdbController.openDevice()
        val initialized = false
        if (connected) {
            gdbController.initializeGdb()
            flashStatus.setText("connected")
        }
    }

}
