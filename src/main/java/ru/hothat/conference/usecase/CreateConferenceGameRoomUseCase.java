package ru.hothat.conference.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.conference.api.dto.ConferenceResponseDTO;
import ru.hothat.conference.api.dto.CreateConferenceGameRoomRequestDTO;
import ru.hothat.conference.domain.ConferenceRules;
import ru.hothat.conference.store.ConferenceStore;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.realtime.spi.RealtimeChangeBus;
import ru.hothat.room.spi.RoomDirectoryPort;
import ru.hothat.room.spi.RoomLineupPort;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Завести игровую комнату этим составом.
 *
 * <p>Право хозяина. Комнату заводит область комнаты через
 * {@link RoomLineupPort} — одной транзакцией с отметкой в видео-чате: комната
 * без отметки никого не позовёт, отметка без комнаты позовёт в отказ.
 *
 * <p>Состав называет хозяин: сервер знает, кто в видео-чате, но не знает,
 * кто сейчас на связи. Чужие в списке отбрасываются, хозяин добавляется
 * всегда — он сядет первым. Остальные входят сами по ссылке, каждый со
 * своей обоймой и своей проверкой допуска: сажать их отсюда значило бы
 * обойти правила входа в комнату.
 *
 * <p>Пока заведённая комната набирается, повторное нажатие отвечает ею же:
 * две комнаты на один созвон разведут людей по разным столам.
 */
@Service
@RequiredArgsConstructor
public class CreateConferenceGameRoomUseCase {

    private final ConferenceAccess access;
    private final ConferenceStore store;
    private final ConferenceProjections projections;
    private final RoomLineupPort rooms;
    private final RoomDirectoryPort roomDirectory;
    private final RealtimeChangeBus changes;
    private final Clock clock;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public ConferenceResponseDTO run(HotHatUser user, String conferenceId,
                                     CreateConferenceGameRoomRequestDTO request) {
        ConferenceAccess.Opened opened = access.requireHost(conferenceId, user.uid());

        String existing = opened.conference().gameRoomId();
        if (existing != null && roomDirectory.find(existing).map(RoomDirectoryPort.RoomBrief::open).orElse(false)) {
            return new ConferenceResponseDTO(projections.view(opened));
        }

        Set<String> participants = new LinkedHashSet<>();
        opened.participants().forEach(row -> participants.add(row.uid()));
        Set<String> seated = new LinkedHashSet<>();
        seated.add(user.uid());
        for (String uid : request.participantUidsOrEmpty()) {
            if (participants.contains(uid)) {
                seated.add(uid);
            }
        }
        if (seated.size() > ConferenceRules.MAX_GAME_PLAYERS) {
            throw ApiException.of("CONFERENCE_GAME_TOO_MANY", 409);
        }

        String roomId = rooms.openPrivateRoom(new RoomLineupPort.PrivateRoomOrder(
                user.uid(), "", ConferenceRules.gameRoomCapacityFor(seated.size())));
        store.attachGameRoom(conferenceId, roomId, List.copyOf(seated), Instant.now(clock));
        changes.conferenceChanged(conferenceId);
        return new ConferenceResponseDTO(projections.view(access.requireHost(conferenceId, user.uid())));
    }
}
