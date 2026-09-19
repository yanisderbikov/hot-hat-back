package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Куда сервис складывает мем-видео и записи партий.
 *
 * <p>Заменяет {@code POST /api/media} с {@code action=config_status}. Прежний
 * адрес лежал в {@code permitAll} (A12) и отвечал 503 всем подряд, когда
 * хранилище не настроено; теперь имя бакета и адрес узла видит только
 * администратор, а «не настроено» — это ответ 200 с {@code configured: false},
 * а не ошибка: вопрос был задан именно об этом.
 */
@Schema(description = "Настройки файлового хранилища")
public record ObjectStorageConfigResponseDTO(

        @Schema(description = "Хранилище настроено и им можно пользоваться", example = "true",
                type = "boolean")
        boolean configured,

        @Schema(description = "Тип хранилища; null — оно не настроено", example = "s3-compatible",
                nullable = true)
        String provider,

        @Schema(description = "Бакет; null — хранилище не настроено", example = "hot-hat-media",
                nullable = true)
        String bucket,

        @Schema(description = "Адрес узла хранилища; null — оно не настроено",
                example = "https://s3.example.net", nullable = true)
        String endpoint) {
}
