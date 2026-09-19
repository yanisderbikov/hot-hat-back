package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Заменить аватар целиком. */
@Schema(description = "Запрос на замену аватара")
public record ReplaceAvatarRequestDTO(

        /**
         * Порог 120000 символов и три допустимых формата перенесены из старого
         * сервиса дословно: картинка лежит прямо в строке таблицы, и предел
         * длины — единственное, что удерживает размер строки профиля.
         */
        @Schema(description = "Картинка как data-URL: webp, jpeg или png, не длиннее 120000 символов",
                example = "data:image/webp;base64,UklGRhIAAABXRUJQ",
                pattern = "^data:image/(?:webp|jpeg|png);base64,.*", maxLength = 120000)
        @NotBlank(message = "Аватарка обязательна.")
        @Size(max = 120000, message = "Аватарка слишком большая.")
        @Pattern(regexp = "^data:image/(?:webp|jpeg|png);base64,.*",
                flags = Pattern.Flag.CASE_INSENSITIVE, message = "Некорректная аватарка.")
        String avatarDataUrl) {
}
