package com.example.myapplication_test;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class SensorSenderService extends Service implements SensorEventListener {
    private SensorManager sensorManager;
    private Map<String, float[]> sensorDataMap = new HashMap<>();
    private Handler handler;
    private Runnable sendTask;
    private Socket socket;
    private PrintWriter writer;
    private String ip;
    private int port;
    private boolean isSending = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ip = intent.getStringExtra("ip");
        port = intent.getIntExtra("port", 0);
        connectAndStart();
        return START_STICKY;
    }

    private void connectAndStart() {
        new Thread(() -> {
            try {
                socket = new Socket(ip, port);
                writer = new PrintWriter(socket.getOutputStream(), true);
                registerSensors();
                startSending();
            } catch (Exception ignored) {}
        }).start();
    }

    private void registerSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        int[] types = {
                Sensor.TYPE_ACCELEROMETER,
                Sensor.TYPE_MAGNETIC_FIELD,
                Sensor.TYPE_GYROSCOPE,
                Sensor.TYPE_LIGHT,
                Sensor.TYPE_ORIENTATION
        };
        for (int type : types) {
            Sensor sensor = sensorManager.getDefaultSensor(type);
            if (sensor != null) {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
            }
        }
    }

    private void startSending() {
        handler = new Handler(Looper.getMainLooper());
        sendTask = new Runnable() {
            @Override
            public void run() {
                if (writer != null) {
                    try {
                        JSONObject json = new JSONObject();
                        for (Map.Entry<String, float[]> entry : sensorDataMap.entrySet()) {
                            float[] values = entry.getValue();
                            json.put(entry.getKey(), values.length == 1 ? values[0] : new JSONArray(values));
                        }
                        writer.println(json.toString());
                    } catch (Exception ignored) {}
                }
                handler.postDelayed(this, 100);
            }
        };
        handler.post(sendTask);
        isSending = true;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        sensorDataMap.put(event.sensor.getName(), event.values.clone());
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onDestroy() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (handler != null && sendTask != null) handler.removeCallbacks(sendTask);
        try {
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}
        isSending = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
