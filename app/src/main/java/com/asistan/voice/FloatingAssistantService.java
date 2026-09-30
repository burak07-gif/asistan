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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FloatingAssistantService extends Service {
    private static final String TAG = "FloatingAssistant";
    private static final String CHANNEL_ID = "assistant_active";
    private static final int NOTIFICATION_ID = 51;
    static final String ACTION_STOP = "com.asistan.voice.STOP";
    static final String ACTION_RESUME_WAKE_WORD = "com.asistan.voice.RESUME_WAKE_WORD";
    static final String EXTRA_INITIAL_SPOKEN_TEXT = "com.asistan.voice.INITIAL_SPOKEN_TEXT";
    private static final String PREFERENCES = "assistant_settings";
    private static final String PREF_ENABLED = "assistant_enabled";
    private static final Pattern WAKE_WORD = Pattern.compile(
            "(?iu)\\bhey\\s+(?:asistan|assistant)\\b[\\s,.;:!?-]*");
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private BlackHoleView orb;
    private WindowManager.LayoutParams layoutParams;
    private SpeechRecognizer wakeRecognizer;
    private boolean wakeListening;
    private boolean wakePaused;
    private boolean stopping;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(PREF_ENABLED, false).apply();
            stopping = true;
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_RESUME_WAKE_WORD.equals(intent.getAction())) {
            wakePaused = false;
        } else if (intent == null
                && !getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(PREF_ENABLED, false)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission is missing; the floating assistant cannot start.");
            stopSelf();
            return START_NOT_STICKY;
        }

        createNotificationChannel();
        Notification notification = buildNotification("Hey Asistan uyandırması açık. Duraklatmak için balona dokun.");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                            | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        if (orb == null) {
            showOrb();
        }
        if (!wakePaused) {
            startWakeWordListening();
        } else if (intent != null && ACTION_RESUME_WAKE_WORD.equals(intent.getAction())) {
            updateNotification("Hey Asistan uyandırması yeniden dinliyor.");
        }
        return START_STICKY;
    }

    private void showOrb() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        int size = dp(82);
        layoutParams = new WindowManager.LayoutParams(
                size, size,
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
                        pauseWakeWordListening();
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

    private void startWakeWordListening() {
        if (wakePaused || wakeListening || stopping) {
            return;
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Microphone permission is missing; wake-word listening is unavailable.");
            updateNotification("Mikrofon izni gerekli; Asistan uygulamasını açıp izin ver.");
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "No speech recognition service is available.");
            updateNotification("Bu telefonda ses tanıma hizmeti bulunamadı.");
            return;
        }

        try {
            destroyWakeRecognizer();
            wakeRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            wakeRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onEvent(int eventType, Bundle params) { }
                @Override public void onPartialResults(Bundle partialResults) { }

                @Override
                public void onResults(Bundle results) {
                    wakeListening = false;
                    ArrayList<String> matches = results.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null) {
                        for (String match : matches) {
                            if (handleWakePhrase(match)) {
                                return;
                            }
                        }
                    }
                    scheduleWakeWordRetry(350);
                }

                @Override
                public void onError(int error) {
                    wakeListening = false;
                    if (error != SpeechRecognizer.ERROR_CLIENT
                            && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        Log.w(TAG, "Wake-word speech recognition error: " + error);
                    }
                    scheduleWakeWordRetry(
                            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 1500 : 800);
                }
            });

            Intent request = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            request.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            request.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
            request.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR");
            request.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            request.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            wakeListening = true;
            wakeRecognizer.startListening(request);
        } catch (SecurityException | IllegalStateException error) {
            wakeListening = false;
            Log.e(TAG, "Could not start wake-word recognition.", error);
            updateNotification("Uyandırma başlatılamadı: mikrofon iznini ve ses tanımayı kontrol et.");
            scheduleWakeWordRetry(2000);
        }
    }

    private boolean handleWakePhrase(String spoken) {
        Matcher wakeMatch = WAKE_WORD.matcher(spoken);
        if (!wakeMatch.find()) {
            return false;
        }

        String command = spoken.substring(wakeMatch.end()).trim();
        wakePaused = true;
        wakeListening = false;
        destroyWakeRecognizer();
        updateNotification("Hey Asistan uyandı. Dinleme, sohbet ekranı kapanınca sürer.");

        Intent open = new Intent(this, VoiceCaptureActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (!command.isEmpty()) {
            open.putExtra(EXTRA_INITIAL_SPOKEN_TEXT, command);
        }
        try {
            startActivity(open);
        } catch (SecurityException | IllegalStateException error) {
            Log.e(TAG, "Could not open the assistant after the wake phrase.", error);
            wakePaused = false;
            scheduleWakeWordRetry(1000);
        }
        return true;
    }

    private void scheduleWakeWordRetry(long delayMillis) {
        mainHandler.removeCallbacksAndMessages(null);
        mainHandler.postDelayed(this::startWakeWordListening, delayMillis);
    }

    private void pauseWakeWordListening() {
        wakePaused = true;
        wakeListening = false;
        mainHandler.removeCallbacksAndMessages(null);
        destroyWakeRecognizer();
        updateNotification("Mikrofon duraklatıldı. Konuşmayı bitirince “Hey Asistan” yeniden açılır.");
    }

    private void destroyWakeRecognizer() {
        if (wakeRecognizer != null) {
            wakeRecognizer.cancel();
            wakeRecognizer.destroy();
            wakeRecognizer = null;
        }
    }

    private void updateNotification(String message) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(message));
        }
    }

    private Notification buildNotification(String text) {
        Intent stop = new Intent(this, FloatingAssistantService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopAction = PendingIntent.getService(
                this, 0, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Kara Delik Asistan aktif")
                .setContentText(text)
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
        stopping = true;
        mainHandler.removeCallbacksAndMessages(null);
        destroyWakeRecognizer();
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
