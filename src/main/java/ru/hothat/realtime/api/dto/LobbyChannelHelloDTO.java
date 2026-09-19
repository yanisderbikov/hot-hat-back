package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Первый кадр канала {@code /ws/v2/lobby}.
 *
 * <p>Приезжает сразу после рукопожатия и уже несёт витрину: иначе главная
 * была бы обязана сходить ещё и по HTTP, чтобы нарисовать хоть что-то, и до
 * ответа показывать пустой список.
 *
 * <p>Отдельная запись от кадра обновления, хотя данные те же. Разница не в
 * данных, а в смысле: «канал открыт, вот состояние» и «состояние изменилось» —
 * разные события для интерфейса.
 */
@Schema(description = "Кадр открытия канала лобби")
public record LobbyChannelHelloDTO(

        @Schema(description = "Имя кадра", example = "hello", allowableValues = "hello")
        String type,

        @Schema(description = "Витрина на момент открытия канала")
        LobbyRoomsWindowView rooms) {

    /** Имя кадра ставит сервер: у клиента нет причин его выбирать. */
    public LobbyChannelHelloDTO(LobbyRoomsWindowView rooms) {
        this("hello", rooms);
    }
}
