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
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
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

    private static final class AudioItem {
        final short[] samples;
        final int sampleRate;
        AudioItem(short[] samples, int sampleRate) {
            this.samples = samples;
            this.sampleRate = sampleRate;
        }
    }

    private static final class ReplyJob {
        final long serial;
        final long createdAtMs;
        final String context;
        final String focus;
        final boolean engagement;
        ReplyJob(long serial, String context, String focus, boolean engagement) {
            this.serial = serial;
            this.createdAtMs = System.currentTimeMillis();
            this.context = context;
            this.focus = focus;
            this.engagement = engagement;
        }
    }

    private static final int MAX_AUDIO_QUEUE = 3;
    private static final int MAX_TURNS = 8;
    private static final long ENGAGEMENT_GAP_MS = 12_000L;
    private static final long MAX_REPLY_AGE_MS = 12_000L;

    private final Context context;
    private final Listener listener;
    private final ExecutorService audioExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService replyExecutor = Executors.newSingleThreadExecutor();
    private final Deque<AudioItem> audioQueue = new ArrayDeque<>();
    private final Deque<String> recentTurns = new ArrayDeque<>();

    private boolean audioWorkerRunning;
    private boolean replyWorkerRunning;
    private boolean closed;
    private ReplyJob pendingReply;
    private long latestReplySerial;
    private long lastReplyAtMs;
    private long firstSpeechAtMs;
    private String summary = "";

    OpenAiCopilotClient(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    synchronized void submitAudio(short[] samples, int sampleRate) {
        if (closed || samples == null || samples.length == 0) return;
        while (audioQueue.size() >= MAX_AUDIO_QUEUE) audioQueue.removeFirst();
        audioQueue.addLast(new AudioItem(samples.clone(), sampleRate));
        if (!audioWorkerRunning) {
            audioWorkerRunning = true;
            audioExecutor.execute(this::drainAudio);
        }
    }

    private void drainAudio() {
        while (true) {
            AudioItem item;
            synchronized (this) {
                item = audioQueue.pollFirst();
                if (item == null || closed) {
                    audioWorkerRunning = false;
                    return;
                }
            }
            processAudio(item);
        }
    }

    private void processAudio(AudioItem item) {
        String key = apiKey();
        if (key.isEmpty()) {
            listener.onStatus("Няма API key");
            return;
        }

        try {
            listener.onStatus("Разпознавам…");
            String raw = transcribe(key, item.samples, item.sampleRate);
            if (lowQuality(raw)) {
                listener.onStatus("Слушам");
                return;
            }

            String focus = commitTranscript(raw);
            if (focus.isEmpty()) {
                listener.onStatus("Слушам");
                return;
            }

            listener.onTranscript(focus);
            long now = System.currentTimeMillis();
            synchronized (this) {
                if (firstSpeechAtMs == 0L) firstSpeechAtMs = now;
            }

            boolean actionable = isActionable(focus);
            long reference;
            synchronized (this) {
                reference = lastReplyAtMs > 0L ? lastReplyAtMs : firstSpeechAtMs;
            }
            boolean engagement = !actionable && reference > 0L && now - reference >= ENGAGEMENT_GAP_MS;

            if (actionable || engagement) queueReply(focus, engagement);
            else listener.onStatus("Слушам");
        } catch (Throwable ignored) {
            listener.onStatus("AI връзката прекъсна");
        }
    }

    private synchronized String commitTranscript(String raw) {
        String clean = clean(raw);
        if (clean.length() < 2) return "";

        String previous = recentTurns.peekLast();
        if (previous != null) {
            if (sameish(previous, clean)) {
                if (clean.length() > previous.length() + 4) {
                    recentTurns.removeLast();
                    recentTurns.addLast(clean);
                    String growth = uniqueGrowth(previous, clean);
                    return growth.length() >= 2 ? growth : "";
                }
                return "";
            }
            String unique = stripOverlap(previous, clean);
            if (!unique.isEmpty() && unique.length() < clean.length()) clean = unique;
        }

        recentTurns.addLast(clean);
        while (recentTurns.size() > MAX_TURNS) recentTurns.removeFirst();
        return clean;
    }

    private void queueReply(String focus, boolean engagement) {
        synchronized (this) {
            long serial = ++latestReplySerial;
            pendingReply = new ReplyJob(serial, contextTextLocked(), focus, engagement);
            if (replyWorkerRunning) return;
            replyWorkerRunning = true;
        }
        replyExecutor.execute(this::drainReplies);
    }

    private void drainReplies() {
        while (true) {
            ReplyJob job;
            synchronized (this) {
                job = pendingReply;
                pendingReply = null;
                if (job == null || closed) {
                    replyWorkerRunning = false;
                    return;
                }
            }
            processReply(job);
        }
    }

    private void processReply(ReplyJob job) {
        String key = apiKey();
        if (key.isEmpty()) return;
        String primary = "";

        try {
            listener.onStatus("Мисля…");
            primary = primaryReply(key, job.context, job.focus, job.engagement);
            if (!current(job)) return;

            if (!primary.isEmpty()) {
                listener.onReplies(new Replies(primary, "", "", ""));
                synchronized (this) { lastReplyAtMs = System.currentTimeMillis(); }
            }

            Replies complete = variants(key, job.context, job.focus, primary, job.engagement);
            if (!current(job)) return;
            listener.onReplies(complete);
            listener.onStatus("Слушам");
        } catch (Throwable ignored) {
            if (!current(job)) return;
            ReplyGenerator.Replies local = ReplyGenerator.generate(job.context, job.focus);
            listener.onReplies(new Replies(
                    primary.isEmpty() ? local.direct : primary,
                    local.sarcastic,
                    local.funny,
                    local.calm));
            listener.onStatus("Слушам");
        }
    }

    private synchronized boolean current(ReplyJob job) {
        return !closed
                && job.serial == latestReplySerial
                && System.currentTimeMillis() - job.createdAtMs <= MAX_REPLY_AGE_MS;
    }

    void shutdown() {
        synchronized (this) {
            closed = true;
            audioQueue.clear();
            pendingReply = null;
        }
        audioExecutor.shutdownNow();
        replyExecutor.shutdownNow();
    }

    private String apiKey() {
        return context.getSharedPreferences("live_copilot_ai", Context.MODE_PRIVATE)
                .getString("openai_api_key", "").trim();
    }

    private String hostStyle() {
        String value = context.getSharedPreferences("live_copilot_ai", Context.MODE_PRIVATE)
                .getString("host_style", "").trim();
        return value.isEmpty() ? "кратък, естествен, уверен и разговорен" : shorten(value, 160);
    }

    private String keywords() {
        return context.getSharedPreferences("live_copilot_ai", Context.MODE_PRIVATE)
                .getString("stt_keywords", "").trim();
    }

    private synchronized String contextTextLocked() {
        StringBuilder out = new StringBuilder();
        if (!summary.isEmpty()) out.append("Резюме: ").append(summary).append('\n');
        for (String turn : recentTurns) out.append("- ").append(turn).append('\n');
        return out.toString().trim();
    }

    private synchronized String transcriptionPrompt() {
        StringBuilder p = new StringBuilder(
                "Български TikTok Live разговор. Транскрибирай точно бърза разговорна реч. Не измисляй несигурни думи."
        );
        String terms = keywords();
        if (!terms.isEmpty()) p.append(" Възможни имена и термини: ").append(shorten(terms, 220)).append('.');
        if (!summary.isEmpty()) p.append(" Контекст: ").append(shorten(summary, 220)).append('.');
        Object[] turns = recentTurns.toArray();
        for (int i = Math.max(0, turns.length - 2); i < turns.length; i++) {
            p.append(" Преди: ").append(shorten(String.valueOf(turns[i]), 130)).append('.');
        }
        return shorten(p.toString(), 650);
    }

    private String transcribe(String key, short[] samples, int sampleRate) throws Exception {
        byte[] wav = wav(samples, sampleRate);
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                HttpResult r = transcriptionRequest(key, wav);
                if (r.code >= 200 && r.code < 300) return clean(new JSONObject(r.body).optString("text", ""));
                if (r.code != 429 && r.code < 500) throw new IllegalStateException("STT " + r.code);
                last = new IllegalStateException("STT " + r.code);
            } catch (Exception e) {
                last = e;
            }
            if (attempt == 0) Thread.sleep(500L);
        }
        throw last == null ? new IllegalStateException("STT") : last;
    }

    private HttpResult transcriptionRequest(String key, byte[] wav) throws Exception {
        String boundary = "----LiveCopilot" + System.nanoTime();
        HttpURLConnection c = connection("https://api.openai.com/v1/audio/transcriptions", key, 40_000);
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        try (DataOutputStream out = new DataOutputStream(c.getOutputStream())) {
            field(out, boundary, "model", "gpt-transcribe");
            field(out, boundary, "language", "bg");
            field(out, boundary, "prompt", transcriptionPrompt());
            field(out, boundary, "response_format", "json");
            out.writeBytes("--" + boundary + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"live.wav\"\r\n");
            out.writeBytes("Content-Type: audio/wav\r\n\r\n");
            out.write(wav);
            out.writeBytes("\r\n--" + boundary + "--\r\n");
        }
        int code = c.getResponseCode();
        return new HttpResult(code, read(c, code));
    }

    private String primaryReply(String key, String fullContext, String focus, boolean engagement) throws Exception {
        String task = engagement
                ? "Няма директен въпрос. Дай кратка естествена реплика за продължаване на темата или за включване на зрителите."
                : "Отговори на последната смислена реплика или въпрос.";
        String system = "Ти си незабележим AI суфльор за TikTok Live. " + task +
                " Отговорът е за изговаряне на живо, максимум 18 думи. Не повтаряй въпроса. Не измисляй факти. " +
                "Стил на водещия: " + hostStyle() + ". Върни само репликата.";

        HttpResult r = responses(key, request(system, "Контекст:\n" + fullContext + "\n\nФокус:\n" + focus, 90));
        if (r.code < 200 || r.code >= 300) throw new IllegalStateException("reply " + r.code);
        return modelLine(extractText(new JSONObject(r.body)));
    }

    private Replies variants(String key, String fullContext, String focus, String direct, boolean engagement) throws Exception {
        String system = "Ти си AI суфльор за TikTok Live. Направи три различни алтернативи на основния отговор: " +
                "sarcastic = лек остроумен сарказъм без обиди; funny = забавен и свързан; calm = спокоен и уважителен. " +
                "Всеки максимум 18 думи. Не прави минимални преформулировки. Добави summary до 45 думи за устойчивия контекст. " +
                "Стил на водещия: " + hostStyle() + ". Върни само JSON с sarcastic, funny, calm, summary.";
        String user = "Контекст:\n" + fullContext + "\n\nФокус:\n" + focus +
                "\n\nОсновен:\n" + direct + "\n\nРежим: " + (engagement ? "engagement" : "reply");
        HttpResult r = responses(key, request(system, user, 260));
        if (r.code < 200 || r.code >= 300) throw new IllegalStateException("variants " + r.code);

        String text = extractText(new JSONObject(r.body)).trim();
        int a = text.indexOf('{');
        int b = text.lastIndexOf('}');
        if (a >= 0 && b > a) text = text.substring(a, b + 1);
        JSONObject json = new JSONObject(text);

        String nextSummary = clean(json.optString("summary", ""));
        if (!nextSummary.isEmpty()) {
            synchronized (this) { summary = shorten(nextSummary, 420); }
        }

        return new Replies(
                fallback(direct, "Кажи го още веднъж."),
                fallback(json.optString("sarcastic"), "Добре, това вече заслужава втори дубъл."),
                fallback(json.optString("funny"), "Това влезе директно в рубриката „интересно“!"),
                fallback(json.optString("calm"), "Разбирам те — нека го кажем спокойно."));
    }

    private JSONObject request(String system, String user, int maxTokens) throws Exception {
        JSONObject req = new JSONObject();
        req.put("model", "gpt-5.6-luna");
        req.put("max_output_tokens", maxTokens);
        JSONObject reasoning = new JSONObject();
        reasoning.put("effort", "none");
        req.put("reasoning", reasoning);
        JSONArray input = new JSONArray();
        input.put(message("system", system));
        input.put(message("user", user));
        req.put("input", input);
        return req;
    }

    private HttpResult responses(String key, JSONObject req) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                HttpURLConnection c = connection("https://api.openai.com/v1/responses", key, 35_000);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.getOutputStream().write(req.toString().getBytes(StandardCharsets.UTF_8));
                int code = c.getResponseCode();
                String body = read(c, code);
                if ((code == 429 || code >= 500) && attempt == 0) {
                    Thread.sleep(450L);
                    continue;
                }
                return new HttpResult(code, body);
            } catch (Exception e) {
                last = e;
                if (attempt == 0) Thread.sleep(450L);
            }
        }
        throw last == null ? new IllegalStateException("responses") : last;
    }

    private static HttpURLConnection connection(String url, String key, int readTimeout) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12_000);
        c.setReadTimeout(readTimeout);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + key);
        return c;
    }

    private static JSONObject message(String role, String text) throws Exception {
        JSONObject m = new JSONObject();
        m.put("role", role);
        JSONArray content = new JSONArray();
        JSONObject part = new JSONObject();
        part.put("type", "input_text");
        part.put("text", text);
        content.put(part);
        m.put("content", content);
        return m;
    }

    private static String extractText(JSONObject root) {
        JSONArray output = root.optJSONArray("output");
        if (output == null) return "";
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) continue;
            JSONArray content = item.optJSONArray("content");
            if (content == null) continue;
            for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                if (part != null && "output_text".equals(part.optString("type"))) return part.optString("text", "");
            }
        }
        return "";
    }

    private static boolean isActionable(String text) {
        String v = normalize(text);
        if (text.contains("?")) return true;
        return containsAny(v,
                "как ", "какво ", "защо ", "кой ", "коя ", "къде ", "кога ", "колко ",
                "може ли", "дали ", "нали ", "имаш ли", "искаш ли", "мислиш ли", "кажи ми",
                "цена", "струва", "поръч", "куп", "откъде си", "години", "на колко",
                "здравей", "здрасти", "обичам", "харесвам", "красив", "красива", "готин", "готина",
                "грозен", "грозна", "тъп", "тъпа", "идиот", "hate", "love you");
    }

    private static boolean lowQuality(String value) {
        String s = clean(value);
        if (s.length() < 2) return true;
        int alnum = 0;
        for (int i = 0; i < s.length(); i++) if (Character.isLetterOrDigit(s.charAt(i))) alnum++;
        if (alnum < 2) return true;
        String v = s.toLowerCase(Locale.ROOT);
        return v.equals("music") || v.equals("музика") || v.equals("[music]") || v.equals("...");
    }

    private static String stripOverlap(String previous, String current) {
        List<String> a = words(previous);
        List<String> b = words(current);
        int max = Math.min(Math.min(a.size(), b.size()), 12);
        int best = 0;
        for (int n = 2; n <= max; n++) {
            boolean match = true;
            for (int i = 0; i < n; i++) {
                if (!a.get(a.size() - n + i).equals(b.get(i))) { match = false; break; }
            }
            if (match) best = n;
        }
        if (best == 0 || best >= b.size()) return current;
        String[] original = clean(current).split("\\s+");
        StringBuilder out = new StringBuilder();
        for (int i = best; i < original.length; i++) {
            if (out.length() > 0) out.append(' ');
            out.append(original[i]);
        }
        return out.toString().trim();
    }

    private static String uniqueGrowth(String previous, String current) {
        String tail = stripOverlap(previous, current);
        if (!tail.equals(current) && !tail.isEmpty()) return tail;
        String p = previous.toLowerCase(Locale.ROOT);
        String c = current.toLowerCase(Locale.ROOT);
        if (c.startsWith(p) && current.length() > previous.length()) return clean(current.substring(previous.length()));
        return current;
    }

    private static boolean sameish(String a, String b) {
        String x = normalize(a);
        String y = normalize(b);
        if (x.equals(y)) return true;
        if (x.length() < 10 || y.length() < 10) return false;
        if (x.contains(y) || y.contains(x)) return true;
        List<String> aw = words(x);
        List<String> bw = words(y);
        int common = 0;
        for (String w : aw) if (bw.contains(w)) common++;
        return common / (double) Math.max(aw.size(), bw.size()) >= 0.86;
    }

    private static List<String> words(String value) {
        String s = normalize(value);
        List<String> out = new ArrayList<>();
        if (s.isEmpty()) return out;
        for (String w : s.split("\\s+")) if (!w.isEmpty()) out.add(w);
        return out;
    }

    private static String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N} ]", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private static boolean containsAny(String value, String... needles) {
        for (String n : needles) if (value.contains(n)) return true;
        return false;
    }

    private static String modelLine(String value) {
        String s = clean(value);
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("“") && s.endsWith("”"))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return shorten(s, 180);
    }

    private static String fallback(String value, String defaultValue) {
        String s = clean(value);
        return s.isEmpty() ? defaultValue : shorten(s, 180);
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String shorten(String value, int max) {
        String s = clean(value);
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static void field(DataOutputStream out, String boundary, String name, String value) throws Exception {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.writeBytes("\r\n");
    }

    private static String read(HttpURLConnection c, int code) throws Exception {
        InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) out.append(line);
        }
        return out.toString();
    }

    private static byte[] wav(short[] samples, int sampleRate) throws Exception {
        int size = samples.length * 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + size);
        ascii(out, "RIFF"); leInt(out, 36 + size); ascii(out, "WAVE"); ascii(out, "fmt ");
        leInt(out, 16); leShort(out, 1); leShort(out, 1); leInt(out, sampleRate);
        leInt(out, sampleRate * 2); leShort(out, 2); leShort(out, 16); ascii(out, "data"); leInt(out, size);
        for (short s : samples) leShort(out, s);
        return out.toByteArray();
    }

    private static void ascii(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        out.write(b, 0, b.length);
    }
    private static void leShort(ByteArrayOutputStream out, int v) { out.write(v & 255); out.write((v >> 8) & 255); }
    private static void leInt(ByteArrayOutputStream out, int v) {
        out.write(v & 255); out.write((v >> 8) & 255); out.write((v >> 16) & 255); out.write((v >> 24) & 255);
    }

    private static final class HttpResult {
        final int code;
        final String body;
        HttpResult(int code, String body) { this.code = code; this.body = body == null ? "" : body; }
    }
}
