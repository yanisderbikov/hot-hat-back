package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Страница админского каталога записей.
 *
 * <p>Заменяет {@code POST /api/recordings} с {@code action=admin_list} — ветку
 * администратора внутри общего {@code case}, где право проверялось прямо в
 * аргументе вызова ({@code RecordingsController:51}).
 */
@Schema(description = "Каталог записей администратора")
public record AdminRecordingsPageResponseDTO(

        @Schema(description = "Записи, свежие сверху")
        List<AdminRecordingCardView> items,

        @Schema(description = "Ключ следующей страницы; сейчас всегда null — движок отдаёт "
                + "первую страницу и не умеет продолжать", example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Предел, который применили к этой странице", example = "50", type = "integer")
        int limit) {
}
