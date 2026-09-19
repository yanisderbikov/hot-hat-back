package ru.hothat.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Сжатая для мобильных версия ролика, присланная админкой.
 *
 * <p>Проверки формы переехали в аннотации: раньше «не видео», «слишком
 * большое» и «нулевая длительность» были тремя ручными {@code if} внутри
 * сервиса, причём два первых отвечали 400 без текста
 * ({@code MediaServiceImpl:398-410}). Здесь неверное тело не доходит до
 * сценария вовсе.
 *
 * <p>Длительность теперь отвергается, а не подрезается молча. Прежний движок
 * зажимал её в отрезок и сохранял: попросив пятнадцать секунд, администратор
 * получал десять и не узнавал об этом.
 *
 * <p>{@code @JsonAlias} оставлен под старые имена в змеином регистре: их шлёт
 * сегодняшняя админка ({@code app-core.js:5739}), и ломать её раньше времени
 * незачем.
 */
@Schema(description = "Оптимизированная версия мема")
public record SaveOptimizedMemeRequestDTO(

        /**
         * Ролик приезжает data-URL, а не файлом: сжимает его сам браузер
         * администратора, и отдельная загрузка в хранилище тут ни при чём.
         * Потолок в 760 000 знаков — тот же, что стоял в движке.
         */
        @Schema(description = "Ролик как data-URL; только видео",
                example = "data:video/webm;base64,GkXfo59ChoEB…")
        @NotBlank(message = "Не приложено видео.")
        @JsonAlias("data_url")
        @Pattern(regexp = "^data:video/[A-Za-z0-9.+-]+;base64,[A-Za-z0-9+/=]+$",
                message = "Ожидается видео в виде data-URL.")
        @Size(max = 760000, message = "Слишком большое видео: предел 760 000 знаков.")
        String dataUrl,

        @Schema(description = "Тип содержимого ролика; не задан — video/webm", example = "video/webm",
                nullable = true)
        @Size(max = 100, message = "Слишком длинный тип содержимого.")
        String mime,

        /**
         * Постеру дан тот же потолок, что и ролику: это один его кадр, и
         * весить больше самого ролика он не может по смыслу.
         */
        @Schema(description = "Постер как data-URL или ссылка; можно не задавать", nullable = true,
                example = "data:image/webp;base64,UklGRl…")
        @Size(max = 760000, message = "Слишком большой постер: предел 760 000 знаков.")
        String poster,

        @Schema(description = "Длительность ролика в миллисекундах", example = "5000", type = "integer")
        @NotNull(message = "Не указана длительность ролика.")
        @JsonAlias("duration_ms")
        @Min(value = 100, message = "Слишком короткий ролик: минимум 100 мс.")
        @Max(value = 10000, message = "Слишком длинный ролик: максимум 10 секунд.")
        Integer durationMs,

        @Schema(description = "Метка версии оптимизатора; не задана — mobile-v1", example = "mobile-v1",
                nullable = true)
        @JsonAlias("optimized_version")
        @Size(max = 40, message = "Слишком длинная метка версии.")
        String optimizedVersion) {
}
