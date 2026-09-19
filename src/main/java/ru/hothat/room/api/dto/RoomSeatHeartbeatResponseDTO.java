package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Отметка присутствия игрока.
 *
 * <p>Чистое обновление отметки и ничего больше: ни передачи хозяйства, ни
 * уборки, ни сверки состава. Раньше этот же вызов был поводом сделать всё
 * сразу, и обычный тик раз в полминуты мог отобрать у человека комнату.
 *
 * <p>В ответе — и записанная отметка, и серверное время. Их разница и есть
 * поправка часов, ради которой фронтенд до сих пор писал в свою строку
 * {@code clockProbeAt} и тут же перечитывал её с сервера
 * ({@code app-core.js:8053}).
 */
@Schema(description = "Принятая отметка присутствия")
public record RoomSeatHeartbeatResponseDTO(

        @Schema(description = "Записанная отметка, миллисекунды эпохи",
                example = "1788600000000", type = "integer")
        long lastSeenAtMs,

        @Schema(description = "Серверное время на момент ответа: по нему клиент правит свои часы",
                example = "1788600000000", type = "integer")
        long serverTimeMs,

        @Schema(description = "Сколько отметка считается свежей, миллисекунды. Порог держит сервер, "
                + "чтобы у клиента не было своей копии", example = "300000", type = "integer")
        long aliveWindowMs) {
}
