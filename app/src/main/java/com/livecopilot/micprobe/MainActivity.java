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
    private TextView permissionState;
    private TextView accessibilityState;
    private TextView apiState;
    private TextView resultText;
    private EditText apiKeyInput;

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
        root.setPadding(dp(20), dp(24), dp(20), dp(40));
        scroll.addView(root);

        TextView title = text("Live Copilot v0.5", 26, Color.rgb(20, 20, 24));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView intro = text(
                "Тази версия използва реалния аудио поток от overlay-а и cloud speech-to-text за по-точно разпознаване на бърза българска реч. " +
                        "После AI използва последните реплики като контекст и прави 4 различни варианта за отговор.",
                15, Color.DKGRAY);
        intro.setPadding(0, dp(12), 0, dp(18));
        root.addView(intro);

        root.addView(sectionTitle("1. Микрофон"));
        permissionState = text("", 14, Color.DKGRAY);
        root.addView(permissionState);
        Button micButton = button("Разреши микрофона");
        micButton.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC));
        root.addView(micButton);

        root.addView(sectionTitle("2. Accessibility overlay"));
        accessibilityState = text("", 14, Color.DKGRAY);
        root.addView(accessibilityState);
        Button accessibilityButton = button("Отвори Accessibility settings");
        accessibilityButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibilityButton);

        root.addView(sectionTitle("3. AI разпознаване"));
        apiState = text("", 14, Color.DKGRAY);
        root.addView(apiState);
        apiKeyInput = new EditText(this);
        apiKeyInput.setHint("OpenAI API key");
        apiKeyInput.setSingleLine(true);
        apiKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        String savedKey = getSharedPreferences("live_copilot_ai", Context.MODE_PRIVATE)
                .getString("openai_api_key", "");
        if (!savedKey.isEmpty()) apiKeyInput.setText(savedKey);
        root.addView(apiKeyInput);
        Button saveKey = button("Запази API key");
        saveKey.setOnClickListener(v -> {
            String key = apiKeyInput.getText().toString().trim();
            getSharedPreferences("live_copilot_ai", Context.MODE_PRIVATE)
                    .edit().putString("openai_api_key", key).apply();
            Toast.makeText(this, key.isEmpty() ? "API key е изтрит" : "API key е запазен", Toast.LENGTH_SHORT).show();
            refreshState();
        });
        root.addView(saveKey);

        TextView apiNote = text(
                "За прототипа ключът се пази локално на телефона. Не го слагай в GitHub и не го споделяй. За публичната версия ще използваме собствен backend.",
                12, Color.GRAY);
        root.addView(apiNote);

        root.addView(sectionTitle("4. Тест с TikTok"));
        TextView testInstructions = text(
                "Натисни START от плаващия прозорец и отвори TikTok. На всеки няколко секунди приложението изпраща кратък аудио сегмент за по-точна транскрипция. " +
                        "Гледай реда 'Чух:' — ако той е точен, отговорите вече се правят върху правилния контекст.",
                14, Color.DKGRAY);
        root.addView(testInstructions);
        Button tiktokButton = button("Отвори TikTok");
        tiktokButton.setOnClickListener(v -> openTikTok());
        root.addView(tiktokButton);

        root.addView(sectionTitle("Последен резултат"));
        resultText = text("", 14, Color.DKGRAY);
        GradientDrawable resultBg = new GradientDrawable();
        resultBg.setColor(Color.rgb(245, 245, 248));
        resultBg.setCornerRadius(dp(14));
        resultText.setBackground(resultBg);
        resultText.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.addView(resultText);

        return scroll;
    }

    private void refreshState() {
        if (permissionState == null) return;
        boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean accessibility = isOurAccessibilityServiceEnabled();
        String key = getSharedPreferences("live_copilot_ai", Context.MODE_PRIVATE)
                .getString("openai_api_key", "").trim();

        permissionState.setText(mic ? "✓ RECORD_AUDIO е разрешен" : "✗ RECORD_AUDIO още не е разрешен");
        accessibilityState.setText(accessibility ? "✓ Live Copilot overlay е включен" : "✗ Overlay услугата още не е включена");
        apiState.setText(key.isEmpty() ? "✗ Няма API key — cloud разпознаването няма да работи" : "✓ API key е записан");
        resultText.setText(ProbeResultStore.summary(this));
    }

    private boolean isOurAccessibilityServiceEnabled() {
        ComponentName expected = new ComponentName(this, MicProbeAccessibilityService.class);
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
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
        t.setPadding(0, dp(18), 0, dp(6));
        return t;
    }

    private TextView text(String value, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.1f);
        return t;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, dp(8), 0, dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
