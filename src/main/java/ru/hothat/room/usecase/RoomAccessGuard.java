package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomSpectator;
import ru.hothat.repository.GetterRoom;
import ru.hothat.room.api.dto.RoomSeatKind;
import ru.hothat.room.store.RoomRows;

import java.util.Optional;

/**
 * Кто и что вправе делать в комнате — и единственная дверь к её строке.
 *
 * <p>В плане это предикат {@code @roomAuthz} из {@code ru.hothat.security.authz}.
 * Пакета безопасности ещё нет, а заводить его из области комнаты нельзя,
 * поэтому предусловие стоит в начале сценария — там же, где стоит проверка
 * друга у переписки и проверка команды у префлайта. Имя бина уже то самое:
 * когда пакет появится, форма {@code @PreAuthorize("@roomAuthz.isMember(#roomId)")}
 * заработает без переименования.
 *
 * <p><b>Почему не SpEL сегодня.</b> Отказ {@code @PreAuthorize} уходит наружу
 * как {@code AccessDeniedException}, а общий обработчик объясняет любой такой
 * отказ нехваткой прав администратора. Человек, зашедший по чужой ссылке на
 * комнату, читал бы «Нет прав администратора» — на игровом экране это прямая
 * неправда. Здесь отказ называет себя сам: «только для участников комнаты»,
 * «только для хозяина», «только для зрителей».
 *
 * <p><b>Замок.</b> Все пишущие сценарии берут комнату через
 * {@link #requireRoomForWrite(String)} и делают это <b>первым</b> действием,
 * до чтения мест и составов. Один и тот же порядок у всех означает, что две
 * одновременные записи в комнату выстраиваются в очередь, а взаимная
 * блокировка невозможна: захватывается ровно одна строка.
 */
@Component("roomAuthz")
@RequiredArgsConstructor
public class RoomAccessGuard {

    private final GetterRoom getterRoom;
    private final RoomRows roomRows;

    /** Комната для чтения. Отсутствие — 404. */
    public Room requireRoom(String roomId) {
        return getterRoom.getById(roomId).orElseThrow(() -> ApiException.of("ROOM_NOT_FOUND", 404));
    }

    /**
     * Комната под замком записи: с этого начинается каждый пишущий сценарий.
     *
     * <p>Замок берётся до первого чтения состава — именно этим он и отличается
     * от прежнего сравнения {@code updatedAt}, которое читало, решало и писало
     * тремя шагами и допускало между ними чужую запись (находка B2).
     */
    public Room requireRoomForWrite(String roomId) {
        return roomRows.lock(roomId)
                .orElseThrow(() -> ApiException.of("ROOM_NOT_FOUND", 404));
    }

    /**
     * Место игрока в комнате.
     *
     * <p>Код 403, а не 404: «тебя тут нет» — это отказ в праве, а не
     * ненайденный ресурс. Комната существует, и говорить обратное значило бы
     * отправить экран искать другую.
     */
    public RoomPlayer requireMember(HotHatUser user, String roomId) {
        return getterRoom.getPlayer(roomId, user.uid())
                .orElseThrow(() -> ApiException.of("ROOM_MEMBER_ONLY", 403));
    }

    /** Место зрителя в комнате. */
    public RoomSpectator requireSpectator(HotHatUser user, String roomId) {
        return getterRoom.getSpectator(roomId, user.uid())
                .orElseThrow(() -> ApiException.of("SPECTATOR_ONLY", 403));
    }

    /**
     * Кем человек сидит в комнате — игроком или зрителем — и под каким именем.
     *
     * <p>Возвращает место, а не «да/нет»: снимок комнаты и чат сразу
     * подписывают отправителя тем, кем он сидит, и второй вопрос «а он игрок
     * или зритель, и как его зовут» стоил бы второго чтения.
     */
    public Seat requireSeat(HotHatUser user, String roomId) {
        Optional<RoomPlayer> player = getterRoom.getPlayer(roomId, user.uid());
        if (player.isPresent()) {
            return new Seat(RoomSeatKind.PLAYER, user.uid(), player.get().getName());
        }
        Optional<RoomSpectator> spectator = getterRoom.getSpectator(roomId, user.uid());
        if (spectator.isPresent()) {
            return new Seat(RoomSeatKind.SPECTATOR, user.uid(), spectator.get().getName());
        }
        throw ApiException.of("ROOM_MEMBER_ONLY", 403);
    }

    /**
     * Место в комнате в объёме, нужном чату и снимку.
     *
     * @param name имя-снимок из строки места; может быть пустым у старых строк
     */
    public record Seat(RoomSeatKind kind, String uid, String name) {
    }

    /**
     * Хозяин комнаты.
     *
     * <p>Хозяйство хранится колонкой {@code created_by} и меняется при
     * передаче, поэтому «создатель» и «хозяин» — не одно и то же, и спрашивать
     * надо именно текущее значение.
     */
    public void requireHost(HotHatUser user, Room room) {
        if (!user.uid().equals(room.getCreatedBy())) {
            throw ApiException.of("HOST_ONLY", 403);
        }
    }

    /** Комната под замком, взятая хозяином: две проверки одним шагом. */
    public Room requireHostRoomForWrite(HotHatUser user, String roomId) {
        Room room = requireRoomForWrite(roomId);
        requireHost(user, room);
        return room;
    }
}
