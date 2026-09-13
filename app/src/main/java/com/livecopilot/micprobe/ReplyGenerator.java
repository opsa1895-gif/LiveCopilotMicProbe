package com.livecopilot.micprobe;

import java.util.Locale;

final class ReplyGenerator {
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

    static Replies generate(String raw) {
        return generate(raw, raw);
    }

    static Replies generate(String context, String focus) {
        String full = context == null ? "" : context.trim();
        String text = focus == null ? "" : focus.trim();
        if (text.isEmpty()) text = full;
        String lower = text.toLowerCase(Locale.ROOT);
        String allLower = full.toLowerCase(Locale.ROOT);
        if (text.isEmpty()) return new Replies("—", "—", "—", "—");

        if (containsAny(lower, "здравей", "здрасти", "hello", "hi ", "добър вечер", "добро утро")) {
            return new Replies(
                    "Здрасти! Радвам се, че си тук — как минава денят ти?",
                    "О, още един смел човек влезе в live-а — добре дошъл!",
                    "Здрасти! Влизането е безплатно, излизането е по желание.",
                    "Здравей и добре дошъл! Радвам се, че се включи."
            );
        }

        if (containsAny(lower, "колко струва", "цена", "колко е", "price", "струва")) {
            String subject = previousSubject(full, text);
            return new Replies(
                    subject.isEmpty() ? "Кой точно вариант имаш предвид, за да кажа точната цена?" : "За " + subject + " — кажи кой вариант гледаш и ще уточня точната цена.",
                    "Цената не я крия — само ми кажи за кой вариант говорим.",
                    "Само да не питаш за цената на целия TikTok — кой вариант имаш предвид?",
                    "Разбира се — уточни варианта и ще ти отговоря точно."
            );
        }

        if (containsAny(lower, "откъде си", "къде живееш", "where are you from")) {
            return new Replies(
                    "Ще кажа общо, но не и лични детайли на live.",
                    "Локацията ми е почти държавна тайна — почти.",
                    "От място с интернет и прекалено много TikTok.",
                    "Мога да кажа общия район, но не споделям личен адрес на live."
            );
        }

        if (containsAny(lower, "на колко си", "години си", "how old")) {
            return new Replies(
                    "Предпочитам да не казвам точната си възраст на live.",
                    "Достатъчно, за да знам кога въпросът е капан.",
                    "На толкова, че рожденият ден идва подозрително бързо.",
                    "Благодаря за въпроса, но възрастта си я пазя лична."
            );
        }

        if (containsAny(lower, "харесвам", "много си", "красив", "красива", "готин", "готина", "обичам", "love you")) {
            return new Replies(
                    "Благодаря ти, много е приятно да го чуя!",
                    "Продължавай така и ще ти пазя VIP място.",
                    "Благодаря! Его-то ми току-що поиска собствен профил.",
                    "Много мило от твоя страна, благодаря ти."
            );
        }

        if (containsAny(lower, "грозен", "грозна", "тъп", "тъпа", "смешен си", "смешна си", "hate", "идиот")) {
            return new Replies(
                    "Няма проблем да не сме на едно мнение — продължаваме.",
                    "Толкова усилие за коментар, а можеше просто да кажеш „здрасти“.",
                    "Записвам го в папката „непоискани ревюта“.",
                    "Разбирам, че не ти допада. Нека държим разговора нормален."
            );
        }

        if (lower.contains("защо")) {
            String subject = previousSubject(full, text);
            return new Replies(
                    subject.isEmpty() ? "Кажи кое точно имаш предвид и ще отговоря конкретно." : "Ако говориш за " + subject + ", кажи кое точно те интересува и ще отговоря конкретно.",
                    "Защото животът явно отказва да ни дава лесните въпроси.",
                    "Тук вече ни трябва драматична музика и още един детайл.",
                    "Уточни само едно нещо, за да не ти дам неточен отговор."
            );
        }

        if (containsAny(lower, "как", "какво", "кой", "коя", "кога", "къде", "може ли", "дали", "?")) {
            String shortQuote = shorten(text, 48);
            String contextHint = previousSubject(full, text);
            return new Replies(
                    contextHint.isEmpty()
                            ? "За „" + shortQuote + "“ — дай още един детайл и ще отговоря точно."
                            : "За " + contextHint + ": уточни само последния детайл и ще отговоря точно.",
                    "Добър въпрос — само че кристалната топка иска още един детайл.",
                    "Този въпрос заслужава още една мозъчна клетка — уточни го малко.",
                    "Разбрах въпроса. Кажи само един детайл, за да не гадая."
            );
        }

        if (containsAny(allLower, "цена", "струва", "колко")) {
            return new Replies(
                    "Разбрах — още сме на темата за цената. Кажи кой вариант сравняваме.",
                    "Ценовият сериал продължава — кой вариант е главният герой?",
                    "Окей, цената още е в кадър. Кой точно вариант гледаме?",
                    "Да останем на същата тема — уточни варианта и ще е по-точно."
            );
        }

        String q = shorten(text, 52);
        String subject = previousSubject(full, text);
        return new Replies(
                subject.isEmpty() ? "Разбрах: „" + q + "“. Продължи още малко, за да отговоря конкретно." : "Разбрах — говорим за " + subject + ". Кажи последния детайл и ще отговоря конкретно.",
                "Смело твърдение. Сега остава да видим дали ще издържи втори въпрос.",
                "Това вече звучи като история, която ще стане интересна след малко.",
                "Разбирам те. Продължи — пазя контекста."
        );
    }

    private static String previousSubject(String context, String focus) {
        if (context == null || context.isEmpty() || focus == null || focus.isEmpty()) return "";
        int idx = context.lastIndexOf(focus);
        String before = idx > 0 ? context.substring(0, idx) : "";
        before = before.replace('|', ' ').replace('\n', ' ').trim();
        if (before.isEmpty()) return "";
        String[] words = before.split("\\s+");
        int start = Math.max(0, words.length - 6);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < words.length; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(words[i]);
        }
        return shorten(sb.toString(), 42);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String n : needles) if (value.contains(n)) return true;
        return false;
    }

    private static String shorten(String s, int max) {
        String clean = s == null ? "" : s.replace('\n', ' ').trim();
        if (clean.length() <= max) return clean;
        return clean.substring(0, Math.max(1, max - 1)) + "…";
    }
}
