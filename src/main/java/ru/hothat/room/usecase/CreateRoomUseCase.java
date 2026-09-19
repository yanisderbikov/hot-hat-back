package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.CreateRoomRequestDTO;
import ru.hothat.room.api.dto.CreatedRoomResponseDTO;
import ru.hothat.room.domain.RoomPhase;
import ru.hothat.sabotage.spi.SabotageArmoryPort;
import ru.hothat.util.Divisions;
import ru.hothat.util.Ids;
import ru.hothat.util.Json;

import java.time.Instant;
import java.util.List;

/**
 * Завести комнату и сесть в неё хозяином.
 *
 * <p>Комната и первое место пишутся одной транзакцией — это строка 2 таблицы
 * девяти транзакций через границу (§7.3 плана). До сих пор их писал пакет в
 * браузере ({@code createRoom()}, {@code app-core.js:12101}), и между двумя
 * записями было окно, в котором комната существовала пустой: уборщик
 * брошенных комнат вправе снести именно такую.
 *
 * <p>Идентификатор, хозяина, фазу, дивизион и счётчик присутствия ставит
 * сервер. Раньше их назначал себе клиент — включая {@code createdBy}, то есть
 * хозяйство было полем, которое вызывающий вписывал сам (находка A1).
 *
 * <p>Пять заряженных мемов обязательны до создания комнаты, а не при старте
 * партии: хозяин без обоймы завёл бы комнату, собрал в неё людей и упёрся бы
 * в отказ на старте — вместе со всеми, кого позвал.
 */
@Service
@RequiredArgsConstructor
public class CreateRoomUseCase {

    /** Обойма мемов принадлежит области диверсий, а не оболочке учётки. */
    private final SabotageArmoryPort armory;
    /** Имя, аватар и дивизион — у карточки игрока, а не в оболочке учётки. */
    private final PlayerCardPort cards;
    private final SaverRoom saverRoom;
    private final RoomSeats roomSeats;
    private final RoomProjections projections;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public CreatedRoomResponseDTO run(HotHatUser user, CreateRoomRequestDTO request) {
        List<String> loadout = armory.requireLoadout(user.uid());
        PlayerCardPort.Card card = cards.card(user.uid()).orElse(null);

        String division = Divisions.normalize(card == null ? null : card.divisionLanguage());
        String gameLanguage = request.gameLanguage() == null
                ? division : request.gameLanguage().wireValue();
        // Тестовая комната — админский инструмент: в ней сидят боты и включены
        // владельческие поблажки. Постороннему флаг не отказывают, а
        // игнорируют: он приезжает из адресной строки, и падать из-за
        // случайного «?test=1» комната не должна.
        boolean testRoom = request.testRoomOrDefault() && user.admin();
        long now = System.currentTimeMillis();

        String roomId = Ids.newRoomId();
        Room room = Room.builder()
                .id(roomId)
                .name(Json.str(request.name(), 80))
                .phase(RoomPhase.SETUP.wireValue())
                .createdBy(user.uid())
                .hostLastSetupActivityAt(now)
                .gameMode(request.gameModeOrDefault().wireValue())
                .isPrivate(request.privateRoomOrDefault())
                .isTestRoom(testRoom)
                .testOwnerUid(testRoom ? user.uid() : null)
                .divisionLanguage(division)
                .gameLanguage(Divisions.normalize(gameLanguage))
                .maxPlayers(request.capacityOrDefault())
                .maxParticipants(request.capacityOrDefault())
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

        RoomSeats.Seated seated = roomSeats.seatPlayer(room, user.uid(), card, loadout,
                RoomSeats.Origin.self());
        return new CreatedRoomResponseDTO(projections.room(room), projections.seat(seated.player(), now));
    }
}
