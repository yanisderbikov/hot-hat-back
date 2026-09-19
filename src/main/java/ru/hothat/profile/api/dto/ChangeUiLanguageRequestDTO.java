package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Переключить язык интерфейса. */
@Schema(description = "Запрос на смену языка интерфейса")
public record ChangeUiLanguageRequestDTO(

        /**
         * Выбор ограничен языком своего дивизиона и английским — это правило
         * предметной области, оно остаётся на сервере: прислать сюда можно
         * любой из девяти кодов, а вернётся допустимый.
         */
        @Schema(description = "Желаемый язык интерфейса", example = "en")
        @NotNull(message = "Язык интерфейса обязателен.")
        DivisionLanguage uiLanguage) {
}
