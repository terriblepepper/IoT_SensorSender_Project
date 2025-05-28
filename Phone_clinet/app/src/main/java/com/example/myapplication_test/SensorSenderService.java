package com.example.myapplication_test;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SensorSenderService extends Service implements SensorEventListener, LocationListener { // 实现 LocationListener
    private static final String TAG = "SensorSenderService";

    public static final String ACTION_CONNECTION_STATUS_UPDATE = "com.example.myapplication_test.CONNECTION_STATUS_UPDATE";
    public static final String EXTRA_STATUS_CODE = "extra_status_code";
    public static final String EXTRA_STATUS_MESSAGE = "extra_status_message";

    public static final int STATUS_DISCONNECTED = 0;
    public static final int STATUS_CONNECTING = 1;
    public static final int STATUS_CONNECTED = 2;
    public static final int STATUS_FAILED = 3;

    private SensorManager sensorManager;
    private LocationManager locationManager; // 添加 LocationManager
    private final Map<String, float[]> sensorDataMap = new HashMap<>();
    private Handler mainThreadHandler;
    private ExecutorService networkExecutor;

    private Socket socket;
    private PrintWriter writer;
    private String ip;
    private int port;

    private volatile int currentConnectionStatus = STATUS_DISCONNECTED;
    private Runnable sendDataTask;
    private Handler dataSendHandler;

    // 用于存储位置数据的键名
    private static final String JSON_KEY_LOCATION = "Location"; // 用于发送的JSON键名
    private static final String JSON_KEY_ACCELEROMETER = "Accelerometer";
    private static final String JSON_KEY_ORIENTATION = "Orientation";
    private static final String JSON_KEY_LIGHT = "Light";


    @Override
    public void onCreate() {
        super.onCreate();
        mainThreadHandler = new Handler(Looper.getMainLooper());
        networkExecutor = Executors.newSingleThreadExecutor();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE); // 初始化 LocationManager
        dataSendHandler = mainThreadHandler;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.w(TAG, "Service restarted with null intent. Stopping service.");
            broadcastConnectionStatus(STATUS_FAILED, "服务重启异常，请重新连接");
            stopSelf();
            return START_NOT_STICKY;
        }

        String newIp = intent.getStringExtra("ip");
        int newPort = intent.getIntExtra("port", 0);

        if ((currentConnectionStatus == STATUS_CONNECTING || currentConnectionStatus == STATUS_CONNECTED) &&
                (!newIp.equals(this.ip) || newPort != this.port)) {
            Log.i(TAG, "New connection request to different IP/Port. Disconnecting previous.");
            disconnectAndCleanup();
        } else if (currentConnectionStatus == STATUS_CONNECTED && newIp.equals(this.ip) && newPort == this.port) {
            Log.i(TAG, "Already connected to " + newIp + ":" + newPort + ". Ignoring request.");
            broadcastConnectionStatus(STATUS_CONNECTED, "已连接到: " + this.ip + ":" + this.port);
            return START_STICKY;
        }

        this.ip = newIp;
        this.port = newPort;

        Log.i(TAG, "Attempting to connect to " + ip + ":" + port);
        connectAndStartSending();

        return START_STICKY;
    }

    private void connectAndStartSending() {
        if (currentConnectionStatus == STATUS_CONNECTING || currentConnectionStatus == STATUS_CONNECTED) {
            Log.w(TAG, "Connection attempt while already connecting or connected. Current status: " + currentConnectionStatus);
            return;
        }

        broadcastConnectionStatus(STATUS_CONNECTING, "正在连接到 " + ip + ":" + port + "...");
        currentConnectionStatus = STATUS_CONNECTING;

        networkExecutor.submit(() -> {
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(ip, port), 10000);
                writer = new PrintWriter(socket.getOutputStream(), true);

                Log.i(TAG, "Successfully connected to " + ip + ":" + port);
                currentConnectionStatus = STATUS_CONNECTED;
                broadcastConnectionStatus(STATUS_CONNECTED, "已连接到: " + ip + ":" + port);
                registerSensorsAndLocation(); // 修改：注册传感器和位置监听
                startPeriodicDataSending();

            } catch (IOException e) {
                Log.e(TAG, "Connection failed: " + e.getMessage(), e);
                currentConnectionStatus = STATUS_FAILED;
                broadcastConnectionStatus(STATUS_FAILED, "连接失败: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "An unexpected error occurred during connection: " + e.getMessage(), e);
                currentConnectionStatus = STATUS_FAILED;
                broadcastConnectionStatus(STATUS_FAILED, "连接时发生未知错误");
            }
        });
    }

    private void registerSensorsAndLocation() {
        mainThreadHandler.post(() -> {
            if (sensorManager == null) return;
            // 只注册需要的传感器
            Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            Sensor orientation = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
            Sensor light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

            if (accelerometer != null)
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            if (orientation != null)
                sensorManager.registerListener(this, orientation, SensorManager.SENSOR_DELAY_UI);
            if (light != null)
                sensorManager.registerListener(this, light, SensorManager.SENSOR_DELAY_UI);

            Log.d(TAG, "Specified sensors registered.");

            // 注册位置监听
            if (locationManager != null) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        Log.d(TAG, "Requesting location updates for service...");
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, this);
                        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, this);
                    } catch (SecurityException e) {
                        Log.e(TAG, "SecurityException when requesting location updates in service: " + e.getMessage());
                        // 将位置错误信息也放入 sensorDataMap，以便发送
                        synchronized (sensorDataMap) {
                            sensorDataMap.put(JSON_KEY_LOCATION, new float[]{-1f, -1f}); // 表示错误或不可用
                        }
                    }
                } else {
                    Log.w(TAG, "Location permission not granted for service.");
                    synchronized (sensorDataMap) {
                        sensorDataMap.put(JSON_KEY_LOCATION, new float[]{-2f, -2f}); // 表示权限问题
                    }
                }
            }
        });
    }

    private void startPeriodicDataSending() {
        if (sendDataTask != null) {
            dataSendHandler.removeCallbacks(sendDataTask);
        }

        sendDataTask = new Runnable() {
            @Override
            public void run() {
                if (currentConnectionStatus != STATUS_CONNECTED || writer == null || socket == null || socket.isClosed()) {
                    Log.w(TAG, "Not connected or writer/socket is invalid. Stopping data sending task.");
                    if (currentConnectionStatus == STATUS_CONNECTED) {
                        currentConnectionStatus = STATUS_FAILED;
                        broadcastConnectionStatus(STATUS_FAILED, "连接意外断开，停止发送数据");
                        disconnectAndCleanup();
                    }
                    return;
                }

                networkExecutor.submit(() -> {
                    try {
                        JSONObject json = new JSONObject();
                        synchronized (sensorDataMap) {
                            if (sensorDataMap.isEmpty() && !sensorDataMap.containsKey(JSON_KEY_LOCATION)) {
                                Log.v(TAG, "Sensor data map is empty, skipping send.");
                                return;
                            }
                            // 直接从 sensorDataMap 获取指定键的数据
                            if (sensorDataMap.containsKey(JSON_KEY_LOCATION)) {
                                json.put(JSON_KEY_LOCATION, new JSONArray(sensorDataMap.get(JSON_KEY_LOCATION)));
                            }
                            if (sensorDataMap.containsKey(JSON_KEY_ACCELEROMETER)) {
                                json.put(JSON_KEY_ACCELEROMETER, new JSONArray(sensorDataMap.get(JSON_KEY_ACCELEROMETER)));
                            }
                            if (sensorDataMap.containsKey(JSON_KEY_ORIENTATION)) {
                                json.put(JSON_KEY_ORIENTATION, new JSONArray(sensorDataMap.get(JSON_KEY_ORIENTATION)));
                            }
                            if (sensorDataMap.containsKey(JSON_KEY_LIGHT)) {
                                // 光线传感器通常只有一个值
                                float[] lightValue = sensorDataMap.get(JSON_KEY_LIGHT);
                                if (lightValue != null && lightValue.length > 0) {
                                    json.put(JSON_KEY_LIGHT, lightValue[0]);
                                }
                            }
                        }
                        if (json.length() > 0) {
                            Log.v(TAG, "Sending JSON: " + json.toString());
                            writer.println(json.toString());
                            if (writer.checkError()) {
                                throw new IOException("PrintWriter encountered an error during send.");
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error creating JSON: " + e.getMessage(), e);
                    } catch (IOException e) {
                        Log.e(TAG, "Error sending data: " + e.getMessage(), e);
                        currentConnectionStatus = STATUS_FAILED;
                        mainThreadHandler.post(() -> broadcastConnectionStatus(STATUS_FAILED, "数据发送失败: " + e.getMessage()));
                        disconnectAndCleanup();
                    } catch (Exception e) {
                        Log.e(TAG, "Unexpected error during data send: " + e.getMessage(), e);
                        currentConnectionStatus = STATUS_FAILED;
                        mainThreadHandler.post(() -> broadcastConnectionStatus(STATUS_FAILED, "发送数据时未知错误"));
                        disconnectAndCleanup();
                    }
                });

                if (currentConnectionStatus == STATUS_CONNECTED) {
                    dataSendHandler.postDelayed(this, 1000);
                }
            }
        };
        dataSendHandler.post(sendDataTask);
        Log.d(TAG, "Periodic data sending task started.");
    }

    private void stopPeriodicDataSending() {
        if (sendDataTask != null) {
            dataSendHandler.removeCallbacks(sendDataTask);
            sendDataTask = null;
            Log.d(TAG, "Periodic data sending task stopped.");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        String jsonKey;
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                jsonKey = JSON_KEY_ACCELEROMETER;
                break;
            case Sensor.TYPE_ORIENTATION:
                jsonKey = JSON_KEY_ORIENTATION;
                break;
            case Sensor.TYPE_LIGHT:
                jsonKey = JSON_KEY_LIGHT;
                break;
            default:
                return; // 不是我们关心的传感器
        }
        synchronized (sensorDataMap) {
            sensorDataMap.put(jsonKey, event.values.clone());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // --- LocationListener Callbacks ---
    @Override
    public void onLocationChanged(@NonNull Location location) {
        Log.d(TAG, "Service onLocationChanged: " + location.getLatitude() + ", " + location.getLongitude());
        float[] locationData = {(float) location.getLatitude(), (float) location.getLongitude()};
        synchronized (sensorDataMap) {
            sensorDataMap.put(JSON_KEY_LOCATION, locationData);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(TAG, "Service Location Status Changed: " + provider + " status: " + status);
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
        Log.d(TAG, "Service Location Provider Enabled: " + provider);
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        Log.d(TAG, "Service Location Provider Disabled: " + provider);
        // 可以选择在这里更新 sensorDataMap 中 Location 的状态为不可用
        // 例如，如果 GPS 和 Network 都禁用了
        if (locationManager != null) {
            boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            if (!gpsEnabled && !networkEnabled) {
                synchronized (sensorDataMap) {
                    sensorDataMap.put(JSON_KEY_LOCATION, new float[]{-3f, -3f}); // 表示服务已禁用
                }
            }
        }
    }
    // --- End LocationListener Callbacks ---


    private void disconnectAndCleanup() {
        Log.i(TAG, "disconnectAndCleanup called. Current status: " + currentConnectionStatus);
        stopPeriodicDataSending();

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
            Log.d(TAG, "Sensors unregistered.");
        }
        if (locationManager != null) { // 注销位置监听
            try {
                locationManager.removeUpdates(this);
                Log.d(TAG, "Service location updates removed.");
            } catch (SecurityException e) {
                Log.e(TAG, "Error removing service location updates", e);
            }
        }

        networkExecutor.submit(() -> {
            try {
                if (writer != null) {
                    writer.close();
                    writer = null;
                }
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                    socket = null;
                }
                Log.i(TAG, "Socket and writer closed.");
            } catch (IOException e) {
                Log.e(TAG, "Error closing socket/writer: " + e.getMessage(), e);
            }
        });

        if (currentConnectionStatus != STATUS_FAILED && currentConnectionStatus != STATUS_DISCONNECTED) {
            if (currentConnectionStatus == STATUS_CONNECTED || currentConnectionStatus == STATUS_CONNECTING) {
                broadcastConnectionStatus(STATUS_DISCONNECTED, "连接已断开");
            }
        }
        currentConnectionStatus = STATUS_DISCONNECTED;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "SensorSenderService onDestroy.");
        disconnectAndCleanup();
        if (networkExecutor != null) {
            networkExecutor.shutdown();
        }
        broadcastConnectionStatus(STATUS_DISCONNECTED, "服务已停止");
        super.onDestroy();
    }

    private void broadcastConnectionStatus(int statusCode, String message) {
        Log.d(TAG, "Broadcasting status: " + statusCode + ", Message: " + message);
        Intent intent = new Intent(ACTION_CONNECTION_STATUS_UPDATE);
        intent.putExtra(EXTRA_STATUS_CODE, statusCode);
        intent.putExtra(EXTRA_STATUS_MESSAGE, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}