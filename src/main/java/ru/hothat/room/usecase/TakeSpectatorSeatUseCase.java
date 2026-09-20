package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.room.api.dto.SpectatorSeatResponseDTO;
import ru.hothat.room.domain.RoomAccessPolicy;
import ru.hothat.room.domain.RoomPhase;
import ru.hothat.sabotage.spi.SabotageArmoryPort;
import ru.hothat.util.Divisions;

/**
 * Занять место зрителя.
 *
 * <p>Правила перенесены из {@code joinAsSpectator()}
 * ({@code app-core.js:12429}): приватную комнату не смотрят, закрытую тоже, а
 * до начала партии зрителей не бывает вовсе — все, кто в комнате, ещё игроки.
 * Четвёртое правило браузер не держал и держать не мог: сидящий игрок этой
 * же комнаты получает {@code PLAYER_CANNOT_WATCH} — ему место игрока, а не
 * зрителя, и интерфейс возвращает его за стол.
 *
 * <p>Тела у запроса нет: имя и аватар сервер берёт из карточки. Раньше их
 * присылал браузер, и подписаться в зале можно было как угодно.
 *
 * <p>Пять заряженных мемов требуются и здесь. Правило уровня учётной записи, а
 * не партии: в комнату не входят ни по какому пути, пока обойма не собрана, —
 * иначе зритель, которого хозяин переведёт за стол, сорвал бы старт всей
 * комнате.
 */
@Service
@RequiredArgsConstructor
public class TakeSpectatorSeatUseCase {

    private final RoomAccessGuard roomAuthz;
    /** Обойма мемов принадлежит области диверсий, а не оболочке учётки. */
    private final SabotageArmoryPort armory;
    /** Имя, аватар и дивизион — у карточки игрока, а не в оболочке учётки. */
    private final PlayerCardPort cards;
    private final RoomSeats roomSeats;
    private final RoomProjections projections;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public SpectatorSeatResponseDTO run(HotHatUser user, String roomId) {
        Room room = roomAuthz.requireRoomForWrite(roomId);
        armory.requireLoadout(user.uid());

        RoomAccessPolicy.RoomFacts facts = new RoomAccessPolicy.RoomFacts(
                RoomPhase.fromWire(room.getPhase()),
                room.isClosed(),
                Boolean.TRUE.equals(room.getIsPrivate()),
                Boolean.TRUE.equals(room.getRanked()),
                Boolean.TRUE.equals(room.getManagedMatchmaking()),
                Divisions.normalize(room.getDivisionLanguage()),
                Divisions.normalize(RoomProjections.gameLanguage(room)),
                room.effectiveMaxPlayers());
        RoomAccessPolicy.refuseSpectatorSeat(facts, roomSeats.playerSeated(roomId, user.uid())).ifPresent(refusal -> {
            throw RoomRefusals.of(refusal);
        });

        RoomSeats.Watching watching = roomSeats.seatSpectator(room, user.uid(), cards.card(user.uid()).orElse(null));
        return new SpectatorSeatResponseDTO(
                projections.room(room),
                projections.spectator(watching.spectator()),
                watching.created());
    }
}
