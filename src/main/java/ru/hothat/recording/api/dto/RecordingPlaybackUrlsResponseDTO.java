package ru.hothat.recording.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Подписанные ссылки на запись.
 *
 * <p>Заменяет {@code POST /api/recordings} с {@code action=urls}.
 *
 * <p>Ссылок две, потому что различаются они не адресом файла, а заголовком
 * {@code Content-Disposition}: по первой браузер проигрывает видео в теге,
 * по второй — предлагает сохранить его файлом с человеческим именем.
 */
@Schema(description = "Ссылки на просмотр и скачивание записи")
public record RecordingPlaybackUrlsResponseDTO(

        @Schema(description = "Запись, на которую выданы ссылки", example = "hat-0f3a9c1d7b2e5480-3")
        String recordingId,

        @Schema(description = "Ссылка для просмотра в плеере",
                example = "https://s3.example.net/hot-hat/game-recordings/hat-0f3a9c1d7b2e5480/game-3.mp4?X-Amz-Signature=…")
        String watchUrl,

        @Schema(description = "Ссылка для скачивания файлом: имя файла уже вшито в подпись",
                example = "https://s3.example.net/hot-hat/game-recordings/hat-0f3a9c1d7b2e5480/game-3.mp4?response-content-disposition=…")
        String downloadUrl,

        @Schema(description = "Когда обе ссылки перестанут работать; сейчас это полчаса от выдачи. "
                + "Время серверное: у клиента часы могут уехать", example = "1788601800000", type = "integer")
        long expiresAtMs) {
}
