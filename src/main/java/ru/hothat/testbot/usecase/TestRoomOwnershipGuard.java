package ru.hothat.testbot.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.common.api.ErrorCode;
import ru.hothat.config.HotHatUser;
import ru.hothat.room.spi.RoomDirectoryPort;

/**
 * Тестовая комната — своя, а не любая.
 *
 * <p>Условие «комната помечена тестовой и её завёл именно этот админ» живёт
 * у владельца комнаты — {@link RoomDirectoryPort.RoomBrief#ownedTestRoomOf}, —
 * а не копией здесь. Копия здесь была, вместе с падением на {@code createdBy}
 * при пустом {@code testOwnerUid}, и совпадать с оригиналом внутри движка
 * ботов ей полагалось на честном слове: два разных ответа на один вопрос
 * развели бы права по разным адресам одного класса.
 *
 * <p>Одной роли {@code ADMIN} здесь мало. Админов больше одного, тестовых
 * комнат тоже, и без этой проверки любой админ дотягивался бы до чужой
 * тестовой комнаты.
 *
 * <p>Сущность комнаты наружу не выходит: у проверки нет результата, только
 * отказ.
 */
@Component
@RequiredArgsConstructor
public class TestRoomOwnershipGuard {

    private final RoomDirectoryPort rooms;

    public void requireOwnedTestRoom(HotHatUser admin, String roomId) {
        RoomDirectoryPort.RoomBrief room = rooms.find(roomId).orElseThrow(ErrorCode.ROOM_NOT_FOUND::raise);
        if (!room.ownedTestRoomOf(admin.uid())) {
            throw ErrorCode.TEST_ROOM_ONLY.raise();
        }
    }
}
