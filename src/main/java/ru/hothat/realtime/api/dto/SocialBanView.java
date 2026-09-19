package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Состояние бана — та же форма, что у {@code GET /api/v2/auth/me/ban-state}.
 *
 * <p>Заменяет живую подписку на документ {@code bans/{uid}}
 * ({@code realtime-social.js:161}): страница узнавала о бане тем, что документ
 * появился, и молча выбрасывала игрока на главную. Здесь у бана есть причина,
 * и её можно показать — «вас заблокировали» без причины это обращение в
 * поддержку, которого можно было не допустить.
 */
@Schema(description = "Состояние бана слушателя")
public record SocialBanView(

        @Schema(description = "Заблокирована ли учётка", example = "false", type = "boolean")
        boolean banned,

        @Schema(description = "Причина блокировки; null — не заблокирован либо причину не указали",
                example = "Оскорбления в игровом чате", nullable = true)
        String reason,

        @Schema(description = "Когда заблокировали, миллисекунды эпохи; null — не заблокирован",
                example = "1788600000000", type = "integer", nullable = true)
        Long bannedAtMs) {
}
