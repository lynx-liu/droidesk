package com.avdi.droidesk;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class DeskService extends Service {
    private static final String TAG = "DeskService";

    @Override
    public void onCreate() {
        super.onCreate();
        if (!Utils.isRunning("ssh")) {
            new Thread(() -> Utils.runSSH(getApplicationContext(),5555)).start();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
