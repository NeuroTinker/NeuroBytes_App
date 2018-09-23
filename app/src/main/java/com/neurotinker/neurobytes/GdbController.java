package com.neurotinker.neurobytes;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.renderscript.ScriptGroup;
import android.util.Log;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.felhr.utils.HexData;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final Integer textSizeOffset = 0x44;
    private final Integer textOffset = 0x10000;
    private final Integer fingerprintOffset = 0x23e00;
    private final Integer fingerprintAddress = 0x08003e00;
    private final Integer fingerprintSize = 0xc;
    private Integer timeout = 0;
    private final Integer TIMEOUT = 50;
    private boolean quitFlag = false;
    private UsbFlashService flashService;

    private Integer deviceId;
    private Integer deviceType;

    private View view;
    private PopupWindow popupWindow;
    private TextView statusTextView;

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

    public GdbController(UsbFlashService flashService) {
        this.flashService = flashService;
        this.state = State.STOPPED;
    }

    public void start(PopupWindow popupWindow) {
        this.popupWindow = popupWindow;
        this.view = popupWindow.getContentView();
        this.statusTextView = view.findViewById(R.id.flashstatus_id);

        View cancelBtnView = view.findViewById(R.id.cancelbutton_id);
        cancelBtnView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stop();
            }
        });

        this.state = State.INITIALIZING;
        for (String s : gdbInitSequence) {
            messageQueue.add(s.getBytes());
        }
        /**
         * Flash the connected NeuroBytes board with correct firmware
         */
        flashService.OpenDevice();
        flashService.StartReadingThread();
        sendNextMessage();
        GdbCallbackRunnable callback = new GdbCallbackRunnable(flashService);
//                flashService.StartReadingThread();
        timerHandler.postDelayed(callback, 10);
//                flashService.CloseTheDevice();
    }

    public void stop() {
        this.quitFlag = true;
        flashService.CloseTheDevice();
        popupWindow.dismiss();
    }

    private byte[] concat(byte[] arr1, byte[] arr2) {
        byte[] bytes = new byte[arr1.length + arr2.length];
        System.arraycopy(arr1, 0, bytes, 0, arr1.length);
        System.arraycopy(arr2, 0, bytes, arr1.length, arr2.length);
        return bytes;
    }

    private byte[] concat(byte[] arr1, byte[]... arrs) {
        for (byte[] arr : arrs) {
            arr1 = concat(arr1, arr);
        }
        return arr1;
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

    private LinkedList<byte[]> getFlashSequence(Integer deviceType) {

        Firmware firmware = Firmware.get(deviceType);

        try {
            InputStream inStream = new BufferedInputStream(new FileInputStream(firmware.elfPath));
            DataInputStream dataInStream = new DataInputStream(inStream);

            int length = 0;
            int fLoc = 0;

            /**
             * Skip to start of .text program header
             */
            length = dataInStream.skipBytes(textSizeOffset);
            fLoc += length;
            if (length != textSizeOffset) Log.d(TAG, "incorrect skip");

            /**
             * Read .text size and calculate number of blocks
             */
            byte[] programHeader = new byte[4];
            length = dataInStream.read(programHeader, 0, 4);
            if (length != 4) Log.d(TAG, "incorrect read");
            fLoc += length;
            Log.d(TAG, "program header hex: " + HexData.hexToString(programHeader));
            ByteBuffer buff;
            buff = ByteBuffer.wrap(programHeader);
            buff.order(ByteOrder.BIG_ENDIAN);
            buff.rewind();
            Log.d(TAG, HexData.hexToString(buff.array()));
            Integer textSize = ByteBuffer.wrap(programHeader).getInt(0);
            textSize = Integer.reverseBytes(textSize);
            Log.d(TAG, textSize.toString());
            Log.d(TAG, "firmware size: " + Integer.toString(textSize));
            int numBlocks = (textSize / blocksize);
            int extraBlockSize = textSize % blocksize;

            /**
             * Skip to the start of the .text section
             */
            length = dataInStream.skipBytes(textOffset - fLoc);
//            if (length != textOffset)
//                Log.d(TAG, "only skipped " + Integer.toString(length) + " bytes");
            fLoc += length;

            /**
             * Read .text content into blocks of size [blocksize]
             */
            byte[][] textBlocks = new byte[numBlocks][blocksize];
            for (int i = 0; i < numBlocks; i++) {
                length = dataInStream.read(textBlocks[i], 0, blocksize);
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
//                Log.d(TAG, Integer.toString(i));
//                Log.d(TAG, "address " + Integer.toHexString(address));
                flashSequence.add(buildFlashCommand(address, textBlocks[i]));
                address += blocksize;
            }
            if (extraBlockSize > 0) flashSequence.add(buildFlashCommand(address, extraBlock));
            flashSequence.add(buildFlashCommand(fingerprintAddress, fingerprint));

            flashSequence.add("vFlashDone".getBytes());
            flashSequence.add("vRun;".getBytes());
            flashSequence.add("c".getBytes());
//            flashSequence.add("R00".getBytes());

            return flashSequence;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    class Fingerprint {
        public Integer deviceType;
        public Integer deviceId;
        public Integer version;

        public Fingerprint(String encoded) {
            int [] unencoded = new int[3];
            char [] field = new char[8];
            for (int i = 0; i < 3; i++) {
                // get each 32 bit field (8 chars)
                for (int k=i*8, j=7; j >= 0; k++, j--) {
                    // convert each 8 char field into big endian format
                    field[j] = encoded.charAt(k - ((j+1) % 2) + (j % 2));
                }
                unencoded[i] = ByteBuffer.wrap(HexData.stringTobytes(String.valueOf(field))).getInt();
            }
            this.deviceType = unencoded[0];
        }
    }

    class GdbCallbackRunnable implements Runnable {
        private UsbFlashService flashService;
        public GdbCallbackRunnable(UsbFlashService flashService) {
            this.flashService = flashService;
        }
        @Override
        public void run() {
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
                            statusTextView.setText("NeuroBytes found! Trying to connect...");
                        } else if (messageEncoded.contains("E")) {
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
                            // successful fingerprint transfer
                            Log.d(TAG, "fingerprint string: " + messageEncoded);
                            Fingerprint fing = new Fingerprint(messageEncoded);
                            deviceType = fing.deviceType;
                            Log.d(TAG, "fingerprint int: " + deviceType.toString());
                            state = State.FLASHING;
                            statusTextView.setText("Flashing...");
                            sendNextMessage();
                        }
                    } else if (state == State.FLASHING) {
                        if (messageEncoded.contains("OK") || messageEncoded.contains("T05")) {
                            // send flash messages until done
                            if (sendNextMessage()) {
                                state = State.DONE;
                                statusTextView.setText("Flashing completed. Please disconnect NeuroBytes board.");
                            }
                        }
                    } else if (state == State.DONE) {
                        if (messageEncoded.contains("W") || messageEncoded.contains("X")) {
                            // target disconnected
                            state = State.DETECTING;
                            sendNextMessage();
                            statusTextView.setText("Waiting for NeuroBytes connection");
                        }
                    }
                }

            } else {
                if (timeout++ >= TIMEOUT && state != State.DETECTING && state != State.DONE) {
                    state = State.DETECTING;
                    timeout = 0;
                    Log.d(TAG, "timeout");
                }
            }
            if (!quitFlag) {
                timerHandler.postDelayed(this, 10);
            } else {
                state = State.STOPPED;
            }
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
                statusTextView.setText("NID online. Waiting for NeuroBytes");
                this.state = State.DETECTING;
                sendNextMessage();
            } else if (this.state == State.DETECTING) {
                Log.d(TAG, "detect");
                for (String s : gdbDetectSequence) {
                    messageQueue.add(s.getBytes());
                }
                sendNextMessage();
            } else if (this.state == State.CONNECTING) {
                for (String s : gdbFingerprintSequence) {
                    messageQueue.add(s.getBytes());
                }
                sendNextMessage();
            } else if (this.state == State.FLASHING) {
                messageQueue.addAll(getFlashSequence(deviceType));
                sendNextMessage();
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

        String csumHexStr = Integer.toHexString(csum);
        if (csum <= 0xf) csumHexStr = '0' + csumHexStr;

        /**
         * Build packet
         */
        return concat(startTok.getBytes(), msg, csumTok.getBytes(), csumHexStr.getBytes());
    }
}


