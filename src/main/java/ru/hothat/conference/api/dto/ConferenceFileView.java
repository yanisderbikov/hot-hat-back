package ru.hothat.conference.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Вложение сообщения.
 *
 * <p>Ссылка подписана на два часа и подписывается заново на каждом чтении
 * ленты: постоянного открытого адреса у вложений видео-чата нет, их видят
 * только участники.
 */
@Schema(description = "Файл, приложенный к сообщению видео-чата")
public record ConferenceFileView(

        @Schema(description = "Имя файла", example = "скрин.png")
        String name,

        @Schema(description = "Тип содержимого", example = "image/png")
        String mime,

        @Schema(description = "Размер в байтах", example = "204800", type = "integer")
        long sizeBytes,

        @Schema(description = "Подписанная ссылка на просмотр; пустая строка — хранилище не настроено",
                example = "https://s3.hot-hat.ru/hot-hat/conference/vc-0f3a9c1d7b2e5480/...")
        String url) {
}
