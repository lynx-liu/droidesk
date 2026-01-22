package com.avdi.droidesk;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView tvDeviceCode;
    private Handler handler;
    private Runnable updateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 创建显示设备码的 TextView
        tvDeviceCode = new TextView(this);
        tvDeviceCode.setTextSize(32);
        tvDeviceCode.setGravity(Gravity.CENTER);
        setContentView(tvDeviceCode);

        // 定时刷新设备码显示
        handler = new Handler(Looper.getMainLooper());
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateDeviceCode();
                handler.postDelayed(this, 1000);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(updateRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(updateRunnable);
    }

    private void updateDeviceCode() {
        int port = ForwardUtils.getCurrentPort();
        if (port > 0) {
            tvDeviceCode.setText("访问码：" + port);
        } else {
            tvDeviceCode.setText("访问码：获取中...");
        }
    }
}