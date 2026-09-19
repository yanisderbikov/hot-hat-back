package ru.hothat.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Запись игры, которой поделились в переписке.
 *
 * <p>Общая проекция: включается полем в ответы разных операций. Название и
 * длительность сервер копирует в сообщение в момент отправки — иначе
 * собеседник, у которого нет прав на саму запись, увидел бы пустую карточку.
 */
@Schema(description = "Вложенная запись игры")
public record ChatRecordingAttachmentView(

        @Schema(description = "Идентификатор записи: по нему берутся ссылки на просмотр и скачивание",
                example = "hat-0f3a9c1d7b2e5480-3")
        String recordingId,

        @Schema(description = "Подпись карточки: название комнаты, а если его нет — её идентификатор",
                example = "Пятничная шляпа")
        String title,

        @Schema(description = "Длительность записи в наносекундах", example = "912000000000", type = "integer")
        long durationNs,

        @Schema(description = "Язык игры, на котором сделана запись", example = "ru")
        String gameLanguage) {
}
