package com.neurotinker.neurobytes

import android.util.Log
import com.felhr.utils.HexData
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.*

package com.neurotinker.neurobytes

import android.util.Log

import com.felhr.utils.HexData

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.LinkedList
import java.util.Queue

class GdbUtils {
    private val TAG = GdbUtils::class.java.simpleName

    private val gdbFingerprintSequence = arrayOf("m08003e00,c")
    private val gdbDetectSequence = arrayOf("qRcmd,73", "vAttach;1")
    private val gdbEnterSwd = "qRcmd,656e7465725f73776"
    private val gdbEnterUart = "qRcmd,656e7465725f75617274"
    private val gdbEnterDfu = "qRcmd,656e7465725f646675"
    private val messageQueue = LinkedList<ByteArray>()
    private val prevMessage: ByteArray? = null

    private val elfFilename = "main.elf"
    private val blocksize = 0x80
    private val textSizeOffset = 0x44
    private val textOffset = 0x10000
    private val fingerprintOffset = 0x23e00
    private val fingerprintAddress = 0x08003e00
    private val fingerprintSize = 0xc
    private val timeout = 0
    private val TIMEOUT = 50
    private val quitFlag = false

    fun buildFlashCommand(address: Int, data: ByteArray): ByteArray {
        val cmd = StringBuilder("vFlashWrite:")
        cmd.append(Integer.toHexString(address))
        cmd.append(":")
        return concat(cmd.toString().toByteArray(), escapeChars(data))
    }

    private fun isBadChar(bb: Byte): Boolean {
        return bb.toInt() == 0x23 || bb.toInt() == 0x24 || bb.toInt() == 0x7d
    }

    private fun escapeChars(bytes: ByteArray): ByteArray {

        var numBadChars = 0
        for (b in bytes) {
            if (isBadChar(b)) {
                numBadChars += 1
            }
        }

        val escapedBytes = ByteArray(bytes.size + numBadChars)
        var i = 0
        var j = 0
        while (i < bytes.size) {
            //                    if (bytes[i] > 126) Log.d("Greater", Integer.toString(i));
            if (isBadChar(bytes[i])) {
                escapedBytes[j] = 0x7d // escape char
                escapedBytes[++j] = (bytes[i] xor 0x20.toByte()).toByte()
            } else {
                escapedBytes[j] = bytes[i]
                //                        escapedBytes[j] = 0xF;
            }
            i++
            j++
        }
        return escapedBytes
    }

    private fun getFlashSequence(deviceType: Int?): LinkedList<ByteArray> {

        val firmware = Firmware.get(deviceType)

        try {
            val inStream = BufferedInputStream(FileInputStream(firmware.elfPath))
            val dataInStream = DataInputStream(inStream)

            var length = 0
            var fLoc = 0

            /**
             * Skip to start of .text program header
             */
            length = dataInStream.skipBytes(textSizeOffset)
            fLoc += length
            if (length != textSizeOffset) Log.d(TAG, "incorrect skip")

            /**
             * Read .text size and calculate number of blocks
             */
            val programHeader = ByteArray(4)
            length = dataInStream.read(programHeader, 0, 4)
            if (length != 4) Log.d(TAG, "incorrect read")
            fLoc += length
            Log.d(TAG, "program header hex: " + HexData.hexToString(programHeader))
            val buff: ByteBuffer
            buff = ByteBuffer.wrap(programHeader)
            buff.order(ByteOrder.BIG_ENDIAN)
            buff.rewind()
            Log.d(TAG, HexData.hexToString(buff.array()))
            var textSize: Int? = ByteBuffer.wrap(programHeader).getInt(0)
            textSize = Integer.reverseBytes(textSize!!)
            Log.d(TAG, textSize.toString())
            Log.d(TAG, "firmware size: " + Integer.toString(textSize))
            val numBlocks = textSize / blocksize
            val extraBlockSize = textSize % blocksize

            /**
             * Skip to the start of the .text section
             */
            length = dataInStream.skipBytes(textOffset - fLoc)
            //            if (length != textOffset)
            //                Log.d(TAG, "only skipped " + Integer.toString(length) + " bytes");
            fLoc += length

            /**
             * Read .text content into blocks of size [blocksize]
             */
            val textBlocks = Array(numBlocks) { ByteArray(blocksize) }
            for (i in 0 until numBlocks) {
                length = dataInStream.read(textBlocks[i], 0, blocksize)
                if (length != blocksize) {
                    Log.d(TAG, "only read " + i + "th block " + Integer.toString(length) + " bytes")
                }
                fLoc += length
            }

            /**
             * If there is extra .text content with size not >= [blocksize],
             * put it into [extrablock]
             */
            val extraBlock = ByteArray(extraBlockSize)
            if (extraBlockSize > 0) {
                length = dataInStream.read(extraBlock, 0, extraBlockSize)
                if (length != extraBlockSize) {
                    Log.d(TAG, "only read extra block " + Integer.toString(length) + " bytes")
                }
                fLoc += length
            }

            /**
             * Skip to the .fingerprint section
             */
            dataInStream.skipBytes(fingerprintOffset - fLoc)

            /**
             * Read the .fingerprint section.
             * Note: the fingerprint size is always less than [blocksize]
             */
            val fingerprint = ByteArray(fingerprintSize)
            length = dataInStream.read(fingerprint, 0, fingerprintSize)
            if (fingerprintSize != length) {
                Log.d(TAG, ".fingerprint load failed")
                Log.d(TAG, "only read " + Integer.toString(length) + " bytes")
            }

            Log.d(TAG, "fingerprint: " + HexData.hexToString(fingerprint))

            /**
             * Build flash command sequence
             */
            val flashSequence = LinkedList<ByteArray>()

            flashSequence.add("vFlashErase:08000000,00004000".toByteArray())

            var address = 0x8000000
            for (i in 0 until numBlocks) {
                //                Log.d(TAG, Integer.toString(i));
                //                Log.d(TAG, "address " + Integer.toHexString(address));
                flashSequence.add(buildFlashCommand(address, textBlocks[i]))
                address += blocksize
            }
            if (extraBlockSize > 0) flashSequence.add(buildFlashCommand(address, extraBlock))
            flashSequence.add(buildFlashCommand(fingerprintAddress, fingerprint))

            flashSequence.add("vFlashDone".toByteArray())
            flashSequence.add("vRun;".toByteArray())
            flashSequence.add("c".toByteArray())
            //            flashSequence.add("R00".getBytes());

            return flashSequence
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    internal inner class Fingerprint(encoded: String) {
        var deviceType: Int? = null
        var deviceId: Int? = null
        var version: Int? = null

        init {
            val unencoded = IntArray(3)
            val field = CharArray(8)
            for (i in 0..2) {
                // get each 32 bit field (8 chars)
                var k = i * 8
                var j = 7
                while (j >= 0) {
                    // convert each 8 char field into big endian format
                    field[j] = encoded[k - (j + 1) % 2 + j % 2]
                    k++
                    j--
                }
                unencoded[i] = ByteBuffer.wrap(HexData.stringTobytes(String(field))).int
            }
            this.deviceType = unencoded[0]
            this.deviceId = unencoded[1]
            this.version = unencoded[2]
        }
    }

    companion object {
        val gdbConnectUnderSrstCommand = "\$qRcmd,636f6e6e6563745f7372737420656e61626c65#1b"
        val gdbInitSequence = arrayOf("!", "qRcmd,747020656e", "qRcmd,v", gdbConnectUnderSrstCommand)
        var ACK = byteArrayOf('+'.toByte())

        fun getGdbInitSequence(): List<ByteArray> {
            val seq = LinkedList<ByteArray>()
            for (m in gdbInitSequence) {
                seq.add(m.toByteArray())
            }
            return seq
        }

        fun buildPacket(msg: ByteArray): ByteArray {
            val startTok = "$"
            val csumTok = "#"

            /**
             * Calculate checksum
             */
            var csum: Int? = 0
            for (b in msg) {
                csum += b.toInt()
                val tmp = byteArrayOf(b)
            }
            csum %= 256
            csum = csum and 0xFF

            var csumHexStr = Integer.toHexString(csum)
            if (csum <= 0xf) csumHexStr = "0$csumHexStr"

            /**
             * Build packet
             */
            return concat(startTok.toByteArray(), msg, csumTok.toByteArray(), csumHexStr.toByteArray())
        }

        private fun concat(arr1: ByteArray, arr2: ByteArray): ByteArray {
            val bytes = ByteArray(arr1.size + arr2.size)
            System.arraycopy(arr1, 0, bytes, 0, arr1.size)
            System.arraycopy(arr2, 0, bytes, arr1.size, arr2.size)
            return bytes
        }

        private fun concat(arr1: ByteArray, vararg arrs: ByteArray): ByteArray {
            var arr1 = arr1
            for (arr in arrs) {
                arr1 = concat(arr1, arr)
            }
            return arr1
        }

        fun isDataValid(data: ByteArray): Boolean {
            val asciiData = String(data, Charset.forName("UTF-8"))

            return if (asciiData.contains("-")) {
                false
            } else true

        }

        fun isEndOfMessage(data: ByteArray): Boolean {
            val asciiData = String(data, Charset.forName("UTF-8"))

            return if (asciiData.contains("#")) {
                true
            } else {
                false
            }
        }

        fun getMessageContent(data: ByteArray): String {
            val asciiData = String(data, Charset.forName("UTF-8"))

            return asciiData.split("[$#]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
        }
    }
}
