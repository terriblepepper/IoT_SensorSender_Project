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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class InfoPanelFragment extends Fragment implements SensorEventListener {
    private TextView tv_sensor_data, tv_server_status;
    private SensorManager mSensorMgr;
    private Map<String, String> sensorDataMap = new HashMap<>();
    private LocationManager locationManager;
    private boolean locationAvailable = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_info_panel, container, false);
        tv_sensor_data = view.findViewById(R.id.tv_sensor_data);
        tv_server_status = view.findViewById(R.id.tv_server_status);

        mSensorMgr = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        registerSensors();
        initLocationService();

        // 服务器状态可通过接口或全局变量传递，这里仅作演示
        tv_server_status.setText("服务器未连接");

        // 初始时提示经纬度不可用
        sensorDataMap.put("经纬度", "获取失败请打开位置信息");
        updateSensorDataDisplay();

        return view;
    }

    private void registerSensors() {
        Sensor accelerometer = mSensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor magneticField = mSensorMgr.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        Sensor gyroscope = mSensorMgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor light = mSensorMgr.getDefaultSensor(Sensor.TYPE_LIGHT);
        Sensor orientation = mSensorMgr.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        if (accelerometer != null) mSensorMgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        if (magneticField != null) mSensorMgr.registerListener(this, magneticField, SensorManager.SENSOR_DELAY_UI);
        if (gyroscope != null) mSensorMgr.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI);
        if (light != null) mSensorMgr.registerListener(this, light, SensorManager.SENSOR_DELAY_UI);
        if (orientation != null) mSensorMgr.registerListener(this, orientation, SensorManager.SENSOR_DELAY_UI);
    }

    private void initLocationService() {
        locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // 没有权限，提示失败
            locationAvailable = false;
            sensorDataMap.put("经纬度", "获取失败请打开位置信息");
            updateSensorDataDisplay();
            ActivityCompat.requestPermissions(requireActivity(), new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, 1);
            return;
        }
        // 同时监听 GPS 和网络定位
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, locationListener);
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            locationAvailable = true;
            sensorDataMap.put("经纬度", String.format(Locale.CHINESE, "纬度=%.6f°, 经度=%.6f°", latitude, longitude));
            updateSensorDataDisplay();
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        // 如果没有获取到位置，始终显示失败提示
        if (!locationAvailable) {
            sensorDataMap.put("经纬度", "获取失败请打开位置信息");
            updateSensorDataDisplay();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        String sensorName;
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                sensorName = "加速度传感器";
                sensorDataMap.put(sensorName, String.format(Locale.CHINESE, "x=%.2f, y=%.2f, z=%.2f",
                        event.values[0], event.values[1], event.values[2]));
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                sensorName = "磁场传感器";
                sensorDataMap.put(sensorName, String.format(Locale.CHINESE, "x=%.2f, y=%.2f, z=%.2f",
                        event.values[0], event.values[1], event.values[2]));
                break;
            case Sensor.TYPE_GYROSCOPE:
                sensorName = "陀螺仪传感器";
                sensorDataMap.put(sensorName, String.format(Locale.CHINESE, "x=%.2f, y=%.2f, z=%.2f",
                        event.values[0], event.values[1], event.values[2]));
                break;
            case Sensor.TYPE_LIGHT:
                sensorName = "光线传感器";
                sensorDataMap.put(sensorName, String.format(Locale.CHINESE, "光强=%.2f lx", event.values[0]));
                break;
            case Sensor.TYPE_ORIENTATION:
                sensorName = "方向传感器";
                sensorDataMap.put(sensorName, String.format(Locale.CHINESE, "方位角=%.2f°, 俯仰角=%.2f°, 滚转角=%.2f°",
                        event.values[0], event.values[1], event.values[2]));
                break;
            default:
                return;
        }
        updateSensorDataDisplay();
    }

    private void updateSensorDataDisplay() {
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
        mSensorMgr.unregisterListener(this);
        if (locationManager != null && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.removeUpdates(locationListener);
        }
    }
}