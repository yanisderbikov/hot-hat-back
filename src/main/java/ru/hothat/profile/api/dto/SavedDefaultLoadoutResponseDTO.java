package ru.hothat.profile.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Обойма после сохранения — ровно в том виде, в каком её теперь читает игра.
 *
 * <p>Отвечать сохранённым составом, а не пустым 204, здесь обязательно:
 * сервер обойму нормализует (выбрасывает повторы, обрезает лишнее), и без
 * ответа клиент рисовал бы у себя не то, что лежит на сервере. Готовность
 * называет та же проекция {@link MemeLoadoutStatusView}, что и при чтении.
 */
@Schema(description = "Сохранённая обойма мемов")
public record SavedDefaultLoadoutResponseDTO(

        @Schema(description = "Что в итоге сохранено, по порядку слотов",
                example = "[\"meme-1a2b3c4d5e6f\",\"builtin-bmw-drugoy-ne-znayu\"]")
        List<String> memeIds,

        @Schema(description = "Насколько обойма собрана после сохранения")
        MemeLoadoutStatusView status) {
}
