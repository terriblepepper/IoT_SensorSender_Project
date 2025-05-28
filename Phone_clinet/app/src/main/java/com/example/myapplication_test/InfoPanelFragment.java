package com.example.myapplication_test;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import java.util.LinkedHashMap; // 使用 LinkedHashMap 保持插入顺序
import java.util.Locale;
import java.util.Map;

public class InfoPanelFragment extends Fragment implements SensorEventListener {
    private static final String TAG = "InfoPanelFragment";
    private TextView tv_sensor_data;
    private SensorManager mSensorMgr;
    // 使用 LinkedHashMap 保持数据显示的顺序
    private Map<String, String> sensorDataMap = new LinkedHashMap<>();
    private LocationManager locationManager;
    private boolean locationAvailable = false;

    // 定义要显示的传感器键名
    private static final String KEY_LOCATION = "经纬度";
    private static final String KEY_ACCELEROMETER = "加速度传感器";
    private static final String KEY_ORIENTATION = "方向传感器";
    private static final String KEY_LIGHT = "光线传感器";


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_info_panel, container, false);
        tv_sensor_data = view.findViewById(R.id.tv_sensor_data);

        mSensorMgr = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);

        // 初始化显示的传感器数据
        sensorDataMap.put(KEY_LOCATION, "获取失败请打开位置信息");
        sensorDataMap.put(KEY_ACCELEROMETER, "未获取到数据");
        sensorDataMap.put(KEY_ORIENTATION, "未获取到数据");
        sensorDataMap.put(KEY_LIGHT, "未获取到数据");

        updateSensorDataDisplay(); // 初始显示

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");
        registerSensorsAndLocation();
        // 如果没有获取到位置，确保提示信息正确
        if (!locationAvailable) {
            sensorDataMap.put(KEY_LOCATION, "获取失败请打开位置信息");
        }
        updateSensorDataDisplay();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called");
        unregisterSensorsAndLocation();
    }

    private void registerSensorsAndLocation() {
        // 注册传感器
        Sensor accelerometer = mSensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor orientation = mSensorMgr.getDefaultSensor(Sensor.TYPE_ORIENTATION); // 注意：TYPE_ORIENTATION 已弃用，建议使用 getRotationMatrix 和 getOrientation
        Sensor light = mSensorMgr.getDefaultSensor(Sensor.TYPE_LIGHT);

        if (accelerometer != null) mSensorMgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        if (orientation != null) mSensorMgr.registerListener(this, orientation, SensorManager.SENSOR_DELAY_UI);
        if (light != null) mSensorMgr.registerListener(this, light, SensorManager.SENSOR_DELAY_UI);

        // 初始化位置服务
        initLocationService();
    }

    private void unregisterSensorsAndLocation() {
        mSensorMgr.unregisterListener(this);
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(locationListener);
                Log.d(TAG, "Location updates removed.");
            } catch (SecurityException e) {
                Log.e(TAG, "Error removing location updates", e);
            }
        }
    }


    private void initLocationService() {
        locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationAvailable = false;
            sensorDataMap.put(KEY_LOCATION, "获取失败请打开位置信息");
            updateSensorDataDisplay();
            ActivityCompat.requestPermissions(requireActivity(), new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, 1);
            return;
        }
        try {
            Log.d(TAG, "Requesting location updates...");
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, locationListener);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException in initLocationService: " + e.getMessage());
            locationAvailable = false;
            sensorDataMap.put(KEY_LOCATION, "位置权限错误，请检查");
            updateSensorDataDisplay();
        }
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            Log.d(TAG, "Location changed: " + location.getLatitude() + ", " + location.getLongitude());
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            locationAvailable = true;
            sensorDataMap.put(KEY_LOCATION, String.format(Locale.CHINESE, "纬度=%.6f°, 经度=%.6f°", latitude, longitude));
            updateSensorDataDisplay();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override
        public void onProviderEnabled(@NonNull String provider) {}

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            Log.d(TAG, "Provider disabled: " + provider);
            // locationAvailable = false; // 只有当所有提供者都禁用时才设置
            // 检查是否还有其他可用的提供者
            boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            if (!gpsEnabled && !networkEnabled) {
                locationAvailable = false;
                sensorDataMap.put(KEY_LOCATION, "位置服务已禁用");
            } else {
                sensorDataMap.put(KEY_LOCATION, provider + " 已禁用，尝试其他提供者");
            }
            updateSensorDataDisplay();
        }
    };


    @Override
    public void onSensorChanged(SensorEvent event) {
        String sensorDisplayName;
        String valueString = "";

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                sensorDisplayName = KEY_ACCELEROMETER;
                valueString = String.format(Locale.CHINESE, "x=%.2f, y=%.2f, z=%.2f",
                        event.values[0], event.values[1], event.values[2]);
                break;
            case Sensor.TYPE_ORIENTATION:
                sensorDisplayName = KEY_ORIENTATION;
                valueString = String.format(Locale.CHINESE, "方位角=%.1f°, 俯仰角=%.1f°, 滚转角=%.1f°",
                        event.values[0], event.values[1], event.values[2]);
                break;
            case Sensor.TYPE_LIGHT:
                sensorDisplayName = KEY_LIGHT;
                valueString = String.format(Locale.CHINESE, "光强=%.2f lx", event.values[0]);
                break;
            default:
                return;
        }
        sensorDataMap.put(sensorDisplayName, valueString);
        updateSensorDataDisplay();
    }

    private void updateSensorDataDisplay() {
        if (!isAdded() || tv_sensor_data == null) {
            return;
        }
        StringBuilder sensorDataDisplay = new StringBuilder("实时传感器数据：\n");
        for (Map.Entry<String, String> entry : sensorDataMap.entrySet()) {
            sensorDataDisplay.append(String.format(Locale.CHINESE, "%s：%s\n", entry.getKey(), entry.getValue()));
        }
        tv_sensor_data.setText(sensorDataDisplay.toString());
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView called");
        // unregisterSensorsAndLocation(); // 已经在 onPause 中处理
        tv_sensor_data = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && (grantResults[0] == PackageManager.PERMISSION_GRANTED || (grantResults.length > 1 && grantResults[1] == PackageManager.PERMISSION_GRANTED))) {
                Log.d(TAG, "Location permission granted.");
                initLocationService(); // 权限授予后重新初始化
            } else {
                Log.d(TAG, "Location permission denied.");
                locationAvailable = false;
                sensorDataMap.put(KEY_LOCATION, "位置权限被拒绝");
                updateSensorDataDisplay();
            }
        }
    }
}