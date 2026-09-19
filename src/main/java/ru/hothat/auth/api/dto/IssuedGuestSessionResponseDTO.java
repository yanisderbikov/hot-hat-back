package ru.hothat.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Открытая гостевая сессия.
 *
 * <p>Форма повторяет ответ обычного входа, и запись всё равно своя: гостю
 * достаётся другая роль ({@code ROLE_GUEST}) и другой набор открытых адресов —
 * лобби, превью комнаты, апгрейд учётки и правовые согласия, — а значит и в
 * спецификации это должен быть отдельный ответ с отдельным описанием. Слитый
 * ответ означал бы, что читатель спецификации не видит разницы между гостем и
 * игроком до самого поля {@code kind}.
 */
@Schema(description = "Выданная гостевая сессия")
public record IssuedGuestSessionResponseDTO(

        @Schema(description = "Пара токенов сессии; access-токен несёт роль гостя")
        SessionTokensView tokens,

        @Schema(description = "Гостевая учётная запись: kind всегда guest, почты нет")
        AccountView account) {
}
