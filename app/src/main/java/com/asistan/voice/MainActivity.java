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

public final class MainActivity extends Activity {
    private static final int REQUEST_OVERLAY = 100;
    private static final int REQUEST_PERMISSIONS = 101;
    private boolean startWhenReady;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildScreen());
    }

    private View buildScreen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(32), dp(24), dp(24));
        page.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(11, 16, 32), Color.rgb(20, 29, 53), Color.rgb(10, 12, 25)}));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView emblem = text("◉", 58, 0xFF70E7FF, Typeface.BOLD);
        emblem.setGravity(Gravity.CENTER);
        content.addView(emblem, matchWrap());

        TextView title = text("Kara Delik Asistan", 27, Color.WHITE, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(8);
        content.addView(title, titleParams);

        TextView subtitle = text(
                "Sesli sohbet, Google'a gitme ve izin verdiğinde SMS gönderme.",
                16, 0xFFC4CBE0, Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(12);
        subtitleParams.bottomMargin = dp(20);
        content.addView(subtitle, subtitleParams);

        TextView details = text(
                "Kullanmak için balona dokun: mikrofon sürekli dinlemez. " +
                "Ses tanımayı telefonundaki Android hizmeti sağlar; bu hizmet ağ kullanabilir. " +
                "Sohbet yanıtı almak için söylediğin metin ücretsiz, anahtarsız " +
                "Pollinations hizmetine internet üzerinden gönderilir. " +
                "SMS komutları cihazında işlenir; otomatik gönderim için SMS ve " +
                "rehber izinlerini vermen gerekir. Diğer mesajlaşma uygulamalarında " +
                "gönderim senin onayına bırakılır.",
                14, 0xFFD6DCEF, Typeface.NORMAL);
        details.setPadding(dp(16), dp(16), dp(16), dp(16));
        details.setBackground(rounded(0x332B3A5B, dp(18)));
        content.addView(details, matchWrap());

        status = text("Asistan henüz başlatılmadı.", 14, 0xFF9DAAC8, Typeface.NORMAL);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(22);
        content.addView(status, statusParams);

        Button start = button("Kara delik balonunu başlat");
        start.setOnClickListener(view -> enableAssistant());
        LinearLayout.LayoutParams buttonParams = matchWrap();
        buttonParams.topMargin = dp(12);
        content.addView(start, buttonParams);

        Button stop = button("Balonu durdur");
        stop.setOnClickListener(view -> {
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
            Intent permissionScreen = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(permissionScreen, REQUEST_OVERLAY);
            return;
        }

        if (missingRuntimePermissions().length > 0) {
            requestPermissions(missingRuntimePermissions(), REQUEST_PERMISSIONS);
            return;
        }
        startOverlayService();
    }

    private String[] missingRuntimePermissions() {
        java.util.ArrayList<String> missing = new java.util.ArrayList<>();
        addIfMissing(missing, Manifest.permission.RECORD_AUDIO);
        addIfMissing(missing, Manifest.permission.SEND_SMS);
        addIfMissing(missing, Manifest.permission.READ_CONTACTS);
        if (Build.VERSION.SDK_INT >= 33) {
            addIfMissing(missing, Manifest.permission.POST_NOTIFICATIONS);
        }
        return missing.toArray(new String[0]);
    }

    private void addIfMissing(java.util.List<String> missing, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            missing.add(permission);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            startOverlayService();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY && startWhenReady) {
            startWhenReady = false;
            if (Settings.canDrawOverlays(this)) {
                enableAssistant();
            } else {
                status.setText("Balon için diğer uygulamaların üzerinde gösterme izni gerekli.");
            }
        }
    }

    private void startOverlayService() {
        if (!Settings.canDrawOverlays(this)) {
            status.setText("Balon izni verilmedi. Ayarlar'dan izin verip tekrar dene.");
            return;
        }
        Intent service = new Intent(this, FloatingAssistantService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
        status.setText("Asistan çalışıyor. Ekranın köşesindeki kara deliğe dokun.");
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.rgb(9, 15, 31));
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(rounded(0xFF70E7FF, dp(16)));
        return button;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setTypeface(Typeface.DEFAULT, style);
        return text;
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
