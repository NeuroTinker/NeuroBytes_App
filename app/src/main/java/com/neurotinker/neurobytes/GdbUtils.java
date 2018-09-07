package com.neurotinker.neurobytes;

import android.util.Log;

import com.felhr.utils.HexData;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Queue;

public final class GdbUtils {
    private final String TAG = GdbUtils.class.getSimpleName();

    private final String[] gdbFingerprintSequence = {"m08003e00,c"};
    private final String[] gdbDetectSequence = {"qRcmd,73", "vAttach;1"};
    private final String gdbEnterSwd = "qRcmd,656e7465725f73776";
    private final String gdbEnterUart = "qRcmd,656e7465725f75617274";
    private final String gdbEnterDfu = "qRcmd,656e7465725f646675";
    private final String gdbConnectUnderSrstCommand = "$qRcmd,636f6e6e6563745f7372737420656e61626c65#1b";
    private final String[] gdbInitSequence = {"!", "qRcmd,747020656e", "qRcmd,v", gdbConnectUnderSrstCommand};
    private Queue<byte[]> messageQueue = new LinkedList<>();
    private byte[] prevMessage;
    public static byte[] ACK = {'+'};

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

    public static byte[] buildPacket(byte[] msg) {
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

    private static byte[] concat(byte[] arr1, byte[] arr2) {
        byte[] bytes = new byte[arr1.length + arr2.length];
        System.arraycopy(arr1, 0, bytes, 0, arr1.length);
        System.arraycopy(arr2, 0, bytes, arr1.length, arr2.length);
        return bytes;
    }

    private static byte[] concat(byte[] arr1, byte[]... arrs) {
        for (byte[] arr : arrs) {
            arr1 = concat(arr1, arr);
        }
        return arr1;
    }

    public byte[] buildFlashCommand(int address, byte[] data) {
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
            this.deviceId = unencoded[1];
            this.version = unencoded[2];
        }
    }

    public static boolean isDataValid(byte[] data) {
        String asciiData = new String(data, Charset.forName("UTF-8"));

        if (asciiData.contains("-")) {
            return false;
        }

        return true;
    }

    public static boolean isEndOfMessage(byte[] data) {
        String asciiData = new String(data, Charset.forName("UTF-8"));

        if (asciiData.contains("#")) {
            return true;
        } else {
            return false;
        }
    }

    public static String getMessageContent(byte[] data) {
        String asciiData = new String(data, Charset.forName("UTF-8"));

        return asciiData.split("[$#]")[1];
    }
}
