package ru.hothat.testbot.usecase;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic no-charge staging of a player-authored webcam performance.
 * Never sends words to TTS by guessing what the actor should say: the exact
 * dialogue can be written naturally after a speech verb, without quotation marks.
 * A long brief is refused BEFORE contacting paid video or audio providers.
 */
@Schema(description = "Бесплатный план Нейроподмены: проверенные слова, движения, длительность и промпты до оплаты")
public record NeuroScenePlan(
        @Schema(description = "Исходное задание владельца после проверки и нормализации") String assignment,
        @Schema(description = "Точный текст русской речи для синтеза без сценических инструкций") String spokenText,
        @Schema(description = "Запланированная длительность итоговой сцены в секундах") int durationSeconds,
        @Schema(description = "Положение начала произнесения речи относительно начала сцены, секунды") double speechStartSeconds,
        @Schema(description = "Промпт Kling с движениями и сохранением внешности и фона") String videoPrompt,
        @Schema(description = "Инструкции русской озвучки и переноса голоса") String speechStyle) {

    private static final Pattern QUOTED = Pattern.compile("«([^»]+)»|\"([^\"\\n]+)\"");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern SPEECH_CUE = Pattern.compile(
            "(?iu)(?:сказать|скажи|сказал|скажет|говорит|произнести|произносит|произнес[её]т)\\s*(?:[:—–-]\\s*)?(.+)");
    private static final Pattern ACTION_AFTER_SPEECH = Pattern.compile(
            "(?iuU)\\s*(?:[,;.]\\s*)?(?:(?:и\\s+)?(?:затем|потом|после этого)\\s+|и\\s+)?"
            + "(?:показыва[\\p{L}]*|показать|грозит|погроз[\\p{L}]*|поднима[\\p{L}]*|"
            + "улыб[\\p{L}]*|смен[\\p{L}]*|поменя[\\p{L}]*|замен[\\p{L}]*|измен[\\p{L}]*|вста[\\p{L}]*|уход[\\p{L}]*|уходит|выходит|машет|помаш[\\p{L}]*|"
            + "смотря\\s+в\\s+камер[\\p{L}]*|глядя\\s+в\\s+камер[\\p{L}]*|"
            + "всё\\s+это\\s+говорит|все\\s+это\\s+говорит)\\b");
    private static final String BASE =
            "Create a NEW ultra-photorealistic uncut webcam performance. Use @Video1 only " +
            "to preserve the real player's appearance, age, facial proportions, hair, clothes, " +
            "and natural facial expressiveness. " +
            "Do NOT copy source movements. Exactly one real person. Fixed camera, " +
            "consistent actor geometry, natural microexpressions, blinking, " +
            "eye contact, breathing, believable anatomy and fully visible hands. ";
    private static final String END =
            "Follow the assigned sequence completely, with real hand gestures, no frozen poses. " +
            "No jump cuts, no changing identity, no captions, no added people, no wardrobe change. " +
            "Movements and speech must feel like an actual spontaneous webcam recording. ";

    public static NeuroScenePlan plan(String raw) {
        return plan(raw,10,3);
    }

    /**
     * Video character has 12-15 seconds for distinct spoken phrases,
     * gestures and walking out. Never squeeze the full brief into nine sec.
     * Legacy jobs retain the original 4-10-second contract unchanged.
     */
    public static NeuroScenePlan planForCharacter(String raw) {
        return plan(raw,15,12);
    }

    private static NeuroScenePlan plan(String raw,int maxSeconds,int minSeconds) {
        if (raw == null) throw invalid("NEURO_ASSIGNMENT_REQUIRED");
        String brief = Normalizer.normalize(raw, Normalizer.Form.NFC)
                .replace('\n',' ').replace('\r',' ').replaceAll("\\s+", " ").trim();
        if (brief.length() < 12 || brief.length() > 950) {
            throw invalid("NEURO_ASSIGNMENT_LENGTH_INVALID");
        }
        List<String> lines = new ArrayList<>();
        Matcher quotes = QUOTED.matcher(brief);
        while (quotes.find()) {
            String phrase = (quotes.group(1) != null ? quotes.group(1) : quotes.group(2)).trim();
            if (!phrase.isEmpty()) lines.add(phrase);
        }
        String spoken = lines.isEmpty() ? extractUnquotedSpeech(brief)
                : String.join(" ... ", lines);
        String lc = brief.toLowerCase(Locale.ROOT);
        if (spoken.isBlank()) {
            throw invalid("NEURO_SPEECH_NOT_UNAMBIGUOUS");
        }
        if (spoken.length() > 220) throw invalid("NEURO_SPEECH_TOO_LONG");
        int words = countWords(spoken);
        // Give real Russian speech more room than the previous 2.7 words/second
        // heuristic. Rhythmically spoken rap can run a little faster, but
        // explicit pauses still consume timeline even when TTS inserts them.
        boolean rap = has(lc,"рэп","рэпер","зачит","в ритм","как рэп","как реп");
        double voiceSeconds = Math.max(1.4, words / (rap ? 2.7 : 2.45));
        if (spoken.contains(" ... ")) voiceSeconds += .55;
        boolean keyboard = has(lc,"клавиатур","keyboard");
        boolean picksObject = keyboard && has(lc,"возьм","взять","подним","берет","берёт","схват");
        boolean putsBack = keyboard && has(lc,"полож","верн","опуст","убер");
        double movement = .5;
        // A turn or eye contact changes the staging rather than adding a
        // second person or substituting the entire source performance.
        boolean turn = has(lc,"поворач","смотр","взгляд","камер");
        if (turn) movement += .55;
        if (has(lc,"палец","thumbs","лайк")) movement += .95;
        if (has(lc,"кулак","грозит","угрож")) movement += .85;
        if (has(lc,"машет","помах","прощан")) movement += 1.0;
        if (has(lc,"вста","поднимается со стула","встает","встаёт")) movement += .8;
        if (has(lc,"уход","уйти","выход","покида")) movement += 1.05;
        if (has(lc,"улыб","смеет")) movement += .25;
        if (has(lc,"пауз","помолч","замира") && !spoken.contains(" ... ")) movement += .55;
        if (picksObject) movement += 1.35;
        if (putsBack) movement += 1.15;
        // Holding an object while delivering a line takes place concurrently
        // with speech, rather than adding its whole duration a second time.
        if (keyboard && has(lc,"держ","голов")) movement += .35;
        int seconds = (int)Math.ceil(voiceSeconds + movement);
        // Never silently truncate a user's exact words; drop or overlap
        // secondary gestures first. If even the spoken words cannot fit at
        // a natural pace, reject without spending on Kling.
        if (voiceSeconds + .35 > maxSeconds) {
            throw invalid(maxSeconds==10 ? "NEURO_SPOKEN_WORDS_EXCEED_TEN_SECONDS"
                    : "NEURO_SPOKEN_WORDS_EXCEED_FIFTEEN_SECONDS");
        }
        boolean compressed = seconds > maxSeconds;
        seconds = Math.max(minSeconds, Math.min(maxSeconds, seconds));
        double start = turn ? .6 : .3;
        boolean changeBackground = has(lc, "смени фон", "сменить фон", "изменить фон",
                "поменяй фон", "замени фон", "на фоне", "в другой комнате", "переместись");
        String sceneRule = changeBackground
                ? "Only the explicitly instructed background change is allowed; preserve the person's identity, camera realism and continuity. "
                : "BACKGROUND LOCK: The original webcam room, wall, furniture, objects, lighting, "
                  + "camera angle, focal length, framing and background MUST remain unchanged in every frame. "
                  + "Do not move the camera, replace the room, or invent objects even if the actor stands or exits. ";
        String choreography = keyboard && picksObject
                ? "ACTION PRIORITY 1: take a REAL computer keyboard with both hands from the desk just outside the frame, " +
                  "raise it onto the top of the head, hold it securely with both hands while facing the camera and " +
                  "performing the requested spoken line. The keyboard is deliberately requested, not a new room/background. " +
                  (putsBack ? "ACTION PRIORITY 2 AFTER the spoken line: lower the keyboard carefully onto the same offscreen desk. " : "")
                : "";
        String timing = "TIMING: first allow " +
                String.format(Locale.ROOT,"%.1f",Math.min(1.7, Math.max(.3,movement*.38))) +
                " seconds for initial action; speech MUST finish before the final 1.0 second of the clip. ";
        String simplify = compressed
                ? "The brief exceeds the time available: preserve the full spoken words and the main physical action; " +
                  "perform secondary gestures only when they naturally fit alongside speech. DO NOT rush, splice, " +
                  "speed up body movement, add extra gestures, or pretend a requested prop was handled. "
                : "";
        String videoPrompt = BASE + sceneRule + "Total duration EXACTLY " + seconds +
                " seconds. " + choreography + timing + simplify +
                "Directed actor instructions in Russian, in the exact requested order: " +
                brief + ". The ONLY spoken dialogue is «" + spoken + "». " +
                "The actor mouths the words while actually speaking them, not before or after; " +
                "outside speech the mouth stays naturally closed. " + END;
        if (videoPrompt.length() > 2500) throw invalid("NEURO_PROVIDER_PROMPT_TOO_LONG");
        String speechStyle = "Speak the EXACT Russian quotation clearly with the target player's " +
                "natural, warm, conversational rhythm, full pronunciation, and appropriate " +
                "expressive emotion. Preserve all specified pauses and punctuation. " +
                "Do not hurry or read stage directions aloud. No extra words. " +
                (rap ? "Read the exact words like clear, expressive spoken Russian rap with a steady beat, " +
                  "strong rhythmic accents and audible pauses, WITHOUT music or singing. " : "") +
                "Delivery directions: " + brief;
        return new NeuroScenePlan(brief, spoken, seconds, start, videoPrompt, speechStyle);
    }

    /**
     * Natural-language speech extraction. No quotation marks are required.
     * Reject unclear input instead of synthesizing directions as dialogue.
     * Stage actions bound speech, while a spoken pause becomes silence.
     */
    private static String extractUnquotedSpeech(String brief) {
        Matcher cue = SPEECH_CUE.matcher(brief);
        if (!cue.find()) return "";
        String candidate = cue.group(1).trim();
        // Everyday instructions often say "сказать выразительно как рекламу - ...".
        // The dash marks the actual spoken phrase, not an acting instruction.
        int speechDash = candidate.indexOf(" - ");
        if (speechDash >= 0 && speechDash < 90) candidate = candidate.substring(speechDash+3).trim();
        else {
            speechDash = candidate.indexOf(" — ");
            if (speechDash >= 0 && speechDash < 90) candidate = candidate.substring(speechDash+3).trim();
        }
        candidate = candidate.replaceFirst("(?iu)^(?:[:—–-]\\\\s*)+", "").trim();
        // A new sentence that describes acting/delivery is not dialogue.
        candidate = candidate.replaceFirst("(?iu)[.!?]\\\\s+(?:пусть|затем|потом|после\\\\s+этого)\\\\s+.*$", "");
        Matcher action = ACTION_AFTER_SPEECH.matcher(candidate);
        if (action.find()) candidate = candidate.substring(0, action.start()).trim();
        candidate = candidate.replaceAll("(?iuU)\\\\(?\\\\s*пауз[ауеы]\\\\s*\\\\)", " ... ");
        candidate = candidate.replaceAll("(?iuU)\\b(?:делает\\s+)?пауз[ауеы]\\b", " ... ");
        candidate = candidate.replaceAll("\\s*\\.\\.\\.\\s*", " ... ")
                .replaceAll("\\s+", " ").replaceAll("^[\\s:—–-]+|[\\s,;—–-]+$", "").trim();
        return candidate;
    }

    private static boolean has(String input, String... candidates) {
        for (String s : candidates) if (input.contains(s)) return true;
        return false;
    }

    private static int countWords(String s) {
        int words=0;
        Matcher m=WORD.matcher(s);
        while (m.find()) words++;
        return words;
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
