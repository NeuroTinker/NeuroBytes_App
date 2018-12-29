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
import java.util.List;
import java.util.Queue;

public final class GdbUtils {
    private static final String TAG = GdbUtils.class.getSimpleName();

    public static final String gdbConnectUnderSrstEnable = "qRcmd,636f6e6e6563745f7372737420656e61626c65";
    public static final String gdbConnectUnderSrstDisable = "qRcmd,636f6e6e6563745f737273742064697361626c65";
    private static final String gdbHardSrstCommand = "qRcmd,686172645f73727374";
//    private static final String gdbKillCommand = "vKill;1";
    private static final String gdbKillCommand = "k";
    private static final String gdbScan = "qRcmd,73";
    private static final String gdbAttach = "vAttach;1";
    public static final String[] gdbDetectSequence = {
            /**
             * This is weird and probably means there is a problem with
             * the way SRST is being handled
            */
//            gdbHardSrstCommand,
            gdbConnectUnderSrstEnable,
            gdbScan,
            gdbAttach,
            gdbConnectUnderSrstDisable,
            gdbScan,
            gdbKillCommand,
            gdbAttach,
//            "qRcmd,73",
//            "vAttach;1"
    };
    private static final String gdbEnterSwd = "qRcmd,656e7465725f73776";
    private static final String[] gdbEnterSwdSequence = {gdbEnterSwd};
    private final String gdbEnterUart = "qRcmd,656e7465725f75617274";
    public static final String gdbEnterDfu = "qRcmd,656e7465725f646675";
    public static final String[] gdbInitSequence = {"!", "qRcmd,747020656e", "qRcmd,v",
//            gdbConnectUnderSrstCommand,
//            gdbEnterSwd
    };
    private static final String[] gdbCheckConnectionSequence = {"?"};
    private static final String[] gdbEnterDfuSequence = {gdbEnterDfu};
    public static final String[] gdbFingerprintSequence = {"m08003e00,c"};
    public static final String[] gdbEraseSequence = {"vFlashErase:08000000,00004000"};

    private Queue<byte[]> messageQueue = new LinkedList<>();
    private byte[] prevMessage;
    public static byte[] ACK = {'+'};

    private final String elfFilename = "main.elf";
    private static final Integer blocksize = 0x80;
    private static final Integer textSizeOffset = 0x44;
    private static final Integer dataSizeOffset = 0x64;
    private static final Integer textOffset = 0x10000;
    private static final Integer dataOffset = 0x20000;
    private static final Integer fingerprintOffset = 0x23e00;
    private static final Integer fingerprintAddress = 0x08003e00;
    private static final Integer fingerprintSize = 0xc;
    private Integer timeout = 0;
    private final Integer TIMEOUT = 50;
    private boolean quitFlag = false;

    public static List<byte[]> getCheckConnectionSequence() {
        return getMessageSequence(gdbCheckConnectionSequence);
    }

    private static List<byte[]> getMessageSequence(String[] messageSeq) {
        List<byte[]> seq = new LinkedList<byte[]>();
        for (String m : messageSeq) {
            seq.add(m.getBytes());
        }
        return seq;
    }

    public static List<byte[]> getGdbDetectSequence() {
        return getMessageSequence(gdbDetectSequence);
    }

    public static List<byte[]> getGdbInitSequence() {
        return getMessageSequence(gdbInitSequence);
    }

    public static List<byte[]> getEnterSwdSequence() {
        return getMessageSequence(gdbEnterSwdSequence);
    }

    public static List<byte[]> getEnterDfuSequence() {
        return getMessageSequence(gdbEnterDfuSequence);
    }

    public static List<byte[]> getFingerprintSequence() {
        return getMessageSequence(gdbFingerprintSequence);
    }

    public static List<byte[]> getEraseSequence() {
        return getMessageSequence(gdbEraseSequence);
    }

    public static class Fingerprint {
        public void setDeviceType(Integer deviceType) {
            this.deviceType = deviceType;
        }

        public Integer deviceType;

        public void setDeviceId(Integer deviceId) {
            this.deviceId = deviceId;
        }

        public Integer deviceId;
        public Integer version;

        public Fingerprint() {

        }

        public Fingerprint(String encoded) {
            int [] decoded = new int[3];
            char [] field = new char[8];
            for (int i = 0; i < 3; i++) {
                // get each 32 bit field (8 chars)
                for (int k=i*8, j=7; j >= 0; k++, j--) {
                    // convert each 8 char field into big endian format
                    field[j] = encoded.charAt(k - ((j+1) % 2) + (j % 2));
                }
                decoded[i] = ByteBuffer.wrap(HexData.stringTobytes(String.valueOf(field))).getInt();
            }
            this.deviceType = decoded[0];
        }
    }

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

    public static byte[] buildFlashCommand(int address, byte[] data) {
        StringBuilder cmd = new StringBuilder("vFlashWrite:");
        cmd.append(Integer.toHexString(address));
        cmd.append(":");
        byte[] bytes = concat(cmd.toString().getBytes(), escapeChars(data));
        return bytes;
    }

    private static boolean isBadChar(byte bb) {
        return (bb == 0x23 || bb == 0x24 || bb == 0x7d);
    }

    private static byte[] escapeChars(byte[] bytes) {

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

    public static LinkedList<byte[]> getFlashSequence(Integer deviceType) {

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
             * Skip to start of .data program header
             */
            length = dataInStream.skipBytes(dataSizeOffset - fLoc);
            fLoc += length;
            if (fLoc != dataSizeOffset) Log.d(TAG, "incorrect skip");

            /**
             * Read .data size and calculate number of blocks
             */
            length = dataInStream.read(programHeader, 0, 4);
            if (length != 4) Log.d(TAG, "incorrect read");
            fLoc += length;
            Log.d(TAG, "program header hex: " + HexData.hexToString(programHeader));
            buff = ByteBuffer.wrap(programHeader);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            buff.rewind();
            Log.d(TAG, HexData.hexToString(buff.array()));
            Integer dataSize = buff.getInt(0);
            int numDataBlocks = (dataSize / blocksize);
            int extraDataBlockSize = dataSize % blocksize;
            Log.d(TAG, "data size: " + Integer.toString(dataSize));


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
             * Skip to start of the .data section
             */
            length = dataInStream.skipBytes(dataOffset - fLoc);
            fLoc += length;

            /**
             * Read .data content into blocks of size [blocksize]
             */
            byte[][] dataBlocks = new byte[numDataBlocks][blocksize];
            for (int i=0; i< numDataBlocks; i++) {
                length = dataInStream.read(dataBlocks[i], 0, blocksize);
                fLoc += length;
            }

            /**
             * Read extra .data block
             */
            byte[] extraDataBlock = new byte[extraDataBlockSize];
            if (extraDataBlockSize > 0) {
                length = dataInStream.read(extraDataBlock, 0, extraDataBlockSize);
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

//            flashSequence.add("vRun;".getBytes());
//            flashSequence.add("c".getBytes());
//            flashSequence.add("R00".getBytes());
            flashSequence.add(("vFlashErase:0800" +
                    "0000,00004000").getBytes());

            int address = 0x8000000;
            for (int i = 0; i < numBlocks; i++) {
//                Log.d(TAG, Integer.toString(i));
//                Log.d(TAG, "address " + Integer.toHexString(address));
                flashSequence.add(buildFlashCommand(address, textBlocks[i]));
                address += blocksize;
            }
            if (extraBlockSize > 0) flashSequence.add(buildFlashCommand(address, extraBlock));
            address += extraBlockSize;
//            address = 0x20000000;
            for (int i = 0; i < numDataBlocks; i++) {
                flashSequence.add(buildFlashCommand(address, dataBlocks[i]));
                address += blocksize;
            }
            if (extraDataBlockSize > 0) flashSequence.add(buildFlashCommand(address, extraDataBlock));


            flashSequence.add(buildFlashCommand(fingerprintAddress, fingerprint));

            flashSequence.add("vFlashDone".getBytes());
            flashSequence.add("vRun;".getBytes());
//            flashSequence.add("c".getBytes());
//            flashSequence.add("R00".getBytes());

            return flashSequence;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isDataAck(byte[] data) {
        String asciiData = new String(data, Charset.forName("UTF-8"));

        if (asciiData.contains("+")) {
            return true;
        }

        return false;
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

    public static String getAsciiFromMessageBytes(byte[] message) {
        return new String(message, Charset.forName("UTF-8"));
    }

    public static String getMessageContent(byte[] data) {
        String asciiData = new String(data, Charset.forName("UTF-8"));
        if (asciiData.contains("+")) return "+";
        return asciiData.split("[$#]")[1];
    }
}
