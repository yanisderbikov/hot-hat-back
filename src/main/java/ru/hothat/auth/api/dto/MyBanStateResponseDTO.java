package ru.hothat.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Состояние бана своей учётки.
 *
 * <p>Заменяет клиентскую подписку на документ {@code bans/{uid}}
 * ({@code live-preview.js:99}, {@code realtime-social.js:113}): страница
 * узнавала о бане тем, что документ появился, и выбрасывала игрока. Гостю
 * этот путь закрыт вовсе — живой канал социальных событий только для
 * зарегистрированных, — поэтому забаненному гостю в превью комнаты никто не
 * говорил, что он забанен.
 *
 * <p>Причина названа полем: «вас заблокировали» без причины — это обращение
 * в поддержку, которого можно было не допустить.
 */
@Schema(description = "Состояние бана учётной записи")
public record MyBanStateResponseDTO(

        @Schema(description = "Заблокирована ли учётка", example = "false")
        boolean banned,

        @Schema(description = "Причина блокировки; null — не заблокирован либо причину не указали",
                example = "Оскорбления в игровом чате", nullable = true)
        String reason,

        @Schema(description = "Когда заблокировали, миллисекунды эпохи; null — не заблокирован",
                example = "1788600000000", type = "integer", nullable = true)
        Long bannedAtMs) {
}
