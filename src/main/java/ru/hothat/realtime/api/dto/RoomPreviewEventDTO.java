package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.lobby.api.dto.LobbyRoomPreviewResponseDTO;

/**
 * Кадр превью выбранной комнаты в канале лобби.
 *
 * <p>Приходит дважды: сразу в ответ на {@link SpotlightFrameDTO} и затем при
 * каждом изменении показываемой комнаты.
 *
 * <p>Внутри — <b>тело ответа</b> {@code GET /api/v2/lobby/rooms/{roomId}}, а
 * не его копия. Это единственное место во всех семи каналах, где вложен
 * {@code …ResponseDTO}, и выбор сделан сознательно: у превью шестнадцать
 * плоских полей, и запись-двойник рядом с оригиналом разъехалась бы с ним при
 * первой же правке. Ровно от такого расхождения канал и лечит — в переписке
 * два источника одного списка с разной формой стоили фронту двух веток
 * отрисовки. Когда область лобби выделит из своего ответа проекцию
 * {@code …View}, здесь изменится один тип.
 *
 * <p>Текущего слова в превью нет и быть не может: наружу выходит только уже
 * отгаданное. Это держит форма ответа, а не добрая воля клиента.
 */
@Schema(description = "Кадр превью комнаты")
public record RoomPreviewEventDTO(

        @Schema(description = "Имя кадра", example = "preview", allowableValues = "preview")
        String type,

        @Schema(description = "Комната крупным планом; ровно то же тело, что у "
                + "GET /api/v2/lobby/rooms/{roomId}")
        LobbyRoomPreviewResponseDTO room) {

    /** Имя кадра ставит сервер: у клиента нет причин его выбирать. */
    public RoomPreviewEventDTO(LobbyRoomPreviewResponseDTO room) {
        this("preview", room);
    }
}
