package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Итог сверки библиотеки мемов с бакетом.
 *
 * <p>Заменяет {@code POST /api/meme-library-sync}, доступный сегодня любому
 * вошедшему: сверка перебирает две тысячи объектов хранилища и переписывает
 * строки библиотеки — это работа администратора, а не читателя каталога.
 *
 * <p>Сверка нужна после сбоев загрузки, когда файл уже уехал в хранилище, а
 * метаданные записать не успели.
 */
@Schema(description = "Итог сверки библиотеки мемов с хранилищем")
public record MemeLibraryReconciliationResponseDTO(

        @Schema(description = "Сколько видео нашлось в хранилище", example = "184", type = "integer")
        int storageVideos,

        @Schema(description = "Сколько записей библиотеки создано заново по файлам", example = "3",
                type = "integer")
        int recovered,

        @Schema(description = "Сколько записей уже было в библиотеке", example = "181", type = "integer")
        int existing,

        @Schema(description = "Сколько объектов хранилища просмотрено", example = "372", type = "integer")
        int scannedObjects) {
}
