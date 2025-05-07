package com.example.myapplication_test;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SensorsFragment extends Fragment {
    private TextView tv_sensor_list;
    private final String[] mSensorType = {
            "加速度", "磁场", "方向", "陀螺仪", "光线",
            "压力", "温度", "距离", "重力", "线性加速度",
            "旋转矢量", "湿度", "环境温度", "无标定磁场", "无标定旋转矢量",
            "未校准陀螺仪", "特殊动作", "步行检测", "计步器", "地磁旋转矢量",
            "心跳速率", "倾斜检测", "唤醒手势", "掠过手势", "拾起手势",
            "手腕倾斜", "设备方向", "六自由度姿态", "静止检测", "运动检测",
            "心跳检测", "动态元事件", "未知", "低延迟离体检测", "低延迟体外检测"};

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sensors, container, false);
        tv_sensor_list = view.findViewById(R.id.tv_sensor_list);
        showSensorInfo();
        return view;
    }

    private void showSensorInfo() {
        SensorManager mSensorMgr = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensorList = mSensorMgr.getSensorList(Sensor.TYPE_ALL);

        // 按类型分组
        Map<Integer, List<Sensor>> typeMap = new HashMap<>();
        for (Sensor sensor : sensorList) {
            int type = sensor.getType();
            if (!typeMap.containsKey(type)) {
                typeMap.put(type, new ArrayList<>());
            }
            typeMap.get(type).add(sensor);
        }

        StringBuilder show_content = new StringBuilder("当前支持的传感器包括：\n");
        for (int i = 0; i < mSensorType.length; i++) {
            int type = i + 1;
            if (typeMap.containsKey(type)) {
                show_content.append(mSensorType[i]).append("：\n");
                for (Sensor sensor : typeMap.get(type)) {
                    show_content.append("  ").append(sensor.getName()).append("\n");
                }
            }
        }
        tv_sensor_list.setText(show_content.toString());
    }
}