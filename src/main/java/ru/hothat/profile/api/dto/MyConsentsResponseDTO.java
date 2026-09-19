package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Правовые согласия текущей учётной записи.
 *
 * <p>Сегодня эти поля лежат в профиле и наружу не отдаются вовсе: клиент
 * узнаёт о непринятых документах только тем, что сервер отказывает в игре.
 */
@Schema(description = "Состояние правовых согласий игрока")
public record MyConsentsResponseDTO(

        @Schema(description = "Приняты ли документы хоть раз", example = "true", type = "boolean")
        boolean accepted,

        @Schema(description = "Подтверждено ли совершеннолетие", example = "true", type = "boolean")
        boolean adultConfirmed,

        @Schema(description = "Когда документы приняты, миллисекунды эпохи; null — не приняты",
                example = "1788600000000", type = "integer", nullable = true)
        Long acceptedAtMs,

        @Schema(description = "Версии принятых документов; null — игрок ещё ничего не принимал",
                nullable = true)
        ConsentVersionsView versions) {
}
