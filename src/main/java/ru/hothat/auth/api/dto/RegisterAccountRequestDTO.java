package ru.hothat.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.hothat.common.validation.Nickname;

/**
 * Регистрация полноценной учётной записи.
 *
 * <p>Ник обязателен и занимается сразу: он же становится отображаемым именем,
 * и без него игрока некому назвать в комнате. Дивизиона здесь нет — его
 * выбирают отдельным шагом в области профиля, и выбор этот необратим.
 */
@Schema(description = "Регистрация по почте и паролю")
public record RegisterAccountRequestDTO(

        @Schema(description = "Почта; регистр не важен, адрес должен быть свободен",
                example = "player@example.com", maxLength = 320)
        @NotBlank(message = "Не указан e-mail.")
        @Email(message = "Некорректный e-mail.")
        @Size(max = 320, message = "Слишком длинный e-mail.")
        String email,

        /**
         * Шесть, а не восемь: столько обещает форма регистрации, и это обещание
         * переведено на девять языков. Верхний предел — 72 значащих байта
         * BCrypt плюс запас; всё, что длиннее, стойкости не добавляет.
         */
        @Schema(description = "Пароль, не короче 6 знаков", example = "s3cret-pass",
                minLength = 6, maxLength = 128, format = "password")
        @NotBlank(message = "Не указан пароль.")
        @Size(min = 6, max = 128, message = "Пароль: от 6 до 128 знаков.")
        String password,

        @Schema(description = "Ник: латиница, цифры и подчёркивание, 3–20 знаков, начинается с буквы",
                example = "petya", pattern = "^[A-Za-z][A-Za-z0-9_]{2,19}$")
        @NotBlank(message = "Не указан ник.")
        @Nickname
        String nickname) {
}
