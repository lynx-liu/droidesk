package com.avdi.droidesk;

import android.app.Application;
import android.content.Intent;

public class AppBoot extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Intent intent = new Intent(Intent.ACTION_RUN);
        intent.setClass(this, DeskService.class);
        startService(intent);
    }
}
