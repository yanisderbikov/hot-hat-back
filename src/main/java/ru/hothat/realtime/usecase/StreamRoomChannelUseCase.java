package ru.hothat.realtime.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.PlayerAmmoView;
import ru.hothat.game.port.WordSubmissionPort;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;
import ru.hothat.game.usecase.MatchViewAssembler;
import ru.hothat.realtime.api.dto.RoomChannelView;
import ru.hothat.realtime.api.dto.RoomChatWindowView;
import ru.hothat.realtime.api.dto.RoomMatchView;
import ru.hothat.realtime.api.dto.RoomSnapshotView;
import ru.hothat.realtime.api.dto.RoomWordsView;
import ru.hothat.room.api.dto.RoomChatPageResponseDTO;
import ru.hothat.room.api.dto.RoomSnapshotResponseDTO;
import ru.hothat.room.usecase.GetRoomSnapshotUseCase;
import ru.hothat.room.usecase.ReadRoomChatUseCase;

import java.util.List;

/**
 * Показать комнату слушателю канала {@code /ws/v2/room/{roomId}}.
 *
 * <p>Сценарий один и тот же для обоих кадров: и для приветственного, и для
 * каждого обновления канал спрашивает одно — «как выглядит комната для этого
 * участника сейчас». Различие между кадрами придумывает транспорт.
 *
 * <p>Право проверяется здесь, а не в транспорте, и проверяется на <b>каждом</b>
 * чтении: подписаться на комнату может только тот, у кого в ней есть место —
 * игрока либо зрителя. Проверку исполняет {@link GetRoomSnapshotUseCase} тем
 * же {@code roomAuthz.requireSeat}, каким её исполняет {@code GET
 * /api/v2/room/{roomId}}; выгнанный перестаёт получать кадры сразу, а не
 * когда закроет вкладку.
 *
 * <p>Все четыре части кадра собирают те же сценарии и сборщики, что отвечают
 * на адреса HTTP: снимок — {@link GetRoomSnapshotUseCase}, чат —
 * {@link ReadRoomChatUseCase}, партия — {@link MatchViewAssembler}, слова —
 * {@link WordSubmissionPort}. Своей копии сборки здесь нет ни одной: канал и
 * адрес не должны показывать разное.
 *
 * <p>Кадр несёт всё, что экран комнаты до сих пор собирал шестью подписками
 * старого шлюза, и ровно с тем же охватом по слушателю: чужие места, составы,
 * зрители, чат и счётчики боезапаса — общие; своё снаряжение, свои клипы
 * Подмены, свои слова и слово хода — только своё. Чужого содержимого обоймы в
 * кадре нет, и это свойство сборщика, а не транспорта.
 *
 * <p><b>Почему партия собирается сборщиком, а не сценарием.</b> У
 * {@code GetMatchStateUseCase} право объявлено предикатом
 * {@code @gameAuthz.isMemberOrSpectator}, а бин {@code gameAuthz} живёт
 * областью <i>запроса</i>. В потоке сокета запроса нет, и вызов такого
 * сценария упал бы на попытке создать бин. Поэтому канал берёт партию у
 * {@link MatchStore} и {@link MatchViewAssembler} — ровно тем же способом и в
 * том же порядке, что и сценарий, — а право на комнату проверено выше.
 * Правило «слово видит только объясняющий» при этом остаётся у сборщика: он
 * единственная точка, через которую состояние партии выходит наружу.
 *
 * <p>Транзакция одна на все чтения и {@code readOnly}: иначе состав мог бы
 * приехать из состояния до чужой пересадки, а команды — после.
 */
@Service
@RequiredArgsConstructor
public class StreamRoomChannelUseCase {

    private final GetRoomSnapshotUseCase roomSnapshot;
    private final ReadRoomChatUseCase roomChat;
    private final MatchStore matchStore;
    private final MatchViewAssembler matchViews;
    private final WordSubmissionPort wordSubmissions;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public RoomChannelView run(HotHatUser user, String roomId) {
        // Первым делом снимок: он же и есть проверка права на комнату.
        RoomSnapshotResponseDTO snapshot = roomSnapshot.run(user, roomId);
        // Умолчания страницы чата — те же, что у адреса HTTP: канал и лента не
        // должны показывать разное количество сообщений одному человеку.
        RoomChatPageResponseDTO chat = roomChat.run(user, roomId, null);
        MatchSession session = matchStore.readSession(roomId);
        // Состав — одним чтением и до поиска своего места: после players()
        // findPlayer берёт своё из сессии, а не вторым запросом.
        List<PlayerAmmoView> ammo = matchViews.ammo(session.players());

        // Своя пачка и общий счётчик — два запроса по ключу, чужих слов канал
        // не читает вовсе: увидеть их значит узнать половину партии заранее.
        List<String> mine = wordSubmissions.read(roomId, user.uid());

        return new RoomChannelView(
                new RoomSnapshotView(snapshot.room(), snapshot.players(), snapshot.teams(),
                        snapshot.spectators(), snapshot.viewerSeat()),
                new RoomChatWindowView(chat.items(), chat.nextCursor(), chat.limit()),
                new RoomMatchView(
                        matchViews.state(session.state(), user.uid()),
                        session.findPlayer(user.uid()).map(matchViews::arsenal).orElse(null),
                        ammo,
                        matchViews.clipsOf(session.state(), user.uid())),
                new RoomWordsView(mine, mine.size(), wordSubmissions.total(roomId)));
    }
}
