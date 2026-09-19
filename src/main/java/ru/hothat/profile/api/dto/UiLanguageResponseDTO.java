package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Язык интерфейса после переключения.
 *
 * <p>Рядом с ним едет дивизион: интерфейс можно переключить только на свой
 * язык или на английский, поэтому просьба о третьем возвращается языком
 * дивизиона, и клиенту нужно видеть оба значения, чтобы объяснить отказ.
 */
@Schema(description = "Действующий язык интерфейса")
public record UiLanguageResponseDTO(

        @Schema(description = "Язык интерфейса, который теперь сохранён")
        DivisionLanguage uiLanguage,

        @Schema(description = "Дивизион игрока: он ограничивает выбор языка")
        DivisionLanguage divisionLanguage) {
}
