package ru.hothat.rating.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Чемпион запрошенного сезона.
 *
 * <p>Заменяет объект {@code champion} из {@code POST /api/portal} с
 * {@code action=ratings}. Отдельный адрес нужен потому, что шапка страницы
 * рейтингов показывает одну строку, а платила за неё двумя полными таблицами.
 */
@Schema(description = "Победитель сезона")
public record SeasonChampionResponseDTO(

        @Schema(description = "Сезон, режим и дивизион, которые сервер в итоге открыл")
        SeasonSelectionView selection,

        @Schema(description = "Чемпион; null — сезон ещё не закрыт или победитель не объявлен",
                nullable = true)
        SeasonChampionView champion) {
}
