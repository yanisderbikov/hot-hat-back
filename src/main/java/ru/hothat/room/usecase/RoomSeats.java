package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomSpectator;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.domain.RoomPresence;
import ru.hothat.room.domain.RosterRules;
import ru.hothat.sabotage.spi.SabotageArmoryPort;
import ru.hothat.util.Json;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Заведение, починка и снятие мест в комнате.
 *
 * <p>Одно место на пять входов: создание комнаты, вход по идентификатору,
 * принятие приглашения, перевод зрителя в игроки и вход зрителем. Все пять
 * заводят одну и ту же строку и одинаково её чинят, и до сих пор это делали
 * три независимые копии — {@code createRoom()} и {@code joinRoom()} в браузере
 * и две в {@code SocialServiceImpl}. Копии успели разойтись: клиентская клала
 * полный боезапас из десяти видов, серверные — усечённый из семи.
 *
 * <p>Имя и аватар всегда берутся из карточки игрока, а не из тела запроса.
 * Это и есть починка A1: под своим именем в комнату мог сесть кто угодно.
 * Исключение одно — идущая партия: там показывается имя, записанное на старте,
 * иначе смена ника посреди игры переименовала бы человека в чужом протоколе.
 */
@Component
@RequiredArgsConstructor
public class RoomSeats {

    /** Так подписан игрок, у которого нет ника. */
    private static final String PLACEHOLDER_PLAYER = "Игрок";

    /** Так подписан зритель без ника. */
    private static final String PLACEHOLDER_SPECTATOR = "Зритель";

    /** Столько знаков имени помещается на плашке под видео. */
    private static final int MAX_NAME_LENGTH = 40;

    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;
    /**
     * Обойма и боезапас принадлежат области диверсий: комната их только
     * записывает в своё место игрока, а называет их набор владелец.
     */
    private final SabotageArmoryPort armory;

    /** Откуда пришёл игрок: это записывается в его место и больше нигде не берётся. */
    public record Origin(String invitedByUid, String inviteId, String promotedByUid) {

        public static Origin self() {
            return new Origin(null, null, null);
        }

        public static Origin invite(String inviterUid, String inviteId) {
            return new Origin(inviterUid, inviteId, null);
        }

        public static Origin promotion(String hostUid) {
            return new Origin(null, null, hostUid);
        }
    }

    /** Место и то, завели ли его этим вызовом. */
    public record Seated(RoomPlayer player, boolean created) {
    }

    /**
     * Завести место игрока или починить существующее.
     *
     * <p>Починка нужна не из вежливости: старые сборки заводили место через
     * приглашение с неполной обоймой мемов, и такой игрок доходил до старта
     * партии, где вся комната упиралась в отказ «у игрока меньше пяти мемов».
     *
     * @param card    карточка игрока области профиля; {@code null} — карточки
     *                нет, тогда имя берётся из строки комнаты или из заглушки
     * @param loadout пять заряженных мемов из карточки: без них в комнату не пускают
     */
    public Seated seatPlayer(Room room, String uid, PlayerCardPort.Card card, List<String> loadout, Origin origin) {
        long now = System.currentTimeMillis();
        Optional<RoomPlayer> existing = getterRoom.getPlayer(room.getId(), uid);
        String frozenTeamId = frozenTeamOf(room, uid);
        RoomPlayer player = existing.orElseGet(() -> newPlayer(room, uid, now, origin));
        boolean created = existing.isEmpty();

        player.setName(displayName(room, uid, card, player.getName()));
        String avatar = card == null ? null : card.avatarDataUrl();
        if (avatar != null && !avatar.isBlank()) {
            player.setAvatarDataUrl(avatar);
        }
        if (frozenTeamId != null) {
            // Партия идёт: команда берётся из замороженного состава, а не из
            // строки игрока. Строку мог обнулить чужой сброс или неудачное
            // восстановление вкладки, и человек оказывался вне своей команды.
            player.setTeamId(frozenTeamId);
        }
        if (created || !armory.loadoutCharged(player.getMemeLoadout())) {
            player.setMemeLoadout(new ArrayList<>(loadout));
        }
        player.setLastSeenAt(now);
        player.setMediaRevision(now);
        saverRoom.savePlayer(player);
        // Одно место на человека: тот, кто раньше смотрел эту комнату из зала,
        // не должен остаться в нём же, сев за стол.
        saverRoom.deleteSpectator(room.getId(), uid);
        return new Seated(player, created);
    }

    /** Есть ли у человека место игрока в комнате — независимо от живости. */
    public boolean playerSeated(String roomId, String uid) {
        return getterRoom.getPlayer(roomId, uid).isPresent();
    }

    /** Место зрителя и то, завели ли его этим вызовом. */
    public record Watching(RoomSpectator spectator, boolean created) {
    }

    /**
     * Завести место зрителя или продлить существующее.
     *
     * <p>Лёгкую подписку превью с главной этот вызов повышает до настоящего
     * зрителя: человек, наводивший на карточку комнаты, теперь её открыл.
     * Обратного повышение не делает — снять признак может только закрытие
     * зрительского места.
     */
    public Watching seatSpectator(Room room, String uid, PlayerCardPort.Card card) {
        long now = System.currentTimeMillis();
        Optional<RoomSpectator> existing = getterRoom.getSpectator(room.getId(), uid);
        RoomSpectator spectator = existing.orElseGet(() -> RoomSpectator.builder()
                .roomId(room.getId())
                .uid(uid)
                .joinedAt(Instant.now())
                .role("spectator")
                .preview(false)
                .build());
        boolean created = existing.isEmpty() || Boolean.TRUE.equals(spectator.getPreview());
        spectator.setPreview(false);
        spectator.setRole("spectator");
        spectator.setName(nickname(card, PLACEHOLDER_SPECTATOR));
        String avatar = card == null ? null : card.avatarDataUrl();
        if (avatar != null && !avatar.isBlank()) {
            spectator.setAvatarDataUrl(avatar);
        }
        spectator.setLastSeenAt(now);
        saverRoom.saveSpectator(spectator);
        return new Watching(spectator, created);
    }

    /**
     * Живые игроки комнаты в порядке рассадки.
     *
     * <p>Одно чтение и один порог на все сценарии, которым важно, «кто сейчас
     * здесь»: жеребьёвка, сторож хозяйства, вместимость, счётчик витрины.
     * Раньше каждый считал сам, и порог в пять минут лежал пятью копиями во
     * фронтенде и двумя на сервере.
     */
    public List<RoomPlayer> alivePlayers(String roomId, long nowMs) {
        List<RoomPlayer> alive = new ArrayList<>();
        for (RoomPlayer player : getterRoom.getPlayers(roomId)) {
            boolean testBot = Boolean.TRUE.equals(player.getIsTestBot());
            long lastSeenAt = player.getLastSeenAt() == null ? 0L : player.getLastSeenAt();
            if (RoomPresence.playerAlive(testBot, lastSeenAt, nowMs)) {
                alive.add(player);
            }
        }
        return alive;
    }

    /**
     * Вычеркнуть игрока из состава его команды.
     *
     * <p>Возвращает команду, из которой вычеркнули, или пусто. Нужно и выходу,
     * и удалению хозяином, и роспуску команды: место, за которым никого нет,
     * продолжало бы получать ходы.
     */
    public Optional<RoomTeam> removeFromTeams(String roomId, String uid) {
        RoomTeam changed = null;
        for (RoomTeam team : getterRoom.getTeams(roomId)) {
            List<String> members = team.getMemberUids() == null ? List.of() : team.getMemberUids();
            if (members.contains(uid)) {
                List<String> kept = new ArrayList<>(members);
                kept.remove(uid);
                team.setMemberUids(kept);
                saverRoom.saveTeam(team);
                changed = team;
            }
        }
        return Optional.ofNullable(changed);
    }

    /**
     * Вычеркнуть игрока из замороженного состава партии.
     *
     * <p>Отдельно от команд комнаты: составы партии — другая карта, и уход
     * посреди игры обязан править обе, иначе ушедший продолжит получать ходы.
     */
    public void removeFromFrozenRoster(Room room, String uid) {
        Map<String, List<String>> rosters = frozenRosters(room);
        if (RosterRules.teamOf(rosters, uid) == null) {
            return;
        }
        room.setTeamRosters(new LinkedHashMap<>(RosterRules.without(rosters, uid)));
    }

    /** Замороженные составы партии как карта «команда → игроки». */
    public static Map<String, List<String>> frozenRosters(Room room) {
        Map<String, List<String>> rosters = new LinkedHashMap<>();
        Json.map(room.getTeamRosters()).forEach((teamId, value) -> rosters.put(teamId, Json.strings(value)));
        return rosters;
    }

    /** За какую команду человек играет в текущей партии; null — он в ней не участвует. */
    public static String frozenTeamOf(Room room, String uid) {
        return RosterRules.teamOf(frozenRosters(room), uid);
    }

    private RoomPlayer newPlayer(Room room, String uid, long now, Origin origin) {
        return RoomPlayer.builder()
                .roomId(room.getId())
                .uid(uid)
                .joinedAt(Instant.now())
                .lastSeenAt(now)
                .mediaRevision(now)
                .teamId(null)
                // Боезапас берётся у владельца снаряжения, а не из копии:
                // усечённый набор из семи видов, который клали серверные
                // ветки, отличался от клиентского и жил до первого старта.
                .arsenal(new LinkedHashMap<>(armory.baseArsenal()))
                .memeLoadout(new ArrayList<>())
                .usedMemeIds(new ArrayList<>())
                .memeAvailableIds(new ArrayList<>())
                .memeReserveIds(new ArrayList<>())
                .memeRecycleQueue(new ArrayList<>())
                .sabotageCooldownUntil(0L)
                .invitedBy(origin.invitedByUid())
                .inviteId(origin.inviteId())
                .promotedBy(origin.promotedByUid())
                .build();
    }

    /**
     * Под каким именем человек сидит в комнате.
     *
     * <p>В наборе — текущий ник из карточки. В идущей партии — имя, записанное
     * на старте: протокол хода, запись игры и подписи под видео должны звать
     * человека так же, как в начале.
     */
    private String displayName(Room room, String uid, PlayerCardPort.Card card, String storedInRoom) {
        String frozen = Json.str(Json.map(room.getGamePlayerNamesByUid()).get(uid));
        if (!"setup".equals(room.getPhase()) && !frozen.isBlank()) {
            return Json.str(frozen, MAX_NAME_LENGTH);
        }
        String fromCard = card == null ? null : card.nickname();
        if (fromCard != null && !fromCard.isBlank()) {
            return Json.str(fromCard, MAX_NAME_LENGTH);
        }
        return storedInRoom == null || storedInRoom.isBlank()
                ? PLACEHOLDER_PLAYER : Json.str(storedInRoom, MAX_NAME_LENGTH);
    }

    private static String nickname(PlayerCardPort.Card card, String placeholder) {
        String nickname = card == null ? null : card.nickname();
        return nickname == null || nickname.isBlank()
                ? placeholder : Json.str(nickname, MAX_NAME_LENGTH);
    }
}
