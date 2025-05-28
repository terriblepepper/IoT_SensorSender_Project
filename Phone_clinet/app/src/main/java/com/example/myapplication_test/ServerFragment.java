package com.example.myapplication_test;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView; // 导入 TextView
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager; // 导入

public class ServerFragment extends Fragment {
    private static final String TAG = "ServerFragment"; // 日志标签

    private EditText et_ip, et_port;
    private Button btn_send;
    private TextView tv_connection_status_message; // 新增的 TextView

    private BroadcastReceiver connectionStatusReceiver;
    // private boolean isSending = false; // 这个标志可以被服务状态替代

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_server, container, false);
        et_ip = view.findViewById(R.id.et_ip);
        et_port = view.findViewById(R.id.et_port);
        btn_send = view.findViewById(R.id.btn_send);
        tv_connection_status_message = view.findViewById(R.id.tv_connection_status_message); // 获取 TextView

        // 根据一个初始的或查询到的服务状态来设置UI
        // 这里我们假设初始是断开状态
        updateUI(SensorSenderService.STATUS_DISCONNECTED, "点击连接开始发送数据");

        btn_send.setOnClickListener(v -> {
            // 判断当前是要连接还是断开，可以基于按钮的文本
            if (btn_send.getText().toString().equalsIgnoreCase(getString(R.string.server_fragment_button_connect))) {
                String ip = et_ip.getText().toString().trim();
                String portStr = et_port.getText().toString().trim();

                if (TextUtils.isEmpty(ip)) {
                    et_ip.setError("IP地址不能为空");
                    return;
                }
                if (TextUtils.isEmpty(portStr)) {
                    et_port.setError("端口号不能为空");
                    return;
                }
                int port;
                try {
                    port = Integer.parseInt(portStr);
                    if (port <= 0 || port > 65535) {
                        et_port.setError("端口号范围是 1-65535");
                        return;
                    }
                } catch (NumberFormatException e) {
                    et_port.setError("端口号必须是数字");
                    return;
                }

                // 清除错误提示
                et_ip.setError(null);
                et_port.setError(null);

                Intent intent = new Intent(requireContext(), SensorSenderService.class);
                intent.putExtra("ip", ip);
                intent.putExtra("port", port);
                requireContext().startService(intent);
                // UI 会在收到服务的 STATUS_CONNECTING 广播后更新
                // btn_send.setText(R.string.server_fragment_button_connecting); // 可以立即给一个反馈
                // setInputsEnabled(false);
            } else { // 如果按钮文本不是 "连接"，则认为是请求断开
                requireContext().stopService(new Intent(requireContext(), SensorSenderService.class));
                // UI 会在收到服务的 STATUS_DISCONNECTED 广播后更新
            }
        });

        setupBroadcastReceiver();
        return view;
    }

    private void setupBroadcastReceiver() {
        connectionStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && SensorSenderService.ACTION_CONNECTION_STATUS_UPDATE.equals(intent.getAction())) {
                    int statusCode = intent.getIntExtra(SensorSenderService.EXTRA_STATUS_CODE, -1);
                    String message = intent.getStringExtra(SensorSenderService.EXTRA_STATUS_MESSAGE);
                    Log.d(TAG, "Received status update: " + statusCode + ", Message: " + message);
                    updateUI(statusCode, message);
                }
            }
        };
    }

    private void updateUI(int statusCode, @Nullable String message) {
        if (isAdded() && tv_connection_status_message != null && btn_send != null) { // 确保Fragment已添加到Activity且View存在
            tv_connection_status_message.setText(message != null ? message : getDefaultMessageForStatus(statusCode));

            switch (statusCode) {
                case SensorSenderService.STATUS_DISCONNECTED:
                case SensorSenderService.STATUS_FAILED:
                    btn_send.setText(R.string.server_fragment_button_connect);
                    setInputsEnabled(true);
                    if (statusCode == SensorSenderService.STATUS_FAILED && message != null) {
                        // 可以用Toast显示更详细的错误，或者tv_connection_status_message已经足够
                        // Toast.makeText(getContext(), "连接失败: " + message, Toast.LENGTH_LONG).show();
                    }
                    break;
                case SensorSenderService.STATUS_CONNECTING:
                    btn_send.setText(R.string.server_fragment_button_connecting);
                    setInputsEnabled(false); // 连接过程中不允许修改IP和端口
                    break;
                case SensorSenderService.STATUS_CONNECTED:
                    btn_send.setText(R.string.server_fragment_button_disconnect);
                    setInputsEnabled(false); // 连接成功后通常也不允许修改IP和端口，除非断开
                    break;
                default:
                    // 未知状态，可以恢复到初始
                    btn_send.setText(R.string.server_fragment_button_connect);
                    setInputsEnabled(true);
                    tv_connection_status_message.setText("未知连接状态");
                    break;
            }
        } else {
            Log.w(TAG, "Fragment not added or views are null, skipping UI update.");
        }
    }

    private String getDefaultMessageForStatus(int statusCode) {
        switch (statusCode) {
            case SensorSenderService.STATUS_DISCONNECTED: return "已断开连接";
            case SensorSenderService.STATUS_FAILED: return "连接失败";
            case SensorSenderService.STATUS_CONNECTING: return "正在连接...";
            case SensorSenderService.STATUS_CONNECTED: return "已连接";
            default: return "未知状态";
        }
    }


    private void setInputsEnabled(boolean enabled) {
        if (et_ip != null && et_port != null && btn_send != null) {
            et_ip.setEnabled(enabled);
            et_port.setEnabled(enabled);
            // 按钮的启用/禁用逻辑可以更细致，例如连接中时禁用按钮
            // btn_send.setEnabled(enabled); // 在updateUI中具体处理按钮文本和状态
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(SensorSenderService.ACTION_CONNECTION_STATUS_UPDATE);
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(connectionStatusReceiver, filter);
        Log.d(TAG, "BroadcastReceiver registered.");
        // 可选: 当Fragment恢复时，可以主动请求一次服务状态，以同步UI
        // 这需要服务支持一个查询状态的Intent Action
        // 或者，服务在 onStartCommand 被调用且已经连接时，主动发送一次状态广播
        // (SensorSenderService的onStartCommand已包含此逻辑)
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(connectionStatusReceiver);
        Log.d(TAG, "BroadcastReceiver unregistered.");
    }
}