package com.neurotinker.neurobytes;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.renderscript.ScriptGroup;
import android.util.Log;

import com.felhr.utils.HexData;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Queue;

public class GdbController {
    private final String TAG = GdbController.class.getSimpleName();
    private final String[] gdbFingerprintSequence = {"m08003e00,c"};
    private final String[] gdbDetectSequence = {"qRcmd,73", "vAttach;1"};
    private final String[] gdbInitSequence = {"!", "qRcmd,747020656e", "qRcmd,v"};
    private Queue<byte[]> messageQueue = new LinkedList<>();
    private byte[] prevMessage;
    private byte[] ACK = {'+'};

    private final String elfFilename = "main.elf";
    private final Integer blocksize = 0x80;
    private final Integer textOffset = 0x10000;
    private final Integer fingerprintOffset = 0x23e00;
    private final Integer fingerprintAddress = 0x08003e00;
    private Integer timeout = 0;
    private final Integer TIMEOUT = 50;
    private boolean quitFlag = false;
    private UsbFlashService flashService;

    private Integer deviceId;
    private Integer deviceType;

    private Context _context;

    enum State {
        STOPPED,
        INITIALIZING,
        DETECTING,
        CONNECTING,
        FLASHING,
        DONE;
    }

    public State state;

    Handler timerHandler = new Handler(Looper.getMainLooper());

    public GdbController(Context _context, UsbFlashService flashService) {
        this.flashService = flashService;
        this._context = _context;
    }

    public void start() {
        this.state = State.INITIALIZING;
        for (String s : gdbInitSequence) {
            messageQueue.add(s.getBytes());
        }
        messageQueue.addAll(downloadElf());
        /**
         * Flash the connected NeuroBytes board with correct firmware
         */
        flashService.OpenDevice();
        flashService.StartReadingThread();
        sendNextMessage();
        Log.d("GDB Received", Boolean.toString(flashService.IsThereAnyReceivedData()));
        GdbCallbackRunnable callback = new GdbCallbackRunnable(flashService);
//                flashService.StartReadingThread();
        timerHandler.postDelayed(callback, 10);
//                flashService.CloseTheDevice();
    }

    public void stop() {
        this.quitFlag = true;
        this.state = State.STOPPED;
    }

    private byte[] concat(byte[] arr1, byte[] arr2) {
        byte[] bytes = new byte[arr1.length + arr2.length];
        System.arraycopy(arr1, 0, bytes, 0, arr1.length);
        System.arraycopy(arr2, 0, bytes, arr1.length, arr2.length);
        return bytes;
    }

    private byte[] concat(byte[] arr1, byte[] arr2, byte[] arr3, byte[] arr4) {
        return concat(concat(concat(arr1, arr2), arr3), arr4);
    }

    private byte[] buildFlashCommand(int address, byte[] data) {
        StringBuilder cmd = new StringBuilder("vFlashWrite:");
        cmd.append(Integer.toHexString(address));
        cmd.append(":");
        byte[] bytes = concat(cmd.toString().getBytes(), escapeChars(data));
        return bytes;
    }

    private boolean isBadChar(byte bb) {
        return (bb == 0x23 || bb == 0x24 || bb == 0x7d);
    }

    private byte[] escapeChars(byte[] bytes) {

        int numBadChars = 0;
        for (byte b : bytes) {
            if (isBadChar(b)) {
                numBadChars += 1;
            }
        }
        Log.d(TAG, "num bad chars: " + Integer.toString(numBadChars));

        byte[] escapedBytes = new byte[bytes.length + numBadChars];
        for (int i = 0,j = 0; i < bytes.length; i++, j++) {
//                    if (bytes[i] > 126) Log.d("Greater", Integer.toString(i));
            if (isBadChar(bytes[i])) {
                escapedBytes[j] = 0x7d; // escape char
                escapedBytes[++j] = (byte) ((bytes[i]) ^ ((byte) 0x20));
            } else {
                escapedBytes[j] = bytes[i];
//                        escapedBytes[j] = 0xF;
            }
        }
        return escapedBytes;
    }

    private LinkedList<byte[]> downloadElf() {
        try {
            URL url = new URL("https://github.com/NeuroTinker/NeuroBytes_Touch_Sensor/raw/master/FIRMWARE/bin/main.elf");
            InputStream inStream = new BufferedInputStream(url.openStream(), 0x2400);
            DataInputStream dataInStream = new DataInputStream(inStream);

            int textSize = 0x1ddc;
            int numBlocks = (textSize / blocksize);
            int extraBlockSize = textSize % blocksize;
            int fingerprintSize = 0xc;
            int length = 0;
            int fLoc = 0;

            /**
             * Skip to the start of the .text section
             */
            length = dataInStream.skipBytes(textOffset);
            if (length != textOffset) Log.d(TAG, "only skipped " + Integer.toString(length) + " bytes");
            fLoc += length;

            /**
             * Read .text content into blocks of size [blocksize]
             */
            byte[][] textBlocks = new byte[numBlocks][blocksize];
            for (int i = 0; i < numBlocks; i++) {
                length = dataInStream.read(textBlocks[i], 0, blocksize);
                Log.d(TAG, HexData.hexToString(textBlocks[i]));
                if (length != blocksize) {
                    Log.d(TAG, "only read " + i + "th block " + Integer.toString(length) + " bytes");
                }
                fLoc += length;
            }

            /**
             * If there is extra .text content with size not >= [blocksize],
             * put it into [extrablock]
             */
            byte[] extraBlock = new byte[extraBlockSize];
            if (extraBlockSize > 0) {
                length = dataInStream.read(extraBlock, 0, extraBlockSize);
                if (length != extraBlockSize) {
                    Log.d(TAG, "only read extra block " + Integer.toString(length) + " bytes");
                }
                fLoc += length;
            }

            /**
             * Skip to the .fingerprint section
             */
            dataInStream.skipBytes(fingerprintOffset - fLoc);

            /**
             * Read the .fingerprint section.
             * Note: the fingerprint size is always less than [blocksize]
             */
            byte[] fingerprint = new byte[fingerprintSize];
            length = dataInStream.read(fingerprint, 0, fingerprintSize);
            if (fingerprintSize != length) {
                Log.d(TAG, ".fingerprint load failed");
                Log.d(TAG, "only read " + Integer.toString(length) + " bytes");
            }

            Log.d(TAG, "fingerprint: " + HexData.hexToString(fingerprint));

            /**
             * Build flash command sequence
             */
            LinkedList<byte[]> flashSequence = new LinkedList<>();

            flashSequence.add("vFlashErase:08000000,00004000".getBytes());

            int address = 0x8000000;
            for (int i = 0; i < numBlocks; i++) {
                Log.d(TAG, Integer.toString(i));
                Log.d(TAG, "address " + Integer.toHexString(address));
                flashSequence.add(buildFlashCommand(address, textBlocks[i]));
                address += blocksize;
            }
            if (extraBlockSize > 0) flashSequence.add(buildFlashCommand(address, extraBlock));
            flashSequence.add(buildFlashCommand(fingerprintAddress, fingerprint));

            flashSequence.add("vFlashDone".getBytes());
//            flashSequence.add("vRun;".getBytes());
            flashSequence.add("R00".getBytes());

            return flashSequence;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void downloadFirmware() {
        try {
            File file = new File(_context.getFilesDir(), "touch_sensor.elf");
            OutputStream outputStream = new FileOutputStream(file.getPath());
            URL url = new URL("https://github.com/NeuroTinker/NeuroBytes_Touch_Sensor/raw/master/FIRMWARE/bin/main.elf");
            InputStream inputStream = url.openStream();
            byte[] data = new byte[4096];
            int count = 0;
            int total = 0;
            while ((count = inputStream.read(data)) != -1) {
                total += count;
                outputStream.write(data, 0, count);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private LinkedList<byte[]> getFlashSequence(Integer deviceType) {
        File file = new File(_context.getFilesDir(), "touch_sensor.elf");
//        InputStream inStream = new BufferedInputStream(, 0x2400);
        try {
            InputStream inStream = new FileInputStream(file.getPath());
            DataInputStream dataInStream = new DataInputStream(inStream);

            int textSize = 0x1ddc;
            int numBlocks = (textSize / blocksize);
            int extraBlockSize = textSize % blocksize;
            int fingerprintSize = 0xc;
            int length = 0;
            int fLoc = 0;

            /**
             * Skip to the start of the .text section
             */
            length = dataInStream.skipBytes(textOffset);
            if (length != textOffset)
                Log.d(TAG, "only skipped " + Integer.toString(length) + " bytes");
            fLoc += length;

            /**
             * Read .text content into blocks of size [blocksize]
             */
            byte[][] textBlocks = new byte[numBlocks][blocksize];
            for (int i = 0; i < numBlocks; i++) {
                length = dataInStream.read(textBlocks[i], 0, blocksize);
                Log.d(TAG, HexData.hexToString(textBlocks[i]));
                if (length != blocksize) {
                    Log.d(TAG, "only read " + i + "th block " + Integer.toString(length) + " bytes");
                }
                fLoc += length;
            }

            /**
             * If there is extra .text content with size not >= [blocksize],
             * put it into [extrablock]
             */
            byte[] extraBlock = new byte[extraBlockSize];
            if (extraBlockSize > 0) {
                length = dataInStream.read(extraBlock, 0, extraBlockSize);
                if (length != extraBlockSize) {
                    Log.d(TAG, "only read extra block " + Integer.toString(length) + " bytes");
                }
                fLoc += length;
            }

            /**
             * Skip to the .fingerprint section
             */
            dataInStream.skipBytes(fingerprintOffset - fLoc);

            /**
             * Read the .fingerprint section.
             * Note: the fingerprint size is always less than [blocksize]
             */
            byte[] fingerprint = new byte[fingerprintSize];
            length = dataInStream.read(fingerprint, 0, fingerprintSize);
            if (fingerprintSize != length) {
                Log.d(TAG, ".fingerprint load failed");
                Log.d(TAG, "only read " + Integer.toString(length) + " bytes");
            }

            Log.d(TAG, "fingerprint: " + HexData.hexToString(fingerprint));

            /**
             * Build flash command sequence
             */
            LinkedList<byte[]> flashSequence = new LinkedList<>();

            flashSequence.add("vFlashErase:08000000,00004000".getBytes());

            int address = 0x8000000;
            for (int i = 0; i < numBlocks; i++) {
                Log.d(TAG, Integer.toString(i));
                Log.d(TAG, "address " + Integer.toHexString(address));
                flashSequence.add(buildFlashCommand(address, textBlocks[i]));
                address += blocksize;
            }
            if (extraBlockSize > 0) flashSequence.add(buildFlashCommand(address, extraBlock));
            flashSequence.add(buildFlashCommand(fingerprintAddress, fingerprint));

            flashSequence.add("vFlashDone".getBytes());
//            flashSequence.add("vRun;".getBytes());
            flashSequence.add("R00".getBytes());

            return flashSequence;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    class GdbCallbackRunnable implements Runnable {
        private UsbFlashService flashService;
        public GdbCallbackRunnable(UsbFlashService flashService) {
            this.flashService = flashService;
        }
        @Override
        public void run() {
            Log.d("GDB Received", Boolean.toString(flashService.IsThereAnyReceivedData()));

            /**
             * Check for received packets
             */
            if (flashService.IsThereAnyReceivedData()) {
                byte[] data = flashService.GetReceivedDataFromQueue();
                String asciiData = new String(data, Charset.forName("UTF-8"));

                if (asciiData.contains("-")) {
                    Log.d(TAG, "message failed");
                    sendPrevMessage();
                    timeout += 25;
                } else {
                    timeout = 0;
                }

                /**
                 * Send ACK if packet fully received.
                 * Not bothering to even check csum or anything...
                 */
                if (asciiData.contains("#")) {
                    flashService.WriteData(ACK);
                }

                /**
                 * Process packet's message content and continue message sequence
                 */
                if (asciiData.contains("$")) {
                    String messageEncoded = asciiData.split("[$#]")[1];
                    Log.d(TAG, "GDB message received " + messageEncoded);
                    if (state == State.INITIALIZING) {
                        if (messageEncoded.contains("OK")) {
                            sendNextMessage();
                        }
                    } else if (state == State.DETECTING) {
                        if (messageEncoded.contains("T05")) {
                            // connection successful
                            state = State.CONNECTING;
                            sendNextMessage();
                        } else if (messageEncoded.contains("OK")) {
                            sendNextMessage();
                        }
                    } else if (state == State.CONNECTING) {
                        if (messageEncoded.contains("E")) {
                            Log.d(TAG, "failed to read fingerprint");
                            timeout += 10;
                            sendNextMessage();
                        } else {
                            // assume successful fingerprint transfer
                            Log.d(TAG, "fingerprint string: " + messageEncoded);
                            deviceType = ByteBuffer.wrap(messageEncoded.getBytes()).asIntBuffer().get(0);
                            deviceId = ByteBuffer.wrap(messageEncoded.getBytes()).asIntBuffer().get(2);
                            Log.d(TAG, "connected to device id: " + deviceId.toString());
                            state = State.FLASHING;
                            sendNextMessage();
                        }
                    } else if (state == State.FLASHING) {
                        if (messageEncoded.contains("OK")) {
                            // send flash messages until done
                            if (sendNextMessage()) state = State.DONE;
                        }
                    } else if (state == State.DONE) {
                        if (messageEncoded.contains("W") || messageEncoded.contains("X")) {
                            // target disconnected
                            state = State.DETECTING;
                            sendNextMessage();
                        }
                    }
                }

            } else {
                if (timeout++ >= TIMEOUT) {
                    state = State.DETECTING;
                    Log.d(TAG, "timeout");
                }
            }
            if (!quitFlag) timerHandler.postDelayed(this, 10);
        }
    }

    private boolean sendNextMessage() {
        // returns true if last message was sent
        if (messageQueue.isEmpty()) {
            /**
             * Use state machine to decide on next messages to send
             */
            if (this.state == State.INITIALIZING) {
                for (String s : gdbDetectSequence) {
                    messageQueue.add(s.getBytes());
                }
                this.state = State.DETECTING;
            } else if (this.state == State.DETECTING) {
                for (String s : gdbDetectSequence) {
                    messageQueue.add(s.getBytes());
                }
            } else if (this.state == State.CONNECTING) {
                for (String s : gdbFingerprintSequence) {
                    messageQueue.add(s.getBytes());
                }
            } else if (this.state == State.FLASHING) {
                messageQueue.addAll(getFlashSequence(deviceType));
            }
        } else {
            byte[] msg = messageQueue.remove();
            Log.d(TAG, "sending message: " + HexData.hexToString(msg));
            prevMessage = msg;
            flashService.WriteData(buildPacket(msg));
            if (messageQueue.isEmpty()) return true;
        }
        return false;
    }

    private void sendPrevMessage() {
        flashService.WriteData(buildPacket(prevMessage));
    }

    private byte[] buildPacket(byte[] msg) {
        final String startTok = "$";
        final String csumTok = "#";

        /**
         * Calculate checksum
         */
        Integer csum = 0;
        for (byte b : msg) {
            csum += b;
            byte[] tmp = {b};
        }
        csum %= 256;
        csum &= 0xFF;

        /**
         * Build packet
         */
        return concat(startTok.getBytes(), msg, csumTok.getBytes(), Integer.toHexString(csum).getBytes());
    }
}
