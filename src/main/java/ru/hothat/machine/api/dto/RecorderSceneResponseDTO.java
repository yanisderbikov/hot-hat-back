package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.team.api.dto.GameMode;

import java.util.List;

/**
 * Сцена: компактный кадр, который страница записи опрашивает четыре раза в
 * секунду ({@code recording-view.js:37}).
 *
 * <p>Форма одна. Когда комната ушла к следующей партии, ответ не меняет состав
 * ключей, как сегодня, а говорит об этом полем {@link #state}; остальные поля
 * пусты. Клиенту больше не нужно различать ответы по наличию {@code staleGame}.
 */
@Schema(description = "Кадр партии для страницы записи")
public record RecorderSceneResponseDTO(

        @Schema(description = "Совпала ли снимаемая партия с текущей партией комнаты")
        RecorderSceneState state,

        @Schema(description = "Комната съёмки", example = "hat-3f1c9a2b7d4e6501")
        String roomId,

        @Schema(description = "Номер снимаемой партии", example = "3", type = "integer")
        int gameNumber,

        @Schema(description = "Фаза комнаты", example = "active",
                allowableValues = {"setup", "turnIntro", "active", "appeal", "between", "finished", "closed"})
        String phase,

        @Schema(description = "Название комнаты; null при state=STALE_GAME",
                example = "Вечерняя шляпа", nullable = true)
        String roomName,

        @Schema(description = "Режим партии; null при state=STALE_GAME", nullable = true)
        GameMode gameMode,

        @Schema(description = "Партия рейтинговая; null при state=STALE_GAME",
                example = "false", type = "boolean", nullable = true)
        Boolean ranked,

        @Schema(description = "Комната закрытая; null при state=STALE_GAME",
                example = "false", type = "boolean", nullable = true)
        Boolean isPrivate,

        @Schema(description = "Дивизион комнаты; null при state=STALE_GAME", nullable = true)
        DivisionLanguage divisionLanguage,

        @Schema(description = "Язык слов партии; null при state=STALE_GAME", nullable = true)
        DivisionLanguage gameLanguage,

        @Schema(description = "Команда, которая ходит сейчас", example = "team-1", nullable = true)
        String currentTeamId,

        @Schema(description = "Её место в очереди ходов; null при state=STALE_GAME",
                example = "0", type = "integer", nullable = true)
        Integer currentTeamIndex,

        @Schema(description = "Очередь ходов")
        List<String> teamOrder,

        @Schema(description = "Сколько слов осталось в шляпе; null при state=STALE_GAME",
                example = "17", type = "integer", nullable = true)
        Integer wordsLeft,

        @Schema(description = "Сколько слов угадано в текущем ходе; null при state=STALE_GAME",
                example = "4", type = "integer", nullable = true)
        Integer currentTurnScore,

        @Schema(description = "Слово, которое объясняют прямо сейчас: запись его показывает намеренно",
                example = "телескоп", nullable = true)
        String currentWord,

        @Schema(description = "Последнее угаданное слово", example = "телескоп", nullable = true)
        String lastGuessedWord,

        @Schema(description = "Что произошло последним", example = "guessed", nullable = true)
        String lastActionType,

        @Schema(description = "Слово последнего действия", example = "телескоп", nullable = true)
        String lastActionWord,

        @Schema(description = "Когда произошло последнее действие, мс эпохи; null при state=STALE_GAME",
                example = "1757068830000", type = "integer", format = "int64", nullable = true)
        Long lastActionAtMs,

        @Schema(description = "Когда ход закончится, мс эпохи; 0 — ход не идёт",
                example = "1757068860000", type = "integer", format = "int64", nullable = true)
        Long turnEndsAtMs,

        @Schema(description = "Когда закончится апелляция, мс эпохи; 0 — апелляции нет",
                example = "0", type = "integer", format = "int64", nullable = true)
        Long appealEndsAtMs,

        @Schema(description = "Партия на паузе; null при state=STALE_GAME",
                example = "false", type = "boolean", nullable = true)
        Boolean gamePaused,

        @Schema(description = "Кто объясняет", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk", nullable = true)
        String explainerUid,

        @Schema(description = "Кто отгадывает", example = "Rp8mQzXc2vNbT4sKuWyA1cEfGhJk", nullable = true)
        String guesserUid,

        @Schema(description = "Диверсия, которую надо проиграть прямо сейчас", nullable = true)
        RecorderSabotageEventView sabotageEvent,

        @Schema(description = "Последние диверсии партии, не больше двадцати четырёх")
        List<RecorderSabotageEventView> sabotageEvents,

        @Schema(description = "Команды партии")
        List<RecorderTeamView> teams,

        @Schema(description = "Игроки в кадре")
        List<RecorderScenePlayerView> players,

        @Schema(description = "Серверное время, мс эпохи: по нему страница правит свои часы",
                example = "1757068800000", type = "integer", format = "int64")
        long serverNowMs) {
}
