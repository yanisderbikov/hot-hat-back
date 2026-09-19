package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.media.api.dto.MemeCardView;

import java.util.List;

/**
 * Библиотека мемов — та же форма, что у {@code GET /api/v2/media/memes}.
 *
 * <p>Общая проекция обоих кадров канала. Карточка — тот же
 * {@link MemeCardView}, что отдаёт адрес HTTP, вместе с признаком «мой»:
 * экран арсенала рисует сетку одним кодом, откуда бы она ни приехала.
 *
 * <p>Снимок целиком, а не дельта: библиотека умещается в одну страницу, а
 * перечитывание надёжнее склейки приращений.
 */
@Schema(description = "Общая библиотека мемов")
public record MemeLibraryWindowView(

        @Schema(description = "Карточки мемов; снятые с публикации сюда не попадают")
        List<MemeCardView> items,

        @Schema(description = "Ключ следующей страницы; сейчас всегда null", example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Предел, который применили к этой странице", example = "60", type = "integer")
        int limit) {
}
