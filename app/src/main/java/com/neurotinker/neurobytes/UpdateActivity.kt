package com.neurotinker.neurobytes

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.felhr.utils.HexData

import kotlinx.android.synthetic.main.activity_update.*
import kotlinx.android.synthetic.main.content_update.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.awaitAll
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
    private val TAG = "UpdateActivity"
    var isConnectedToNid : Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update)
        setSupportActionBar(toolbar)

        connectToNidButton.setOnClickListener {
            launch(UI) {
                isConnectedToNid = connectToNid()
                if (isConnectedToNid) {
                    status.text = "Successfully connected to NID"
                    flashService.StartReadingThread()
                } else {
                    status.text = "Failed to connect to NID"
                }
            }
        }

        initializeGdbButton.setOnClickListener {
            launch(UI) {
                if (isConnectedToNid) {
//                    var initialized = false
//                    initialized = async{ initialized = initializeGdb() != null }
//                    (async{ initializeGdb() }).await()
                    if ((async{ initializeGdb() }).await() != null) {
                        status.text = "GDB initialized. OK."
                    } else {
                        status.text = "Failed to initialize GDB"
                    }
                }
            }
        }

        detectNbButton.setOnClickListener {
            launch(UI) {
                if (isConnectedToNid) {
                    if ((async{ detectNb() }).await()) {
                        status.text = "Detected a NeuroBytes board"
                    } else {
                        status.text = "No NeuroBytes board detected"
                    }
                }
            }
        }

        enterSwdButton.setOnClickListener {
            launch(UI) {
                if (isConnectedToNid) {
                    if ((async{ enterSwd() }).await()) {
                        status.text = "Entered SWD mode"
                    } else {
                        status.text = "Failed to enter SWD mode"
                    }
                }
            }
        }

//        flashButton.setOnClickListener{
//            if (gdbController.state == GdbController.State.INITIALIZED) {
//                gdbController.startFlash(flashStatus, fingerprintStatus)
//                flashStatus.setText("initializing now...")
//            } else {
//                flashStatus.setText("not initialized yet")
//                connectToGdb()
//            }
//            setSupportActionBar(toolbar)
//        }

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
    private suspend fun readMessageBlocking(responseValidator: (String) -> Boolean): String? {
        // wait until data has been received
        var timeout = 0
        var message = ByteArray(64)
        while (!responseValidator(GdbUtils.getAsciiFromMessageBytes(message))) {
            if (flashService.IsThereAnyReceivedData()) {
                if (!GdbUtils.isDataValid(message)) return null
                Log.d(TAG, "received data")
                message = flashService.GetReceivedDataFromQueue()
                if (GdbUtils.isEndOfMessage(message)) {
                    flashService.WriteData(GdbUtils.ACK)
                }
            }
            if (timeout++ > 50) {
                Log.d(TAG, "readMessageBlocking() timed out")
                return null
            }
            delay(10L) // wait 10 milliseconds
        }

//        flashService.WriteData(GdbUtils.ACK)

        return GdbUtils.getMessageContent(message)

    }

    /**
     * Execute a sequence of GDB commands.
     * Each GDB response message is validated by responseValidator
     * and a return value is determined using valueFilter
     */
    private suspend fun executeGdbSequence(
            messageSeq: List<ByteArray>,
            responseValidator: (String) -> Boolean,
            valueFilter: (String) -> String = { it }
    ) : String? {
        var response : String? = null
        for (message in messageSeq) {
            sendMessage(message)
            response = readMessageBlocking(responseValidator)
            Log.d(TAG, "Response received: $response")
            if (response == null) {
                return null
            } else if (responseValidator(response)) {
                continue
            }
        }
        // value filter is performed for LAST message response
        Log.d(TAG, response)
        return response?.let { valueFilter(response) }
    }

    /**
     * Send a message to NID and validate response
     */
    private fun sendMessage(message: ByteArray) {
        Log.d(TAG, "sendMessage() sent: " + HexData.hexToString(message))
        flashService.WriteData(GdbUtils.buildPacket(message))
    }

    /**
     * Sends initialization commands to NID via gdb.
     * Returns true for success
     */
    private suspend fun initializeGdb() : String? {
        val messageSeq : List<ByteArray> = GdbUtils.getGdbInitSequence()
        val responseValidator : (String) -> Boolean = {
            it.contains("OK")
        }

        return executeGdbSequence(messageSeq, responseValidator)
    }

    private suspend fun detectNb() : Boolean {
        val messageSeq : List<ByteArray> = GdbUtils.getGdbDetectSequence()
        val responseValidator : (String) -> Boolean = {
            it.contains("OK") || it.contains("E") || it.contains("T05")
        }

        val response : String = executeGdbSequence(messageSeq, responseValidator) ?: return false
        return response.contains("T05")
    }

    private suspend fun enterSwd() : Boolean {
        val messageSeq : List<ByteArray> = GdbUtils.getEnterSwdSequence()
        val responseValidator : (String) -> Boolean = {
            it.contains("OK")
        }

        val response : String = executeGdbSequence(messageSeq, responseValidator) ?: return false
        return response.contains("OK")
    }

    /**
     * Do we want these GDB functions to return success flags or
     * status strings?
     *
     * Probably abstract status strings to state variables that have
     * strings and enabled-button state
     */

    private suspend fun connectToNid() : Boolean {
        return flashService.OpenDevice()
    }

    private fun disconnectNid() {
        flashService.CloseTheDevice()
    }

}
