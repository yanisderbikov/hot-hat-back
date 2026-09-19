package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.common.validation.Nickname;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Заявка на ник, который занять самому не удалось. */
@Schema(description = "Запрос на разбор ника вручную")
public record SubmitNicknameRequestDTO(

        @Schema(description = "Желаемый ник: латиница, первый символ — буква, длина 3–20",
                example = "Fermer", pattern = "^[A-Za-z][A-Za-z0-9_]{2,19}$")
        @NotBlank(message = "Ник обязателен.")
        @Nickname
        String nickname,

        /** Предел 500 символов — тот же, что обрезает старый сервис перед записью. */
        @Schema(description = "Зачем игроку этот ник; можно не заполнять",
                example = "Это мой ник во всех играх с 2014 года.", maxLength = 500, nullable = true)
        @Size(max = 500, message = "Слишком длинное объяснение.")
        String reason) {
}
