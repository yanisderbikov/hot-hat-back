package ru.hothat.media.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.hothat.media.domain.MemeAssetKey;
import ru.hothat.media.domain.MemeAssetLimits;

/**
 * Заявка на публикацию мема: файлы уже в хранилище, осталась карточка.
 *
 * <p>Заменяет прямую запись документа {@code memeLibrary/{id}} из браузера
 * ({@code app-core.js:6424}), где клиент присылал семнадцать полей, включая
 * автора, адрес ролика, размер, провайдера хранилища и время создания.
 * Здесь клиент говорит только то, чего сервер не знает сам: какой мем, как он
 * называется, сколько идёт, в каком дивизионе показывать и куда именно уехали
 * файлы. Всё остальное сервер ставит сам — автора по токену (A1), адреса по
 * ключам, тип и размер ролика по самому объекту в хранилище. Ссылку на
 * произвольный адрес подсунуть больше нельзя: поля {@code src} в заявке нет.
 *
 * <p>Повтор с тем же {@code memeId} — не ошибка, а та же публикация: сеть
 * могла оборвать ответ уже после записи, а файлы к этому моменту загружены,
 * и заставлять игрока перезаливать ролик из-за потерянного ответа незачем.
 */
@Schema(description = "Запрос на публикацию мема в общей библиотеке")
public record PublishMemeRequestDTO(

        /**
         * Идентификатор придумывает клиент, и иначе быть не может: под него
         * уже выдан билет и по нему построен путь объекта в хранилище.
         * Встроенные ({@code builtin-…}) не принимаются — их заводит посев.
         */
        @Schema(description = "Идентификатор мема, тот же, что в билетах на загрузку",
                example = "meme-9f31ab77c204", pattern = "^meme-[A-Za-z0-9_-]{6,100}$")
        @NotBlank(message = "Нужен идентификатор мема.")
        @Pattern(regexp = "^meme-[A-Za-z0-9_-]{6,100}$", message = "Некорректный идентификатор мема.")
        String memeId,

        @Schema(description = "Название на карточке", example = "BMW — другой не знаю", maxLength = 120)
        @NotBlank(message = "Нужно название мема.")
        @Size(max = 120, message = "Название длиннее 120 символов.")
        String title,

        @Schema(description = "Длительность ролика в миллисекундах: столько же длится диверсия",
                example = "4200", type = "integer", minimum = "100", maximum = "10000")
        @NotNull(message = "Нужна длительность ролика.")
        @Min(value = MemeAssetLimits.MIN_DURATION_MS, message = "Ролик короче 0,1 секунды не проигрывается.")
        @Max(value = MemeAssetLimits.MAX_DURATION_MS, message = "Ролик длиннее 10 секунд не принимаем.")
        Integer durationMs,

        @Schema(description = "Дивизион, в котором мем показывается; можно не задавать — тогда ru",
                example = "ru", allowableValues = {"ru", "en", "de", "es", "fr", "it", "zh", "ja", "kk"},
                nullable = true)
        @Pattern(regexp = "^(?:ru|en|de|es|fr|it|zh|ja|kk)$", message = "Неизвестный дивизион.")
        String divisionLanguage,

        @Schema(description = "Ключ загруженного ролика — тот, что вернул билет на загрузку видео",
                example = "memes/ru/Qk3xZaTb9mNpR2sVuWyA1cEfGhJk/meme-9f31ab77c204/video.webm",
                pattern = MemeAssetKey.PATTERN)
        @NotBlank(message = "Нужен ключ загруженного ролика.")
        @Pattern(regexp = MemeAssetKey.PATTERN, message = "Некорректный путь файла в хранилище.")
        String videoPath,

        @Schema(description = "Ключ загруженной заставки; можно не задавать — карточка обойдётся без неё",
                example = "memes/ru/Qk3xZaTb9mNpR2sVuWyA1cEfGhJk/meme-9f31ab77c204/poster.webp",
                pattern = MemeAssetKey.PATTERN, nullable = true)
        @Pattern(regexp = MemeAssetKey.PATTERN, message = "Некорректный путь заставки в хранилище.")
        String posterPath,

        @Schema(description = "Откуда нарезан ролик; можно не задавать",
                example = "https://www.youtube.com/watch?v=dQw4w9WgXcQ", maxLength = 2048, nullable = true)
        @Size(max = 2048, message = "Ссылка на источник длиннее 2048 символов.")
        @Pattern(regexp = "^https?://\\S+$", message = "Источник должен быть ссылкой http или https.")
        String sourceUrl,

        /**
         * Свободная пометка о способе импорта — её пишет и читает только
         * интерфейс. Предел в 24 знака взят из колонки: сегодня клиент шлёт
         * значения вроде {@code youtube-tab-raw-capture-playing-source} и
         * получает не ошибку запроса, а срыв записи в базу.
         */
        @Schema(description = "Как мем попал в библиотеку: file, record, direct-url и подобное",
                example = "file", maxLength = 24, nullable = true)
        @Size(max = 24, message = "Способ импорта длиннее 24 символов.")
        @Pattern(regexp = "^[a-z0-9-]{1,24}$", message = "Способ импорта записывается латиницей через дефис.")
        String importMode) {
}
