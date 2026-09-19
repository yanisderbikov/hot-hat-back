package ru.hothat.testbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Результат одного шага раннера.
 *
 * <p>Сегодня этот же адрес возвращает семь разных наборов ключей в зависимости
 * от фазы комнаты, а при исчезнувшей комнате — вообще другой объект
 * {@code {roomId, closed}}. Здесь форма одна, а необязательное выражено
 * nullable-полями: клиенту больше не нужно различать ответы по наличию ключа.
 */
@Schema(description = "Итог шага раннера тестовой партии")
public record TestBotTurnResponseDTO(

        @Schema(description = "Фаза комнаты после шага",
                example = "active",
                allowableValues = {"setup", "turnIntro", "active", "appeal", "finished", "closed"})
        String phase,

        @Schema(description = "Что раннер сделал за этот шаг")
        TestBotTurnAction action,

        @Schema(description = "Через сколько миллисекунд разбудить раннер снова", example = "2500", type = "integer")
        long waitMs,

        @Schema(description = "Сколько диверсий выпустили боты за этот шаг; null — шаг был не боевой",
                example = "1", type = "integer", nullable = true)
        Integer botShots,

        @Schema(description = "Написал ли бот в чат на этом шаге; null — шаг был не боевой",
                example = "true", type = "boolean", nullable = true)
        Boolean botChat) {
}
