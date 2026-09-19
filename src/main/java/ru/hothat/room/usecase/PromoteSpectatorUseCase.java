package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.repository.GetterRoom;
import ru.hothat.room.api.dto.PromotedSpectatorResponseDTO;
import ru.hothat.room.domain.RoomPhase;
import ru.hothat.sabotage.spi.SabotageArmoryPort;

import java.util.List;

/**
 * Посадить зрителя за стол.
 *
 * <p>Только до начала партии: составы заморожены, и новый игрок посреди игры
 * попал бы в комнату без места в очереди ходов.
 *
 * <p>Пять заряженных мемов у зрителя обязательны. Отказ здесь дешевле, чем на
 * старте: там в него упрётся вся комната, а причина — «у игрока меньше пяти
 * мемов» — прозвучит для хозяина загадкой.
 *
 * <p>Зрительское место снимается тем же действием: одно и то же лицо не может
 * быть одновременно за столом и в зале, иначе счётчик зрителей считал бы
 * игроков.
 */
@Service
@RequiredArgsConstructor
public class PromoteSpectatorUseCase {

    private final RoomAccessGuard roomAuthz;
    private final GetterRoom getterRoom;
    /** Обойма мемов принадлежит области диверсий, а не оболочке учётки. */
    private final SabotageArmoryPort armory;
    /** Имя, аватар и дивизион — у карточки игрока, а не в оболочке учётки. */
    private final PlayerCardPort cards;
    private final RoomSeats roomSeats;
    private final RoomProjections projections;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public PromotedSpectatorResponseDTO run(HotHatUser user, String roomId, String spectatorUid) {
        Room room = roomAuthz.requireHostRoomForWrite(user, roomId);
        if (!RoomPhase.fromWire(room.getPhase()).isSetup()) {
            throw ApiException.of("GAME_ALREADY_STARTED", 409);
        }
        getterRoom.getSpectator(roomId, spectatorUid)
                .orElseThrow(() -> ApiException.of("SPECTATOR_NOT_FOUND", 404));

        long now = System.currentTimeMillis();
        boolean alreadySeated = getterRoom.getPlayer(roomId, spectatorUid).isPresent();
        if (!alreadySeated && roomSeats.alivePlayers(roomId, now).size() >= room.effectiveMaxPlayers()) {
            throw ApiException.of("ROOM_FULL", 409);
        }

        PlayerCardPort.Card card = cards.card(spectatorUid).orElse(null);
        // Обойма спрашивается у области диверсий по идентификатору: строка
        // оболочки учётки к ней отношения не имеет и читать её незачем.
        List<String> loadout = armory.requireLoadout(spectatorUid);
        RoomSeats.Seated seated = roomSeats.seatPlayer(room, spectatorUid, card, loadout,
                RoomSeats.Origin.promotion(user.uid()));

        return new PromotedSpectatorResponseDTO(
                projections.seat(seated.player(), now), loadout.size());
    }
}
