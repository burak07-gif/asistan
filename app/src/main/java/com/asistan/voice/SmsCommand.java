package com.asistan.voice;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SmsCommand {
    private static final Pattern[] COMMANDS = {
            Pattern.compile("(?is)^(.+?)(?:\\s+(?:kişisine|numarasına)|['’](?:e|a|ye|ya))\\s+" +
                    "(?:şu\\s+|bu\\s+)?(?:mesajı|sms['’]i)\\s+(?:gönder|yolla|at)\\s*[:,-]?\\s*(.+)$"),
            Pattern.compile("(?is)^(.+?)\\s+(?:mesajını|mesajı|sms['’]i)\\s+" +
                    "(?:gönder|yolla|at)\\s*[:,-]?\\s*(.+)$")
    };

    private SmsCommand() { }

    static boolean hasExplicitSendIntent(String lower) {
        return (lower.contains("mesaj") || lower.contains("sms"))
                && (lower.contains("gönder") || lower.contains("yolla") || lower.contains(" at"));
    }

    static Request parse(String spoken) {
        String command = spoken.trim();
        for (Pattern pattern : COMMANDS) {
            Matcher match = pattern.matcher(command);
            if (match.matches()) {
                String recipient = cleanRecipient(match.group(1));
                String message = cleanMessage(match.group(2));
                if (!recipient.isEmpty() && !message.isEmpty()) {
                    return new Request(recipient, message);
                }
            }
        }
        return null;
    }

    static ContactTarget findContact(Activity activity, String requestedName) {
        if (activity.checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        Uri lookup = Uri.withAppendedPath(
                ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
                Uri.encode(requestedName));
        String[] columns = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        try (Cursor cursor = activity.getContentResolver().query(
                lookup, columns, null, null, null)) {
            if (cursor == null) {
                return null;
            }
            ContactTarget found = null;
            String normalizedRequest = normalize(requestedName);
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                String phone = cursor.getString(1);
                if (name == null || phone == null || !normalize(name).equals(normalizedRequest)) {
                    continue;
                }
                if (found != null && !android.telephony.PhoneNumberUtils.compare(
                        activity, found.phone, phone)) {
                    return null;
                }
                found = new ContactTarget(name, phone);
            }
            return found;
        }
    }

    private static String cleanRecipient(String value) {
        String recipient = value.trim()
                .replaceFirst("(?i)^(whatsapp(?:'ta|’ta)?|telegram(?:'da|’da)?)\\s+", "")
                .trim();
        return recipient.replaceFirst("(?i)['’](?:e|a|ye|ya)$", "").trim();
    }

    private static String cleanMessage(String value) {
        return value.trim().replaceFirst("^[\\s:,-]+", "").trim();
    }

    private static String normalize(String value) {
        String lower = value.toLowerCase(new Locale("tr", "TR"));
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9]", "");
    }

    static final class Request {
        final String recipient;
        final String message;

        Request(String recipient, String message) {
            this.recipient = recipient;
            this.message = message;
        }
    }

    static final class ContactTarget {
        final String name;
        final String phone;

        ContactTarget(String name, String phone) {
            this.name = name;
            this.phone = phone;
        }
    }
}
