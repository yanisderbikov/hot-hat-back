package ru.hothat.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Заведённая учётная запись вместе с открытой сессией.
 *
 * <p>Регистрация сразу выдаёт токены: заставлять только что зарегистрировавшегося
 * человека ещё раз вводить пароль незачем, а форма ответа при этом остаётся
 * своей — 201 создаёт ресурс, которого не было, и это другая операция, чем
 * вход.
 */
@Schema(description = "Созданная учётная запись и её сессия")
public record RegisteredAccountResponseDTO(

        @Schema(description = "Пара токенов сессии")
        SessionTokensView tokens,

        @Schema(description = "Созданная учётная запись")
        AccountView account) {
}
