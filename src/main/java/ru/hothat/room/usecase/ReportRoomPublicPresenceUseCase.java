package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.PublicPresenceResponseDTO;

import java.util.List;

/**
 * Пересчитать счётчик игроков, которым комната светит в витрину.
 *
 * <p>Заменяет {@code publishPublicRoomPresence()} ({@code app-core.js:11931}),
 * где число считал и записывал браузер хозяина. Считать его должен сервер: путь
 * {@code rooms/*} в проверке прав на запись пуст (находка A1), то есть «сколько
 * людей в комнате» было полем, которое клиент назначал сам, — а главная верит
 * ему на слово и рисует по нему витрину.
 *
 * <p>Тела у запроса нет: присылать серверу число, которое он и так знает,
 * незачем. Вызов остался сигналом «пересчитай», и шлёт его хозяин, потому что
 * ровно один участник должен это делать — иначе десять вкладок писали бы одну
 * строку десять раз в минуту.
 *
 * <p>Тест-боты в счёт не идут: комната из одних ботов выглядела бы в витрине
 * живой. Кроме тестовой комнаты, где, кроме ботов, никого и нет.
 */
@Service
@RequiredArgsConstructor
public class ReportRoomPublicPresenceUseCase {

    private final RoomAccessGuard roomAuthz;
    private final RoomSeats roomSeats;
    private final SaverRoom saverRoom;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public PublicPresenceResponseDTO run(HotHatUser user, String roomId) {
        Room room = roomAuthz.requireHostRoomForWrite(user, roomId);
        long now = System.currentTimeMillis();

        List<RoomPlayer> alive = roomSeats.alivePlayers(roomId, now);
        boolean countBots = Boolean.TRUE.equals(room.getIsTestRoom());
        int visible = (int) alive.stream()
                .filter(player -> countBots || !Boolean.TRUE.equals(player.getIsTestBot()))
                .count();

        room.setPublicActivePlayers(visible);
        room.setPublicPresenceAt(now);
        // Обновление счётчика — это и признак жизни комнаты: без него уборщик
        // считал бы её брошенной, пока в ней идёт долгая партия.
        room.setLastActivityAt(now);
        saverRoom.save(room);
        return new PublicPresenceResponseDTO(roomId, visible, now);
    }
}
