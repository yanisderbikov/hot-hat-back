package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Профиль сразу после первого входа.
 *
 * <p>Возвращается вся карточка, а не подтверждение: клиенту она нужна прямо
 * здесь — показать ник, подпись дивизиона и язык интерфейса, — и повторный
 * поход за собственным профилем становится не нужен.
 */
@Schema(description = "Карточка игрока после первого входа")
public record CompletedOnboardingResponseDTO(

        @Schema(description = "Карточка игрока")
        ProfileCardView profile) {
}
