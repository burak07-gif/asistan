package com.asistan.voice;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VoiceCaptureActivity extends Activity {
    private TextView transcriptView;
    private TextView answerView;
    private SpeechRecognizer recognizer;
    private TextToSpeech textToSpeech;
    private boolean ttsReady;
    private String pendingSpeech;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildScreen());
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int language = textToSpeech.setLanguage(new Locale("tr", "TR"));
                ttsReady = language != TextToSpeech.LANG_MISSING_DATA
                        && language != TextToSpeech.LANG_NOT_SUPPORTED;
                if (ttsReady && pendingSpeech != null) {
                    speak(pendingSpeech);
                    pendingSpeech = null;
                }
            }
        });
        beginRecognition();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
        beginRecognition();
    }

    private View buildScreen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(24), dp(24), dp(24));
        page.setBackgroundColor(0xFF0B1020);

        TextView title = text("Seni dinliyorum", 24, Color.WHITE, Typeface.BOLD);
        page.addView(title);
        TextView hint = text("Konuşma bittikten sonra yanıt hazırlayacağım.", 14, 0xFFADB8D2, Typeface.NORMAL);
        LinearLayout.LayoutParams hintParams = wrap();
        hintParams.topMargin = dp(8);
        page.addView(hint, hintParams);

        ScrollView scroll = new ScrollView(this);
        LinearLayout messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        transcriptView = text("Mikrofon hazırlanıyor…", 16, 0xFF9DEEFF, Typeface.BOLD);
        answerView = text("", 16, 0xFFE2E8FA, Typeface.NORMAL);
        transcriptView.setPadding(dp(16), dp(16), dp(16), dp(16));
        answerView.setPadding(dp(16), dp(16), dp(16), dp(16));
        messages.addView(transcriptView, wrap());
        messages.addView(answerView, wrap());
        scroll.addView(messages);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(18);
        page.addView(scroll, scrollParams);

        TextView listenAgain = text("Tekrar konuş", 16, 0xFF70E7FF, Typeface.BOLD);
        listenAgain.setGravity(Gravity.CENTER);
        listenAgain.setPadding(dp(16), dp(14), dp(16), dp(14));
        listenAgain.setOnClickListener(view -> {
            if (textToSpeech != null) {
                textToSpeech.stop();
            }
            answerView.setText("");
            transcriptView.setText("Mikrofon hazırlanıyor…");
            beginRecognition();
        });
        page.addView(listenAgain, wrap());

        TextView close = text("Kapat", 16, 0xFF70E7FF, Typeface.BOLD);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(16), dp(14), dp(16), dp(14));
        close.setOnClickListener(view -> finish());
        page.addView(close, wrap());
        return page;
    }

    private void beginRecognition() {
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            transcriptView.setText("Mikrofon izni yok. Uygulamayı açıp izni ver.");
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            transcriptView.setText("Bu telefonda kullanılabilir ses tanıma hizmeti bulunamadı.");
            return;
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { transcriptView.setText("Konuşabilirsin…"); }
            @Override public void onBeginningOfSpeech() { transcriptView.setText("Seni dinliyorum…"); }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { transcriptView.setText("Söylediklerin işleniyor…"); }
            @Override public void onError(int error) {
                transcriptView.setText("Ses tanınamadı (" + error + "). Tekrar konuş'a basıp yeniden dene.");
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> alternatives = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (alternatives == null || alternatives.isEmpty()) {
                    transcriptView.setText("Konuşma anlaşılamadı. Tekrar konuş'a basıp yeniden dene.");
                    return;
                }
                handleRecognizedText(alternatives.get(0));
            }
            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
        Intent request = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        request.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        request.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
        request.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR");
        request.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        recognizer.startListening(request);
    }

    private void handleRecognizedText(String spoken) {
        transcriptView.setText("Sen: " + spoken);
        if (CommandRouter.tryRun(this, spoken, answerView)) {
            speak(answerView.getText().toString());
            return;
        }

        answerView.setText("Yanıt hazırlanıyor…");
        executor.execute(() -> {
            try {
                String answer = AssistantClient.reply(spoken);
                runOnUiThread(() -> {
                    answerView.setText("Asistan: " + answer);
                    speak(answer);
                });
            } catch (Exception error) {
                runOnUiThread(() -> answerView.setText(
                        "Yanıt alınamadı: " + AssistantClient.userMessage(error)));
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) {
            recognizer.destroy();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    private void speak(String message) {
        if (ttsReady) {
            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "assistant-reply");
        } else {
            pendingSpeech = message;
        }
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
