package ru.hothat.realtime.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.HotHatUser;
import ru.hothat.lobby.api.dto.LobbyRoomPageResponseDTO;
import ru.hothat.lobby.usecase.ListOpenRoomsUseCase;
import ru.hothat.realtime.api.dto.LobbyRoomsWindowView;

/**
 * Показать витрину слушателю канала {@code /ws/v2/lobby}.
 *
 * <p>Сценарий один и тот же для обоих кадров: и для приветственного, и для
 * каждого обновления канал спрашивает одно — «как выглядит витрина для этого
 * зрителя сейчас». Различие между кадрами придумывает транспорт.
 *
 * <p>Витрину собирает не этот класс, а {@link ListOpenRoomsUseCase} — тот же,
 * что отвечает на {@code GET /api/v2/lobby/rooms}. Это и есть требование
 * «одна форма на два транспорта», исполненное самым надёжным способом:
 * собирающий код один, разойтись нечему. Своя копия отбора видимых комнат
 * здесь означала бы, что приватная комната однажды приедет в канал, но не в
 * ответ HTTP, — и заметить это было бы некому.
 *
 * <p>Право названо и здесь, хотя стоит и на вызываемом сценарии. Дублирование
 * намеренное: право канала должно читаться на самом канале, а не выясняться
 * переходом по вызову. Проверяется оно на каждом кадре — забаненный
 * перестаёт получать витрину сразу, а не когда закроет вкладку.
 *
 * <p>Гость сюда допущен по решению заказчика (§8 плана): главная — это
 * витрина, ради которой он и пришёл.
 */
@Service
@RequiredArgsConstructor
public class StreamLobbyChannelUseCase {

    private final ListOpenRoomsUseCase openRooms;

    @PreAuthorize("hasAnyRole('GUEST','USER')")
    public LobbyRoomsWindowView run(HotHatUser user) {
        // Параметров у канала нет: предел выбирает сервер, тот же, что у
        // страницы HTTP по умолчанию. Канал и первая загрузка не должны
        // показывать разное число комнат одному и тому же человеку.
        LobbyRoomPageResponseDTO page = openRooms.run(user, null);
        return new LobbyRoomsWindowView(page.items(), page.nextCursor(), page.limit());
    }
}
