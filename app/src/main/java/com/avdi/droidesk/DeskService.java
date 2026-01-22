package com.avdi.droidesk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class DeskService extends Service {
    private static final int NOTIF_ID = 1001;
    private static final String CHANNEL_ID = "droidesk_service";

    @Override
    public void onCreate() {
        super.onCreate();
        startAsForeground();

        new ForwardUtils(this).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Droidesk", NotificationManager.IMPORTANCE_LOW);
                nm.createNotificationChannel(ch);
            }
        }

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        Notification n = b.setContentTitle("Droidesk")
                .setContentText("DeskService running")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .build();

        startForeground(NOTIF_ID, n);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
