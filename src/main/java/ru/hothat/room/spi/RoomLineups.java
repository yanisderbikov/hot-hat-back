package ru.hothat.room.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.room.usecase.RoomFounding;
import ru.hothat.team.api.dto.GameMode;

/**
 * Комнаты под готовый состав: реализация {@link RoomLineupPort}.
 *
 * <p>Своих правил здесь нет: сборку комнаты делает {@link RoomFounding}, та
 * же, что у кнопки «Создать игру». Приватность и язык партии заданы
 * заказом: видео-чат собирают по дружбе, а не по дивизиону, и комната из
 * него намеренно межъязыковая — как любая приватная.
 */
@Service
@RequiredArgsConstructor
public class RoomLineups implements RoomLineupPort {

    private final RoomFounding founding;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String openPrivateRoom(PrivateRoomOrder order) {
        return founding.found(new RoomFounding.Order(
                order.hostUid(), order.name(), order.capacity(), true,
                GameMode.CLASSIC.wireValue(), null, false)).room().getId();
    }
}
