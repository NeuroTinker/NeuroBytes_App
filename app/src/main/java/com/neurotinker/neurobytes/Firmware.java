package com.neurotinker.neurobytes;

import android.os.AsyncTask;

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

    private static final Map<Integer, Firmware> lookup = new HashMap<>();
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
                    "https://github.com/NeuroTinker/" + repoName + "/blob/master/FIRMWARE/bin/main.elf"
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
            fw.elfPath = parentDir + fw.elfName;
        }
    }

    static class UpdateFirmwareAsyncTask extends AsyncTask<Firmware, Integer, Integer> {
        protected Integer doInBackground(Firmware... firmwares) {

            int numComplete = 0;

            for (Firmware fw : firmwares) {
                try {
                    File file = new File(fw.elfPath);
                    OutputStream outputStream = new FileOutputStream(file.getPath());
                    InputStream inputStream = fw.gitUrl.openStream();
                    byte[] data = new byte[4096];
                    int count = 0;
                    int total = 0;
                    while ((count = inputStream.read(data)) != -1) {
                        total += count;
                        outputStream.write(data, 0, count);
                    }
                    numComplete += 1;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            return numComplete;
        }
    }
}
