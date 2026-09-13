package com.livecopilot.micprobe;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class OpenAiCopilotClient {
    interface Listener {
        void onTranscript(String transcript);
        void onReplies(Replies replies);
        void onStatus(String status);
    }

    static final class Replies {
        final String direct;
        final String sarcastic;
        final String funny;
        final String calm;

        Replies(String direct, String sarcastic, String funny, String calm) {
            this.direct = direct;
            this.sarcastic = sarcastic;
            this.funny = funny;
            this.calm = calm;
        }
    }

    private static final class PendingAudio {
        final short[] samples;
        final int sampleRate;
        PendingAudio(short[] samples, int sampleRate) {
            this.samples = samples;
            this.sampleRate = sampleRate;
        }
    }

    private final Context context;
    private final Listener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Deque<String> recentTranscript = new ArrayDeque<>();
    private boolean workerRunning;
    private PendingAudio pendingLatest;

    OpenAiCopilotClient(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    synchronized void submitAudio(short[] samples, int sampleRate) {
        if (samples == null || samples.length == 0) return;
        pendingLatest = new PendingAudio(samples.clone(), sampleRate);
        if (!workerRunning) {
            workerRunning = true;
            executor.execute(this::drainAudio);
        }
    }

    private void drainAudio() {
        while (true) {
            PendingAudio item;
            synchronized (this) {
                item = pendingLatest;
                pendingLatest = null;
                if (item == null) {
                    workerRunning = false;
                    return;
                }
            }
            process(item);
        }
    }

    private void process(PendingAudio item) {
        String apiKey = apiKey();
        if (apiKey.isEmpty()) {
            listener.onStatus("Липсва OpenAI API key. Добави го в основното приложение.");
            return;
        }
        try {
            listener.onStatus("Разпознавам речта по-точно…");
            String transcript = transcribe(apiKey, item.samples, item.sampleRate).trim();
            if (transcript.isEmpty()) {
                listener.onStatus("Слушам…");
                return;
            }

            synchronized (recentTranscript) {
                if (recentTranscript.isEmpty() || !sameish(recentTranscript.peekLast(), transcript)) {
                    recentTranscript.addLast(transcript);
                    while (recentTranscript.size() > 6) recentTranscript.removeFirst();
                } else {
                    recentTranscript.removeLast();
                    recentTranscript.addLast(transcript);
                }
            }

            listener.onTranscript(transcript);
            listener.onStatus("Правя 4 отговора от контекста…");
            Replies replies = generateReplies(apiKey, contextText());
            listener.onReplies(replies);
            listener.onStatus("Готово ✓ • продължавам да слушам");
        } catch (Throwable t) {
            listener.onStatus("AI грешка: " + safeMessage(t));
        }
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private String apiKey() {
        return context.getSharedPreferences("live_copilot_ai", Context.MODE_PRIVATE)
                .getString("openai_api_key", "")
                .trim();
    }

    private String contextText() {
        StringBuilder sb = new StringBuilder();
        synchronized (recentTranscript) {
            for (String part : recentTranscript) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(part);
            }
        }
        return sb.toString();
    }

    private String transcriptionPrompt() {
        String context = contextText();
        if (context.isEmpty()) {
            return "Разговор от TikTok Live на български език. Изписвай естествен български текст и правилна пунктуация.";
        }
        if (context.length() > 500) context = context.substring(context.length() - 500);
        return "Това е продължение на TikTok Live разговор на български. Предишен контекст: " + context;
    }

    private String transcribe(String apiKey, short[] samples, int sampleRate) throws Exception {
        byte[] wav = wav(samples, sampleRate);
        String boundary = "----LiveCopilot" + System.nanoTime();
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.openai.com/v1/audio/transcriptions").openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(45_000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + apiKey);
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream out = new DataOutputStream(c.getOutputStream())) {
            writeField(out, boundary, "model", "gpt-4o-transcribe");
            writeField(out, boundary, "language", "bg");
            writeField(out, boundary, "prompt", transcriptionPrompt());
            writeField(out, boundary, "response_format", "json");
            out.writeBytes("--" + boundary + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"live.wav\"\r\n");
            out.writeBytes("Content-Type: audio/wav\r\n\r\n");
            out.write(wav);
            out.writeBytes("\r\n--" + boundary + "--\r\n");
        }

        String body = readResponse(c);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Transcription HTTP " + code + ": " + compactError(body));
        }
        return new JSONObject(body).optString("text", "");
    }

    private Replies generateReplies(String apiKey, String transcript) throws Exception {
        String system = "Ти си AI суфльор за TikTok Live. Имаш последните реплики от реален разговор. " +
                "Разбери какво реално пита или казва последният човек и използвай предишните реплики само за контекст. " +
                "Дай точно 4 кратки, различни и готови за изговаряне отговора: direct, sarcastic, funny, calm. " +
                "direct трябва да е най-точният и конкретен. sarcastic = лек и остроумен сарказъм без обиди. " +
                "funny = забавен, но свързан с казаното. calm = спокоен и уважителен. " +
                "Не повтаряй въпроса. Не измисляй факти. Всеки вариант максимум 22 думи. " +
                "Пиши на български, освен ако последната реплика очевидно е на друг език. " +
                "Върни САМО JSON с ключове direct, sarcastic, funny, calm.";

        JSONObject req = new JSONObject();
        req.put("model", "gpt-5.6-luna");
        req.put("max_output_tokens", 260);

        JSONArray input = new JSONArray();
        input.put(message("system", system));
        input.put(message("user", "Последни реплики, най-новата е последна:\n" + transcript));
        req.put("input", input);

        HttpURLConnection c = (HttpURLConnection) new URL("https://api.openai.com/v1/responses").openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(45_000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + apiKey);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = req.toString().getBytes(StandardCharsets.UTF_8);
        c.getOutputStream().write(bytes);

        String body = readResponse(c);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Responses HTTP " + code + ": " + compactError(body));
        }

        String text = extractOutputText(new JSONObject(body)).trim();
        int first = text.indexOf('{');
        int last = text.lastIndexOf('}');
        if (first >= 0 && last > first) text = text.substring(first, last + 1);
        JSONObject json = new JSONObject(text);
        return new Replies(
                fallback(json.optString("direct"), "Кажи го още веднъж, за да ти отговоря точно."),
                fallback(json.optString("sarcastic"), "Добре, това заслужава втори дубъл."),
                fallback(json.optString("funny"), "Това беше добър момент за повторение."),
                fallback(json.optString("calm"), "Кажи го още веднъж и ще отговоря спокойно.")
        );
    }

    private static JSONObject message(String role, String text) throws Exception {
        JSONObject m = new JSONObject();
        m.put("role", role);
        JSONArray content = new JSONArray();
        JSONObject item = new JSONObject();
        item.put("type", "input_text");
        item.put("text", text);
        content.put(item);
        m.put("content", content);
        return m;
    }

    private static String extractOutputText(JSONObject root) {
        JSONArray output = root.optJSONArray("output");
        if (output == null) return "";
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) continue;
            JSONArray content = item.optJSONArray("content");
            if (content == null) continue;
            for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                if (part != null && "output_text".equals(part.optString("type"))) {
                    return part.optString("text", "");
                }
            }
        }
        return "";
    }

    private static boolean sameish(String a, String b) {
        if (a == null || b == null) return false;
        String x = a.toLowerCase().replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").trim();
        String y = b.toLowerCase().replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").trim();
        if (x.equals(y)) return true;
        return x.length() > 12 && y.length() > 12 && (x.contains(y) || y.contains(x));
    }

    private static void writeField(DataOutputStream out, String boundary, String name, String value) throws Exception {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.writeBytes("\r\n");
    }

    private static String readResponse(HttpURLConnection c) throws Exception {
        InputStream stream = c.getResponseCode() >= 200 && c.getResponseCode() < 300
                ? c.getInputStream() : c.getErrorStream();
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static byte[] wav(short[] samples, int sampleRate) throws Exception {
        int dataSize = samples.length * 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + dataSize);
        writeAscii(out, "RIFF");
        writeLeInt(out, 36 + dataSize);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeLeInt(out, 16);
        writeLeShort(out, 1);
        writeLeShort(out, 1);
        writeLeInt(out, sampleRate);
        writeLeInt(out, sampleRate * 2);
        writeLeShort(out, 2);
        writeLeShort(out, 16);
        writeAscii(out, "data");
        writeLeInt(out, dataSize);
        for (short s : samples) writeLeShort(out, s);
        return out.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        out.write(b, 0, b.length);
    }

    private static void writeLeShort(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }

    private static void writeLeInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 24) & 0xff);
    }

    private static String compactError(String body) {
        if (body == null) return "";
        String s = body.replace('\n', ' ').replace('\r', ' ').trim();
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        return m == null ? t.getClass().getSimpleName() : m;
    }
}
