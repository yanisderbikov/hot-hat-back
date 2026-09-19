package ru.hothat.lobby.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Подтверждение того, что превью ещё смотрят.
 *
 * <p>Заменяет {@code updateDoc spectators.lastSeenAt} из браузера
 * ({@code live-preview.js:102}): отметку времени ставит сервер. Раньше её
 * писал клиент своими часами, и зритель с уехавшими часами оставался «живым»
 * вечно либо исчезал сразу.
 *
 * <p>Ответ несёт время сервера не для красоты: по нему клиент видит, что
 * сессия жива, и знает, когда стучаться снова, не заводя своего таймера от
 * момента открытия.
 */
@Schema(description = "Отметка о том, что превью открыто")
public record RoomPreviewHeartbeatResponseDTO(

        @Schema(description = "Комната, которую смотрим", example = "hat-0f3a9c1d7b2e5480",
                pattern = "^hat-[a-f0-9]{16}$")
        String roomId,

        @Schema(description = "Когда сервер отметил превью живым, миллисекунды эпохи",
                example = "1788600000000", type = "integer")
        long lastSeenAtMs,

        @Schema(description = "Через сколько миллисекунд стучаться снова", example = "60000", type = "integer")
        long heartbeatIntervalMs) {
}
