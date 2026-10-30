package ru.hothat.testbot.usecase;

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
 * dialog must be placed inside «...» or "..." in the assignment.
 * A long brief is refused BEFORE contacting paid video or audio providers.
 */
public record NeuroScenePlan(
        String assignment,
        String spokenText,
        int durationSeconds,
        double speechStartSeconds,
        String videoPrompt,
        String speechStyle) {

    private static final Pattern QUOTED = Pattern.compile("«([^»]+)»|\"([^\"\\n]+)\"");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final String BASE =
            "Create a NEW ultra-photorealistic uncut webcam performance. Use @Video1 only " +
            "to preserve the real player's appearance, age, facial proportions, hair, clothes, " +
            "room, lighting, webcam angle, and natural facial expressiveness. " +
            "Do NOT copy source movements. Exactly one real person. Fixed camera, " +
            "consistent geometry and background, natural microexpressions, blinking, " +
            "eye contact, breathing, believable anatomy and fully visible hands. ";
    private static final String END =
            "Follow the assigned sequence completely, with real hand gestures, no frozen poses. " +
            "No jump cuts, no changing identity, no captions, no added people, no wardrobe change. " +
            "Movements and speech must feel like an actual spontaneous webcam recording. ";

    public static NeuroScenePlan plan(String raw) {
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
        String spoken = String.join(" ... ", lines);
        String lc = brief.toLowerCase(Locale.ROOT);
        if (lines.isEmpty()) {
            throw invalid("NEURO_SPEECH_NEEDS_QUOTATION_MARKS");
        }
        if (spoken.length() > 220) throw invalid("NEURO_SPEECH_TOO_LONG");
        int words = countWords(spoken);
        double voiceSeconds = Math.max(1.8, words / 2.7);
        double movement = .5;
        // A turn or eye contact changes the staging rather than adding a
        // second person or substituting the entire source performance.
        boolean turn = has(lc,"поворач","смотр","взгляд","камер");
        if (turn) movement += .55;
        if (has(lc,"палец","thumbs","лайк")) movement += .95;
        if (has(lc,"кулак","грозит","угрож")) movement += .85;
        if (has(lc,"машет","помах","прощан")) movement += 1.0;
        if (has(lc,"вста","поднимается со стула","встает")) movement += .8;
        if (has(lc,"уход","уйти","выход","покида")) movement += 1.05;
        if (has(lc,"улыб","смеет")) movement += .25;
        if (has(lc,"пауз","помолч","замира")) movement += .55;
        int seconds = (int)Math.ceil(voiceSeconds + movement);
        if (seconds > 10) {
            throw invalid("NEURO_ASSIGNMENT_EXCEEDS_TEN_SECONDS");
        }
        seconds = Math.max(4, seconds);
        double start = turn ? .6 : .3;
        String videoPrompt = BASE + "Total duration EXACTLY " + seconds +
                " seconds. Directed actor instructions in Russian, in the exact requested order: " +
                brief + ". The ONLY spoken dialogue is «" + spoken + "». " +
                "The actor mouths the words while actually speaking them, not before or after; " +
                "outside speech the mouth stays naturally closed. " + END;
        if (videoPrompt.length() > 2500) throw invalid("NEURO_PROVIDER_PROMPT_TOO_LONG");
        String speechStyle = "Speak the EXACT Russian quotation clearly with the target player's " +
                "natural, warm, conversational rhythm, full pronunciation, and appropriate " +
                "expressive emotion. Preserve all specified pauses and punctuation. " +
                "Do not hurry or read stage directions aloud. No extra words. " +
                "Delivery directions: " + brief;
        return new NeuroScenePlan(brief, spoken, seconds, start, videoPrompt, speechStyle);
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
