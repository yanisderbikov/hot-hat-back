package ru.hothat.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Пара токенов сессии.
 *
 * <p>Общая проекция пяти ответов: вход, гостевой вход, регистрация, апгрейд
 * гостя и обновление пары. Именно здесь правило «пара DTO на операцию» и
 * велит вкладывать запись, а не переиспользовать один {@code AuthResponseDTO}
 * на всё сразу: у каждой операции своя запись ответа, но форма самой пары
 * одна, и держать пять её копий значит однажды выдать шестую с другим именем
 * поля.
 *
 * <p>Refresh-токен непрозрачен: это не JWT, разобрать его клиенту нечем, в
 * базе он лежит хешем. Срок его жизни в ответе не назван намеренно — клиенту
 * незачем планировать по нему, он просто хранит токен до отказа сервера.
 */
@Schema(description = "Пара токенов: короткий access и долгий refresh")
public record SessionTokensView(

        @Schema(description = "Access-токен для заголовка Authorization; живёт минуты",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJRazN4WmFUYiJ9.7bNqXm2wLpK9sVuWyA")
        String accessToken,

        /**
         * Схема названа полем, а не подразумевается: клиент склеивает заголовок
         * сам, и «Bearer» в трёх местах фронтенда сегодня зашит строкой.
         */
        @Schema(description = "Схема заголовка Authorization; всегда Bearer", example = "Bearer")
        String tokenType,

        @Schema(description = "Непрозрачная строка обмена; меняется при каждом обновлении пары",
                example = "8kQvTn2xR7mL0pWzYcBdFgHjKsNvUaXe1rTyUiOpAsD")
        String refreshToken,

        @Schema(description = "Сколько секунд жить access-токену", example = "900", type = "integer")
        long expiresInSeconds) {

    /** Единственное место, где имя схемы записано словом. */
    public static final String BEARER = "Bearer";
}
