package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Один языковой дивизион справочника.
 *
 * <p>Общая проекция каталога: те же поля описывают дивизион и в списке,
 * и в подсказке по стране.
 */
@Schema(description = "Языковой дивизион")
public record DivisionView(

        @Schema(description = "Код дивизиона")
        DivisionLanguage code,

        @Schema(description = "Флаг для подписи", example = "🇷🇺")
        String flag,

        @Schema(description = "Локаль для форматирования дат и чисел", example = "ru-RU")
        String locale,

        @Schema(description = "Название языка на нём самом", example = "Русский")
        String name,

        @Schema(description = "Название дивизиона на его языке", example = "Русский дивизион")
        String divisionName) {
}
