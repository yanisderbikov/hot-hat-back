package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.common.validation.Nickname;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Первый вход: дивизион, ник, аватар и правовые согласия одним запросом.
 *
 * <p>Сегодня браузер делает это четырьмя обращениями подряд
 * ({@code setDoc users/…}, затем {@code set_division}, {@code set_nickname},
 * {@code record_consent}). Обрыв связи между вторым и третьим оставлял
 * учётную запись с закреплённым навсегда дивизионом и без ника — состояние,
 * из которого игрок сам выбраться не мог.
 */
@Schema(description = "Данные первого входа")
public record CompleteOnboardingRequestDTO(

        @Schema(description = "Дивизион, за которым игрок закрепляется навсегда", example = "ru")
        @NotNull(message = "Дивизион обязателен.")
        DivisionLanguage divisionLanguage,

        @Schema(description = "Ник: латиница, первый символ — буква, длина 3–20",
                example = "Vasya", pattern = "^[A-Za-z][A-Za-z0-9_]{2,19}$")
        @NotBlank(message = "Ник обязателен.")
        @Nickname
        String nickname,

        @Schema(description = "Аватар как data-URL; можно не задавать — тогда аватара просто нет",
                example = "data:image/webp;base64,UklGRhIAAABXRUJQ",
                pattern = "^data:image/(?:webp|jpeg|png);base64,.*", maxLength = 120000, nullable = true)
        @Size(max = 120000, message = "Аватарка слишком большая.")
        @Pattern(regexp = "^data:image/(?:webp|jpeg|png);base64,.*",
                flags = Pattern.Flag.CASE_INSENSITIVE, message = "Некорректная аватарка.")
        String avatarDataUrl,

        @Schema(description = "Версии правовых документов, которые игрок принял")
        @NotNull(message = "Версии документов обязательны.")
        @Valid
        ConsentVersionsView versions,

        @Schema(description = "Подтверждение совершеннолетия", example = "true", type = "boolean")
        @NotNull(message = "Подтверждение возраста обязательно.")
        Boolean adultConfirmed) {
}
