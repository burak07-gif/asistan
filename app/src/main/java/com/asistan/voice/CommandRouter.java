package com.asistan.voice;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.telephony.SmsManager;
import android.widget.TextView;

import java.util.Locale;

final class CommandRouter {
    private CommandRouter() { }

    static boolean tryRun(Activity activity, String spoken, TextView result) {
        String lower = spoken.toLowerCase(new Locale("tr", "TR"));
        if (lower.contains("google") && containsAny(lower, "gir", "aç", "git", "ara")) {
            openGoogle(activity, lower);
            result.setText("Google açılıyor.");
            return true;
        }

        if (!SmsCommand.hasExplicitSendIntent(lower)) {
            return false;
        }

        SmsCommand.Request request = SmsCommand.parse(spoken);
        if (request == null) {
            result.setText("Mesaj komutunu anlayamadım; güvenlik için hiçbir mesaj gönderilmedi. " +
                    "Örnek: “Ahmet'e şu mesajı gönder: Yarın saat üçte görüşelim.”");
            return true;
        }

        SmsCommand.ContactTarget target = SmsCommand.findContact(activity, request.recipient);
        if (target == null) {
            result.setText("Rehberde bu kişi için tek bir telefon numarası bulamadım. " +
                    "Mesaj gönderilmedi; rehber iznini kontrol et veya kişiyi daha açık söyle.");
            return true;
        }

        if (lower.contains("whatsapp")) {
            boolean opened = openWhatsAppDraft(activity, target.phone, request.message);
            result.setText(opened
                    ? "WhatsApp taslağı açıldı. Göndermek için WhatsApp'ta senin onayın gerekir."
                    : "WhatsApp açılamadı; SMS gönderilmedi.");
            return true;
        }

        if (lower.contains("telegram")) {
            boolean opened = openTelegramDraft(activity, request.message);
            result.setText(opened
                    ? "Telegram paylaşımı açıldı. Kişiyi seçip göndermeyi Telegram'da onayla."
                    : "Telegram açılamadı; SMS gönderilmedi.");
            return true;
        }

        if (containsAny(lower, "signal", "instagram", "messenger", "discord", "viber")) {
            result.setText("Bu mesajlaşma uygulamasına otomatik gönderim desteklenmiyor; SMS gönderilmedi.");
            return true;
        }

        if (activity.checkSelfPermission(Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            boolean opened = openSmsDraft(activity, target.phone, request.message);
            result.setText(opened
                    ? "SMS izni verilmedi; mesaj taslağını açtım. Göndermek için tuşa sen bas."
                    : "SMS izni yok ve SMS taslağı açacak bir uygulama bulunamadı.");
            return true;
        }

        try {
            SmsManager manager = activity.getSystemService(SmsManager.class);
            if (manager == null) {
                throw new IllegalStateException("Bu telefonda SMS servisi bulunamadı.");
            }
            java.util.ArrayList<String> parts = manager.divideMessage(request.message);
            if (parts.size() == 1) {
                manager.sendTextMessage(target.phone, null, request.message, null, null);
            } else {
                manager.sendMultipartTextMessage(target.phone, null, parts, null, null);
            }
            result.setText("SMS gönderim isteği telefona verildi: " + target.name);
        } catch (SecurityException | IllegalArgumentException | IllegalStateException error) {
            result.setText("SMS gönderilemedi: " + error.getMessage());
        }
        return true;
    }

    private static void openGoogle(Activity activity, String spoken) {
        if (spoken.contains(" ara ")) {
            String query = spoken.substring(spoken.indexOf(" ara ") + 5).trim();
            Intent search = new Intent(Intent.ACTION_WEB_SEARCH);
            search.putExtra("query", query);
            try {
                activity.startActivity(search);
                return;
            } catch (ActivityNotFoundException ignored) {
                // Fall back to the Google app or its website below.
            }
        }
        Intent google = activity.getPackageManager()
                .getLaunchIntentForPackage("com.google.android.googlequicksearchbox");
        if (google != null) {
            activity.startActivity(google);
        } else {
            activity.startActivity(new Intent(
                    Intent.ACTION_VIEW, Uri.parse("https://www.google.com")));
        }
    }

    private static boolean openSmsDraft(Activity activity, String phone, String message) {
        try {
            Intent draft = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone, null));
            draft.putExtra("sms_body", message);
            activity.startActivity(draft);
            return true;
        } catch (ActivityNotFoundException error) {
            return false;
        }
    }

    private static boolean openWhatsAppDraft(Activity activity, String phone, String message) {
        String digits = android.telephony.PhoneNumberUtils.stripSeparators(phone);
        String url = "https://wa.me/" + digits + "?text=" + Uri.encode(message);
        Intent draft = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            activity.startActivity(draft);
            return true;
        } catch (ActivityNotFoundException ignored) {
            return false;
        }
    }

    private static boolean openTelegramDraft(Activity activity, String message) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, message);
        share.setPackage("org.telegram.messenger");
        try {
            activity.startActivity(share);
            return true;
        } catch (ActivityNotFoundException error) {
            return false;
        }
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
