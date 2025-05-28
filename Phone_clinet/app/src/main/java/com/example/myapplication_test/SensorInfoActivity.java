package com.example.myapplication_test;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;
import androidx.fragment.app.Fragment;
import com.google.android.material.appbar.MaterialToolbar;
import androidx.appcompat.app.ActionBarDrawerToggle;

public class SensorInfoActivity extends AppCompatActivity {
    private DrawerLayout drawerLayout;
    private NavigationView navView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_info); // 确保 R.layout.activity_sensor_info 是你的主活动布局文件

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        navView = findViewById(R.id.nav_view);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // 默认显示信息面板
        if (savedInstanceState == null) { // 仅在 Activity 首次创建时加载默认 Fragment
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new InfoPanelFragment())
                    .commit();
            navView.setCheckedItem(R.id.nav_info_panel); // 设置导航菜单的默认选中项
        }

        navView.setNavigationItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int id = item.getItemId();

            if (id == R.id.nav_info_panel) {
                selectedFragment = new InfoPanelFragment();
            } else if (id == R.id.nav_server) {
                selectedFragment = new ServerFragment();
            }
            // SensorsFragment 的导航逻辑已被移除

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
                drawerLayout.closeDrawers();
            }
            return true;
        });
    }
}