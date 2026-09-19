package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import ru.hothat.common.validation.PlayerUid;

/**
 * Кому хозяин передаёт комнату.
 *
 * <p>Цель обязана быть живым участником: отдать комнату тому, чья вкладка
 * закрылась, значит оставить её без хозяина совсем — а вернуть её обратно
 * сможет только сторож бездействия, и то через три минуты.
 */
@Schema(description = "Новый хозяин комнаты")
public record TransferHostRequestDTO(

        @Schema(description = "Кому передать комнату; себе — нельзя",
                example = "2c9d4e7aP3lM5nB8vC1xZ0qW6eR4", pattern = "^[A-Za-z0-9_-]{1,160}$")
        @NotBlank(message = "Не выбран новый хозяин комнаты.")
        @PlayerUid
        String targetUid) {
}
