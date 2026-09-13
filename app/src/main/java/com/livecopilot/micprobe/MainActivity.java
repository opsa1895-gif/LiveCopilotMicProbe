package com.livecopilot.micprobe;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_MIC = 1001;
    private TextView permissionState;
    private TextView accessibilityState;
    private TextView resultText;

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

        TextView title = text("Live Copilot — Mic Probe", 26, Color.rgb(20, 20, 24));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView intro = text(
                "Това не е още AI асистентът. Това е първият технически прототип, който проверява най-рисковата част: " +
                        "можем ли да получаваме микрофона през Accessibility overlay, докато TikTok е отпред и записва същия микрофон.\n\n" +
                        "Прототипът не чете TikTok съдържание, не натиска бутони и не записва аудио във файл. Показва само нивото на входящия звук и дали Android е заглушил нашия audio client.",
                15,
                Color.DKGRAY
        );
        intro.setPadding(0, dp(12), 0, dp(18));
        root.addView(intro);

        root.addView(sectionTitle("1. Микрофон"));
        permissionState = text("", 14, Color.DKGRAY);
        root.addView(permissionState);
        Button micButton = button("Разреши микрофона");
        micButton.setOnClickListener(v -> requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO},
                REQ_MIC
        ));
        root.addView(micButton);

        root.addView(sectionTitle("2. Accessibility overlay"));
        accessibilityState = text("", 14, Color.DKGRAY);
        root.addView(accessibilityState);
        Button accessibilityButton = button("Отвори Accessibility settings");
        accessibilityButton.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibilityButton);

        root.addView(sectionTitle("3. Тест с TikTok"));
        TextView testInstructions = text(
                "След като услугата е включена, ще се появи плаващ прозорец. Натисни START, отвори TikTok, стартирай Live и говори 15–30 секунди. " +
                        "Ако overlay-ят показва реални dBFS стойности и не показва 'client silenced', нашата страна получава звук. " +
                        "После провери от live/replay или от втори зрител дали TikTok също е получил гласа.",
                14,
                Color.DKGRAY
        );
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

        Button refresh = button("Обнови резултата");
        refresh.setOnClickListener(v -> refreshState());
        root.addView(refresh);

        TextView warning = text(
                "Важно: това е sideload/research прототип. Accessibility API има специални изисквания за Google Play. " +
                        "Първо доказваме техническата съвместимост на реални телефони; чак след това решаваме архитектурата за публична версия.",
                12,
                Color.GRAY
        );
        warning.setPadding(0, dp(24), 0, 0);
        root.addView(warning);

        return scroll;
    }

    private void refreshState() {
        if (permissionState == null) return;
        boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean accessibility = isOurAccessibilityServiceEnabled();

        permissionState.setText(mic ? "✓ RECORD_AUDIO е разрешен" : "✗ RECORD_AUDIO още не е разрешен");
        accessibilityState.setText(accessibility
                ? "✓ Live Copilot overlay услугата е включена"
                : "✗ Услугата още не е включена");
        resultText.setText(ProbeResultStore.summary(this));
    }

    private boolean isOurAccessibilityServiceEnabled() {
        ComponentName expected = new ComponentName(this, MicProbeAccessibilityService.class);
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        lp.setMargins(0, dp(8), 0, dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
