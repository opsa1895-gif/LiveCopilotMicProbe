package com.livecopilot.micprobe;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Locale;

final class ProbeResultStore {
    private static final String PREFS = "probe_results";

    private ProbeResultStore() {}

    static void save(
            Context context,
            long durationMs,
            int silenceEvents,
            double peakDb,
            int audibleWindows,
            boolean tiktokSeen,
            String finalStatus
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("has_result", true)
                .putLong("duration_ms", durationMs)
                .putInt("silence_events", silenceEvents)
                .putFloat("peak_db", (float) peakDb)
                .putInt("audible_windows", audibleWindows)
                .putBoolean("tiktok_seen", tiktokSeen)
                .putString("final_status", finalStatus)
                .putString("device", Build.MANUFACTURER + " " + Build.MODEL)
                .putInt("sdk", Build.VERSION.SDK_INT)
                .apply();
    }

    static String summary(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean("has_result", false)) {
            return "Няма завършен тест още.\n\n" +
                    "Целта е първо да докажем само едно нещо: дали нашият Accessibility overlay може да получава реален звук от микрофона, докато TikTok е отпред и също използва микрофона.";
        }

        long durationMs = p.getLong("duration_ms", 0L);
        int silenceEvents = p.getInt("silence_events", 0);
        float peakDb = p.getFloat("peak_db", -90f);
        int audibleWindows = p.getInt("audible_windows", 0);
        boolean tiktokSeen = p.getBoolean("tiktok_seen", false);
        String finalStatus = p.getString("final_status", "unknown");
        String device = p.getString("device", "unknown");
        int sdk = p.getInt("sdk", 0);

        String verdict;
        if (!tiktokSeen) {
            verdict = "НЕОПРЕДЕЛЕНО: по време на теста TikTok не беше засечен като foreground приложение.";
        } else if (silenceEvents > 0 && audibleWindows == 0) {
            verdict = "НЕУСПЕХ ЗА ТАЗИ КОНФИГУРАЦИЯ: Android е заглушил нашия AudioRecord при конкурентния запис.";
        } else if (audibleWindows > 0) {
            verdict = "ОБЕЩАВАЩО: нашето приложение е получавало реален звук, докато TikTok е бил отпред. Следва да потвърдим от TikTok live/replay, че и TikTok е получил гласа едновременно.";
        } else {
            verdict = "НЕОПРЕДЕЛЕНО: няма достатъчно силен аудио сигнал. Повтори теста и говори нормално 15–30 секунди.";
        }

        return String.format(Locale.US,
                "Последен тест\n" +
                "• Устройство: %s (API %d)\n" +
                "• Продължителност: %.1f сек\n" +
                "• TikTok засечен: %s\n" +
                "• Събития 'client silenced': %d\n" +
                "• Peak: %.1f dBFS\n" +
                "• Прозорци с говор/звук: %d\n" +
                "• Финален статус: %s\n\n" +
                "%s",
                device,
                sdk,
                durationMs / 1000.0,
                tiktokSeen ? "ДА" : "НЕ",
                silenceEvents,
                peakDb,
                audibleWindows,
                finalStatus,
                verdict
        );
    }
}
