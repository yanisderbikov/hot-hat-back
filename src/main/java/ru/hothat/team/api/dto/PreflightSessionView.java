package ru.hothat.team.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Снимок проверки готовности пары.
 *
 * <p>Общая проекция: включается полем в ответы старта, чтения и смены
 * готовности. Три адреса отдают один и тот же предмет, и описан он один раз.
 *
 * <p>Живёт пять минут от {@code startedAtMs}; после {@code expiresAtMs} сервер
 * считает проверку несостоявшейся и отдаёт {@code null} вместо снимка.
 */
@Schema(description = "Проверка готовности рейтинговой пары")
public record PreflightSessionView(

        @Schema(description = "Команда, которая проходит проверку: она же идентификатор проверки — "
                + "у пары не может быть двух одновременных", example = "0f3a9c1d-7b2e-4a58-9c40-6d5e2b8a1f37")
        String teamId,

        @Schema(description = "Зачем проверка затеяна")
        PreflightIntent intent,

        @Schema(description = "Режим, в котором пара собирается играть")
        GameMode gameMode,

        @Schema(description = "Комната, в которую целится пара при замысле room; null при быстрой игре",
                example = "hat-0f3a9c1d7b2e5480", nullable = true)
        String requestedRoomId,

        @Schema(description = "Кто из пары начал проверку", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String initiatorUid,

        @Schema(description = "Оба участника: связь и готовность каждого")
        List<PreflightParticipantView> participants,

        @Schema(description = "У обоих отвечают камера и микрофон", example = "true", type = "boolean")
        boolean bothMedia,

        @Schema(description = "Оба нажали «готов»", example = "true", type = "boolean")
        boolean bothReady,

        @Schema(description = "Можно запускать поиск: связь и готовность у обоих. По этому признаку, "
                + "а не по двум предыдущим, клиент включает кнопку", example = "true", type = "boolean")
        boolean canLaunch,

        @Schema(description = "Комната, которую нашёл подбор; null — поиск ещё не дал результата",
                example = "hat-4b2c8e1a9f6d3057", nullable = true)
        String targetRoomId,

        @Schema(description = "Найденная комната набралась и готова к старту", example = "false", type = "boolean")
        boolean roomReady,

        @Schema(description = "Поиск соперников уже запущен", example = "false", type = "boolean")
        boolean searchStarted,

        @Schema(description = "Сколько игроков уже собралось в найденной комнате", example = "2", type = "integer")
        int searchCount,

        @Schema(description = "Поиск истёк по времени и закрылся ни с чем", example = "false", type = "boolean")
        boolean failed,

        @Schema(description = "Когда проверку начали, миллисекунды эпохи", example = "1788600000000", type = "integer")
        long startedAtMs,

        @Schema(description = "Когда снимок менялся в последний раз, миллисекунды эпохи",
                example = "1788600042000", type = "integer")
        long updatedAtMs,

        @Schema(description = "Когда проверка протухнет, миллисекунды эпохи. Часы серверные: "
                + "клиент сравнивает с ними, а не со своими", example = "1788600300000", type = "integer")
        long expiresAtMs) {
}
