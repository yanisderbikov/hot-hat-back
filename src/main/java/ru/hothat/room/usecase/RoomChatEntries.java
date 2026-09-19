package ru.hothat.room.usecase;

import ru.hothat.model.room.RoomChatMessage;
import ru.hothat.util.Ids;
import ru.hothat.util.Json;

import java.time.Instant;

/**
 * Заготовка строки чата комнаты.
 *
 * <p>Одно место на текст и на фотографию: у них общие автор, имя-снимок,
 * место и отметка времени, и различаются они ровно телом. Пока заготовку
 * собирал каждый сценарий сам, разъезжались мелочи — у одного отправителя
 * подпись обрезалась до сорока знаков, у другого нет.
 *
 * <p>Идентификатор ставит сервер. Раньше его придумывал браузер
 * ({@code makeId("chat")}), то есть отправитель мог назначить сообщению любой
 * ключ — в том числе чужой, уже занятый, и тогда правка выглядела бы как
 * подмена чужой реплики.
 */
final class RoomChatEntries {

    /** Столько знаков подписи помещается в ленте. */
    private static final int MAX_NAME_LENGTH = 40;

    private RoomChatEntries() {
    }

    /** Общая часть строки: всё, кроме тела сообщения. */
    static RoomChatMessage.RoomChatMessageBuilder newMessage(String roomId, RoomAccessGuard.Seat seat) {
        long now = System.currentTimeMillis();
        return RoomChatMessage.builder()
                .roomId(roomId)
                .id("chat-" + Ids.hex(8))
                .uid(seat.uid())
                .name(Json.str(seat.name(), MAX_NAME_LENGTH))
                .role(seat.kind().wireValue())
                .isTestBot(false)
                .createdAt(Instant.now())
                .createdAtMs(now);
    }
}
