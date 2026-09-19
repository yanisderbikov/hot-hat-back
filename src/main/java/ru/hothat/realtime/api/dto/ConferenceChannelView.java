package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.conference.api.dto.ConferenceMessageView;
import ru.hothat.conference.api.dto.ConferenceView;

import java.util.List;

/**
 * Видео-чат и его лента — то, что канал {@code /ws/v2/conference/{id}}
 * показывает участнику прямо сейчас.
 *
 * <p>Общая проекция обоих кадров: приветственного и обновляющего. Состав —
 * тот же {@link ConferenceView}, что отдаёт {@code GET /api/v2/conference/{id}},
 * лента — те же {@link ConferenceMessageView}, что у
 * {@code GET /api/v2/conference/{id}/messages}. Экран созвона кормится одной
 * формой, откуда бы она ни приехала.
 *
 * <p>Снимок целиком, а не дельта: окно ленты короткое, а перечитывание
 * надёжнее склейки приращений. Заведённая из видео-чата комната приезжает
 * тем же кадром — так участники узнают, что пора переходить в игру, без
 * отдельной рассылки.
 */
@Schema(description = "Видео-чат и лента для слушателя канала")
public record ConferenceChannelView(

        @Schema(description = "Состав, приглашённые и заведённая комната")
        ConferenceView conference,

        @Schema(description = "Сообщения от старых к новым — в порядке показа")
        List<ConferenceMessageView> messages,

        @Schema(description = "Сколько последних сообщений держит окно", example = "120", type = "integer")
        int limit) {
}
