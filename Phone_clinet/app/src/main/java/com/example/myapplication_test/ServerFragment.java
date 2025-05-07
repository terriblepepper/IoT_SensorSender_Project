package com.example.myapplication_test;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class ServerFragment extends Fragment {
    private EditText et_ip, et_port;
    private Button btn_send;
    private boolean isSending = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_server, container, false);
        et_ip = view.findViewById(R.id.et_ip);
        et_port = view.findViewById(R.id.et_port);
        btn_send = view.findViewById(R.id.btn_send);

        btn_send.setOnClickListener(v -> {
            if (!isSending) {
                String ip = et_ip.getText().toString().trim();
                String portStr = et_port.getText().toString().trim();
                if (!ip.isEmpty() && !portStr.isEmpty()) {
                    int port = Integer.parseInt(portStr);
                    Intent intent = new Intent(requireContext(), SensorSenderService.class);
                    intent.putExtra("ip", ip);
                    intent.putExtra("port", port);
                    requireContext().startService(intent);
                    btn_send.setText("断开连接");
                    et_ip.setEnabled(false);
                    et_port.setEnabled(false);
                    isSending = true;
                }
            } else {
                requireContext().stopService(new Intent(requireContext(), SensorSenderService.class));
                btn_send.setText("连接");
                et_ip.setEnabled(true);
                et_port.setEnabled(true);
                isSending = false;
            }
        });
        return view;
    }
}