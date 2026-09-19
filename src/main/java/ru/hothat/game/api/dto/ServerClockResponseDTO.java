package ru.hothat.game.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Серверные часы.
 *
 * <p>Заменяет калибровку через запись отметки в свою строку и её немедленное
 * чтение обратно ({@code app-core.js:8053-8062}) — то есть запись в базу ради
 * вопроса «который час». Все сроки партии заданы в этом времени, и без
 * поправки клиент считал бы остаток хода по своим часам.
 */
@Schema(description = "Серверное время")
public record ServerClockResponseDTO(

        @Schema(description = "Текущее время сервера, миллисекунды эпохи", example = "1757150385000")
        long serverTimeMs) {
}
