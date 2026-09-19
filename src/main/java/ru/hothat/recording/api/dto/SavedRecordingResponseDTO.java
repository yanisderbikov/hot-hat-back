package ru.hothat.recording.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Итог сохранения записи в личную библиотеку.
 *
 * <p>Заменяет {@code POST /api/recordings} с {@code action=save}
 * ({@code app-core.js:14731}), отвечавший {@code {ok:true, saved:true}} —
 * то есть повторявший запрос и ничего не сообщавший о самой записи. Здесь
 * возвращается карточка: у сохранённой записи пропадает срок хранения и
 * растёт число сохранивших, и клиенту незачем перечитывать библиотеку целиком,
 * чтобы это увидеть.
 */
@Schema(description = "Запись после сохранения в библиотеку")
public record SavedRecordingResponseDTO(

        @Schema(description = "Запись лежит в вашей библиотеке. Всегда true: повторное сохранение "
                + "той же записи не ошибка, а тот же самый итог", example = "true", type = "boolean")
        boolean savedByMe,

        @Schema(description = "Карточка записи уже с новым состоянием")
        RecordingCardView recording) {
}
