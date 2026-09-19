package ru.hothat.common.identity;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Перевод между идентификаторами двух поколений схемы.
 *
 * <p>Таблицы кластера v2 объявляют игрока и запись как uuid, а живая личность
 * до переезда области {@code auth} — это по-прежнему строка base62
 * ({@code AuthServiceImpl.newUid}). Мост существует ровно ради этого стыка
 * и уйдёт вместе с ним.
 *
 * <p>Прямой перевод ({@link #playerId(String)}) в базу не ходит: uuid считается
 * из самого идентификатора той же формулой, что записана в миграции —
 * {@code md5('player:' || uid)}. Поэтому писать в v2 можно, ничего не читая,
 * и в транзакции только для чтения тоже: расчёт не пишет.
 *
 * <p>Обратный перевод без таблицы невозможен — md5 не обращается, — и именно
 * поэтому таблица есть. Строки в неё кладут пишущие сценарии
 * ({@link #rememberPlayers(Collection)}), а игроков, заведённых до переезда,
 * положила сама миграция.
 */
@Component
@RequiredArgsConstructor
public class LegacyIdBridge {

    /** Виды идентификаторов; их же перечисляет ограничение таблицы. */
    private static final String PLAYER = "player";
    private static final String RECORDING = "recording";
    /**
     * Третий вид появился вместе с переездом обоймы мемов: слот обоймы
     * объявлен как uuid, а мем сегодня — строка «meme-<hex>» или
     * «builtin-<slug>». Уйдёт вместе с переездом кластера media.
     */
    private static final String MEME = "meme";
    /**
     * Четвёртый вид — приглашение в комнату: карточка в переписке ссылается на
     * него колонкой uuid, а сам идентификатор сегодня «ri-&lt;hex10&gt;».
     * Уйдёт вместе с переездом комнаты в {@code v2.room_invite}.
     */
    private static final String ROOM_INVITE = "room_invite";

    private final LegacyIdLinks links;

    /** uuid игрока по его uid. Чистый расчёт: обращения к базе нет. */
    public UUID playerId(String uid) {
        return derive(PLAYER, uid);
    }

    /** uuid записи игры по её идентификатору вида {@code hat-<hex>-<номер>}. */
    public UUID recordingId(String recordingId) {
        return derive(RECORDING, recordingId);
    }

    /** uuid мема по его сегодняшнему идентификатору. Тоже чистый расчёт. */
    public UUID memeId(String memeId) {
        return derive(MEME, memeId);
    }

    /** uuid приглашения в комнату по его идентификатору вида {@code ri-<hex10>}. */
    public UUID roomInviteId(String inviteId) {
        return derive(ROOM_INVITE, inviteId);
    }

    /**
     * Запомнить игроков, чьи uuid уходят в таблицы v2.
     *
     * <p>Зовётся из пишущих сценариев и только из них: чтение обязано
     * оставаться чтением. Каждая пара, попавшая в дружбу или в переписку,
     * проходит здесь, поэтому обратный перевод потом находит обе стороны.
     */
    public void rememberPlayers(Collection<String> uids) {
        for (String uid : distinct(uids)) {
            links.remember(derive(PLAYER, uid), PLAYER, uid);
        }
    }

    /** То же для одной записи игры, которой поделились в переписке. */
    public void rememberRecording(String recordingId) {
        if (recordingId != null && !recordingId.isBlank()) {
            links.remember(derive(RECORDING, recordingId), RECORDING, recordingId);
        }
    }

    /**
     * То же для приглашения в комнату.
     *
     * <p>Без этой строки карточка приглашения записалась бы, а прочиталась без
     * комнаты: в колонке лежит uuid, а кнопка «войти» знает старый
     * идентификатор, и обратно md5 не считается.
     */
    public void rememberRoomInvite(String inviteId) {
        if (inviteId != null && !inviteId.isBlank()) {
            links.remember(derive(ROOM_INVITE, inviteId), ROOM_INVITE, inviteId);
        }
    }

    /**
     * Запомнить мемы, чьи uuid уходят в обойму.
     *
     * <p>Без этого обойма записалась бы, а прочиталась пустой: в ответе стоят
     * строковые идентификаторы, а обратно md5 не считается. Мем, загруженный
     * после миграции, проходит здесь на первом же сохранении обоймы.
     */
    public void rememberMemes(Collection<String> memeIds) {
        for (String memeId : distinct(memeIds)) {
            links.remember(derive(MEME, memeId), MEME, memeId);
        }
    }

    /**
     * Обратный перевод пачкой: uuid → uid. Один запрос на весь список.
     *
     * <p>Игрока, которого в мосте нет, в ответе не будет: молчаливая подстановка
     * пустого uid нарисовала бы в списке друзей строку без человека.
     */
    public Map<UUID, String> playerUids(Collection<UUID> playerIds) {
        return legacyIds(PLAYER, playerIds);
    }

    /** Обратный перевод записей игры пачкой; правило то же. */
    public Map<UUID, String> recordingIds(Collection<UUID> recordingIds) {
        return legacyIds(RECORDING, recordingIds);
    }

    /** Обратный перевод мемов пачкой: одна обойма — один запрос, а не пять. */
    public Map<UUID, String> memeIds(Collection<UUID> memeIds) {
        return legacyIds(MEME, memeIds);
    }

    /** Обратный перевод приглашений: страница истории — один запрос на все её карточки. */
    public Map<UUID, String> roomInviteIds(Collection<UUID> inviteIds) {
        return legacyIds(ROOM_INVITE, inviteIds);
    }

    /**
     * Порядок в паре — тот же, что у базы.
     *
     * <p>Ограничения {@code player_low < player_high} проверяет Postgres, а он
     * сравнивает uuid побайтно, то есть без знака. {@link UUID#compareTo}
     * сравнивает два {@code long} со знаком и на половине значений отвечает
     * ровно наоборот. Поэтому пара упорядочивается здесь и только здесь.
     */
    public static boolean before(UUID a, UUID b) {
        int high = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return high != 0
                ? high < 0
                : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits()) < 0;
    }

    /** Меньший из пары — колонка {@code player_low}. */
    public static UUID low(UUID a, UUID b) {
        return before(a, b) ? a : b;
    }

    /** Больший из пары — колонка {@code player_high}. */
    public static UUID high(UUID a, UUID b) {
        return before(a, b) ? b : a;
    }

    private Map<UUID, String> legacyIds(String kind, Collection<UUID> ids) {
        Set<UUID> wanted = new LinkedHashSet<>();
        for (UUID id : ids) {
            if (id != null) {
                wanted.add(id);
            }
        }
        if (wanted.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> byId = new HashMap<>();
        for (LegacyIdLink link : links.findByKindAndV2IdIn(kind, wanted)) {
            byId.put(link.getV2Id(), link.getLegacyId());
        }
        return byId;
    }

    private static List<String> distinct(Collection<String> values) {
        List<String> wanted = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank() && !wanted.contains(value)) {
                wanted.add(value);
            }
        }
        return wanted;
    }

    /**
     * Та же формула, что в миграции: {@code md5(kind || ':' || legacyId)}.
     * Байты md5 ложатся в uuid как есть — без версии и варианта, — потому что
     * именно так их кладёт приведение {@code md5(text)::uuid} в Postgres:
     * {@link UUID#nameUUIDFromBytes} правит два байта и разошлась бы с базой.
     */
    private static UUID derive(String kind, String legacyId) {
        if (legacyId == null || legacyId.isBlank()) {
            throw new IllegalArgumentException("Идентификатор пуст: перевести нечего.");
        }
        byte[] hash = md5((kind + ":" + legacyId).getBytes(StandardCharsets.UTF_8));
        long high = 0;
        long low = 0;
        for (int i = 0; i < 8; i++) {
            high = (high << 8) | (hash[i] & 0xffL);
        }
        for (int i = 8; i < 16; i++) {
            low = (low << 8) | (hash[i] & 0xffL);
        }
        return new UUID(high, low);
    }

    private static byte[] md5(byte[] value) {
        try {
            return MessageDigest.getInstance("MD5").digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
