package ru.hothat.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Обмен refresh-токена на новую пару.
 *
 * <p>Токен идёт телом, а не заголовком {@code Authorization}: заголовок несёт
 * access-токен, и в момент обновления он как раз просрочен. Предел длины
 * назван по тому, что выдаёт сервер (32 случайных байта в base64url — 43
 * знака), с запасом на смену генератора.
 */
@Schema(description = "Обновление пары токенов")
public record RenewSessionRequestDTO(

        @Schema(description = "Refresh-токен, полученный при входе или прошлом обновлении",
                example = "8kQvTn2xR7mL0pWzYcBdFgHjKsNvUaXe1rTyUiOpAsD", maxLength = 512)
        @NotBlank(message = "Не указан refresh-токен.")
        @Size(max = 512, message = "Некорректный refresh-токен.")
        String refreshToken) {
}
