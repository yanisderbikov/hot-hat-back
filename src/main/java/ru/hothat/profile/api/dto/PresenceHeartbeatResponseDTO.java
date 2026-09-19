package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Отметка присутствия.
 *
 * <p>Кроме подтверждения клиент получает часы сервера: по ним он сверяет
 * свои таймеры, а «онлайн» вычисляется как «пинговал за последние две минуты»
 * именно по серверному времени.
 */
@Schema(description = "Результат отметки присутствия")
public record PresenceHeartbeatResponseDTO(

        @Schema(description = "Время сервера, миллисекунды эпохи", example = "1788600000000", type = "integer")
        long serverNowMs) {
}
