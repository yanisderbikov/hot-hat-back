package ru.hothat.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Открытая сессия: пара токенов и учётная запись, которой они принадлежат.
 *
 * <p>Учётка приезжает вместе с токенами, чтобы клиент не делал второй запрос
 * в ту же секунду: экран после входа рисует имя и решает, показывать ли
 * консоль администратора.
 */
@Schema(description = "Выданная сессия")
public record IssuedSessionResponseDTO(

        @Schema(description = "Пара токенов сессии")
        SessionTokensView tokens,

        @Schema(description = "Учётная запись вошедшего")
        AccountView account) {
}
