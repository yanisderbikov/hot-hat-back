package ru.hothat.recording.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Состояние записи одной партии.
 *
 * <p>Заменяет {@code POST /api/recordings} с {@code action=status}
 * ({@code app-core.js:11552}), у которого было две несовместимых формы ответа:
 * {@code {exists:false}} против {@code {exists:true, saved, recording}}.
 * Форма здесь одна, а «записи нет» выражено пустой карточкой — клиенту больше
 * не нужно различать ответы по набору ключей (C6).
 */
@Schema(description = "Состояние записи текущей партии")
public record RecordingStatusResponseDTO(

        @Schema(description = "Запись этой партии заведена. false — комнату не писали "
                + "или ещё не начали", example = "true", type = "boolean")
        boolean exists,

        @Schema(description = "Запись уже лежит в вашей библиотеке: предлагать сохранить её "
                + "второй раз не нужно", example = "false", type = "boolean")
        boolean savedByMe,

        @Schema(description = "Карточка записи; null — записи этой партии нет", nullable = true)
        RecordingCardView recording) {
}
