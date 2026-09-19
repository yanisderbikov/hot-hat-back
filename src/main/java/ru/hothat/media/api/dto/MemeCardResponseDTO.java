package ru.hothat.media.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Один мем.
 *
 * <p>Заменяет точечное чтение {@code memeLibrary/{id}} мимо кеша
 * ({@code app-core.js:5184}): диверсию заказали мемом, которого нет в
 * загруженной библиотеке, и до выстрела остаются секунды.
 */
@Schema(description = "Карточка одного мема")
public record MemeCardResponseDTO(

        @Schema(description = "Запрошенный мем")
        MemeCardView meme) {
}
