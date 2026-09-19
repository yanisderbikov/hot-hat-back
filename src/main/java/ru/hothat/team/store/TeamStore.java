package ru.hothat.team.store;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import ru.hothat.common.identity.LegacyIdBridge;
import ru.hothat.util.Ids;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Единственная дверь области команды в свои таблицы.
 *
 * <p>Наружу отдаёт записи, а не сущности: шесть классов {@code team.store} не
 * публичны и в сигнатуры сценариев не попадают. Здесь же живёт перевод
 * uid ↔ uuid и teamId ↔ uuid, поэтому сценарии продолжают говорить теми
 * идентификаторами, которые стоят в ответах и приходят от клиента.
 *
 * <p>Владелец принадлежности к команде здесь один. Раньше её писали два
 * места сразу — состав в {@code ranked_team.member_uids} и та же связь в
 * {@code app_user.ranked_team_id}, — и «в какой я команде» зависело от того,
 * кого спросить. Теперь ответ один: строка {@code ranked_team_member}.
 *
 * <p>Ни одно чтение не ходит в базу в цикле: карточка команды — это команда,
 * её состав и обратный перевод игроков, сколько бы участников ни было;
 * таблица рейтинга — те же три запроса на сотню команд сразу.
 */
@Component
@RequiredArgsConstructor
public class TeamStore {

    /** Состояния команды и участия; наборы закрыты ограничениями базы. */
    public static final String PENDING = "pending";
    public static final String ACTIVE = "active";
    public static final String ACCEPTED = "accepted";
    public static final String DECLINED = "declined";

    /** Роли в паре; набор закрыт ограничением базы. */
    public static final String CAPTAIN = "captain";
    public static final String PARTNER = "partner";

    private final Teams teams;
    private final TeamLogos logos;
    private final TeamMemberships memberships;
    private final TeamInvitations invitations;
    private final TeamPreflightSessions sessions;
    private final TeamPreflightParticipants participants;
    private final LegacyIdBridge ids;

    // ───────────────────────────── чтение команды ─────────────────────────────

    /**
     * Команда игрока в любом состоянии — та самая единственная строка, которая
     * заменила колонку в карточке игрока. Пусто — команды нет.
     */
    public Optional<TeamRow> teamOf(String uid) {
        if (blank(uid)) {
            return Optional.empty();
        }
        return memberships.findByPlayerId(ids.playerId(uid))
                .flatMap(row -> team(row.getTeamId()));
    }

    /** Команда по идентификатору из адреса; неразбираемый — это «нет такой». */
    public Optional<TeamRow> team(String teamId) {
        UUID id = uuid(teamId);
        return id == null ? Optional.empty() : team(id);
    }

    /**
     * Карточки многих команд разом: строка рейтинга называет команду по имени
     * и составу, и читать их по одной значило бы сто запросов на сотню строк.
     */
    public Map<String, TeamRow> teams(Collection<String> teamIds) {
        List<UUID> wanted = uuids(teamIds);
        if (wanted.isEmpty()) {
            return Map.of();
        }
        List<Team> found = teams.findByIdIn(wanted);
        Map<UUID, List<MemberRow>> byTeam = members(wanted);
        Map<String, TeamRow> result = new LinkedHashMap<>();
        for (Team team : found) {
            result.put(team.getId().toString(),
                    row(team, byTeam.getOrDefault(team.getId(), List.of())));
        }
        return result;
    }

    /** Логотипы названных команд; в карте лежат только те, у кого он есть. */
    public Map<String, String> logos(Collection<String> teamIds) {
        List<UUID> wanted = uuids(teamIds);
        if (wanted.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (TeamLogo logo : logos.findByTeamIdIn(wanted)) {
            result.put(logo.getTeamId().toString(), logo.getDataUrl());
        }
        return result;
    }

    /** Логотип одной команды; пусто — его нет. */
    public Optional<String> logo(String teamId) {
        UUID id = uuid(teamId);
        return id == null ? Optional.empty() : logos.findById(id).map(TeamLogo::getDataUrl);
    }

    /**
     * Занято ли название. Сравнение идёт по тому же ключу, который считает
     * база ({@code lower(btrim(name))}), поэтому ответ здесь и уникальный
     * индекс не могут разойтись.
     */
    public boolean nameTaken(String name) {
        return teams.existsByNameKey(Ids.key(name));
    }

    // ───────────────────────────── основание и роспуск ─────────────────────────────

    /**
     * Основать команду и позвать напарника — одной записью.
     *
     * <p>Команда, строка капитана и приглашение появляются вместе: команда без
     * состава или без приглашения — это пара, которую никто не подтвердит.
     * Транзакцию держит вызывающий сценарий.
     */
    public Founded found(String captainUid, String inviteeUid, String name,
                         String divisionLanguage, String logoDataUrl) {
        ids.rememberPlayers(List.of(captainUid, inviteeUid));
        UUID teamId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        // Команда уходит в базу до всего остального: на неё ссылаются и
        // состав, и логотип, и приглашение, и порядок вставок иначе зависел
        // бы от того, как их разложит очередь действий.
        teams.saveAndFlush(Team.builder()
                .id(teamId)
                .name(name)
                .divisionLanguage(divisionLanguage)
                .status(PENDING)
                .build());
        // Логотипа может не быть вовсе — тогда и строки на 280 КБ не заводим.
        if (!blank(logoDataUrl)) {
            logos.save(TeamLogo.builder().teamId(teamId).dataUrl(logoDataUrl).build());
        }
        memberships.save(TeamMembership.builder()
                .teamId(teamId)
                .playerId(ids.playerId(captainUid))
                .role(CAPTAIN)
                .status(PENDING)
                .build());
        invitations.save(TeamInvitation.builder()
                .id(inviteId)
                .teamId(teamId)
                .inviteePlayerId(ids.playerId(inviteeUid))
                .status(PENDING)
                .build());
        return new Founded(teamId.toString(), inviteId.toString());
    }

    /**
     * Распустить команду. Уносит с собой состав, логотип, приглашение и
     * проверку готовности — всё это ссылается на команду с каскадом.
     *
     * <p>Отклонённое приглашение при этом исчезает вместе с командой, и это
     * намеренно: читают только ждущие ответа, а «отказ такого-то» не
     * показывает ни один экран.
     */
    public void disband(String teamId) {
        UUID id = uuid(teamId);
        if (id == null) {
            return;
        }
        cancel(id);
        invitations.deleteAll(invitations.findByTeamId(id));
        memberships.deleteAll(memberships.findByTeamIdOrderByRoleAsc(id));
        logos.findById(id).ifPresent(logos::delete);
        teams.findById(id).ifPresent(teams::delete);
    }

    // ───────────────────────────── приглашения ─────────────────────────────

    /** Приглашение по номеру вместе с командой, в которую зовут. */
    public Optional<InviteRow> invite(String inviteId) {
        UUID id = uuid(inviteId);
        return id == null ? Optional.empty() : invitations.findById(id).flatMap(this::inviteRow);
    }

    /** Входящие приглашения, ждущие ответа; предел уезжает в базу. */
    public List<InviteRow> pendingInvites(String uid, int limit) {
        if (blank(uid) || limit <= 0) {
            return List.of();
        }
        List<TeamInvitation> found = invitations.findByInviteePlayerIdAndStatusOrderByCreatedAtDesc(
                ids.playerId(uid), PENDING, Limit.of(limit));
        if (found.isEmpty()) {
            return List.of();
        }
        // Команды и их составы — двумя запросами на весь список, а не по одному
        // на приглашение: зовущего называет строка состава, а не копия ника.
        List<UUID> teamIds = new ArrayList<>(found.size());
        for (TeamInvitation invite : found) {
            teamIds.add(invite.getTeamId());
        }
        Map<UUID, Team> byId = new HashMap<>();
        for (Team team : teams.findByIdIn(teamIds)) {
            byId.put(team.getId(), team);
        }
        Map<UUID, List<MemberRow>> byTeam = members(teamIds);
        List<InviteRow> result = new ArrayList<>(found.size());
        for (TeamInvitation invite : found) {
            Team team = byId.get(invite.getTeamId());
            if (team == null) {
                continue;
            }
            result.add(inviteRow(invite, row(team, byTeam.getOrDefault(team.getId(), List.of()))));
        }
        return result;
    }

    /**
     * Согласие: команда становится подтверждённой, оба участника — активными.
     *
     * <p>Строка напарника появляется только здесь. Именно поэтому уникальный
     * индекс «одна команда на игрока» не мешает звать одного и того же
     * человека в две разные команды: пока он не согласился, его строки нет.
     */
    public void acceptInvite(String inviteId, String inviteeUid) {
        UUID id = uuid(inviteId);
        if (id == null) {
            return;
        }
        TeamInvitation invite = invitations.findById(id).orElse(null);
        if (invite == null) {
            return;
        }
        ids.rememberPlayers(List.of(inviteeUid));
        Instant now = Instant.now();
        invite.setStatus(ACCEPTED);
        invite.setAnsweredAt(now);
        invitations.save(invite);

        Team team = teams.findById(invite.getTeamId()).orElse(null);
        if (team != null) {
            team.setStatus(ACTIVE);
            team.setConfirmedAt(now);
            teams.save(team);
        }
        for (TeamMembership member : memberships.findByTeamIdOrderByRoleAsc(invite.getTeamId())) {
            member.setStatus(ACTIVE);
            memberships.save(member);
        }
        memberships.save(TeamMembership.builder()
                .teamId(invite.getTeamId())
                .playerId(ids.playerId(inviteeUid))
                .role(PARTNER)
                .status(ACTIVE)
                .joinedAt(now)
                .build());
    }

    // ───────────────────────────── проверка готовности ─────────────────────────────

    /**
     * Начать проверку заново. Поколение сдвигается, а строки участников
     * удаляются: готовность прошлой проверки новой не достаётся.
     */
    public void startPreflight(String teamId, String intent, String gameMode, String requestedRoomId,
                               String initiatorUid, Instant expiresAt) {
        UUID id = uuid(teamId);
        if (id == null) {
            return;
        }
        ids.rememberPlayers(List.of(initiatorUid));
        // Строки участников удаляются, а сессия переписывается в той же
        // строке: ключ у неё — сама команда, второй проверки у пары быть не
        // может. Флаги готовности прошлой проверки новой не достаются, и
        // поколение сдвигается, чтобы это было видно и в самой строке.
        participants.deleteByTeamId(id);
        Instant now = Instant.now();
        TeamPreflightSession session = sessions.findById(id)
                .orElseGet(() -> TeamPreflightSession.builder().teamId(id).sessionSeq(0).build());
        session.setSessionSeq(session.getSessionSeq() + 1);
        session.setIntent(intent);
        session.setGameMode(gameMode);
        session.setRequestedRoomId(blank(requestedRoomId) ? null : requestedRoomId);
        session.setInitiatorPlayerId(ids.playerId(initiatorUid));
        session.setStartedAt(now);
        session.setUpdatedAt(now);
        session.setExpiresAt(expiresAt);
        // Прошлый поиск новой проверке не наследуется: пара начала заново, и
        // «комната найдена» из прошлой попытки увезло бы её в чужую партию.
        session.setTargetRoomId(null);
        session.setRoomReady(false);
        session.setSearchStarted(false);
        session.setSearchCount(0);
        session.setFailed(false);
        sessions.save(session);
    }

    /** Снимок проверки со всеми участниками; пусто — проверки нет. */
    public Optional<PreflightRow> preflight(String teamId) {
        UUID id = uuid(teamId);
        if (id == null) {
            return Optional.empty();
        }
        return sessions.findById(id).map(session -> preflightRow(session, participants.findByTeamId(id)));
    }

    /**
     * Отчёт о своей камере и микрофоне.
     *
     * <p>Отметку времени ставит сервер: срок жизни признака считает он же, а у
     * клиента часы уезжают. Пропавшая связь снимает и готовность — ограничение
     * базы {@code ck_team_preflight_participant_ready} иначе отвергло бы строку.
     */
    public void reportMedia(String teamId, String uid, boolean mediaOk) {
        UUID id = uuid(teamId);
        if (id == null) {
            return;
        }
        TeamPreflightSession session = sessions.findById(id).orElse(null);
        if (session == null) {
            return;
        }
        ids.rememberPlayers(List.of(uid));
        UUID playerId = ids.playerId(uid);
        Instant now = Instant.now();
        TeamPreflightParticipant row = participants
                .findById(new TeamPreflightParticipantId(id, playerId))
                .orElseGet(() -> TeamPreflightParticipant.builder()
                        .teamId(id)
                        .playerId(playerId)
                        .sessionSeq(session.getSessionSeq())
                        .build());
        row.setSessionSeq(session.getSessionSeq());
        row.setMediaOk(mediaOk);
        row.setMediaOkAt(mediaOk ? now : null);
        if (!mediaOk) {
            row.setReady(false);
        }
        row.setUpdatedAt(now);
        participants.save(row);
    }

    /**
     * Поставить или снять свою готовность.
     *
     * @return {@code false} — связи нет, и «готов» ставить не на что
     */
    public boolean setReady(String teamId, String uid, boolean ready, long mediaFlagTtlMs) {
        UUID id = uuid(teamId);
        if (id == null) {
            return false;
        }
        TeamPreflightSession session = sessions.findById(id).orElse(null);
        if (session == null) {
            return false;
        }
        UUID playerId = ids.playerId(uid);
        TeamPreflightParticipant row = participants
                .findById(new TeamPreflightParticipantId(id, playerId))
                .orElse(null);
        if (ready && !fresh(row, mediaFlagTtlMs)) {
            return false;
        }
        if (row == null) {
            // Снять готовность, которой не было, — это не ошибка: строки нет,
            // и состояние уже такое, каким его просят сделать.
            return true;
        }
        row.setSessionSeq(session.getSessionSeq());
        row.setReady(ready);
        row.setUpdatedAt(Instant.now());
        participants.save(row);
        return true;
    }

    /** Отменить проверку. Повторная отмена — не ошибка: удалять уже нечего. */
    public void cancelPreflight(String teamId) {
        UUID id = uuid(teamId);
        if (id != null) {
            cancel(id);
        }
    }

    /**
     * Что сообщил подбор соперников: найденная комната, её готовность и
     * сколько человек уже собралось.
     *
     * <p>Пишет сюда область лобби через свой порт, а не в таблицу напрямую:
     * иначе у проверки готовности снова оказалось бы два хозяина.
     */
    public void reportSearch(String teamId, String targetRoomId, boolean roomReady,
                             boolean searchStarted, Integer searchCount, boolean failed) {
        UUID id = uuid(teamId);
        if (id == null) {
            return;
        }
        sessions.findById(id).ifPresent(session -> {
            session.setTargetRoomId(blank(targetRoomId) ? session.getTargetRoomId() : targetRoomId);
            session.setRoomReady(roomReady);
            session.setSearchStarted(searchStarted);
            if (searchCount != null) {
                session.setSearchCount(searchCount);
            }
            session.setFailed(failed);
            session.setUpdatedAt(Instant.now());
            sessions.save(session);
        });
    }

    // ───────────────────────────── записи наружу ─────────────────────────────

    /** Команда так, как её видит сценарий: составом из uid, а не из uuid. */
    public record TeamRow(String teamId, String name, String divisionLanguage, String status,
                          long createdAtMs, String captainUid, List<MemberRow> members) {

        /** Идентификаторы участников в порядке показа: капитан первым. */
        public List<String> memberUids() {
            List<String> uids = new ArrayList<>(members.size());
            for (MemberRow member : members) {
                uids.add(member.uid());
            }
            return uids;
        }

        public boolean isActive() {
            return ACTIVE.equals(status);
        }

        public boolean hasMember(String uid) {
            return uid != null && memberUids().contains(uid);
        }
    }

    public record MemberRow(String uid, String role, String status) {
    }

    /** Приглашение вместе с командой: экран называет и то и другое. */
    public record InviteRow(String inviteId, String inviteeUid, String status,
                            long createdAtMs, TeamRow team) {

        public String captainUid() {
            return team.captainUid();
        }
    }

    /** Что вернуло основание команды: сама команда и ушедшее приглашение. */
    public record Founded(String teamId, String inviteId) {
    }

    /** Снимок проверки готовности; свежесть связи считает сценарий. */
    public record PreflightRow(String teamId, String intent, String gameMode, String requestedRoomId,
                               String initiatorUid, long startedAtMs, long updatedAtMs, long expiresAtMs,
                               String targetRoomId, boolean roomReady, boolean searchStarted,
                               int searchCount, boolean failed, List<ParticipantRow> participants) {

        /** Истекла ли проверка. Решает сервер: у клиента часы могут уехать. */
        public boolean expired(long nowMs) {
            return expiresAtMs < nowMs;
        }

        public ParticipantRow participant(String uid) {
            for (ParticipantRow row : participants) {
                if (row.uid().equals(uid)) {
                    return row;
                }
            }
            return null;
        }
    }

    public record ParticipantRow(String uid, boolean mediaOk, long mediaOkAtMs, boolean ready) {

        /** Признак связи протухает; без свежести «готов» ничего не значит. */
        public boolean freshMedia(long nowMs, long ttlMs) {
            return mediaOk && nowMs - mediaOkAtMs < ttlMs;
        }
    }

    // ───────────────────────────── внутреннее ─────────────────────────────

    /** Убрать проверку вместе с её участниками; строк могло и не быть. */
    private void cancel(UUID teamId) {
        participants.deleteByTeamId(teamId);
        sessions.findById(teamId).ifPresent(sessions::delete);
    }

    private Optional<TeamRow> team(UUID teamId) {
        return teams.findById(teamId)
                .map(team -> row(team, members(List.of(teamId)).getOrDefault(teamId, List.of())));
    }

    /**
     * Составы названных команд с обратным переводом игроков — два запроса на
     * любое их число: строки участия и мост uuid → uid.
     */
    private Map<UUID, List<MemberRow>> members(Collection<UUID> teamIds) {
        List<TeamMembership> rows = memberships.findByTeamIdInOrderByRoleAsc(teamIds);
        List<UUID> players = new ArrayList<>(rows.size());
        for (TeamMembership row : rows) {
            players.add(row.getPlayerId());
        }
        Map<UUID, String> uids = ids.playerUids(players);
        Map<UUID, List<MemberRow>> byTeam = new LinkedHashMap<>();
        for (TeamMembership row : rows) {
            String uid = uids.get(row.getPlayerId());
            if (uid == null || uid.isBlank()) {
                // Игрок, которого нет в мосте, — это строка состава без
                // человека: нарисовать её нечем, и подставлять пустой uid
                // хуже, чем пропустить.
                continue;
            }
            byTeam.computeIfAbsent(row.getTeamId(), key -> new ArrayList<>())
                    .add(new MemberRow(uid, row.getRole(), row.getStatus()));
        }
        return byTeam;
    }

    private static TeamRow row(Team team, List<MemberRow> members) {
        String captainUid = null;
        for (MemberRow member : members) {
            if (CAPTAIN.equals(member.role())) {
                captainUid = member.uid();
                break;
            }
        }
        return new TeamRow(
                team.getId().toString(),
                team.getName(),
                team.getDivisionLanguage(),
                team.getStatus(),
                team.getCreatedAt() == null ? 0L : team.getCreatedAt().toEpochMilli(),
                captainUid,
                members);
    }

    private Optional<InviteRow> inviteRow(TeamInvitation invite) {
        return team(invite.getTeamId()).map(team -> inviteRow(invite, team));
    }

    private InviteRow inviteRow(TeamInvitation invite, TeamRow team) {
        String inviteeUid = ids.playerUids(List.of(invite.getInviteePlayerId()))
                .getOrDefault(invite.getInviteePlayerId(), "");
        return new InviteRow(
                invite.getId().toString(),
                inviteeUid,
                invite.getStatus(),
                invite.getCreatedAt() == null ? 0L : invite.getCreatedAt().toEpochMilli(),
                team);
    }

    private PreflightRow preflightRow(TeamPreflightSession session, List<TeamPreflightParticipant> rows) {
        List<UUID> players = new ArrayList<>(rows.size() + 1);
        players.add(session.getInitiatorPlayerId());
        for (TeamPreflightParticipant row : rows) {
            players.add(row.getPlayerId());
        }
        Map<UUID, String> uids = ids.playerUids(players);
        List<ParticipantRow> found = new ArrayList<>(rows.size());
        for (TeamPreflightParticipant row : rows) {
            String uid = uids.get(row.getPlayerId());
            if (uid == null || uid.isBlank()) {
                continue;
            }
            found.add(new ParticipantRow(
                    uid,
                    Boolean.TRUE.equals(row.getMediaOk()),
                    row.getMediaOkAt() == null ? 0L : row.getMediaOkAt().toEpochMilli(),
                    Boolean.TRUE.equals(row.getReady())));
        }
        return new PreflightRow(
                session.getTeamId().toString(),
                session.getIntent(),
                session.getGameMode(),
                session.getRequestedRoomId(),
                uids.getOrDefault(session.getInitiatorPlayerId(), ""),
                session.getStartedAt() == null ? 0L : session.getStartedAt().toEpochMilli(),
                session.getUpdatedAt() == null ? 0L : session.getUpdatedAt().toEpochMilli(),
                session.getExpiresAt() == null ? 0L : session.getExpiresAt().toEpochMilli(),
                session.getTargetRoomId(),
                Boolean.TRUE.equals(session.getRoomReady()),
                Boolean.TRUE.equals(session.getSearchStarted()),
                session.getSearchCount() == null ? 0 : session.getSearchCount(),
                Boolean.TRUE.equals(session.getFailed()),
                found);
    }

    private static boolean fresh(TeamPreflightParticipant row, long ttlMs) {
        return row != null
                && Boolean.TRUE.equals(row.getMediaOk())
                && row.getMediaOkAt() != null
                && System.currentTimeMillis() - row.getMediaOkAt().toEpochMilli() < ttlMs;
    }

    /**
     * Идентификатор команды приходит строкой из адреса. Неразбираемый — это не
     * ошибка запроса, а «такой команды нет»: 400 про существующий формат чужой
     * схемы врал бы сильнее, чем 404.
     */
    private static UUID uuid(String value) {
        if (blank(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<UUID> uuids(Collection<String> values) {
        List<UUID> result = new ArrayList<>(values.size());
        for (String value : values) {
            UUID id = uuid(value);
            if (id != null && !result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
