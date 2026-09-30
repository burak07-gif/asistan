package com.asistan.voice;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class AssistantClient {
    private static final String ENDPOINT = "https://text.pollinations.ai/openai";
    private static final String MODEL = "openai-fast";
    private static final List<JSONObject> HISTORY = new ArrayList<>();

    private AssistantClient() { }

    static synchronized String reply(String text) throws Exception {
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject()
                .put("role", "system")
                .put("content", "Türkçe konuşan, kısa ve açık yanıt veren bir telefon asistanısın. " +
                        "Uygulamaları açtığını veya mesaj gönderdiğini iddia etme; bunları uygulama kendisi yapar."));
        for (JSONObject message : HISTORY) {
            messages.put(message);
        }
        JSONObject userMessage = new JSONObject().put("role", "user").put("content", text);
        messages.put(userMessage);

        JSONObject body = new JSONObject()
                .put("model", MODEL)
                .put("messages", messages)
                .put("temperature", 0.6)
                .put("max_tokens", 500);

        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(35000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            byte[] request = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(request);
            }

            int status = connection.getResponseCode();
            InputStream responseStream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = readFully(responseStream);
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("API HTTP " + status + ": " + response);
            }

            JSONObject parsed = new JSONObject(response);
            JSONArray choices = parsed.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                throw new IllegalStateException("API yanıtında choices alanı bulunamadı.");
            }
            String answer = choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim();
            if (answer.isEmpty()) {
                throw new IllegalStateException("API boş yanıt döndürdü.");
            }

            HISTORY.add(userMessage);
            HISTORY.add(new JSONObject().put("role", "assistant").put("content", answer));
            while (HISTORY.size() > 12) {
                HISTORY.remove(0);
            }
            return answer;
        } finally {
            connection.disconnect();
        }
    }

    static String userMessage(Exception error) {
        String detail = error.getMessage();
        if (error instanceof java.net.SocketTimeoutException) {
            return "Ücretsiz AI sunucusu zaman aşımına uğradı. İnternetini kontrol edip tekrar dene.";
        }
        if (error instanceof java.net.UnknownHostException) {
            return "AI sunucusuna ulaşılamadı. İnternet bağlantını kontrol et.";
        }
        if (detail != null && detail.contains("HTTP 429")) {
            return "Ücretsiz hizmet şu anda yoğun. Biraz sonra tekrar dene.";
        }
        if (detail != null && detail.contains("HTTP ")) {
            return "Ücretsiz AI hizmeti hata verdi (" + detail + ").";
        }
        return detail == null ? "Beklenmeyen bir bağlantı hatası oluştu." : detail;
    }

    private static String readFully(InputStream stream) throws Exception {
        if (stream == null) {
            throw new IllegalStateException("Sunucu yanıt içeriği göndermedi.");
        }
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString();
    }
}
