package ru.hothat.media.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Страница библиотеки мемов.
 *
 * <p>Заменяет запрос коллекции {@code memeLibrary} через документный шлюз и
 * живую подписку на неё ({@code app-core.js:5969}). Живые изменения переезжают
 * в канал {@code /ws/v2/media/memes} — отдельно, потому что первая загрузка и
 * последующие правки нужны экрану в разное время и в разном объёме.
 */
@Schema(description = "Страница общей библиотеки мемов")
public record MemeCatalogPageResponseDTO(

        @Schema(description = "Карточки мемов; снятые с публикации сюда не попадают")
        List<MemeCardView> items,

        @Schema(description = "Ключ следующей страницы; сейчас всегда null — хранилище отдаёт "
                + "первую страницу и не умеет продолжать", example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Предел, который применили к этой странице", example = "60", type = "integer")
        int limit) {
}
