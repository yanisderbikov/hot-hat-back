package ru.hothat.admin.store;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.common.identity.LegacyIdBridge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Единственная дверь области {@code admin} в таблицы модерации.
 *
 * <p>Наружу отдаёт записи, а не сущности: {@code PlayerBan} и
 * {@code ModerationAction} не публичны. Здесь же живёт перевод uid ↔ uuid.
 *
 * <p>Раньше на вопрос «заблокирован ли он» отвечали три источника сразу:
 * булев флаг {@code app_user.banned}, строка {@code user_ban} с причиной и
 * счётчик отзыва токенов. Разойтись им было нечем — только временем. Теперь
 * их два, и они не пересекаются: факт блокировки — строка без {@code lifted_at}
 * здесь, мгновенный отзыв доступа — {@code token_version} в области auth.
 */
@Component
@RequiredArgsConstructor
public class ModerationStore {

    /** Виды действий и подопечных; наборы закрыты ограничениями базы. */
    public static final String SUBJECT_PLAYER = ModerationAction.PLAYER;
    public static final String SUBJECT_ROOM = ModerationAction.ROOM;
    public static final String SUBJECT_MEME = ModerationAction.MEME;
    public static final String SUBJECT_RECORDING = ModerationAction.RECORDING;

    public static final String ACTION_BAN = "ban_user";
    public static final String ACTION_LIFT_BAN = "lift_ban";

    private final PlayerBans bans;
    private final ModerationActions actions;
    private final LegacyIdBridge ids;

    /** Действующая блокировка игрока; пусто — не заблокирован. */
    public Optional<Ban> activeBan(String uid) {
        if (blank(uid)) {
            return Optional.empty();
        }
        return bans.findFirstByPlayerIdAndLiftedAtIsNull(ids.playerId(uid)).map(row -> view(uid, row));
    }

    /**
     * Заблокированные из названных. В карте только те, у кого блокировка
     * действует прямо сейчас; пустой список в базу не идёт.
     */
    public Map<String, Ban> activeBans(Collection<String> uids) {
        List<String> wanted = distinct(uids);
        if (wanted.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> byPlayerId = new LinkedHashMap<>();
        for (String uid : wanted) {
            byPlayerId.put(ids.playerId(uid), uid);
        }
        Map<String, Ban> result = new LinkedHashMap<>();
        for (PlayerBan row : bans.findByPlayerIdInAndLiftedAtIsNull(byPlayerId.keySet())) {
            String uid = byPlayerId.get(row.getPlayerId());
            result.put(uid, view(uid, row));
        }
        return result;
    }

    /**
     * Записать блокировку.
     *
     * <p>Повторная блокировка уже заблокированного не заводит второй строки:
     * её и не примет частичный уникальный индекс. Возвращается действующая —
     * тогда ответ администратору описывает то, что есть на самом деле, а не
     * то, что он только что попросил.
     */
    public Ban ban(String uid, String reason, String byUid) {
        UUID playerId = ids.playerId(uid);
        Optional<PlayerBan> existing = bans.findFirstByPlayerIdAndLiftedAtIsNull(playerId);
        if (existing.isPresent()) {
            return view(uid, existing.get());
        }
        PlayerBan saved = bans.saveAndFlush(PlayerBan.builder()
                .playerId(playerId)
                .reason(reason)
                .bannedBy(ids.playerId(byUid))
                .build());
        return view(uid, saved);
    }

    /**
     * Снять блокировку.
     *
     * @return {@code false} — действующей блокировки не было; тогда и снимать
     *         нечего, а вызывающий вправе не считать это ошибкой
     */
    public boolean liftBan(String uid, String byUid) {
        Optional<PlayerBan> active = bans.findFirstByPlayerIdAndLiftedAtIsNull(ids.playerId(uid));
        if (active.isEmpty()) {
            return false;
        }
        PlayerBan row = active.get();
        row.setLiftedAt(Instant.now());
        row.setLiftedBy(ids.playerId(byUid));
        bans.save(row);
        return true;
    }

    /**
     * Оставить след решения. Автор без внешнего ключа намеренно: запись о том,
     * кто и что решил, обязана пережить учётку решившего.
     */
    public void record(String actorUid, String action, String subjectType, String subjectId,
                       Map<String, Object> details) {
        actions.save(ModerationAction.builder()
                .actorPlayerId(ids.playerId(actorUid))
                .action(action)
                .subjectType(subjectType)
                .subjectId(subjectId)
                .details(details)
                .build());
    }

    /**
     * Блокировка так, как её видит сценарий. Кто заблокировал — uid, а не
     * uuid: этим идентификатором говорят и ответ администратору, и журнал.
     */
    public record Ban(String uid, String reason, String byUid, long bannedAtMs) {
    }

    private Ban view(String uid, PlayerBan row) {
        return new Ban(
                uid,
                row.getReason(),
                ids.playerUids(List.of(row.getBannedBy())).get(row.getBannedBy()),
                row.getBannedAt() == null ? 0L : row.getBannedAt().toEpochMilli());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static List<String> distinct(Collection<String> values) {
        Set<String> wanted = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                wanted.add(value);
            }
        }
        return new ArrayList<>(wanted);
    }
}
