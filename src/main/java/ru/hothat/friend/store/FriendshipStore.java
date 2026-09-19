package ru.hothat.friend.store;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import ru.hothat.common.identity.LegacyIdBridge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Единственная дверь области дружбы в свои таблицы.
 *
 * <p>Наружу отдаёт строки и маленькие записи, а не сущности: {@code Friendship}
 * и {@code FriendshipRequest} не публичны и в сигнатуры сценариев не попадают.
 * Здесь же живёт перевод uid ↔ uuid, поэтому сценарии по-прежнему говорят
 * идентификаторами, которые понимает фронтенд, и о переезде типа ключа не
 * знают вовсе.
 *
 * <p>Ни один метод не читает в цикле: список друзей — это один запрос за
 * связями и один за обратным переводом пары, независимо от числа друзей.
 */
@Component
@RequiredArgsConstructor
public class FriendshipStore {

    /** Состояния заявки; набор закрыт ограничением базы. */
    public static final String PENDING = "pending";
    public static final String ACCEPTED = "accepted";
    public static final String DECLINED = "declined";

    private final Friendships friendships;
    private final FriendshipRequests requests;
    private final LegacyIdBridge ids;

    /**
     * Друзья игрока, новые сверху. Предел уезжает в базу, а не применяется
     * после чтения.
     */
    public List<String> friendUids(String selfUid, int limit) {
        UUID self = ids.playerId(selfUid);
        List<UUID> others = new ArrayList<>();
        for (Friendship link : friendships
                .findByPlayerLowOrPlayerHighOrderByCreatedAtDesc(self, self, Limit.of(limit))) {
            others.add(self.equals(link.getPlayerLow()) ? link.getPlayerHigh() : link.getPlayerLow());
        }
        return toUids(others);
    }

    /** Дружат ли эти двое. Один запрос по первичному ключу. */
    public boolean areFriends(String uidA, String uidB) {
        if (blank(uidA) || blank(uidB)) {
            return false;
        }
        UUID a = ids.playerId(uidA);
        UUID b = ids.playerId(uidB);
        return friendships.existsById(new FriendshipId(LegacyIdBridge.low(a, b), LegacyIdBridge.high(a, b)));
    }

    /**
     * Записать дружбу. Повторная запись той же пары ничего не меняет: пара —
     * это ключ, и второй строки для неё не существует.
     */
    public void link(String uidA, String uidB) {
        ids.rememberPlayers(List.of(uidA, uidB));
        UUID a = ids.playerId(uidA);
        UUID b = ids.playerId(uidB);
        FriendshipId key = new FriendshipId(LegacyIdBridge.low(a, b), LegacyIdBridge.high(a, b));
        if (friendships.existsById(key)) {
            return;
        }
        friendships.save(Friendship.builder()
                .playerLow(key.getPlayerLow())
                .playerHigh(key.getPlayerHigh())
                .build());
    }

    /** Убрать дружбу; {@code false} — её и не было. */
    public boolean unlink(String uidA, String uidB) {
        if (blank(uidA) || blank(uidB)) {
            return false;
        }
        UUID a = ids.playerId(uidA);
        UUID b = ids.playerId(uidB);
        return friendships.deleteByPlayerLowAndPlayerHigh(
                LegacyIdBridge.low(a, b), LegacyIdBridge.high(a, b)) > 0;
    }

    /** Есть ли между этими двумя заявка, ждущая ответа, — в любую сторону. */
    public boolean hasPendingRequest(String uidA, String uidB) {
        if (blank(uidA) || blank(uidB)) {
            return false;
        }
        UUID a = ids.playerId(uidA);
        UUID b = ids.playerId(uidB);
        return requests.existsByPlayerLowAndPlayerHighAndStatus(
                LegacyIdBridge.low(a, b), LegacyIdBridge.high(a, b), PENDING);
    }

    /**
     * Завести заявку.
     *
     * <p>Проверка «такая заявка уже есть» остаётся у вызывающего ради понятной
     * ошибки, но последнее слово — за уникальным индексом базы: два встречных
     * приглашения, отправленных одновременно, до него доходили оба.
     */
    public long openRequest(String requesterUid, String addresseeUid) {
        ids.rememberPlayers(List.of(requesterUid, addresseeUid));
        FriendshipRequest saved = requests.saveAndFlush(FriendshipRequest.builder()
                .requester(ids.playerId(requesterUid))
                .addressee(ids.playerId(addresseeUid))
                .status(PENDING)
                .build());
        return saved.getId();
    }

    /** Заявка по номеру; пусто — такой нет. */
    public Optional<RequestRow> request(long requestId) {
        return requests.findById(requestId).map(row -> rows(List.of(row)).get(0));
    }

    /** Входящие, ждущие ответа. */
    public List<RequestRow> incoming(String uid, int limit) {
        return rows(requests.findByAddresseeAndStatusOrderByCreatedAtDesc(
                ids.playerId(uid), PENDING, Limit.of(limit)));
    }

    /** Исходящие в любом состоянии, кроме отклонённых. */
    public List<RequestRow> outgoing(String uid, int limit) {
        return rows(requests.findByRequesterAndStatusNotOrderByCreatedAtDesc(
                ids.playerId(uid), DECLINED, Limit.of(limit)));
    }

    /**
     * Ответить на заявку. Отметка времени ставится вместе с ответом: «принята,
     * но когда — неизвестно» база не примет, у ответа в контракте есть
     * {@code answeredAtMs}.
     *
     * @return {@code false} — заявки нет или на неё уже ответили
     */
    public boolean answerRequest(long requestId, boolean accept) {
        FriendshipRequest request = requests.findById(requestId).orElse(null);
        // Отвечают только на живую заявку. Без этой проверки повторный ответ
        // переставлял бы отметку времени, а «отклонить» после «принять»
        // оставляло бы дружбу при отклонённой заявке.
        if (request == null || !PENDING.equals(request.getStatus())) {
            return false;
        }
        request.setStatus(accept ? ACCEPTED : DECLINED);
        request.setAnsweredAt(Instant.now());
        requests.save(request);
        return true;
    }

    /**
     * Заявка так, как её видит сценарий: сторонами названы uid, а не uuid.
     *
     * <p>Ников здесь нет намеренно — их больше не хранит и таблица: копия
     * имени устаревала при первой же смене ника, и показывать надо нынешнее.
     */
    public record RequestRow(long id, String requesterUid, String addresseeUid, String status,
                             long createdAtMs, Long answeredAtMs) {
    }

    /** Обратный перевод обеих сторон списка — одним запросом на список. */
    private List<RequestRow> rows(List<FriendshipRequest> found) {
        List<UUID> players = new ArrayList<>(found.size() * 2);
        for (FriendshipRequest request : found) {
            players.add(request.getRequester());
            players.add(request.getAddressee());
        }
        Map<UUID, String> uids = ids.playerUids(players);
        List<RequestRow> result = new ArrayList<>(found.size());
        for (FriendshipRequest request : found) {
            result.add(new RequestRow(
                    request.getId(),
                    uids.getOrDefault(request.getRequester(), ""),
                    uids.getOrDefault(request.getAddressee(), ""),
                    request.getStatus(),
                    request.getCreatedAt() == null ? 0L : request.getCreatedAt().toEpochMilli(),
                    request.getAnsweredAt() == null ? null : request.getAnsweredAt().toEpochMilli()));
        }
        return result;
    }

    /** Пустой идентификатор — это «никого»; переводить его в uuid незачем. */
    private static boolean blank(String uid) {
        return uid == null || uid.isBlank();
    }

    /**
     * Порядок сохраняется, неизвестные пропускаются: игрок, которого нет в
     * мосте, — это строка списка без человека, и рисовать её нечем.
     */
    private List<String> toUids(List<UUID> players) {
        Map<UUID, String> uids = ids.playerUids(players);
        List<String> result = new ArrayList<>(players.size());
        for (UUID player : players) {
            String uid = uids.get(player);
            if (uid != null && !uid.isBlank()) {
                result.add(uid);
            }
        }
        return result;
    }
}
