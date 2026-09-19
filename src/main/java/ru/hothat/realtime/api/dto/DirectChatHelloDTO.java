package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Первый кадр канала {@code /ws/v2/chat/{peerUid}}.
 *
 * <p>Приезжает сразу после рукопожатия и уже несёт переписку: иначе клиент
 * был бы обязан сходить ещё и по HTTP, чтобы нарисовать хоть что-то, а до
 * ответа показывать пустой диалог.
 *
 * <p>Отдельная запись от кадра обновления, хотя данные те же. Разница не в
 * данных, а в смысле: «канал открыт, вот состояние» и «состояние изменилось» —
 * разные события для интерфейса, и различать их по неявным признакам клиенту
 * не придётся.
 */
@Schema(description = "Кадр открытия канала переписки")
public record DirectChatHelloDTO(

        @Schema(description = "Имя кадра", example = "hello", allowableValues = "hello")
        String type,

        @Schema(description = "Переписка на момент открытия канала")
        DirectChatWindowView chat) {

    /** Имя кадра ставит сервер: у клиента нет причин его выбирать. */
    public DirectChatHelloDTO(DirectChatWindowView chat) {
        this("hello", chat);
    }
}
