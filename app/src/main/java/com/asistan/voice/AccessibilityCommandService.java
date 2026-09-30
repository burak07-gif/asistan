package com.asistan.voice;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AccessibilityCommandService extends AccessibilityService {
    private static final Pattern TYPE_IN_FIELD = Pattern.compile(
            "(?iu)^(?:hey\\s+asistan[, ]*)?(?:(arama\\s+kutusuna|arama\\s+alanına|" +
                    "arama\\s+yerine)|buraya\\s+yaz|şuraya\\s+yaz|yaz)\\s+(.+)$");
    private static final Pattern TYPE_TEXT = Pattern.compile(
            "(?iu)^(?:hey\\s+asistan[, ]*)?([\\p{L}0-9 ._-]+?)\\s+alanına\\s+(.+?)\\s+yaz$");
    private static final Pattern CLICK = Pattern.compile(
            "(?iu)^(?:hey\\s+asistan[, ]*)?(?:(.+?)\\s+(?:butonuna|düğmesine)\\s+" +
                    "(?:bas|tıkla|dokun)|(?:butona\\s+bas|düğmeye\\s+bas|" +
                    "butona\\s+tıkla|düğmeye\\s+tıkla|tıkla|dokun)\\s+(.+))$");
    private static final String[] UNSAFE_ACTION_LABELS = {
            "gonder", "send", "satinal", "purchase", "buy", "ode", "payment",
            "delete", "sil", "remove", "submit", "onayla", "confirm", "checkout"
    };

    private static volatile AccessibilityCommandService activeService;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private AccessibilityAction pendingAction;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        activeService = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (activeService == this) {
            activeService = null;
        }
        super.onDestroy();
    }

    static String handleVoiceCommand(String spoken) {
        Matcher click = CLICK.matcher(spoken.trim());
        Matcher type = TYPE_TEXT.matcher(spoken.trim());
        Matcher typeInField = TYPE_IN_FIELD.matcher(spoken.trim());
        String normalized = normalize(spoken);

        AccessibilityAction action;
        if (click.matches()) {
            String label = click.group(1) == null ? click.group(2) : click.group(1);
            action = AccessibilityAction.click(label.trim());
        } else if (type.matches()) {
            action = AccessibilityAction.type(type.group(1).trim(), type.group(2).trim());
        } else if (typeInField.matches()) {
            String field = typeInField.group(1);
            String text = typeInField.group(2).trim();
            if (field != null) {
                text = text.replaceFirst("(?iu)\\s+yaz$", "").trim();
            }
            action = AccessibilityAction.type(field == null ? null : "arama", text);
        } else if (containsAny(normalized, "geridon", "gerigit", "oncekiekrana")) {
            action = AccessibilityAction.back();
        } else if (containsAny(normalized, "asagikaydir", "asagikay")) {
            action = AccessibilityAction.scroll(false);
        } else if (containsAny(normalized, "yukarikaydir", "yukarikay")) {
            action = AccessibilityAction.scroll(true);
        } else {
            return null;
        }

        AccessibilityCommandService service = activeService;
        if (service == null) {
            return "Ekranda işlem yapabilmem için uygulama ayarlarından Kara Delik Asistan erişilebilirlik hizmetini aç.";
        }
        if (service.pendingAction != null) {
            return "Önceki ekran komutu henüz tamamlanmadı; biraz bekleyip tekrar söyle.";
        }
        service.pendingAction = action;
        MAIN_HANDLER.postDelayed(service::performPendingAction, 2200);
        return "Ekrana dönüp komutunu uyguluyorum.";
    }

    static boolean hasPendingAction() {
        AccessibilityCommandService service = activeService;
        return service != null && service.pendingAction != null;
    }

    private void performPendingAction() {
        AccessibilityAction action = pendingAction;
        pendingAction = null;
        if (action == null) {
            return;
        }
        String message = perform(action);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        if (getSharedPreferences("assistant_settings", MODE_PRIVATE)
                .getBoolean("assistant_enabled", false)) {
            Intent resume = new Intent(this, FloatingAssistantService.class);
            resume.setAction(FloatingAssistantService.ACTION_RESUME_WAKE_WORD);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(resume);
            } else {
                startService(resume);
            }
        }
    }

    private String perform(AccessibilityAction action) {
        if (action.kind == AccessibilityAction.BACK) {
            return performGlobalAction(GLOBAL_ACTION_BACK)
                    ? "Geri dönüyorum."
                    : "Geri gitme işlemi yapılamadı.";
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return "Ekrandaki öğelere erişemiyorum. İlgili uygulamayı öne getirip tekrar söyle.";
        }
        try {
            if (action.kind == AccessibilityAction.SCROLL) {
                return scroll(root, action.scrollUp);
            }
            if (action.kind == AccessibilityAction.TYPE) {
                return typeText(root, action.target, action.text);
            }
            return click(root, action.target);
        } finally {
            root.recycle();
        }
    }

    private String click(AccessibilityNodeInfo root, String requestedLabel) {
        String normalizedLabel = normalize(requestedLabel);
        if (containsAny(normalizedLabel, UNSAFE_ACTION_LABELS)) {
            return "Mesaj gönderme, silme ve ödeme gibi son işlemleri güvenlik için otomatik tıklamıyorum.";
        }

        List<AccessibilityNodeInfo> candidates = new ArrayList<>();
        findClickableMatches(root, normalizedLabel, candidates, 0);
        if (candidates.isEmpty()) {
            return "Ekranda “" + requestedLabel + "” adlı tıklanabilir öğe bulamadım.";
        }
        if (candidates.size() > 1) {
            recycleNodes(candidates);
            return "“" + requestedLabel + "” birden fazla öğeyle eşleşti; daha belirgin ad söyle.";
        }

        AccessibilityNodeInfo target = candidates.get(0);
        boolean clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        target.recycle();
        return clicked ? "“" + requestedLabel + "” öğesine dokundum."
                : "“" + requestedLabel + "” öğesine dokunulamadı.";
    }

    private void findClickableMatches(
            AccessibilityNodeInfo node,
            String requestedLabel,
            List<AccessibilityNodeInfo> matches,
            int depth) {
        if (depth > 40 || matches.size() > 1) {
            return;
        }
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        if (matchesLabel(text, requestedLabel) || matchesLabel(description, requestedLabel)) {
            AccessibilityNodeInfo clickable = findClickableAncestor(node);
            if (clickable != null) {
                if (!containsSameNode(matches, clickable)) {
                    matches.add(AccessibilityNodeInfo.obtain(clickable));
                }
                clickable.recycle();
            }
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child != null) {
                findClickableMatches(child, requestedLabel, matches, depth + 1);
                child.recycle();
            }
        }
    }

    private AccessibilityNodeInfo findClickableAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
        for (int depth = 0; depth < 6; depth++) {
            if (current.isClickable()) {
                return current;
            }
            AccessibilityNodeInfo parent = current.getParent();
            current.recycle();
            if (parent == null) {
                return null;
            }
            current = parent;
        }
        current.recycle();
        return null;
    }

    private boolean containsSameNode(List<AccessibilityNodeInfo> nodes, AccessibilityNodeInfo candidate) {
        Rect candidateBounds = new Rect();
        candidate.getBoundsInScreen(candidateBounds);
        for (AccessibilityNodeInfo node : nodes) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            if (bounds.equals(candidateBounds)) {
                return true;
            }
        }
        return false;
    }

    private void recycleNodes(List<AccessibilityNodeInfo> nodes) {
        for (AccessibilityNodeInfo node : nodes) {
            node.recycle();
        }
    }

    private String typeText(AccessibilityNodeInfo root, String requestedField, String text) {
        AccessibilityNodeInfo field = null;
        if (requestedField != null) {
            field = findEditableMatch(root, normalize(requestedField), 0);
            if (field == null) {
                AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (focused != null && focused.isEditable()) {
                    field = focused;
                } else if (focused != null) {
                    focused.recycle();
                }
            }
        } else {
            field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        }
        if (field == null || !field.isEditable()) {
            if (field != null) {
                field.recycle();
            }
            return requestedField == null
                    ? "Yazı alanı seçili değil. Önce alana dokun veya alanın adını söyle."
                    : "“" + requestedField + "” adlı yazı alanını bulamadım.";
        }

        try {
            if (isPasswordField(field)) {
                return "Gizlilik için parola alanlarına sesle yazı yazmıyorum.";
            }
            Bundle arguments = new Bundle();
            arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            if (!field.isFocused()) {
                field.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            }
            return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    ? "Metni yazdım."
                    : "Bu yazı alanına metin girilemedi.";
        } finally {
            field.recycle();
        }
    }

    private AccessibilityNodeInfo findEditableMatch(
            AccessibilityNodeInfo node, String requestedLabel, int depth) {
        if (depth > 40) {
            return null;
        }
        if (node.isEditable()
                && (matchesLabel(node.getText(), requestedLabel)
                || matchesLabel(node.getHintText(), requestedLabel)
                || matchesLabel(node.getContentDescription(), requestedLabel))) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child != null) {
                AccessibilityNodeInfo found = findEditableMatch(child, requestedLabel, depth + 1);
                child.recycle();
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean isPasswordField(AccessibilityNodeInfo field) {
        int variation = field.getInputType() & (InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION);
        return variation == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                || variation == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                || variation == (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    }

    private String scroll(AccessibilityNodeInfo root, boolean up) {
        AccessibilityNodeInfo node = findScrollableNode(root, 0);
        if (node == null) return "Kaydırılabilir bir alan bulamadım.";
        try {
            int direction = up
                    ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
            if (node.performAction(direction)) {
                return up ? "Yukarı kaydırdım." : "Aşağı kaydırdım.";
            }
            return "Kaydırılabilir bir alan bulamadım.";
        } finally {
            node.recycle();
        }
    }

    private AccessibilityNodeInfo findScrollableNode(AccessibilityNodeInfo node, int depth) {
        if (depth > 40) return null;
        if (node.isScrollable()) return AccessibilityNodeInfo.obtain(node);
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child != null) {
                AccessibilityNodeInfo found = findScrollableNode(child, depth + 1);
                child.recycle();
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean matchesLabel(CharSequence label, String requested) {
        if (label == null) {
            return false;
        }
        String normalized = normalize(label.toString());
        return normalized.equals(requested) || normalized.contains(requested);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value.toLowerCase(new Locale("tr", "TR")), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9]", "");
    }

    private static boolean containsAny(String value, String... options) {
        for (String option : options) {
            if (value.contains(normalize(option))) {
                return true;
            }
        }
        return false;
    }

    private static final class AccessibilityAction {
        static final int CLICK = 0;
        static final int TYPE = 1;
        static final int BACK = 2;
        static final int SCROLL = 3;

        final int kind;
        final String target;
        final String text;
        final boolean scrollUp;

        private AccessibilityAction(int kind, String target, String text, boolean scrollUp) {
            this.kind = kind;
            this.target = target;
            this.text = text;
            this.scrollUp = scrollUp;
        }

        static AccessibilityAction click(String target) {
            return new AccessibilityAction(CLICK, target, null, false);
        }

        static AccessibilityAction type(String target, String text) {
            return new AccessibilityAction(TYPE, target, text, false);
        }

        static AccessibilityAction back() {
            return new AccessibilityAction(BACK, null, null, false);
        }

        static AccessibilityAction scroll(boolean up) {
            return new AccessibilityAction(SCROLL, null, null, up);
        }
    }
}
