package com.asistan.voice;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

public final class MainActivity extends Activity {
    private static final int REQUEST_OVERLAY = 100;
    private static final int REQUEST_PERMISSIONS = 101;
    private static final String PREFERENCES = "assistant_settings";
    private static final String PREF_ENABLED = "assistant_enabled";
    private boolean startWhenReady;
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildScreen());
    }

    private View buildScreen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(32), dp(24), dp(24));
        page.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0B1020, 0xFF141D35, 0xFF0A0C19}));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView emblem = text("◉", 58, 0xFF70E7FF, Typeface.BOLD);
        emblem.setGravity(Gravity.CENTER);
        content.addView(emblem, matchWrap());
        TextView title = text("Kara Delik Asistan", 27, Color.WHITE, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        content.addView(title, matchWrap());
        TextView details = text(
                "“Hey Asistan” uyandırması için başlat düğmesine bas. Başladıktan sonra " +
                "mikrofon açık kalır ve kalıcı bildirim görünür; ses tanıma telefonundaki " +
                "Android hizmetini kullanır ve ağ üzerinden ses işleyebilir. Sohbet metni " +
                "ücretsiz Pollinations hizmetine gönderilir. Açık SMS komutları cihazında " +
                "işlenir. WhatsApp ve Telegram taslağı açılır; gönderme için sen onay verirsin.",
                15, 0xFFD6DCEF, Typeface.NORMAL);
        details.setPadding(dp(16), dp(16), dp(16), dp(16));
        details.setBackground(rounded(0x332B3A5B, dp(18)));
        LinearLayout.LayoutParams detailsParams = matchWrap();
        detailsParams.topMargin = dp(18);
        content.addView(details, detailsParams);
        status = text("Asistan henüz başlatılmadı.", 14, 0xFF9DAAC8, Typeface.NORMAL);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(22);
        content.addView(status, statusParams);
        Button start = button("Balonu ve Hey Asistan dinlemesini başlat");
        start.setOnClickListener(view -> enableAssistant());
        LinearLayout.LayoutParams startParams = matchWrap();
        startParams.topMargin = dp(12);
        content.addView(start, startParams);
        Button stop = button("Asistanı durdur");
        stop.setOnClickListener(view -> {
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(PREF_ENABLED, false).apply();
            stopService(new Intent(this, FloatingAssistantService.class));
            status.setText("Asistan durduruldu.");
        });
        LinearLayout.LayoutParams stopParams = matchWrap();
        stopParams.topMargin = dp(10);
        content.addView(stop, stopParams);
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        return page;
    }

    private void enableAssistant() {
        if (!Settings.canDrawOverlays(this)) {
            startWhenReady = true;
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())), REQUEST_OVERLAY);
            return;
        }
        String[] missing = missingPermissions();
        if (missing.length > 0) {
            requestPermissions(missing, REQUEST_PERMISSIONS);
        } else {
            startAssistantService();
        }
    }

    private String[] missingPermissions() {
        ArrayList<String> missing = new ArrayList<>();
        addIfMissing(missing, Manifest.permission.RECORD_AUDIO);
        addIfMissing(missing, Manifest.permission.SEND_SMS);
        addIfMissing(missing, Manifest.permission.READ_CONTACTS);
        if (Build.VERSION.SDK_INT >= 33) {
            addIfMissing(missing, Manifest.permission.POST_NOTIFICATIONS);
        }
        return missing.toArray(new String[0]);
    }

    private void addIfMissing(ArrayList<String> missing, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            missing.add(permission);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != REQUEST_PERMISSIONS) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.setText("Hey Asistan için mikrofon izni gerekli; dinleme başlatılmadı.");
        } else if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            status.setText("Mikrofon durumunu göstermek için bildirim izni gerekli.");
        } else {
            startAssistantService();
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQUEST_OVERLAY && startWhenReady) {
            startWhenReady = false;
            if (Settings.canDrawOverlays(this)) {
                enableAssistant();
            } else {
                status.setText("Balon için diğer uygulamaların üzerinde gösterme izni gerekli.");
            }
        }
    }

    private void startAssistantService() {
        if (!Settings.canDrawOverlays(this)) {
            status.setText("Balon izni verilmedi.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.setText("Mikrofon izni verilmedi; dinleme başlatılmadı.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            status.setText("Bildirim izni verilmedi; dinleme başlatılmadı.");
            return;
        }
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(PREF_ENABLED, true).apply();
        Intent service = new Intent(this, FloatingAssistantService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
        status.setText("Hey Asistan dinliyor. Kapatmak için bildirimde Durdur'a bas.");
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(0xFF090F1F);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(rounded(0xFF70E7FF, dp(16)));
        return button;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
