package ru.hothat.rating.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Страница таблицы игроков.
 *
 * <p>Заменяет массив {@code players} из {@code POST /api/portal} с
 * {@code action=ratings}.
 *
 * <p>Таблица может быть выведена из командной: пока в сезоне не зачли ни одной
 * партии, личных строк ещё нет, и сервер собирает их из очков команд, чтобы
 * вкладка не выглядела пустой у людей, которые уже сыграли. У таких строк не
 * заполнена команда — см. {@link PlayerStandingView}.
 */
@Schema(description = "Таблица игроков за сезон")
public record PlayerRankingPageResponseDTO(

        @Schema(description = "Сезон, режим и дивизион, которые сервер в итоге открыл")
        SeasonSelectionView selection,

        @Schema(description = "Игроки по убыванию очков")
        List<PlayerStandingView> items,

        @Schema(description = "Курсор следующей страницы. Всегда null: переходный движок отдаёт "
                + "первую сотню строк и вглубь таблицы не ходит",
                example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Предел, который применили к этой странице", example = "100", type = "integer")
        int limit) {
}
