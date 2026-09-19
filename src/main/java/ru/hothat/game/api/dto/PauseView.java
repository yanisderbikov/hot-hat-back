package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Пауза партии.
 *
 * <p>Причин ровно две, и они не равны: паузу хозяина снимает только хозяин, а
 * пауза из-за пропавшего игрока держится, пока он не вернулся. Клиент рисует
 * разные экраны, поэтому признак разведён двумя полями, а не одним флагом.
 *
 * <p>Остатки хода и апелляции едут здесь, а не в {@link TurnView}: на паузе
 * у хода нет начала, и дедлайн по нему не посчитать, а таймер на экране
 * должен стоять на замершей цифре, а не обнуляться.
 */
@Schema(description = "Состояние паузы")
public record PauseView(

        @Schema(description = "Партия остановлена", example = "true")
        boolean paused,

        @Schema(description = "Паузу поставил хозяин комнаты: снять её может только он", example = "false")
        boolean hostPaused,

        @Schema(description = "Причина остановки", example = "player_disconnected", nullable = true,
                allowableValues = {"host_paused", "player_disconnected"})
        String reason,

        @Schema(description = "Кого не хватает на связи", example = "[\"pL9Mn2bV3cX4zA5sD6fG7hJ8kL9m\"]")
        List<String> missingUids,

        @Schema(description = "Их имена — чтобы клиент не искал их по составу", example = "[\"Борис\"]")
        List<String> missingNames,

        @Schema(description = "Когда пауза началась, миллисекунды эпохи", example = "1757150370000")
        long startedAtMs,

        @Schema(description = "Сколько миллисекунд хода оставалось в момент паузы; 0 — пауза не в ходе",
                example = "23400")
        long turnRemainingMs,

        @Schema(description = "Сколько миллисекунд голосования оставалось в момент паузы; "
                + "0 — пауза не в апелляции", example = "0")
        long appealRemainingMs) {

    public static PauseView running() {
        return new PauseView(false, false, null, List.of(), List.of(), 0, 0, 0);
    }
}
