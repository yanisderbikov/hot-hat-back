package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.room.domain.HostHandoverOutcome;

/**
 * Что решил сторож бездействующего хозяина.
 *
 * <p>Одна форма ответа вместо пяти. Старый {@code setup_host_watch} отдавал
 * пять несовместимых карт, и клиент различал их по наличию ключей
 * {@code restored}, {@code transferred}, {@code privateRoom} и
 * {@code remainingMs} ({@code app-core.js:1082}) — замечание C6 аудита. Здесь
 * исход назван перечислением, а всё условное выражено nullable-полями.
 *
 * <p>Права — участника, а не хозяина, и это тоже правка: старый метод был
 * единственным в срезе без проверки участия, хотя писал {@code createdBy}
 * (находка A6). Спрашивает сторожа именно не-хозяин: хозяин, который сам себя
 * проверяет на бездействие, бездействующим не бывает.
 */
@Schema(description = "Решение сторожа хозяйства комнаты")
public record HostHandoverResponseDTO(

        @Schema(description = "Что произошло", example = "countdown")
        HostHandoverOutcome outcome,

        @Schema(description = "Новый хозяин; заполнено только при restored и transferred",
                example = "2c9d4e7aP3lM5nB8vC1xZ0qW6eR4", nullable = true)
        String newHostUid,

        @Schema(description = "Как зовут нового хозяина; заполнено вместе с newHostUid",
                example = "petya", nullable = true)
        String newHostName,

        @Schema(description = "Сколько осталось до передачи, миллисекунды; заполнено только "
                + "при countdown", example = "134000", type = "integer", nullable = true)
        Long remainingMs,

        @Schema(description = "Сколько живых участников в комнате", example = "10", type = "integer")
        int activePlayers,

        @Schema(description = "При каком составе сторож включается: не меньше четырёх и не меньше "
                + "вместимости комнаты", example = "10", type = "integer")
        int targetPlayers) {
}
