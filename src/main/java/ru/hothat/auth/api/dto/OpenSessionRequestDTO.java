package ru.hothat.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Вход по почте и паролю.
 *
 * <p>Форма совпадает со старым {@code LoginRequestDTO} — двух полей у входа
 * по паролю и быть не может. Своя запись здесь не ради отличий, а ради
 * независимости: старый DTO обслуживает {@code /api/auth/login}, который
 * остаётся живым, и любая правка в нём (скажем, приход одноразового кода)
 * не должна менять контракт v2 молча.
 */
@Schema(description = "Вход по почте и паролю")
public record OpenSessionRequestDTO(

        @Schema(description = "Почта аккаунта; регистр не важен", example = "player@example.com",
                maxLength = 320)
        @NotBlank(message = "Не указан e-mail.")
        @Email(message = "Некорректный e-mail.")
        @Size(max = 320, message = "Слишком длинный e-mail.")
        String email,

        /**
         * Верхний предел назван, потому что BCrypt всё равно смотрит только на
         * первые 72 байта: всё, что длиннее, не добавляет стойкости, зато
         * попадает в тело запроса, у которого сегодня потолка нет вовсе
         * (аудит A10). Нижнего предела здесь нет намеренно — форма входа не
         * подсказывает, какой длины пароль у существующего аккаунта.
         */
        @Schema(description = "Пароль", example = "s3cret-pass", maxLength = 128, format = "password")
        @NotBlank(message = "Не указан пароль.")
        @Size(max = 128, message = "Слишком длинный пароль.")
        String password) {
}
