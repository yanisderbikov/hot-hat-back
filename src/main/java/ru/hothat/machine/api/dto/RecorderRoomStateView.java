package ru.hothat.machine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.team.api.dto.GameMode;

import java.util.List;
import java.util.Map;

/**
 * Зеркало комнаты для рекордера: всё, из чего страница записи собирает кадр.
 *
 * <p>Раньше это была карта из сорока семи ключей, собранная руками в
 * {@code RecorderStateServiceImpl.roomState}; в спецификации от неё оставался
 * тип «объект». Здесь у каждого поля есть имя, тип и описание.
 *
 * <p>Четыре поля остались свободными структурами — {@code turnGuessedWords},
 * {@code lastTurn}, {@code sabotageLocks} и {@code technicalTermination}. Это
 * не лень: в базе они лежат как {@code jsonb} без схемы, а форму им задаёт
 * область партии. Объявить их схему здесь значило бы зафиксировать чужой
 * контракт в чужой области — они станут записями вместе с переездом
 * {@code game}, и тогда изменится один этот файл.
 *
 * <p>Мешок слов и пул сюда не попадают намеренно: рекордеру они не нужны, а
 * утечка неразыгранных слов в кадр испортила бы саму партию.
 */
@Schema(description = "Состояние комнаты, каким его видит рекордер")
public record RecorderRoomStateView(

        @Schema(description = "Идентификатор комнаты", example = "hat-3f1c9a2b7d4e6501")
        String id,

        @Schema(description = "Название комнаты", example = "Вечерняя шляпа", nullable = true)
        String name,

        @Schema(description = "Фаза комнаты", example = "active",
                allowableValues = {"setup", "turnIntro", "active", "appeal", "between", "finished", "closed"})
        String phase,

        @Schema(description = "Номер снимаемой партии", example = "3", type = "integer")
        int gameNumber,

        @Schema(description = "Режим партии")
        GameMode gameMode,

        @Schema(description = "Партия рейтинговая", example = "false", type = "boolean")
        boolean ranked,

        @Schema(description = "Комната закрытая", example = "false", type = "boolean")
        boolean isPrivate,

        @Schema(description = "Комната тестовая", example = "false", type = "boolean")
        boolean isTestRoom,

        @Schema(description = "Дивизион комнаты")
        DivisionLanguage divisionLanguage,

        @Schema(description = "Язык слов партии")
        DivisionLanguage gameLanguage,

        @Schema(description = "Очередь ходов: идентификаторы команд по порядку")
        List<String> teamOrder,

        @Schema(description = "Составы на момент старта партии: команда — список игроков",
                example = "{\"team-1\":[\"Qk3xZaTb9mNpR2sVuWyA1cEfGhJk\"]}")
        Map<String, List<String>> teamRosters,

        @Schema(description = "Имена участников партии на момент старта: игрок — имя",
                example = "{\"Qk3xZaTb9mNpR2sVuWyA1cEfGhJk\":\"Вася\"}")
        Map<String, String> gamePlayerNamesByUid,

        @Schema(description = "Команда, которая ходит сейчас", example = "team-1", nullable = true)
        String currentTeamId,

        @Schema(description = "Её место в очереди ходов", example = "0", type = "integer")
        int currentTeamIndex,

        @Schema(description = "Слово, которое объясняют прямо сейчас", example = "телескоп", nullable = true)
        String currentWord,

        @Schema(description = "Сколько слов угадано в текущем ходе", example = "4", type = "integer")
        int currentTurnScore,

        @Schema(description = "Сколько слов осталось в шляпе", example = "17", type = "integer")
        int wordsLeft,

        @Schema(description = "Сколько слов было в шляпе на старте партии", example = "40", type = "integer")
        int wordCount,

        @Schema(description = "Кто объясняет", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk", nullable = true)
        String explainerUid,

        @Schema(description = "Имя объясняющего", example = "Вася", nullable = true)
        String explainerName,

        @Schema(description = "Кто отгадывает", example = "Rp8mQzXc2vNbT4sKuWyA1cEfGhJk", nullable = true)
        String guesserUid,

        @Schema(description = "Имя отгадывающего", example = "Петя", nullable = true)
        String guesserName,

        @Schema(description = "Идентификатор текущего хода", example = "turn-6b1f0c", nullable = true)
        String turnId,

        @Schema(description = "Когда ход начался, мс эпохи; null — ход ещё не начат",
                example = "1757068800000", type = "integer", format = "int64", nullable = true)
        Long turnStartedAtMs,

        @Schema(description = "Фактическая длительность хода в секундах с учётом диверсий; "
                + "null — берётся настройка комнаты",
                example = "58.5", type = "number", nullable = true)
        Double turnDurationSeconds,

        @Schema(description = "Настройка длительности хода в секундах", example = "60", type = "integer")
        int turnDuration,

        @Schema(description = "Когда ход закончится, мс эпохи; 0 — ход не идёт",
                example = "1757068860000", type = "integer", format = "int64")
        long turnEndsAtMs,

        @Schema(description = "Слова текущего хода; форму задаёт область партии")
        List<Map<String, Object>> turnGuessedWords,

        @Schema(description = "Последнее угаданное слово", example = "телескоп", nullable = true)
        String lastGuessedWord,

        @Schema(description = "Что произошло последним", example = "guessed", nullable = true)
        String lastActionType,

        @Schema(description = "Слово последнего действия", example = "телескоп", nullable = true)
        String lastActionWord,

        @Schema(description = "Когда произошло последнее действие, мс эпохи",
                example = "1757068830000", type = "integer", format = "int64")
        long lastActionAtMs,

        @Schema(description = "Итоги предыдущего хода; форму задаёт область партии", nullable = true)
        Map<String, Object> lastTurn,

        @Schema(description = "Когда закончится апелляция, мс эпохи; 0 — апелляции нет",
                example = "0", type = "integer", format = "int64")
        long appealEndsAtMs,

        @Schema(description = "Голоса апелляции: игрок — список слов, которые он предлагает отменить",
                example = "{\"Qk3xZaTb9mNpR2sVuWyA1cEfGhJk\":[\"w-12\"]}")
        Map<String, List<String>> appealVotes,

        @Schema(description = "Партия на паузе", example = "false", type = "boolean")
        boolean gamePaused,

        @Schema(description = "Паузу поставил хозяин комнаты", example = "false", type = "boolean")
        boolean hostPaused,

        @Schema(description = "Причина паузы", example = "player-missing", nullable = true)
        String pauseReason,

        @Schema(description = "Кого ждут на паузе")
        List<String> pauseMissingUids,

        @Schema(description = "Имена тех, кого ждут на паузе")
        List<String> pauseMissingNames,

        @Schema(description = "Диверсия, которую надо проиграть прямо сейчас", nullable = true)
        RecorderSabotageEventView sabotageEvent,

        @Schema(description = "Последние диверсии партии, не больше двадцати четырёх")
        List<RecorderSabotageEventView> sabotageEventsRecent,

        @Schema(description = "Сроки блокировок диверсий; форму задаёт область партии")
        Map<String, Object> sabotageLocks,

        @Schema(description = "Техническое завершение партии; форму задаёт область партии", nullable = true)
        Map<String, Object> technicalTermination,

        @Schema(description = "Партия пишется", example = "true", type = "boolean")
        boolean recordGame,

        @Schema(description = "Идентификаторы тестовых ботов комнаты")
        List<String> testBotIds) {
}
