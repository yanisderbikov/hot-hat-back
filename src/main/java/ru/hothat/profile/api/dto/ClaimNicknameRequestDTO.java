package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.common.validation.Nickname;
import jakarta.validation.constraints.NotBlank;

/** Занять ник за собой. */
@Schema(description = "Запрос на смену собственного ника")
public record ClaimNicknameRequestDTO(

        /**
         * Формат тот же, что проверял {@code Ids.NICKNAME}: латиница, первая
         * буква не цифра, 3–20 символов. Проверка стоит здесь, а не в сервисе,
         * потому что это форма запроса, а не правило предметной области.
         */
        @Schema(description = "Новый ник: латиница, первый символ — буква, длина 3–20",
                example = "Vasya", pattern = "^[A-Za-z][A-Za-z0-9_]{2,19}$")
        @NotBlank(message = "Ник обязателен.")
        @Nickname
        String nickname) {
}
