package ru.hothat.rating.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Страница таблицы команд.
 *
 * <p>Заменяет массив {@code teams} из {@code POST /api/portal} с
 * {@code action=ratings}. Раньше одним ответом ехали таблица команд, таблица
 * игроков, чемпион и список команд с друзьями — четыре разных предмета, и
 * страница рейтингов перезапрашивала всё целиком при каждом переключении
 * вкладки ({@code portal.js:149}). Здесь у каждого предмета свой адрес.
 */
@Schema(description = "Таблица команд за сезон")
public record TeamRankingPageResponseDTO(

        @Schema(description = "Сезон, режим и дивизион, которые сервер в итоге открыл")
        SeasonSelectionView selection,

        @Schema(description = "Команды по убыванию очков")
        List<TeamStandingView> items,

        @Schema(description = "Курсор следующей страницы. Всегда null: переходный движок отдаёт "
                + "первую сотню строк и вглубь таблицы не ходит",
                example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Предел, который применили к этой странице", example = "100", type = "integer")
        int limit) {
}
