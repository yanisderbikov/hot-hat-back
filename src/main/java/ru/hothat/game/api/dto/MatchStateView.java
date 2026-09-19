package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.game.domain.MatchPhase;

import java.util.List;
import java.util.Map;

/**
 * Состояние партии целиком.
 *
 * <p>Общая проекция: она же едет в ответах операций, меняющих течение партии.
 * Так сделано намеренно — раньше клиент после каждой записи перечитывал
 * комнату целиком, и между записью и чтением состояние успевало разъехаться.
 *
 * <p>{@code serverTimeMs} здесь не для удобства: все сроки партии — дедлайн
 * хода, конец голосования, перезарядка — заданы в серверном времени, и без
 * отметки «сейчас у сервера столько» клиент не сможет перевести их в свои
 * часы. Именно ради этой поправки существовала клиентская калибровка.
 *
 * <p>Имена полей, которых не было в первом срезе, — {@code lastTurn},
 * {@code lastGuessedWord}, {@code lastActionAtMs}, {@code sabotageEventsRecent},
 * {@code sabotageLocks}, {@code testBotIds} и пара
 * {@code testOwnerExplainer*} — повторяют ключи документа комнаты, которыми
 * экран живёт сегодня: кадр канала заменяет ему подписку на документ, и
 * переименовывать поле в момент переезда значило бы искать все места чтения
 * в четырнадцати тысячах строк одновременно с заменой транспорта.
 */
@Schema(description = "Состояние партии")
public record MatchStateView(

        @Schema(description = "Комната партии", example = "hat-9f2c1a0b7d4e6538")
        String roomId,

        @Schema(description = "Фаза партии", example = "active")
        MatchPhase phase,

        @Schema(description = "Номер партии в комнате: он растёт с каждой новой", example = "3")
        int gameNumber,

        @Schema(description = "Режим партии", example = "sabotage", allowableValues = {"classic", "sabotage"})
        String gameMode,

        @Schema(description = "Рейтинговая партия", example = "false")
        boolean ranked,

        @Schema(description = "Тестовая комната: редкое оружие и награды в ней выключены", example = "false")
        boolean testRoom,

        @Schema(description = "Тест-боты за столом; пусто вне тестовой комнаты. По ним экран выбирает "
                + "живого координатора среди людей", example = "[\"testbot-hat-9f2c1a0b7d4e6538-1\"]")
        List<String> testBotIds,

        @Schema(description = "Партия, в которой прогон тест-ботов считал ходы владельца; "
                + "0 вне тестовой комнаты", example = "3")
        int testOwnerExplainerGameNumber,

        @Schema(description = "Который по счёту ход владельца в этой партии ведёт прогон тест-ботов; "
                + "0 вне тестовой комнаты", example = "2")
        int testOwnerExplainerTurnNumber,

        @Schema(description = "Сколько слов осталось в шляпе", example = "37")
        int wordsLeft,

        @Schema(description = "Последнее угаданное слово партии; null — ещё не угадали ни одного",
                example = "Абажур", nullable = true)
        String lastGuessedWord,

        @Schema(description = "Когда объясняющий нажимал в последний раз, миллисекунды эпохи; "
                + "0 — в этой партии ещё не нажимал", example = "1757150383000")
        long lastActionAtMs,

        @Schema(description = "Чья очередь ходить", example = "team_7d2c9a1b", nullable = true)
        String currentTeamId,

        @Schema(description = "Порядок ходов команд", example = "[\"team_7d2c9a1b\",\"team_1e5f8c3a\"]")
        List<String> teamOrder,

        @Schema(description = "Замороженные на старте составы",
                example = "{\"team_7d2c9a1b\":[\"kZ8Qw1nBv2mX3cL4aS5dF6gH7jK8\"]}")
        Map<String, List<String>> rosters,

        @Schema(description = "Замороженные на старте имена",
                example = "{\"kZ8Qw1nBv2mX3cL4aS5dF6gH7jK8\":\"Аня\"}")
        Map<String, String> playerNames,

        @Schema(description = "Команды и счёт")
        List<TeamScoreView> teams,

        @Schema(description = "Текущий ход; пусто вне хода", nullable = true)
        TurnView turn,

        @Schema(description = "Закрытый ход для экрана итогов; пусто, пока в партии не закрыли ни одного",
                nullable = true)
        LastTurnView lastTurn,

        @Schema(description = "Пауза")
        PauseView pause,

        @Schema(description = "Голосование; пусто вне апелляции", nullable = true)
        AppealView appeal,

        @Schema(description = "Последняя диверсия на сцене глазами слушателя: съёмку Подмены видят "
                + "только снимающий и снимаемый, остальным последней остаётся предыдущая",
                nullable = true)
        SabotageEventView lastSabotage,

        @Schema(description = "Недавние диверсии партии от старых к новым, глазами слушателя: по ним "
                + "сцена доигрывает эффекты, пропущенные при переподключении. Съёмка Подмены есть "
                + "только у снимающего и снимаемого")
        List<SabotageEventView> sabotageEventsRecent,

        @Schema(description = "Занятость дорожек эффектов глазами слушателя")
        SabotageLocksView sabotageLocks,

        @Schema(description = "Техническое завершение партии", nullable = true)
        TechnicalTerminationView termination,

        @Schema(description = "Текущее серверное время, миллисекунды эпохи: по нему клиент правит свои часы",
                example = "1757150385000")
        long serverTimeMs) {
}
