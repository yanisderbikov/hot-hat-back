package ru.hothat.team.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Снимок проверки после смены готовности.
 *
 * <p>Снимок целиком, а не одно записанное значение: нажатие «готов» меняет и
 * сводные признаки — {@code bothReady}, {@code canLaunch}, — и именно по ним
 * клиент включает кнопку запуска. Отдать только своё значение значило бы
 * заставить его тут же перечитать состояние.
 */
@Schema(description = "Итог смены готовности")
public record PreflightReadinessResponseDTO(

        @Schema(description = "Снимок проверки после записи")
        PreflightSessionView session) {
}
