package ru.hothat.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/**
 * Гостевой вход без почты и пароля.
 *
 * <p>Тело можно не присылать вовсе: фронтенд заводит гостя ещё до экрана
 * регистрации, когда имени игрока попросту нет.
 */
@Schema(description = "Гостевой вход")
public record OpenGuestSessionRequestDTO(

        /**
         * Здесь выражение, а не общая аннотация {@code @Nickname}: пустое
         * значение допустимо и означает «придумай сам», а {@code @Nickname}
         * такого не разрешает. Вторая половина выражения — тот же самый
         * образец, что проверяет {@code Ids.NICKNAME}.
         */
        @Schema(description = "Желаемый ник; пусто или отсутствует — сервер придумает свободный Guest######",
                example = "petya", pattern = "^$|^[A-Za-z][A-Za-z0-9_]{2,19}$", nullable = true)
        @Pattern(regexp = "^$|^[A-Za-z][A-Za-z0-9_]{2,19}$", message = "Некорректный ник.")
        String nickname) {
}
