package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Кадр обновления канала {@code /ws/v2/lobby}.
 *
 * <p>Уходит после фиксации транзакции, изменившей комнату, и не чаще, чем раз
 * в две секунды. Ограничение намеренное: строка комнаты пишется на каждом
 * отгаданном слове, а витрина от этого не меняется — без склейки каждое
 * нажатие «угадал» в любой комнате перестраивало бы список всем, кто открыл
 * главную.
 */
@Schema(description = "Кадр изменения витрины")
public record LobbyChannelEventDTO(

        @Schema(description = "Имя кадра", example = "rooms", allowableValues = "rooms")
        String type,

        @Schema(description = "Витрина после изменения")
        LobbyRoomsWindowView rooms) {

    /** Имя кадра ставит сервер: у клиента нет причин его выбирать. */
    public LobbyChannelEventDTO(LobbyRoomsWindowView rooms) {
        this("rooms", rooms);
    }
}
