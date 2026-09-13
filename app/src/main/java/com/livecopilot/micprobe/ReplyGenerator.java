package com.livecopilot.micprobe;

import java.util.Locale;

final class ReplyGenerator {
    static final class Replies {
        final String direct;
        final String sarcastic;
        final String funny;
        final String calm;
        Replies(String direct, String sarcastic, String funny, String calm) {
            this.direct = direct; this.sarcastic = sarcastic; this.funny = funny; this.calm = calm;
        }
    }

    static Replies generate(String raw) { return generate(raw, raw); }

    static Replies generate(String context, String focus) {
        String full = clean(context);
        String text = clean(focus);
        if (text.isEmpty()) text = full;
        if (text.isEmpty()) return new Replies("—","—","—","—");

        String lower = text.toLowerCase(Locale.ROOT);
        int v = Math.floorMod((full + "|" + text).hashCode(), 3);
        String q = shorten(text, 60);
        String topic = topicHint(full, text);

        if (containsAny(lower,"здравей","здрасти","hello","добър вечер","добро утро")) {
            String[] d={"Здрасти! Радвам се, че се включи.","Добре дошъл! Как върви вечерта?","Здрасти! Какво те доведе в live-а?"};
            String[] s={"О, още един смел човек влезе — добре дошъл.","Здрасти — влезе точно навреме за хаоса.","Добре дошъл, още има свободни места на първия ред."};
            String[] f={"Здрасти! Влизането е безплатно, излизането е по желание.","Добре дошъл — тук поне няма входна такса.","Здрасти! Тъкмо ни липсваше още един свидетел."};
            String[] c={"Здравей и добре дошъл.","Радвам се, че се включи.","Здравей, приятно ми е, че си тук."};
            return pick(v,d,s,f,c);
        }

        if (containsAny(lower,"колко струва","цена","колко е","price","струва")) {
            String subject = topic.isEmpty()?"това":topic;
            String[] d={"За "+subject+" — кажи кой вариант имаш предвид, за да дам точна цена.","Уточни модела/варианта и ще кажа точната цена.","Кажи кой точно вариант гледаш и ще ти отговоря конкретно."};
            String[] s={"Цената я има, кристалната топка за варианта още я няма.","Само ми кажи кой вариант — цената няма да избяга.","Цената не се крие, просто иска име на варианта."};
            String[] f={"Само да не питаш за цената на целия TikTok.","Дай варианта, преди калкулаторът да се обиди.","Кажи модела и включваме режима 'сметки'."};
            String[] c={"Разбира се — уточни варианта и ще отговоря точно.","Кажи кой вариант те интересува.","Мога да помогна, само уточни кой продукт/вариант имаш предвид."};
            return pick(v,d,s,f,c);
        }

        if (containsAny(lower,"откъде си","къде живееш","where are you from")) {
            return pick(v,
                    new String[]{"Ще кажа общо, но не и лични детайли на live.","Мога да кажа града/района, без точен адрес.","Ще споделя обща локация, не личен адрес."},
                    new String[]{"Локацията е почти държавна тайна — почти.","Адресът ми днес е 'някъде с интернет'.","Точната локация я пазим за сезон 2."},
                    new String[]{"От място с интернет и прекалено много TikTok.","От държавата на добрия Wi‑Fi.","От място, където батерията винаги е на 12%."},
                    new String[]{"Мога да кажа общия район, но не споделям личен адрес.","Предпочитам да пазя точните лични данни извън live.","Ще кажа общо, но без лични подробности."});
        }

        if (containsAny(lower,"грозен","грозна","тъп","тъпа","идиот","hate")) {
            return pick(v,
                    new String[]{"Може да не сме на едно мнение — продължаваме.","Окей, приемам че не ти допада.","Няма проблем да мислиш различно."},
                    new String[]{"Толкова усилие за коментар, а можеше просто да кажеш 'здрасти'.","Силен старт — конструктивната част явно идва по-късно.","Добре, ревюто е прието без гаранция за действие."},
                    new String[]{"Записвам го в папката 'непоискани ревюта'.","Чудесно, още една звезда в Yelp-а на live-а.","Това влиза директно в архива 'интернет класики'."},
                    new String[]{"Нека държим разговора нормален.","Разбирам, че не ти допада — нека сме уважителни.","Може да сме на различно мнение без обиди."});
        }

        if (lower.contains("защо")) {
            String subject = topic.isEmpty()?q:topic;
            return pick(v,
                    new String[]{"Ако говориш за "+subject+", кажи коя част точно и ще отговоря конкретно.","Причината зависи от детайла — уточни коя част от "+subject+" имаш предвид.","За "+subject+" ми трябва още един конкретен детайл, за да не гадая."},
                    new String[]{"Защото явно лесните въпроси днес са в почивка.","Защото контекстът пак реши да играе на криеница.","Защото животът не поддържа бутон 'обясни накратко'."},
                    new String[]{"Тук вече ни трябва драматична музика и още един детайл.","Това е моментът за лупа, дъска и червен конец.","Добър въпрос — липсва само детективска шапка."},
                    new String[]{"Уточни само едно нещо и ще отговоря по-точно.","Кажи коя част имаш предвид.","Разбирам въпроса — дай ми още един детайл."});
        }

        if (containsAny(lower,"как","какво","кой","коя","кога","къде","може ли","дали","?")) {
            return pick(v,
                    new String[]{"За „"+q+"“ — дай още един детайл и ще отговоря точно.","Разбрах въпроса за „"+q+"“. Уточни само последния детайл.","По „"+q+"“ ми липсва един конкретен детайл, за да отговоря точно."},
                    new String[]{"Добър въпрос — кристалната топка иска още един детайл.","Почти сме там — само контекстът пак закъснява.","Въпросът е добър, инструкцията за употреба още пътува."},
                    new String[]{"Този въпрос заслужава още една мозъчна клетка — уточни го малко.","Дай още един детайл, преди да викнем панела от експерти.","Още една подробност и спирам да гадая като врачка."},
                    new String[]{"Разбрах. Уточни един детайл, за да не гадая.","Кажи само малко повече и ще съм по-точен.","Имам контекста, липсва ми една конкретика."});
        }

        String[] d={"По последното: „"+q+"“ — разбрах посоката. Продължи с конкретния въпрос.","Хващам темата: „"+q+"“. Кажи какво точно искаш да решим.","Разбрах фокуса: „"+q+"“. Дай следващия детайл."};
        String[] s={"Добре, това вече се движи — почти стигнахме до същината.","Окей, сюжетът напредна с цели два сантиметра.","Сега вече поне знаем в коя посока е влакът."};
        String[] f={"Това вече е нов епизод, не повторение.","Добре, сценаристът най-после смени сцената.","Ето, това вече звучи като развитие на сюжета."};
        String[] c={"Разбирам. Продължи — пазя последния контекст.","Следя те. Кажи следващата част.","Имам последната реплика и контекста — продължи."};
        return pick(v,d,s,f,c);
    }

    private static Replies pick(int v, String[] d, String[] s, String[] f, String[] c) {
        return new Replies(d[v%d.length], s[v%s.length], f[v%f.length], c[v%c.length]);
    }

    private static String topicHint(String context, String focus) {
        if (context == null || context.isEmpty()) return "";
        int idx = context.lastIndexOf(focus);
        String before = idx > 0 ? context.substring(0,idx) : context;
        before = before.replace('|',' ').replace('\n',' ').trim();
        if (before.isEmpty()) return "";
        String[] words = before.split("\\s+");
        int start = Math.max(0, words.length-7);
        StringBuilder sb = new StringBuilder();
        for (int i=start;i<words.length;i++) { if (sb.length()>0) sb.append(' '); sb.append(words[i]); }
        return shorten(sb.toString(),46);
    }

    private static boolean containsAny(String value, String... needles) { for (String n:needles) if (value.contains(n)) return true; return false; }
    private static String clean(String s) { return s==null?"":s.replace('\n',' ').replaceAll("\\s+"," ").trim(); }
    private static String shorten(String s, int max) { String c=clean(s); return c.length()<=max?c:c.substring(0,Math.max(1,max-1))+"…"; }
}
