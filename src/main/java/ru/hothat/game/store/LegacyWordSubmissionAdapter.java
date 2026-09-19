package ru.hothat.game.store;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.game.port.WordSubmissionPort;
import ru.hothat.model.room.RoomWordSubmission;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Переходник к сданным словам.
 *
 * <p>Единственная реализация {@link WordSubmissionPort}: слова шляпы лежат в
 * {@code room_word_submission}, куда их кладёт
 * {@code PUT /api/v2/game/{roomId}/word-submissions/me}. Уйдёт вместе с
 * переездом комнаты в {@code v2.word_submission} — вместе со своим фронтом,
 * не раньше.
 */
@Component
@RequiredArgsConstructor
public class LegacyWordSubmissionAdapter implements WordSubmissionPort {

    /** Больше сотни слов от одного человека шляпа не принимает. */
    private static final int PER_PLAYER_LIMIT = 100;
    /** Длиннее восьмидесяти знаков — уже не слово, а рассказ. */
    private static final int WORD_LENGTH_LIMIT = 80;

    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;

    @Override
    public List<String> read(String roomId, String uid) {
        return getterRoom.getWordSubmissions(roomId).stream()
                .filter(submission -> submission.getSubmissionId().equals(uid))
                .findFirst()
                .map(submission -> List.copyOf(submission.getWords()))
                .orElse(List.of());
    }

    @Override
    public SubmissionView write(String roomId, String uid, List<String> words, boolean testBot) {
        List<RoomWordSubmission> all = getterRoom.getWordSubmissions(roomId);
        RoomWordSubmission mine = all.stream()
                .filter(submission -> submission.getSubmissionId().equals(uid))
                .findFirst()
                .orElseGet(() -> RoomWordSubmission.builder()
                        .roomId(roomId)
                        .submissionId(uid)
                        .isTestBotSubmission(testBot)
                        .build());

        // Слова добавляются к уже сданным, а не заменяют их: экран набора шлёт
        // то, что человек напечатал сейчас, и потерять прежние было бы обидно.
        List<String> merged = normalize(concat(mine.getWords(), words));
        mine.setWords(new ArrayList<>(merged));
        mine.setCount(merged.size());
        mine.setUpdatedAt(Instant.now());
        saverRoom.saveWordSubmission(mine);

        int othersTotal = all.stream()
                .filter(submission -> !submission.getSubmissionId().equals(uid))
                .mapToInt(submission -> submission.getCount() == null ? 0 : submission.getCount())
                .sum();
        int total = othersTotal + merged.size();

        // Счётчик в комнате ведётся здесь же, одной транзакцией с заявкой.
        // Пока комната остаётся моделью чтения для экрана настройки, «сколько
        // слов в шляпе» и порог «минимум пять» берутся именно из неё: разойдись
        // счётчик с заявками — хозяин увидел бы кнопку «Начать игру» неактивной
        // при полной шляпе, и понять почему было бы неоткуда. Ревизия при этом
        // растёт на каждую сдачу: по ней старт партии узнаёт, что слова
        // изменились между показом экрана и нажатием.
        getterRoom.getById(roomId).ifPresent(room -> {
            room.setWordCount(total);
            room.setWordRevision(room.getWordRevision() == null ? 1 : room.getWordRevision() + 1);
            saverRoom.save(room);
        });
        return new SubmissionView(List.copyOf(merged), merged.size(), total);
    }

    @Override
    public List<String> collect(String roomId, int limit) {
        List<String> words = new ArrayList<>();
        for (RoomWordSubmission submission : getterRoom.getWordSubmissions(roomId)) {
            for (String word : submission.getWords() == null ? List.<String>of() : submission.getWords()) {
                if (words.size() >= limit) {
                    return words;
                }
                String clean = clean(word);
                if (!clean.isEmpty()) {
                    words.add(clean);
                }
            }
        }
        return words;
    }

    @Override
    public int total(String roomId) {
        return getterRoom.getWordSubmissions(roomId).stream()
                .mapToInt(submission -> submission.getCount() == null ? 0 : submission.getCount())
                .sum();
    }

    private static List<String> concat(List<String> previous, List<String> added) {
        List<String> all = new ArrayList<>(previous == null ? List.<String>of() : previous);
        all.addAll(added == null ? List.of() : added);
        return all;
    }

    /** Без пустот, без повторов без учёта регистра, не длиннее сотни. */
    private static List<String> normalize(List<String> words) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String word : words) {
            String clean = clean(word);
            if (clean.isEmpty() || !seen.add(clean.toLowerCase(Locale.ROOT))) {
                continue;
            }
            result.add(clean);
            if (result.size() >= PER_PLAYER_LIMIT) {
                break;
            }
        }
        return result;
    }

    private static String clean(String word) {
        if (word == null) {
            return "";
        }
        String clean = word.replaceAll("\\s+", " ").trim();
        return clean.length() <= WORD_LENGTH_LIMIT ? clean : clean.substring(0, WORD_LENGTH_LIMIT);
    }
}
