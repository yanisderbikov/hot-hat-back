package ru.hothat.lobby.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.lobby.api.dto.LobbyRoomCardView;
import ru.hothat.lobby.api.dto.LobbyRoomPageResponseDTO;
import ru.hothat.lobby.api.dto.LobbyRoomPhase;
import ru.hothat.lobby.api.dto.LobbyRoomQueryDTO;
import ru.hothat.lobby.domain.LobbyVisibility;
import ru.hothat.model.room.Room;
import ru.hothat.repository.GetterRoom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Показать витрину открытых комнат.
 *
 * <p>Заменяет живую подписку {@code query(rooms, where phase in […])}
 * ({@code home/home.js:93}). Меняется не только транспорт: раньше сервер
 * отдавал браузеру каждую комнату целиком, а решал, показывать ли её,
 * браузер. Приватная комната, чужая тестовая и служебная комната недособранного
 * подбора приезжали всем — вместе с мешком неразыгранных слов и текущим
 * словом. Теперь отбор делает сервер, и невидимая комната сервера не покидает.
 *
 * <p>Число игроков тоже считает сервер. Браузер считал его двумя способами:
 * по счётчику комнаты, пока тот свежий, и по составу — когда протух; за
 * составом он ходил отдельной подпиской на каждую комнату. Здесь оба способа
 * остались, но состав всех нуждающихся комнат читается одним запросом.
 */
@Service
@RequiredArgsConstructor
public class ListOpenRoomsUseCase {

    private final GetterRoom getterRoom;
    private final LobbyRoomDirectory directory;

    @PreAuthorize("hasAnyRole('GUEST','USER')")
    @Transactional(readOnly = true)
    public LobbyRoomPageResponseDTO run(HotHatUser user, LobbyRoomQueryDTO query) {
        // Единственное место умолчания: параметров может не быть вовсе,
        // и «не задано» не должно разъезжаться по контроллеру и мапперу.
        int limit = query == null || query.limit() == null ? LobbyReadLimits.ROOMS : query.limit();
        long now = System.currentTimeMillis();
        String viewerUid = user == null ? null : user.uid();

        // Шесть запросов по индексу (phase) — по одному на живую фазу, и ни
        // одного лишнего. Это не чтение в цикле по коллекции: набор фаз закрыт
        // перечислением и не зависит ни от числа комнат, ни от размера страницы.
        // Один запрос «фаза среди …» здесь пока невозможен: у хранилища есть
        // только выборка по одной фазе, а заводить чужой репозиторий из области
        // лобби нельзя.
        List<Room> scanned = new ArrayList<>();
        for (LobbyRoomPhase phase : LobbyRoomPhase.values()) {
            scanned.addAll(getterRoom.getByPhase(phase.wireValue(), LobbyReadLimits.ROOMS_SCAN_PER_PHASE));
        }

        List<Room> visible = scanned.stream()
                .filter(room -> LobbyVisibility.visibleTo(facts(room), viewerUid))
                .toList();

        // За составом ходим только к тем комнатам, чей собственный счётчик
        // протух: в обычный час таких нет вовсе, и запрос не уходит.
        List<String> stale = visible.stream()
                .filter(room -> !LobbyVisibility.publicCountFresh(presenceAt(room), now))
                .map(Room::getId)
                .toList();
        LobbyRoomDirectory.Snapshot counts = directory.countAlivePlayers(stale, now);

        List<LobbyRoomCardView> items = new ArrayList<>();
        for (Room room : visible) {
            int players = LobbyVisibility.publicCountFresh(presenceAt(room), now)
                    ? Math.max(0, room.getPublicActivePlayers() == null ? 0 : room.getPublicActivePlayers())
                    : counts.alive(room.getId());
            // Пустая комната витрине не нужна: войти в неё можно по прямому
            // идентификатору, а в списке она только занимает строку. Так же
            // поступал и браузер.
            if (players > 0) {
                items.add(card(room, players));
            }
        }

        // Порядок задаём явно: выборка по фазе его не обещает, а страница
        // обрезается по пределу — без сортировки в неё попадали бы случайные
        // комнаты. Сначала людные: в них интереснее смотреть и проще доиграть.
        items.sort(Comparator.<LobbyRoomCardView>comparingInt(LobbyRoomCardView::activePlayers).reversed()
                .thenComparing(LobbyRoomCardView::roomId));
        return new LobbyRoomPageResponseDTO(
                items.size() > limit ? List.copyOf(items.subList(0, limit)) : List.copyOf(items),
                null,
                limit);
    }

    private static LobbyVisibility.RoomFacts facts(Room room) {
        return new LobbyVisibility.RoomFacts(
                room.getClosedAt() != null,
                Boolean.TRUE.equals(room.getIsPrivate()),
                Boolean.TRUE.equals(room.getManagedMatchmaking()),
                Boolean.TRUE.equals(room.getMatchmakingReady()),
                Boolean.TRUE.equals(room.getIsTestRoom()),
                room.getTestOwnerUid());
    }

    private static long presenceAt(Room room) {
        return room.getPublicPresenceAt() == null ? 0L : room.getPublicPresenceAt();
    }

    private static LobbyRoomCardView card(Room room, int activePlayers) {
        return new LobbyRoomCardView(
                room.getId(),
                LobbyRooms.name(room),
                // Фазу уже проверил запрос: сюда попадают только живые комнаты.
                LobbyRoomPhase.live(room.getPhase()).orElse(LobbyRoomPhase.SETUP),
                LobbyRooms.mode(room),
                Boolean.TRUE.equals(room.getRanked()),
                LobbyRooms.gameLanguage(room),
                LobbyRooms.divisionLanguage(room),
                room.effectiveMaxPlayers(),
                activePlayers,
                Boolean.TRUE.equals(room.getIsTestRoom()));
    }
}
