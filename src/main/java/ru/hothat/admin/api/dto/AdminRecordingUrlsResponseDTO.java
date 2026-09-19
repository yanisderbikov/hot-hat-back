package ru.hothat.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Подписанные ссылки на запись, выданные администратору.
 *
 * <p>Отдельный адрес и отдельная пара DTO, потому что различается не форма, а
 * право: игроку ссылку дают, если он запись сохранил или ею с ним поделились,
 * администратору — на любую. Раньше это была одна операция с булевым
 * {@code admin} в сигнатуре сервиса, и оба правила жили в одном {@code if}.
 */
@Schema(description = "Ссылки на просмотр и скачивание записи для администратора")
public record AdminRecordingUrlsResponseDTO(

        @Schema(description = "Запись, на которую выданы ссылки", example = "hat-0f3a9c1d7b2e5480-3")
        String recordingId,

        @Schema(description = "Ссылка для просмотра в плеере",
                example = "https://s3.example.net/hot-hat/game-recordings/hat-0f3a9c1d7b2e5480/game-3.mp4?X-Amz-Signature=…")
        String watchUrl,

        @Schema(description = "Ссылка для скачивания файлом", 
                example = "https://s3.example.net/hot-hat/game-recordings/hat-0f3a9c1d7b2e5480/game-3.mp4?response-content-disposition=…")
        String downloadUrl,

        @Schema(description = "Когда обе ссылки перестанут работать — полчаса от выдачи, время серверное",
                example = "1788601800000", type = "integer")
        long expiresAtMs) {
}
