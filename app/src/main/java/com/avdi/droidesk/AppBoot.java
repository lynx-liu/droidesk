package com.avdi.droidesk;

import android.app.Application;
import android.content.Intent;

public class AppBoot extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Intent intent = new Intent(this, DeskService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
}
