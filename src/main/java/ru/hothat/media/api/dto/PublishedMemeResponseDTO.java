package ru.hothat.media.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Опубликованный мем.
 *
 * <p>Отдаётся целиком, а не одним идентификатором: половину полей карточки
 * заполнил сервер (адреса файлов, тип и размер ролика, автор, время), и без
 * ответа клиенту пришлось бы перечитывать библиотеку, чтобы показать
 * только что добавленный мем.
 */
@Schema(description = "Карточка мема после публикации")
public record PublishedMemeResponseDTO(

        @Schema(description = "Опубликованный мем")
        MemeCardView meme) {
}
