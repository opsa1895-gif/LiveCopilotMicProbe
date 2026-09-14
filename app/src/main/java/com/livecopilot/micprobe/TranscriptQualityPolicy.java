package com.livecopilot.micprobe;

import java.util.Locale;

final class TranscriptQualityPolicy {
    private TranscriptQualityPolicy() {}

    static boolean isLowQuality(String value) {
        String clean = clean(value);
        if (clean.length() < 2) return true;

        int alnum = 0;
        for (int i = 0; i < clean.length(); i++) {
            if (Character.isLetterOrDigit(clean.charAt(i))) alnum++;
        }
        if (alnum < 2) return true;

        String normalized = clean.toLowerCase(Locale.ROOT)
                .replaceAll("[\\[\\](){}<>]", "")
                .replaceAll("\\s+", " ")
                .trim();

        return normalized.equals("music")
                || normalized.equals("музика")
                || normalized.equals("background music")
                || normalized.equals("фонова музика")
                || normalized.equals("applause")
                || normalized.equals("аплодисменти")
                || normalized.equals("laughter")
                || normalized.equals("смях")
                || normalized.equals("silence")
                || normalized.equals("тишина")
                || normalized.equals("noise")
                || normalized.equals("шум")
                || normalized.equals("inaudible")
                || normalized.equals("unintelligible")
                || normalized.equals("неразбираемо")
                || normalized.equals("мм")
                || normalized.equals("ммм")
                || normalized.equals("хм")
                || normalized.equals("ъм")
                || normalized.equals("ъъ")
                || normalized.equals("ъъъ")
                || normalized.equals("uh")
                || normalized.equals("um")
                || normalized.equals("hmm");
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
