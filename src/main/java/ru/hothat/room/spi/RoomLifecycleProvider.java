package ru.hothat.room.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.game.port.RoomLifecyclePort;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.domain.RoomPhase;
import ru.hothat.room.usecase.RoomAccessGuard;
import ru.hothat.util.Divisions;
import ru.hothat.util.Json;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Дверь, через которую область партии распоряжается комнатой.
 *
 * <p>Порт объявлен у потребителя — {@link RoomLifecyclePort} в
 * {@code ru.hothat.game.port}, — а реализация принадлежит владельцу таблиц,
 * то есть этой области. Так и должно быть: писать в {@code room} продолжает
 * комната, партия лишь просит. Правило «у колонки один писатель» не
 * нарушается, и партии не приходится знать ни про репозитории комнаты, ни про
 * её сущности.
 *
 * <p>{@code @Primary} осталось от временного переходника, который жил в
 * области партии над теми же репозиториями, пока этой реализации не было.
 * Переходник удалён — реализация порта теперь одна, — а пометка сохранена
 * намеренно: она называет владельца порта на случай, если в чужой области
 * снова заведут «пока свою» реализацию.
 *
 * <p><b>Почему распространение транзакции разное у чтений и записей.</b>
 * Предикаты прав партии ({@code @gameAuthz.isHost}, {@code isMember},
 * {@code isMemberOrSpectator}) читают комнату через этот же порт, а SpEL
 * считает их <i>до</i> входа в метод — то есть до того, как откроется
 * транзакция сценария. Требовать транзакцию у чтения значило бы отвергать
 * каждый запрос к партии ещё на проверке прав. Записи, наоборот, объявлены
 * {@code MANDATORY}: старт партии и её техзавершение меняют комнату и партию
 * вместе (§7.3, строки 4 и 6), и своя транзакция здесь означала бы, что
 * половина изменения переживёт падение второй половины.
 *
 * <p>Записи берут строку комнаты под замком и делают это первым действием —
 * тем же порядком, что и сценарии комнаты. Порядок один на обе области,
 * поэтому взаимной блокировки между ними не бывает. Партия, к слову, берёт тот
 * же замок у себя ({@code MatchRoomRepo.lock}), и повторный захват той же
 * строки в той же транзакции ничего не стоит.
 */
@Service
@Primary
@RequiredArgsConstructor
public class RoomLifecycleProvider implements RoomLifecyclePort {

    /** Столько длится ход, если в комнате не записано ничего. */
    private static final int DEFAULT_TURN_SECONDS = 60;

    private final RoomAccessGuard roomAuthz;
    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Optional<RoomLifecycleView> find(String roomId) {
        return getterRoom.getById(roomId).map(RoomLifecycleProvider::view);
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public List<RoomSeatView> seats(String roomId) {
        return getterRoom.getPlayers(roomId).stream().map(RoomLifecycleProvider::seat).toList();
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Optional<RoomSeatView> seat(String roomId, String uid) {
        return getterRoom.getPlayer(roomId, uid).map(RoomLifecycleProvider::seat);
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public boolean isHost(String roomId, String uid) {
        return getterRoom.getById(roomId).map(room -> uid.equals(room.getCreatedBy())).orElse(false);
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public boolean isMember(String roomId, String uid) {
        return getterRoom.getPlayer(roomId, uid).isPresent();
    }

    /**
     * Зритель комнаты.
     *
     * <p>Лёгкая подписка превью с главной зрителем не считается: человек
     * навёл на карточку в витрине, а не открыл комнату, и показывать ему
     * состояние идущей партии незачем.
     */
    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public boolean isSpectator(String roomId, String uid) {
        return getterRoom.getSpectator(roomId, uid)
                .filter(seat -> !Boolean.TRUE.equals(seat.getPreview()))
                .isPresent();
    }

    /**
     * Партия началась.
     *
     * <p>Комната делает здесь ровно две вещи, и обе — её собственные.
     * Освобождает зрительские места игроков: место зрителя и место игрока —
     * одно кресло, и остаться в обоих нельзя. И отодвигает уборщика: партия
     * может идти час без единой другой записи в строку комнаты.
     *
     * <p>Фазу, составы и номер партии пишет сама партия — это её данные,
     * лежащие сегодня в строке комнаты только потому, что таблицы
     * {@code match} ещё нет.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void onMatchStarted(String roomId, int gameNumber, List<String> playerUids) {
        Room room = roomAuthz.requireRoomForWrite(roomId);
        playerUids.forEach(uid -> saverRoom.deleteSpectator(roomId, uid));
        room.setLastActivityAt(System.currentTimeMillis());
        saverRoom.save(room);
    }

    /**
     * Партия закончилась.
     *
     * <p>Состояние партии здесь не стирается намеренно: на экране у игроков
     * итоги, и обнулить их в тот же миг значило бы показать пустой стол вместо
     * счёта. К набору комнату возвращает хозяин — кнопкой, у которой свой адрес
     * и свой выбор глубины: с роспуском команд или без.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void onMatchFinished(String roomId, int gameNumber, String reason) {
        Room room = roomAuthz.requireRoomForWrite(roomId);
        room.setLastActivityAt(System.currentTimeMillis());
        saverRoom.save(room);
    }

    /**
     * Комната закрывается: доигрывать некому.
     *
     * <p>Помечается закрытой, а не стирается. Разница существенная: строка
     * комнаты — это и есть строка идущей партии, и удалить её посреди
     * транзакции, которая эту партию пишет, значит уронить транзакцию или
     * воскресить комнату следующей записью. Опустевшую комнату всё равно
     * подметёт уборщик — по тем же правилам, что и любую другую брошенную.
     *
     * <p>Ещё одна причина: закрытую комнату видят те, кто в ней остался.
     * Стёртая ответила бы им «комната не найдена» — то есть выглядела бы как
     * поломка, а не как конец партии.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void closeRoom(String roomId, String reason) {
        Room room = roomAuthz.requireRoomForWrite(roomId);
        long now = System.currentTimeMillis();
        room.setPhase(RoomPhase.CLOSED.wireValue());
        room.setClosedAt(Instant.ofEpochMilli(now));
        room.setClosedReason(Json.str(reason, 200));
        room.setLastActivityAt(now);
        saverRoom.save(room);
    }

    /**
     * Отметка присутствия игрока.
     *
     * <p>Замок комнаты не берётся: пишется одна своя строка места, и никто,
     * кроме её владельца, в неё не пишет. Захватывать ради этого строку
     * комнаты значило бы выстраивать в очередь весь стол на каждое
     * сердцебиение.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void touchSeat(String roomId, String uid, long atMs) {
        getterRoom.getPlayer(roomId, uid).ifPresent(player -> {
            player.setLastSeenAt(atMs);
            saverRoom.savePlayer(player);
        });
    }

    /**
     * Игрок ушёл: присутствие и признаки связи гасятся немедленно.
     *
     * <p>До опроса видеосвязи, а не после: её список участников отдаёт
     * ушедшего ещё несколько секунд, и запоздалая проверка сняла бы паузу зря.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void dropSeatPresence(String roomId, String uid) {
        getterRoom.getPlayer(roomId, uid).ifPresent(player -> {
            player.setLastSeenAt(0L);
            player.setCameraEnabled(false);
            player.setMicrophoneEnabled(false);
            player.setMediaReadyAt(0L);
            saverRoom.savePlayer(player);
        });
    }

    private static RoomLifecycleView view(Room room) {
        return new RoomLifecycleView(
                room.getId(),
                room.getName(),
                Json.str(room.getCreatedBy()),
                room.getPhase(),
                Math.max(0, room.getGameNumber()),
                room.getGameMode(),
                Boolean.TRUE.equals(room.getRanked()),
                Boolean.TRUE.equals(room.getIsTestRoom()),
                Boolean.TRUE.equals(room.getRecordGame()),
                Divisions.normalize(room.getDivisionLanguage()),
                Divisions.normalize(room.getGameLanguage()),
                room.effectiveMaxPlayers(),
                // Настройка комнаты, а не длительность идущего хода: соседняя
                // колонка turn_duration_seconds принадлежит партии.
                room.getTurnDuration() == null ? DEFAULT_TURN_SECONDS : room.getTurnDuration(),
                room.getTestBotIds() == null ? List.of() : List.copyOf(room.getTestBotIds()));
    }

    private static RoomSeatView seat(RoomPlayer player) {
        return new RoomSeatView(player.getUid(), player.getName(), player.getTeamId(),
                Boolean.TRUE.equals(player.getIsTestBot()),
                player.getLastSeenAt() == null ? 0L : player.getLastSeenAt());
    }
}
