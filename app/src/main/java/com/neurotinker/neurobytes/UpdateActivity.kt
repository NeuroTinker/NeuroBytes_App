package com.neurotinker.neurobytes

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.felhr.utils.HexData
import com.neurotinker.neurobytes.GdbUtils.gdbEnterDfu

import kotlinx.android.synthetic.main.activity_update.*
import kotlinx.android.synthetic.main.content_update.*
import kotlinx.coroutines.experimental.*

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

        var job: Job = Job()
        var fingerprint : GdbUtils.Fingerprint? = null

        Firmware.updatePath(getFilesDir().getPath())
        val updateFirmwareTask = Firmware.UpdateFirmwareAsyncTask()
        updateFirmwareTask.execute(Firmware.Interneuron)

        connectToNidButton.setOnClickListener {

            isConnectedToNid = connectToNid()
            if (isConnectedToNid) {
                nidStatus.text = "Attempting to connect to NID..."
//                        flashService.StartReadingThread()
            } else {
                nidStatus.text = "Failed to connect to NID"
            }

            job.cancel()
            job = GlobalScope.launch(Dispatchers.Main) {
                flashService.StartReadingThread()
                if (isConnectedToNid) {
                    if (initializeGdb() != null) {
                        nidStatus.text = "GDB initialized. OK."
                    } else {
                        nidStatus.text = "Failed to initialize GDB"
                    }
                }
                delay(200L)
                if (isConnectedToNid) {
                    if (enterSwd()) {
                        nidStatus.text = "Nid connected. Initialized."
                    } else {
                        nidStatus.text = "Nid connected. Initialized."
                    }
                }
                flashService.StopReadingThread()
            }
        }

        initializeGdbButton.setOnClickListener {
                job.cancel()
                job = GlobalScope.launch(Dispatchers.Main) {
                    flashService.StartReadingThread()
                    if (isConnectedToNid) {
//                    var initialized = false
//                    initialized = async{ initialized = initializeGdb() != null }
//                    (async{ initializeGdb() }).await()
                        if (initializeGdb() != null) {
                            nidStatus.text = "GDB initialized. OK."
                        } else {
                            nidStatus.text = "Failed to initialize GDB"
                        }
                    }
                    flashService.StopReadingThread()
                }
        }

        detectNbButton.setOnClickListener {
                job.cancel()
                job = GlobalScope.launch(Dispatchers.Main) {
                    flashService.StartReadingThread()
                    nidStatus.text = "Looking for NeuroBytes board..."
                    if (isConnectedToNid) {
                        if (detectNb()) {
                            boardStatus.text = "Detected a NeuroBytes board"
                        } else {
                            boardStatus.text = "No NeuroBytes board detected"
                        }
                    }
                    delay(200L)
                    if (isConnectedToNid && useFingerprint.isChecked) {
                        fingerprint = getFingerprint()
                        if (fingerprint != null) {
                            boardSelect.setSelection(fingerprint!!.deviceType)
//                        boardType.text = fingerprint!!.deviceType.toString()
                            boardStatus.text = "Fingerprint read successfully"
                        } else {
                            boardStatus.text = "Unable to get fingerprint"
                        }
                    }
                    boardImage.setImageResource(when (boardSelect.selectedItemPosition) {
                        1 -> R.drawable.interneuron_square
                        2 -> R.drawable.photoreceptor_square
                        3 -> R.drawable.motor_square
                        5 -> R.drawable.touch_square
                        4 -> R.drawable.tonic_square
                        6 -> R.drawable.boards_subitem_pressure_sensory_neuron
                        else -> R.drawable.clear
                    })
                    if (autoFlash.isChecked) {
                        if (isConnectedToNid) {
                            nidStatus.text = "Flashing..."
                            // allow manual fingerprint selection
                            if (boardSelect.selectedItemPosition != 0 && !useFingerprint.isChecked) {
                                val selectedFingerprint = GdbUtils.Fingerprint()
                                selectedFingerprint.setDeviceType(boardSelect.selectedItemPosition)
                                if (flash(selectedFingerprint.deviceType)) {
                                    boardStatus.text = "flash success"
                                } else {
                                    boardStatus.text = "flash fail"
                                }
                            } else if (fingerprint != null) {

                                if (flash(fingerprint!!.deviceType)) {
                                    boardStatus.text = "flash success"
                                } else {
                                    boardStatus.text = "flash fail"
                                }
                            }
                        }
                    }
                    nidStatus.text = "Nid connected. Idle."

                    flashService.StopReadingThread()

                }
        }

        enterSwdButton.setOnClickListener {
                job.cancel()
                job = GlobalScope.launch(Dispatchers.Main) {
                    flashService.StartReadingThread()
                    delay(200L)
                    if (isConnectedToNid) {
                        if (enterSwd()) {
                            nidStatus.text = "Entered SWD mode"
                        } else {
                            nidStatus.text = "Failed to enter SWD mode"
                        }
                    }
                    flashService.StopReadingThread()
                }
        }

        getFingerprintButton.setOnClickListener {
            job.cancel()
            job = GlobalScope.launch(Dispatchers.Main) {
                flashService.StartReadingThread()
                delay(200L)
                if (isConnectedToNid) {
                    fingerprint = getFingerprint()
                    if (fingerprint != null) {
                        boardImage.setImageResource(when (fingerprint!!.deviceType) {
                            1 -> R.drawable.interneuron_square
                            2 -> R.drawable.photoreceptor_square
                            3 -> R.drawable.motor_square
                            4 -> R.drawable.touch_square
                            5 -> R.drawable.tonic_square
                            6 -> R.drawable.boards_subitem_pressure_sensory_neuron
                            else -> R.drawable.clear
                        })
                        boardSelect.setSelection(fingerprint!!.deviceType)
//                        boardType.text = fingerprint!!.deviceType.toString()
                        nidStatus.text = "Fingerprint read successfully"
                    } else {
                        nidStatus.text = "Unable to get fingerprint"
                    }
                }
                flashService.StopReadingThread()
            }
        }

        flashButton.setOnClickListener{
            job.cancel()
            job = GlobalScope.launch(Dispatchers.Main) {
                flashService.StartReadingThread()
                delay(200L)

                if (isConnectedToNid) {
                    nidStatus.text = "Flashing..."
                    // allow manual fingerprint selection
                    if (boardSelect.selectedItemPosition != 0 && !autoDetect.isChecked) {
                        val selectedFingerprint = GdbUtils.Fingerprint()
                        selectedFingerprint.setDeviceType(boardSelect.selectedItemPosition)
                        if (flash(selectedFingerprint.deviceType)) {
                            boardStatus.text = "flash success"
                        } else {
                            boardStatus.text = "flash fail"
                        }
                    } else if (fingerprint != null) {



                        if (flash(fingerprint!!.deviceType)) {
                            boardStatus.text = "flash success"
                        } else {
                            boardStatus.text = "flash fail"
                        }
                    }
                    nidStatus.text = "Nid connected. Idle."
                }
            }
        }

        eraseButton.setOnClickListener{
            job.cancel()
            job = GlobalScope.launch(Dispatchers.Main) {
                flashService.StartReadingThread()
                delay(200L)
                if (isConnectedToNid) {
                    if (erase()) {
                        boardStatus.text = "erase success"
                    } else {
                        boardStatus.text = "erase failed"
                    }
                }
            }
        }

        dfuButton.setOnClickListener {
            // enter dfu mode
            if (isConnectedToNid) {
                sendMessage(gdbEnterDfu.toByteArray())
                flashService.CloseTheDevice()
            }
            flashService.CloseTheDevice()
            val intent = Intent(this, DfuActivity::class.java)
            startActivity(intent)
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
    private suspend fun readMessageBlocking(
            responseValidator: (String) -> Boolean,
            Timeout: Int = 5,
            stopOnValid: Boolean = false
    ): String? {
        // wait until data has been received
        var timeout: Int = 0
        var message = ByteArray(64)
        var response: String? = null
        while (timeout < Timeout) {
            Log.d(TAG, "read")
            if (flashService.IsThereAnyReceivedData()) {
                if (!GdbUtils.isDataValid(message)) break
                message = flashService.GetReceivedDataFromQueue()
                Log.d(TAG, "received message: $message")
                if (GdbUtils.isEndOfMessage(message)) {
                    flashService.WriteData(GdbUtils.ACK)
                    Log.d(TAG, "received complete message: ${GdbUtils.getMessageContent(message)}")
                }
                timeout = 0
            } else {
                timeout += 1
            }
            if (responseValidator(GdbUtils.getAsciiFromMessageBytes(message))) {
                response = GdbUtils.getMessageContent(message)
                Log.d(TAG, "Valid response received: $response")
                if (stopOnValid) return response
            }
            delay(10L) // wait 10 milliseconds
        }

//        flashService.WriteData(GdbUtils.ACK)

        return response

    }

    /**
     * Execute a sequence of GDB commands.
     * Each GDB response message is validated by responseValidator
     * and a return value is determined using valueFilter
     */
    private suspend fun executeGdbSequence(
            messageSeq: List<ByteArray>,
            responseValidator: (String) -> Boolean,
            valueFilter: (String) -> String = { it },
            timeout: Int = 5,
            stopOnValid: Boolean = false
    ) : String? {
        var response : String? = null
        for (message in messageSeq) {
            sendMessage(message)
            response = readMessageBlocking(responseValidator, timeout, stopOnValid)
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
            it.contains("OK") || it.contains("E") || it.contains("T05") || it.contains("+")
        }

        val response : String = executeGdbSequence(messageSeq, responseValidator) ?: return false
        if (response.contains("T05")) {
            return true
        }
        return readMessageBlocking({ it.contains("T05")}) != null
    }

    private suspend fun enterSwd() : Boolean {
        val messageSeq : List<ByteArray> = GdbUtils.getEnterSwdSequence()
        val responseValidator : (String) -> Boolean = {
            it.contains("OK") || it.contains("E")
        }

        val response : String = executeGdbSequence(messageSeq, responseValidator) ?: return false
        return response.contains("OK")
    }

    private suspend fun getFingerprint(): GdbUtils.Fingerprint? {
        val messageSeq : List<ByteArray> = GdbUtils.getFingerprintSequence()
        val responseValidator : (String) -> Boolean = {
            it.contains("$") && it.length > 10
        }

        val response : String = executeGdbSequence(messageSeq, responseValidator) ?: return null
        return GdbUtils.Fingerprint(response)
    }

    private suspend fun flash(deviceType: Int): Boolean {
        val messageSeq: List<ByteArray> = GdbUtils.getFlashSequence(deviceType)
        val responseValidator: (String) -> Boolean = {
            it.contains("OK") || it.contains("T05")
//            || it.contains("+")
            || it.contains("X1D")
        }

        val response : String = executeGdbSequence(messageSeq, responseValidator, timeout = 20, stopOnValid = true) ?: return false
        return true
    }

    private suspend fun erase(): Boolean {
        val messageSeq: List<ByteArray> = GdbUtils.getEraseSequence()
        val responseValidator: (String) -> Boolean = {
            it.contains("OK") || it.contains("T05")
        }

        val response: String = executeGdbSequence(messageSeq, responseValidator, timeout = 50) ?: return false
        return true
    }

    /**
     * Do we want these GDB functions to return success flags or
     * status strings?
     *
     * Probably abstract status strings to state variables that have
     * strings and enabled-button state
     */

    private fun connectToNid() : Boolean {
        return flashService.OpenDevice()
    }

    private fun disconnectNid() {
        flashService.CloseTheDevice()
    }

}
