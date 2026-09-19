package ru.hothat.admin.usecase;

import org.springframework.stereotype.Component;
import ru.hothat.admin.api.dto.AdminRecordingCardView;
import ru.hothat.admin.api.dto.AdminRecordingParticipantView;
import ru.hothat.admin.api.dto.AdminRecordingTeamView;
import ru.hothat.model.media.GameRecording;
import ru.hothat.util.Divisions;
import ru.hothat.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Строка записи — в админскую карточку.
 *
 * <p>Отдельно от игроцкой проекции намеренно: администратору нужна кухня
 * рекордера, игроку — нет. Общая часть не переписана вторично — обе карточки
 * строятся из одной и той же строки таблицы, просто разными наборами полей.
 *
 * <p>Состав и команды лежат в записи колонками {@code jsonb} — снимком на
 * момент партии, а не ссылками на живые строки. Так и задумано: команда могла
 * распасться, игрок — сменить ник, а запись должна показывать, кто играл
 * тогда. Поэтому здесь разбор карты, а не чтение соседних таблиц, и ни одного
 * обращения к базе на строку списка.
 */
@Component
public class AdminRecordingMapper {

    public AdminRecordingCardView card(GameRecording recording) {
        List<String> winnerTeamIds = recording.getWinnerTeamIds() == null
                ? List.of() : recording.getWinnerTeamIds();
        return new AdminRecordingCardView(
                recording.getId(),
                blankToNull(recording.getRoomId()),
                recording.getGameNumber() == null ? 0 : recording.getGameNumber(),
                title(recording),
                recording.getGameMode(),
                Boolean.TRUE.equals(recording.getRanked()),
                Boolean.TRUE.equals(recording.getIsPrivate()),
                Boolean.TRUE.equals(recording.getIsTestRoom()),
                Divisions.normalize(recording.getDivisionLanguage()),
                Divisions.normalize(recording.getGameLanguage() == null
                        ? recording.getDivisionLanguage() : recording.getGameLanguage()),
                participants(recording.getParticipants()),
                teams(recording.getTeams(), winnerTeamIds),
                Json.str(recording.getStatus()),
                // Ноль в движке значит «ещё не случилось»; наружу это null:
                // ноль миллисекунд эпохи — это 1970 год, а не отсутствие значения.
                positiveOrNull(recording.getStartedAtMs()),
                positiveOrNull(recording.getFinishedAtMs()),
                positiveOrNull(recording.getExpiresAtMs()),
                recording.getSavedCount() == null ? 0 : recording.getSavedCount(),
                recording.getSavedBy() == null ? List.of() : List.copyOf(recording.getSavedBy()),
                recording.getSizeBytes() == null ? 0L : recording.getSizeBytes(),
                recording.getDurationNs() == null ? 0L : recording.getDurationNs(),
                blankToNull(recording.getEgressId()),
                positiveOrNull(recording.getRecorderReadyAtMs()),
                positiveOrNull(recording.getRecorderStartSignalAtMs()),
                positiveOrNull(recording.getEgressActiveAtMs()),
                Boolean.TRUE.equals(recording.getPrewarmed()),
                Boolean.TRUE.equals(recording.getSecretWordRecorded()),
                blankToNull(recording.getTerminationReason()),
                error(recording));
    }

    private static String title(GameRecording recording) {
        if (recording.getRoomName() != null && !recording.getRoomName().isBlank()) {
            return recording.getRoomName();
        }
        return recording.getRoomId() == null || recording.getRoomId().isBlank()
                ? "Игра" : recording.getRoomId();
    }

    private static List<AdminRecordingParticipantView> participants(List<Map<String, Object>> rows) {
        List<AdminRecordingParticipantView> result = new ArrayList<>();
        for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
            result.add(new AdminRecordingParticipantView(
                    Json.str(row.get("uid")),
                    Json.str(row.get("nickname")),
                    blankToNull(Json.str(row.get("teamId"))),
                    Json.bool(row.get("isTestBot"))));
        }
        return result;
    }

    private static List<AdminRecordingTeamView> teams(List<Map<String, Object>> rows,
                                                      List<String> winnerTeamIds) {
        List<AdminRecordingTeamView> result = new ArrayList<>();
        for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
            String teamId = Json.str(row.get("id"));
            result.add(new AdminRecordingTeamView(
                    teamId,
                    Json.str(row.get("name")),
                    (int) Json.num(row.get("score")),
                    winnerTeamIds.contains(teamId)));
        }
        return result;
    }

    /** Ошибка старта важнее ошибки выгрузки: без старта выгружать было нечего. */
    private static String error(GameRecording recording) {
        String startError = blankToNull(recording.getStartError());
        return startError != null ? startError : blankToNull(recording.getEgressError());
    }

    private static Long positiveOrNull(Long value) {
        return value == null || value <= 0 ? null : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
