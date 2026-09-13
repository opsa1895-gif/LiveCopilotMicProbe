package com.livecopilot.micprobe;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_MIC = 1001;

    private TextView micState;
    private TextView accessibilityState;
    private TextView aiState;
    private EditText apiKeyInput;
    private EditText keywordsInput;
    private EditText styleInput;
    private LinearLayout advancedBox;
    private Button advancedButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(36));
        scroll.addView(root);

        TextView title = text("Live Copilot", 28, Color.rgb(20, 20, 24));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView subtitle = text(
                "Кратки AI подсказки върху TikTok Live — без да ти запълват екрана.",
                15, Color.DKGRAY);
        subtitle.setPadding(0, dp(6), 0, dp(18));
        root.addView(subtitle);

        LinearLayout stateCard = new LinearLayout(this);
        stateCard.setOrientation(LinearLayout.VERTICAL);
        stateCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.rgb(246, 246, 249));
        cardBg.setCornerRadius(dp(14));
        stateCard.setBackground(cardBg);
        micState = text("", 14, Color.DKGRAY);
        accessibilityState = text("", 14, Color.DKGRAY);
        aiState = text("", 14, Color.DKGRAY);
        stateCard.addView(micState);
        stateCard.addView(accessibilityState);
        stateCard.addView(aiState);
        root.addView(stateCard);

        Button micButton = button("Разреши микрофона");
        micButton.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC));
        root.addView(micButton);

        Button accessibilityButton = button("Настрой Accessibility overlay");
        accessibilityButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibilityButton);

        root.addView(sectionTitle("AI настройка"));

        apiKeyInput = input("OpenAI API key", true);
        apiKeyInput.setText(SecretStore.loadApiKey(this));
        root.addView(apiKeyInput);

        advancedButton = button("Допълнителни настройки");
        root.addView(advancedButton);

        advancedBox = new LinearLayout(this);
        advancedBox.setOrientation(LinearLayout.VERTICAL);
        advancedBox.setVisibility(View.GONE);
        keywordsInput = input("Имена/думи за по-точно чуване", false);
        styleInput = input("Твоят стил, напр. кратък, остроумен", false);
        advancedBox.addView(keywordsInput);
        advancedBox.addView(styleInput);
        root.addView(advancedBox);

        String savedKeywords = getSharedPreferences("live_copilot_ai", Context.MODE_PRIVATE)
                .getString("stt_keywords", "");
        String savedStyle = getSharedPreferences("live_copilot_ai", Context.MODE_PRIVATE)
                .getString("host_style", "");
        keywordsInput.setText(savedKeywords);
        styleInput.setText(savedStyle);

        advancedButton.setOnClickListener(v -> {
            boolean show = advancedBox.getVisibility() != View.VISIBLE;
            advancedBox.setVisibility(show ? View.VISIBLE : View.GONE);
            advancedButton.setText(show ? "Скрий допълнителните" : "Допълнителни настройки");
        });

        Button saveButton = button("Запази настройките");
        saveButton.setOnClickListener(v -> saveSettings());
        root.addView(saveButton);

        TextView note = text(
                "API ключът се пази криптирано чрез Android Keystore на този телефон. За публична версия ще преместим API достъпа зад backend.",
                12, Color.GRAY);
        note.setPadding(0, dp(4), 0, dp(14));
        root.addView(note);

        Button tiktokButton = button("Отвори TikTok");
        tiktokButton.setOnClickListener(v -> openTikTok());
        root.addView(tiktokButton);

        TextView hint = text(
                "В overlay-а: START започва слушане, стилът се сменя с един бутон, а „—“ свива всичко до малък AI прозорец. Дълго натискане върху „—“ показва диагностика.",
                12, Color.GRAY);
        hint.setPadding(0, dp(12), 0, 0);
        root.addView(hint);

        return scroll;
    }

    private void saveSettings() {
        String key = apiKeyInput.getText().toString().trim();
        boolean keySaved = SecretStore.saveApiKey(this, key);
        getSharedPreferences("live_copilot_ai", Context.MODE_PRIVATE).edit()
                .putString("stt_keywords", keywordsInput.getText().toString().trim())
                .putString("host_style", styleInput.getText().toString().trim())
                .apply();

        Toast.makeText(
                this,
                keySaved ? "Настройките са запазени" : "API ключът не можа да се запази сигурно",
                Toast.LENGTH_SHORT).show();
        refreshState();
    }

    private void refreshState() {
        if (micState == null) return;
        boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean accessibility = isOurAccessibilityServiceEnabled();
        String key = SecretStore.loadApiKey(this);

        micState.setText(mic ? "✓ Микрофон" : "○ Микрофонът чака разрешение");
        accessibilityState.setText(accessibility ? "✓ Overlay включен" : "○ Overlay не е включен");
        aiState.setText(key.isEmpty() ? "○ AI ключът не е настроен" : "✓ AI готов");
    }

    private boolean isOurAccessibilityServiceEnabled() {
        ComponentName expected = new ComponentName(this, MicProbeAccessibilityService.class);
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;

        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            ComponentName component = ComponentName.unflattenFromString(splitter.next());
            if (expected.equals(component)) return true;
        }
        return false;
    }

    private void openTikTok() {
        String[] packages = {"com.zhiliaoapp.musically", "com.ss.android.ugc.trill"};
        for (String pkg : packages) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                startActivity(launch);
                return;
            }
        }
        Toast.makeText(this, "TikTok не беше намерен на телефона.", Toast.LENGTH_LONG).show();
    }

    private TextView sectionTitle(String value) {
        TextView t = text(value, 18, Color.rgb(25, 25, 30));
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(0, dp(20), 0, dp(7));
        return t;
    }

    private EditText input(String hint, boolean password) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        if (password) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        lp.setMargins(0, dp(3), 0, dp(3));
        e.setLayoutParams(lp);
        return e;
    }

    private TextView text(String value, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.08f);
        return t;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        lp.setMargins(0, dp(7), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
