package ru.hothat.recording.usecase;

import org.springframework.stereotype.Component;
import ru.hothat.recording.api.dto.RecordingCardView;
import ru.hothat.recording.api.dto.RecordingParticipantView;
import ru.hothat.recording.api.dto.RecordingStatus;
import ru.hothat.recording.api.dto.RecordingTeamView;
import ru.hothat.recording.store.RecordingStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Карточка записи для клиента.
 *
 * <p>Собирается из пяти отдельных строк — паспорта, задания, отметок
 * рекордера, файла и срока хранения. Форма ответа при этом та же, что была у
 * строки на шестьдесят три колонки: у неё есть потребители во фронте.
 *
 * <p>Счёт победителя и сумма очков СЧИТАЮТСЯ по командам, а не читаются из
 * денормализованных колонок. Раньше их было две, они хранились рядом со
 * списком команд и расходились с ним при любой правке счёта.
 */
@Component
public class RecordingCards {

    public RecordingCardView card(RecordingStore.Card card) {
        RecordingStore.Passport passport = card.passport();
        RecordingStatus status = RecordingStatus.fromWire(card.stage());
        RecordingStore.Job job = card.job();
        return new RecordingCardView(
                passport.recordingId(),
                blankToNull(passport.roomId()),
                passport.gameNumber(),
                // Подписи не было — её строит клиент из идентификатора комнаты,
                // а если и комнаты уже не назвать, то из имени самой записи.
                title(passport),
                passport.gameMode(),
                passport.ranked(),
                passport.privateRoom(),
                passport.testRoom(),
                passport.divisionLanguage(),
                passport.gameLanguage(),
                participants(card.participants()),
                teams(card.teams()),
                card.winningScore(),
                card.totalScore(),
                passport.wordCount(),
                status,
                card.playable(),
                job == null ? null : zeroToNull(job.startRequestedAtMs()),
                job == null ? null : job.endedAtMs(),
                card.expiresAtMs(),
                card.saveCount(),
                card.sizeBytes(),
                card.durationNs(),
                job == null ? null : blankToNull(job.failureReason()));
    }

    public List<RecordingCardView> cards(List<RecordingStore.Card> rows, int limit) {
        List<RecordingCardView> items = new ArrayList<>(Math.min(rows.size(), Math.max(0, limit)));
        for (RecordingStore.Card row : rows) {
            if (items.size() >= limit) {
                break;
            }
            items.add(card(row));
        }
        return items;
    }

    private static List<RecordingParticipantView> participants(List<RecordingStore.Participant> rows) {
        List<RecordingParticipantView> items = new ArrayList<>(rows.size());
        for (RecordingStore.Participant row : rows) {
            items.add(new RecordingParticipantView(row.uid(), row.nickname(),
                    blankToNull(row.teamId()), row.bot()));
        }
        return items;
    }

    private static List<RecordingTeamView> teams(List<RecordingStore.Team> rows) {
        List<RecordingTeamView> items = new ArrayList<>(rows.size());
        for (RecordingStore.Team row : rows) {
            // Победа — признак у команды. Раньше это были два параллельных
            // массива идентификаторов и имён рядом со списком команд, и при
            // ничьей сшивка по идентификатору расходилась.
            items.add(new RecordingTeamView(row.teamId(), row.name(), row.memberUids(),
                    blankToNull(row.rankedTeamId()), row.score(), row.winner()));
        }
        return items;
    }

    private static String title(RecordingStore.Passport passport) {
        if (passport.title() != null && !passport.title().isBlank()) {
            return passport.title();
        }
        return passport.roomId() == null || passport.roomId().isBlank()
                ? passport.recordingId() : passport.roomId();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Long zeroToNull(long value) {
        return value == 0 ? null : value;
    }
}
