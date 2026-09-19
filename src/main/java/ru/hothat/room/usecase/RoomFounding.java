package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.model.room.Room;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.domain.RoomPhase;
import ru.hothat.sabotage.spi.SabotageArmoryPort;
import ru.hothat.util.Divisions;
import ru.hothat.util.Ids;
import ru.hothat.util.Json;

import java.time.Instant;
import java.util.List;

/**
 * Общее тело двух способов завести комнату: с главной и из видео-чата.
 *
 * <p>Отдельный класс, а не вызов чужого сценария: сценарию запрещено звать
 * сценарий (§7.2), а строка комнаты и первое место пишутся одной
 * транзакцией по одним и тем же правилам, откуда бы ни пришёл заказ. Две
 * копии этой сборки разошлись бы на первой же правке — как когда-то
 * разошлись три ветки рассадки.
 *
 * <p>Идентификатор, хозяина, фазу, дивизион и счётчик присутствия ставит
 * сервер; заказчик называет только то, что решает сам.
 */
@Component
@RequiredArgsConstructor
public class RoomFounding {

    /** Столько знаков названия помещается в паспорт комнаты. */
    private static final int MAX_NAME = 80;

    /** Обойма мемов принадлежит области диверсий, а не оболочке учётки. */
    private final SabotageArmoryPort armory;
    /** Имя, аватар и дивизион — у карточки игрока, а не в оболочке учётки. */
    private final PlayerCardPort cards;
    private final SaverRoom saverRoom;
    private final RoomSeats roomSeats;

    /**
     * Заказ на комнату.
     *
     * @param gameLanguage язык слов; {@code null} — дивизион хозяина
     * @param testRoom     тестовая комната: право на неё проверяет вызывающий
     */
    public record Order(String hostUid, String name, int capacity, boolean privateRoom,
                        String gameMode, String gameLanguage, boolean testRoom) {
    }

    /** Заведённая комната и место хозяина в ней — одной транзакцией. */
    public record Founded(Room room, RoomSeats.Seated seat, long nowMs) {
    }

    /**
     * Завести комнату и посадить хозяина.
     *
     * <p>Пять заряженных мемов обязательны до создания, а не при старте
     * партии: хозяин без обоймы завёл бы комнату, собрал в неё людей и упёрся
     * бы в отказ на старте — вместе со всеми, кого позвал.
     */
    public Founded found(Order order) {
        List<String> loadout = armory.requireLoadout(order.hostUid());
        PlayerCardPort.Card card = cards.card(order.hostUid()).orElse(null);

        String division = Divisions.normalize(card == null ? null : card.divisionLanguage());
        String gameLanguage = order.gameLanguage() == null ? division : order.gameLanguage();
        long now = System.currentTimeMillis();

        Room room = Room.builder()
                .id(Ids.newRoomId())
                .name(Json.str(order.name(), MAX_NAME))
                .phase(RoomPhase.SETUP.wireValue())
                .createdBy(order.hostUid())
                .hostLastSetupActivityAt(now)
                .gameMode(order.gameMode())
                .isPrivate(order.privateRoom())
                .isTestRoom(order.testRoom())
                .testOwnerUid(order.testRoom() ? order.hostUid() : null)
                .divisionLanguage(division)
                .gameLanguage(Divisions.normalize(gameLanguage))
                .maxPlayers(order.capacity())
                .maxParticipants(order.capacity())
                // Хозяин уже за столом: счётчик витрины стартует с единицы,
                // иначе комната полминуты выглядела бы пустой.
                .publicActivePlayers(1)
                .publicPresenceAt(now)
                .lastActivityAt(now)
                .createdAt(Instant.now())
                .build();
        if (room.getName().isBlank()) {
            // Безымянная комната подписывается хвостом идентификатора здесь, а
            // не на экране: то же имя уезжает в приглашение и в витрину.
            room.setName(RoomProjections.displayName(room));
        }
        saverRoom.save(room);

        RoomSeats.Seated seated = roomSeats.seatPlayer(room, order.hostUid(), card, loadout,
                RoomSeats.Origin.self());
        return new Founded(room, seated, now);
    }
}
