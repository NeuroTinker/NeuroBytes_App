package com.neurotinker.neurobytes;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public enum Firmware {
    Interneuron (1, "interneuron.elf", "NeuroBytes_Interneuron"),
    Photoreceptor (2, "photoreceptor.elf", "NeuroBytes_Photoreceptor"),
    MotorNeuron (3, "motor_neuron.elf", "NeuroBytes_Motor_Neuron"),
    TouchSensor (4, "touch_sensor.elf", "NeuroBytes_Touch_Sensor"),
    TonicNeuron (5, "tonic_neuron.elf", "NeuroBytes_Tonic_Neuron"),
    ForceSensor (6, "force_sensor.elf", "NeuroBytes_Force_Sensor");

    public final Integer deviceId;
    public final String elfName;
    public final URL gitUrl;
    public String elfPath;

    public static final Map<Integer, Firmware> lookup = new HashMap<>();
    static {
        for (Firmware fw : Firmware.values()) {
            lookup.put(fw.deviceId, fw);
        }
    }

    Firmware(int deviceId, String elfName, String repoName) {
        this.deviceId = deviceId;
        this.elfName = elfName;
        try {
            this.gitUrl = new URL(
                    "https://github.com/NeuroTinker/" + repoName + "/blob/master/FIRMWARE/bin/main.elf?raw=true"
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Firmware get(Integer deviceId) {
        return lookup.get(deviceId);
    }

    public static void updatePath(String parentDir) {
        for (Firmware fw : Firmware.values()) {
            fw.elfPath = parentDir + "/" + fw.elfName;
        }
    }

    static class UpdateFirmwareAsyncTask extends AsyncTask<Firmware, Integer, Integer> {
        private final String TAG = Firmware.class.getSimpleName();
        protected Integer doInBackground(Firmware... firmwares) {

            int numComplete = 0;
//            Log.d(TAG, firmwares.toString());
            for (Firmware fw : Firmware.values()) {
                try {
                    File file = new File(fw.elfPath);
                    Log.d(TAG, fw.elfPath);
                    Log.d(TAG, fw.gitUrl.toString());
                    OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file.getPath()), 0x2400);
                    InputStream inputStream = new BufferedInputStream(fw.gitUrl.openStream(), 0x2400);
                    DataInputStream dataInputStream = new DataInputStream(inputStream);
                    Log.d(TAG, Integer.toString(inputStream.available()));
                    byte[] data = new byte[4096];
                    int count = 0;
                    int total = 0;
                    while ((count = dataInputStream.read(data)) != -1) {
                        total += count;
                        outputStream.write(data, 0, count);
                    }
                    numComplete += 1;
                    outputStream.close();
                    inputStream.close();
                    dataInputStream.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            Log.d(TAG, "done");

            return numComplete;
        }
    }
}
