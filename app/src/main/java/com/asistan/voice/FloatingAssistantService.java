package com.asistan.voice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

public final class FloatingAssistantService extends Service {
    private static final String TAG = "FloatingAssistant";
    private static final String CHANNEL_ID = "assistant_active";
    private static final int NOTIFICATION_ID = 51;
    private WindowManager windowManager;
    private BlackHoleView orb;
    private WindowManager.LayoutParams layoutParams;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "stop".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission is missing; the floating assistant cannot start.");
            stopSelf();
            return START_NOT_STICKY;
        }

        createNotificationChannel();
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        if (orb == null) {
            showOrb();
        }
        return START_STICKY;
    }

    private void showOrb() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        int size = dp(82);
        layoutParams = new WindowManager.LayoutParams(
                size,
                size,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = Math.max(0, getResources().getDisplayMetrics().widthPixels - size - dp(12));
        layoutParams.y = dp(180);

        orb = new BlackHoleView(this);
        orb.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    orb.beginDrag(event.getRawX(), event.getRawY());
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    layoutParams.x = Math.max(0, Math.min(
                            getResources().getDisplayMetrics().widthPixels - size,
                            layoutParams.x + Math.round(event.getRawX() - orb.lastRawX())));
                    layoutParams.y = Math.max(0, Math.min(
                            getResources().getDisplayMetrics().heightPixels - size,
                            layoutParams.y + Math.round(event.getRawY() - orb.lastRawY())));
                    orb.track(event.getRawX(), event.getRawY());
                    windowManager.updateViewLayout(orb, layoutParams);
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    if (!orb.wasDragged()) {
                        Intent open = new Intent(this, VoiceCaptureActivity.class);
                        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(open);
                    }
                    orb.finishDrag();
                    return true;
                default:
                    return false;
            }
        });
        windowManager.addView(orb, layoutParams);
    }

    private Notification buildNotification() {
        Intent stop = new Intent(this, FloatingAssistantService.class);
        stop.setAction("stop");
        PendingIntent stopAction = PendingIntent.getService(
                this,
                0,
                stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Kara Delik Asistan aktif")
                .setContentText("Dinleme kapalı. Konuşmak için ekrandaki balona dokun.")
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Durdur", stopAction)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Asistan durumu", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Kayan asistanın açık olduğunu ve nasıl durdurulacağını gösterir.");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        if (orb != null && windowManager != null) {
            windowManager.removeView(orb);
            orb = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
