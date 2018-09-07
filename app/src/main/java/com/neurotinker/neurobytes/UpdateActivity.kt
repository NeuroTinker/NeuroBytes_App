package com.neurotinker.neurobytes

import android.os.Bundle
import android.support.v7.app.AppCompatActivity

import kotlinx.android.synthetic.main.activity_update.*
import kotlinx.android.synthetic.main.content_update.*

const val UPDATE_INTENT = "com.neurontinker.neurobytes.UPDATE"

class UpdateActivity : AppCompatActivity() {

    /**
     * Update NeuroBytes firmware through NID
     *
     * Manages three different device state machines:
     * - Usb Connection - Connected / Disconnected
     * - NID - Unkown / SWD / UART / DFU
     * - Connected NeuroBytes Board - None / Reading Fingerprint / Flashing / Stopped (waiting for disconnect)
     *
     */

    private val flashService = UsbFlashService(this, 0x6018, 0x1d50)
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

    /**
     * Send a message to NID and send response to callback
     */
    private  fun sendMessage(message: ByteArray) {
        flashService.WriteData(GdbUtils.buildPacket(message))
    }

    private fun readMessageBlocking(): String? {
        // wait until data has been received
        var timeout = 0
        while (!flashService.IsThereAnyReceivedData()) {
            timeout += 1
            if (timeout > 50) {
                return null
            }
        }

        val message : ByteArray = flashService.GetReceivedDataFromQueue()

        return if (GdbUtils.isDataValid(message)) {
            if (GdbUtils.isEndOfMessage(message)) {
                sendMessage(GdbUtils.ACK)
            }

            GdbUtils.getMessageContent(message)
        } else {
            null
        }
    }

    private suspend fun connectToNid() : Boolean? {
        val connected = gdbController.openDevice()
        if (connected) {
            flashStatus.text = "connected"
        } else {
            return null
        }
        return connected
    }

    fun connectToGdb() {
        val connected = gdbController.openDevice()
        val initialized = false
        if (connected) {
            gdbController.initializeGdb()
            flashStatus.text = "connected"
        }
    }

}
