# Live Copilot — Mic Probe v0.1

Това е **първият Android proof-of-concept** за идеята „TikTok отдолу, AI overlay отгоре“.

## Какво доказва този прототип

Не започваме още с AI, транскрипция или TikTok коментари. Първо атакуваме най-рисковия технически въпрос:

> Може ли AccessibilityService с видим `TYPE_ACCESSIBILITY_OVERLAY` да получава реален микрофонен сигнал, докато TikTok е foreground и също използва микрофона?

Android официално описва сценарий, при който **Accessibility service + обикновено приложение** могат да получават входящо аудио едновременно, когато UI на accessibility услугата е отгоре. Това обаче не гарантира поведение на всеки телефон и всяка версия на TikTok. Ако другото приложение маркира capture-а си като `privacySensitive=true`, конкурентният capture може да бъде забранен. Затова този прототип измерва реалното поведение вместо да разчитаме на теория.

## Какво има вътре

- AccessibilityService с плаващ `TYPE_ACCESSIBILITY_OVERLAY`.
- `AudioRecord` със source `VOICE_RECOGNITION`.
- На Android 11+ нашият AudioRecord изрично е `setPrivacySensitive(false)`.
- `AudioRecordingCallback` + `AudioRecordingConfiguration.isClientSilenced()` за директно засичане дали Android ни е заглушил заради concurrent capture policy.
- dBFS meter за доказателство, че идва реален ненулев сигнал.
- Засичане дали TikTok е бил foreground по време на теста.
- Запазване на последния тест локално в SharedPreferences.
- **Не** записва аудио във файл.
- **Не** чете TikTok UI съдържание.
- **Не** натиска автоматично TikTok бутони.

## Как да го пуснеш

1. Отвори папката в актуален Android Studio.
2. Инсталирай Android SDK Platform 37, ако Android Studio го поиска.
3. Build/Run върху **истински Android телефон** с Android 10 или по-нов. Емулатор не е достатъчен за този тест.
4. В приложението разреши Microphone.
5. Отвори Accessibility settings и включи `Live Copilot overlay`.
6. Ще се появи плаващият прозорец.
7. Натисни `START`.
8. Отвори TikTok и стартирай Live.
9. Говори нормално 15–30 секунди.
10. Следи overlay-а:
   - ако показва примерно `-35 dBFS`, нашето приложение получава реален звук;
   - ако пише `ANDROID Е ЗАГЛУШИЛ НАШИЯ MIC CLIENT`, concurrent capture не е разрешен в тази конфигурация.
11. Натисни `STOP`, върни се в приложението и виж резултата.
12. Задължително провери и от страна на TikTok (втори зрител или replay), че гласът се е предавал там. Нашият прототип може да докаже само нашата половина на едновременното получаване.

## Как тълкуваме резултатите

### A. TikTok чува + overlay чува
Това е зеленият сценарий. Следващата версия добавя streaming speech-to-text и AI подсказки.

### B. TikTok чува, overlay е `client silenced`
Тази версия на TikTok/Android/производител не позволява нашия concurrent capture по този път. Тогава тестваме други разрешени архитектури, а не „хакваме“ системните privacy controls.

### C. Overlay чува, TikTok не чува
Нашият capture е взел приоритет или TikTok има различно поведение. Това също не е годна архитектура за продукта.

## За Google Play

Този build е **research/sideload прототип**, не Play Store release. AccessibilityService е sensitive API и употребата му трябва да бъде коректно декларирана, с prominent disclosure/consent за приложения, които не са accessibility tools. Освен това не бива да се използва за заобикаляне на Android privacy/security controls. Преди публична версия трябва да направим отделен policy review на архитектурата.

## Следващ етап при успешен тест

1. 16 kHz PCM се пакетира на малки блокове (например 20–100 ms).
2. Streaming speech-to-text прави частичен transcript с ниска латентност.
3. Държим rolling context, например последните 60–120 секунди + компактно резюме на по-стария разговор.
4. AI engine генерира **кратка подсказка**, не дълъг отговор.
5. Overlay показва 1–2 реда: въпрос, hook, напомняне за CTA, идея за смяна на тема и т.н.
6. AI не трябва да говори постоянно: използваме cooldown, confidence и triggers, за да не пречи на хоста.

## Техническа бележка

Проектът е с `minSdk 29`, защото Android 10 въвежда съвременната concurrent audio input policy, която точно тестваме.
