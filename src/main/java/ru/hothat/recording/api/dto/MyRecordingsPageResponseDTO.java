package ru.hothat.recording.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Страница личной библиотеки записей.
 *
 * <p>Заменяет {@code POST /api/recordings} с {@code action=list_mine}
 * ({@code portal.js:55}), отдававший голый массив под ключом
 * {@code recordings}.
 */
@Schema(description = "Страница личной библиотеки записей")
public record MyRecordingsPageResponseDTO(

        @Schema(description = "Записи от свежих к старым: сначала те, что закончились позже")
        List<RecordingCardView> items,

        @Schema(description = "Курсор следующей страницы. Всегда null: движок отдаёт "
                + "сохранённые записи одним куском и вглубь не ходит", example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Предел, который применили к этой странице", example = "50", type = "integer")
        int limit) {
}
