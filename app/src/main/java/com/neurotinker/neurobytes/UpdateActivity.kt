package com.neurotinker.neurobytes

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.format.Time

import kotlinx.android.synthetic.main.activity_update.*
import kotlinx.android.synthetic.main.content_update.*
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch

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
    var isConnectedToNid : Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update)
        setSupportActionBar(toolbar)

        connectToNidButton.setOnClickListener {
            launch(UI) {
//                isConnectedToNid = connectToNid()
                status.text = "test"
            }
        }

        initializeGdbButton.setOnClickListener {
            launch {
                if (isConnectedToNid) {
                    var initialized : Boolean = false
                    async{ initialized = initializeGdb() != null }
                    if (initialized) {
                        status.setText("GDB initialized. OK.")
                    } else {
                        status.setText("Failed to initialize GDB")
                    }
                }
            }
        }

        connectToNbButton.setOnClickListener {

        }

        flashButton.setOnClickListener{
//            if (gdbController.state == GdbController.State.INITIALIZED) {
//                gdbController.startFlash(flashStatus, fingerprintStatus)
//                flashStatus.setText("initializing now...")
//            } else {
//                flashStatus.setText("not initialized yet")
//                connectToGdb()
//            }
//            setSupportActionBar(toolbar)
        }

        cancelButton.setOnClickListener {

        }

        updateFirmware.setOnClickListener{
            Firmware.updatePath(filesDir.path)
            Firmware.UpdateFirmwareAsyncTask().execute()
        }
    }

    override fun onPause() {
        super.onPause()

        disconnectNid()
    }

    /**
     * Wait until a message has been received.
     * Checks if the message is valid and returns the message string.
     */
    private suspend fun readMessageBlocking(): String? {
        // wait until data has been received
        var timeout = 0
        while (!flashService.IsThereAnyReceivedData()) {
            if (timeout++ > 50) {
                return null
            }
            delay(10L) // wait 10 milliseconds
        }

        val message : ByteArray = flashService.GetReceivedDataFromQueue()

        return if (GdbUtils.isDataValid(message)) {
            if (GdbUtils.isEndOfMessage(message)) {
                flashService.WriteData(GdbUtils.ACK)
            }

            GdbUtils.getMessageContent(message)
        } else {
            null
        }
    }

    /**
     * Execute a sequence of GDB commands.
     * Each GDB response message is validated by responseValidater
     * and a return value is determined using valueFilter
     */
    private suspend fun executeGdbSequence(
            messageSeq: List<ByteArray>,
            responseValidater: (String) -> Boolean,
            valueFilter: (String) -> String = { it }
    ) : String? {

        var response : String? = null
        for (message in messageSeq) {
            response = readMessageBlocking()
            if (response == null) {
                return null
            } else if (responseValidater(response)) {
                continue
            }
        }
        // value filter is performed for LAST message response
        return response?.let { valueFilter(response) }
    }

    /**
     * Send a message to NID and validate response
     */
    private suspend fun sendMessage(message: ByteArray) {
        flashService.WriteData(GdbUtils.buildPacket(message))

    }

    /**
     * Sends initialization commands to NID via gdb.
     * Returns true for success
     */
    private suspend fun initializeGdb() : String? {
        val messageSeq : List<ByteArray> = GdbUtils.getGdbInitSequence()
        val responseValidater : (String) -> Boolean = {
            it.contains("OK")
        }

        return executeGdbSequence(messageSeq, responseValidater)

    }

    private suspend fun connectToNb() {

    }

    private suspend fun connectToNid() : Boolean {
        val connected = flashService.OpenDevice()
        return if (connected) {
//            flashStatus.text = "connected"
            true
        } else {
//            flashStatus.text = "unable to connect to NID"
            false
        }
    }

    private fun disconnectNid() {
        flashService.CloseTheDevice()
    }

}
