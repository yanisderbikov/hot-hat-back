package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Подсказка дивизиона для экрана регистрации.
 *
 * <p>Именно подсказка: значение подставляется в поле выбора, но закрепляет
 * дивизион только явное действие игрока. Страну определяет обратный
 * прокси-сервер, а не браузер, поэтому подсказка приходит с сервера.
 */
@Schema(description = "Предполагаемый дивизион по стране запроса")
public record DivisionSuggestionResponseDTO(

        @Schema(description = "Код страны из заголовка прокси-сервера; null — страна неизвестна",
                example = "RU", pattern = "^[A-Z]{2}$", nullable = true)
        String countryHint,

        @Schema(description = "Дивизион, предлагаемый по стране; при неизвестной стране — английский")
        DivisionLanguage suggestedDivision) {
}
