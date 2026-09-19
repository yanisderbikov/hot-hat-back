package ru.hothat.conference.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Одно сообщение чата видео-чата так, как его показывают участникам.
 *
 * <p>Общая проекция: та же у ответа отправки, у истории и у кадра канала.
 */
@Schema(description = "Сообщение чата видео-чата")
public record ConferenceMessageView(

        @Schema(description = "Идентификатор сообщения: по нему клиент отличает своё эхо от нового",
                example = "184203", type = "integer")
        long id,

        @Schema(description = "Кто отправил", example = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk")
        String fromUid,

        @Schema(description = "Ник отправителя сейчас", example = "Vasya")
        String fromNickname,

        @Schema(description = "Аватар отправителя как data-URL; null — аватара нет", nullable = true)
        String fromAvatarDataUrl,

        @Schema(description = "Текст; null — сообщение состоит из одного файла", example = "Погнали!",
                nullable = true)
        String text,

        @Schema(description = "Вложение; null — сообщение без файла", nullable = true)
        ConferenceFileView file,

        @Schema(description = "Момент отправки, миллисекунды эпохи", example = "1788600000000", type = "integer")
        long createdAtMs) {
}
