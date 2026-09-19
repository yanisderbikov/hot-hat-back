package ru.hothat.media.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Карточка мема так, как её видит игрок.
 *
 * <p>Общая проекция: включается полем в ответы каталога, чтения одного мема и
 * публикации. Форма описана один раз — обёртки разные, данные одни.
 *
 * <p>Ролик и заставка названы дважды: адресом ({@code videoUrl}) и ключом в
 * хранилище ({@code videoPath}). Это не дубль. По адресу видео играется
 * прямо, а по ключу берётся подписанная ссылка
 * ({@code POST /api/v2/media/tickets/playbacks}), которую браузер кеширует
 * два часа вместе с range-запросами; ради этого клиент и держит ключ.
 */
@Schema(description = "Мем-ролик в общей библиотеке")
public record MemeCardView(

        @Schema(description = "Идентификатор мема", example = "meme-9f31ab77c204",
                pattern = "^(?:meme|builtin)-[A-Za-z0-9_-]{6,100}$")
        String id,

        @Schema(description = "Название, которое видно на карточке", example = "BMW — другой не знаю")
        String title,

        @Schema(description = "Длительность ролика в миллисекундах", example = "4200", type = "integer")
        int durationMs,

        /**
         * Может быть пустым у старых строк, чей ролик лежит прямо в базе
         * (поле {@code dataUrl} прежней модели). Наружу такие байты не
         * отдаются: страница библиотеки весила бы десятки мегабайт.
         */
        @Schema(description = "Постоянный адрес ролика; null — у легаси-строки файла в хранилище нет",
                example = "https://api.hot-hat.ru/api/v2/media/files/memes/ru/Qk3xZaTb/meme-9f31ab77c204/video.webm",
                nullable = true)
        String videoUrl,

        @Schema(description = "Ключ ролика в хранилище — с ним просят билет на воспроизведение; null у легаси-строки",
                example = "memes/ru/Qk3xZaTb9mNpR2sVuWyA1cEfGhJk/meme-9f31ab77c204/video.webm", nullable = true)
        String videoPath,

        @Schema(description = "Постоянный адрес заставки; null — заставки нет",
                example = "https://api.hot-hat.ru/api/v2/media/files/memes/ru/Qk3xZaTb/meme-9f31ab77c204/poster.webp",
                nullable = true)
        String posterUrl,

        @Schema(description = "Ключ заставки в хранилище; null — заставки нет",
                example = "memes/ru/Qk3xZaTb9mNpR2sVuWyA1cEfGhJk/meme-9f31ab77c204/poster.webp", nullable = true)
        String posterPath,

        @Schema(description = "Тип содержимого ролика", example = "video/webm",
                allowableValues = {"video/webm", "video/mp4", "video/ogg"}, nullable = true)
        String mime,

        @Schema(description = "Размер ролика в байтах", example = "3145728", type = "integer")
        long byteSize,

        @Schema(description = "Кто выложил мем", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String ownerUid,

        @Schema(description = "Как показать имя выложившего", example = "Vasya")
        String ownerName,

        @Schema(description = "Дивизион, в котором мем показывается", example = "ru",
                allowableValues = {"ru", "en", "de", "es", "fr", "it", "zh", "ja", "kk"})
        String divisionLanguage,

        @Schema(description = "Встроенный мем: заведён посевом, снять его нельзя", example = "false",
                type = "boolean")
        boolean builtin,

        @Schema(description = "Откуда нарезан ролик, если он пришёл по ссылке; null — источника не было",
                example = "https://www.youtube.com/watch?v=dQw4w9WgXcQ", nullable = true)
        String sourceUrl,

        @Schema(description = "Как мем попал в библиотеку", example = "file", maxLength = 24, nullable = true)
        String importMode,

        @Schema(description = "Когда мем появился в библиотеке, миллисекунды эпохи",
                example = "1788600000000", type = "integer")
        long createdAtMs,

        /**
         * Считается для того, кто спрашивает: снять мем вправе только автор.
         * Без этого поля интерфейсу пришлось бы сравнивать uid'ы самому — и
         * он уже это делал, но по признаку администратора, отчего кнопки
         * «Удалить» не было у автора вовсе.
         */
        @Schema(description = "Ваш ли это мем: только автор может снять его с публикации",
                example = "true", type = "boolean")
        boolean mine) {
}
