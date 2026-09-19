package ru.hothat.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Своя учётная запись: только учётная часть.
 *
 * <p>Заменяет учётную половину {@code GET /api/auth/me}. Профильная половина
 * (аватар, дивизион, язык интерфейса, обойма мемов) уехала в
 * {@code GET /api/v2/profile/me}: она меняется своими адресами и своей
 * частотой, а здесь лежит то, что меняется раз в жизни аккаунта.
 */
@Schema(description = "Своя учётная запись")
public record MyAccountResponseDTO(

        @Schema(description = "Учётная запись")
        AccountView account,

        /**
         * У гостя пароля нет вовсе, и экран смены пароля ему показывать нельзя.
         * Выводить это из {@code kind} клиент не должен: род и наличие пароля —
         * разные вопросы, и однажды они разойдутся (вход по одноразовой ссылке).
         */
        @Schema(description = "Задан ли пароль. У гостя всегда false", example = "true")
        boolean passwordSet,

        @Schema(description = "Когда учётку зарегистрировали, миллисекунды эпохи; "
                + "у гостя null — он не регистрировался",
                example = "1788600000000", type = "integer", nullable = true)
        Long registeredAtMs,

        @Schema(description = "Когда пароль меняли в последний раз, миллисекунды эпохи; "
                + "null — пароля нет или его не меняли",
                example = "1788600000000", type = "integer", nullable = true)
        Long passwordUpdatedAtMs) {
}
