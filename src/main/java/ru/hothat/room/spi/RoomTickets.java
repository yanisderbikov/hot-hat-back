package ru.hothat.room.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.model.room.Room;
import ru.hothat.model.room.RoomPlayer;
import ru.hothat.model.room.RoomTeam;
import ru.hothat.repository.GetterRoom;
import ru.hothat.repository.SaverRoom;
import ru.hothat.util.Divisions;
import ru.hothat.util.Ids;
import ru.hothat.util.Json;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Комнаты подбора: реализация {@link RoomTicketPort}.
 *
 * <p>Переезд той половины {@code MatchmakingServiceImpl}, которая писала в
 * комнату. Вторая половина — отбор — осталась в лобби: здесь нет ни одного
 * решения о том, кого с кем сводить, только записи.
 *
 * <p>Название автокомнаты, слоты команд и стартовые места пары собраны в одном
 * месте по той же причине, по которой в одном месте собрана рассадка обычной
 * комнаты: три ветки, заводившие эти строки порознь, успели разойтись.
 */
@Service
@RequiredArgsConstructor
public class RoomTickets implements RoomTicketPort {

    /** Столько длится ход в автокомнате по умолчанию. */
    private static final int DEFAULT_TURN_SECONDS = 60;

    /** Вес комнаты без собственного рейтинга: середина шкалы. */
    private static final int NEUTRAL_RATING = 1000;

    private static final String QUICK_ROOM_NAME = "Быстрая автокомната";
    private static final String RANKED_ROOM_NAME = "Рейтинговая автокомната";

    private final GetterRoom getterRoom;
    private final SaverRoom saverRoom;

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public List<TicketRoom> openTicketRooms(int limit) {
        List<TicketRoom> rooms = new ArrayList<>();
        for (Room room : getterRoom.getByPhase("setup", limit)) {
            if (Boolean.TRUE.equals(room.getManagedMatchmaking())) {
                rooms.add(ticket(room));
            }
        }
        return rooms;
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Optional<TicketRoom> ticketRoom(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return Optional.empty();
        }
        return getterRoom.getById(roomId).map(RoomTickets::ticket);
    }

    @Override
    @Transactional
    public TicketRoom openManagedRoom(NewManagedRoom spec) {
        String id = Ids.newRoomId();
        long now = System.currentTimeMillis();
        List<String> memberUids = new ArrayList<>();
        Map<String, Object> names = new LinkedHashMap<>();
        if (spec.pair() != null) {
            for (PairMember member : spec.pair().members()) {
                memberUids.add(member.uid());
                names.put(member.uid(), member.nickname());
            }
        } else if (spec.applicant() != null) {
            memberUids.add(spec.applicant().uid());
            names.put(spec.applicant().uid(), spec.applicant().nickname());
        }

        // Слоты команд заводятся сразу все: комната на десятерых — это пять
        // слотов по двое, и порядок у них тот же, что покажет экран.
        List<String> teamOrder = new ArrayList<>();
        for (int i = 1; i <= spec.maxPlayers() / 2; i++) {
            teamOrder.add("team-" + i);
        }
        Map<String, Object> slots = new LinkedHashMap<>();
        Map<String, Object> assignments = new LinkedHashMap<>();
        if (spec.pair() != null) {
            slots.put("team-1", spec.pair().rankedTeamId());
            memberUids.forEach(uid -> assignments.put(uid, "team-1"));
        }

        Room room = Room.builder()
                .id(id)
                .name(spec.ranked() ? RANKED_ROOM_NAME : QUICK_ROOM_NAME)
                .phase("setup")
                .createdBy(spec.hostUid())
                .managedMatchmaking(true)
                .ranked(spec.ranked())
                .gameMode(spec.gameMode())
                .isPrivate(false)
                .maxParticipants(spec.maxPlayers())
                .maxPlayers(spec.maxPlayers())
                .ratingTarget(spec.ratingTarget())
                .divisionLanguage(spec.divisionLanguage())
                .gameLanguage(spec.matchmakingLanguage())
                .matchmakingLanguage(spec.matchmakingLanguage())
                .lastActivityAt(now)
                .matchmakingStartedAt(now)
                .matchmakingDeadline(spec.deadlineMs())
                .matchmakingUids(memberUids)
                .matchmakingNames(names)
                .matchmakingTeamIds(spec.pair() == null
                        ? new ArrayList<>() : new ArrayList<>(List.of(spec.pair().rankedTeamId())))
                .rankedTeamSlots(slots)
                .rankedTeamAssignments(assignments)
                .matchmakingReady(false)
                .matchmakingMinPlayers(spec.maxPlayers())
                .turnDuration(DEFAULT_TURN_SECONDS)
                .teamOrder(teamOrder)
                .gameNumber(0)
                .build();
        saverRoom.save(room);

        List<RoomTeam> teams = new ArrayList<>();
        for (int i = 1; i <= spec.maxPlayers() / 2; i++) {
            boolean pairSlot = spec.pair() != null && i == 1;
            teams.add(RoomTeam.builder()
                    .roomId(id)
                    .teamId("team-" + i)
                    .name(pairSlot ? spec.pair().name() : "Команда " + i)
                    .order(i - 1)
                    .memberUids(pairSlot ? new ArrayList<>(memberUids) : new ArrayList<>())
                    .rankedTeamId(pairSlot ? spec.pair().rankedTeamId() : null)
                    .score(0)
                    .build());
        }
        saverRoom.saveTeams(teams);

        if (spec.pair() != null) {
            seatPairMembers(id, "team-1", spec.pair(), now);
        }
        return ticket(room);
    }

    @Override
    @Transactional
    public TicketRoom addApplicant(String roomId, Applicant applicant) {
        Room room = require(roomId);
        List<String> uids = new ArrayList<>(room.getMatchmakingUids());
        if (!uids.contains(applicant.uid())) {
            uids.add(applicant.uid());
        }
        Map<String, Object> names = Json.map(room.getMatchmakingNames());
        names.put(applicant.uid(), applicant.nickname());
        room.setMatchmakingUids(uids);
        room.setMatchmakingNames(names);
        room.setLastActivityAt(System.currentTimeMillis());
        saverRoom.save(room);
        return ticket(room);
    }

    @Override
    @Transactional
    public TicketRoom seatPair(String roomId, RankedPair pair) {
        Room room = require(roomId);
        Map<String, Object> slots = Json.map(room.getRankedTeamSlots());
        List<String> teamIds = new ArrayList<>(room.getMatchmakingTeamIds());
        String slotId;
        if (teamIds.contains(pair.rankedTeamId())) {
            // Пара уже здесь: это повторная подача заявки, и слот у неё тот же.
            slotId = slots.entrySet().stream()
                    .filter(entry -> pair.rankedTeamId().equals(Json.str(entry.getValue())))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow(() -> ApiException.of("ROOM_FULL", 409));
        } else {
            slotId = claimSlot(room, pair, slots, teamIds);
        }

        long now = System.currentTimeMillis();
        RoomTeam team = getterRoom.getTeam(room.getId(), slotId)
                .orElseGet(() -> RoomTeam.builder().roomId(room.getId()).teamId(slotId).build());
        team.setName(pair.name());
        team.setMemberUids(new ArrayList<>(pair.members().stream().map(PairMember::uid).toList()));
        team.setRankedTeamId(pair.rankedTeamId());
        team.setScore(0);
        saverRoom.saveTeam(team);

        seatPairMembers(room.getId(), slotId, pair, now);
        return ticket(room);
    }

    @Override
    @Transactional
    public TicketRoom markReady(String roomId) {
        Room room = require(roomId);
        room.setMatchmakingReady(true);
        saverRoom.save(room);
        return ticket(room);
    }

    @Override
    @Transactional
    public void expire(String roomId) {
        Room room = require(roomId);
        room.setMatchmakingExpired(true);
        room.setClosedAt(Instant.now());
        room.setPhase("closed");
        saverRoom.save(room);
    }

    @Override
    @Transactional
    public void withdraw(String roomId, List<String> uids, String rankedTeamId) {
        Room room = getterRoom.getById(roomId == null ? "" : roomId).orElse(null);
        if (room == null) {
            return;
        }
        List<String> standing = new ArrayList<>(room.getMatchmakingUids());
        standing.removeAll(uids);
        Map<String, Object> names = Json.map(room.getMatchmakingNames());
        uids.forEach(names::remove);
        List<String> teamIds = new ArrayList<>(room.getMatchmakingTeamIds());
        if (rankedTeamId != null) {
            teamIds.remove(rankedTeamId);
        }
        room.setMatchmakingNames(names);
        room.setMatchmakingTeamIds(teamIds);
        if (standing.isEmpty()) {
            // Комната, из которой ушёл последний, никого больше не дождётся:
            // держать её открытой значило бы показывать в витрине пустоту.
            room.setPhase("closed");
            room.setClosedAt(Instant.now());
            room.setMatchmakingUids(new ArrayList<>());
            room.setMatchmakingNames(new LinkedHashMap<>());
        } else {
            room.setMatchmakingUids(standing);
        }
        saverRoom.save(room);
    }

    /**
     * Занять свободный слот команды под пару.
     *
     * <p>Двое сразу: свободных мест должно хватить на обоих, иначе один из
     * напарников остался бы за дверью. Слот берётся первый незанятый — так же,
     * как их нумерует создание комнаты.
     */
    private String claimSlot(Room room, RankedPair pair, Map<String, Object> slots, List<String> teamIds) {
        int capacity = room.effectiveMaxPlayers();
        List<String> uids = new ArrayList<>(room.getMatchmakingUids());
        if (uids.size() + pair.members().size() > capacity) {
            throw ApiException.of("ROOM_FULL", 409);
        }
        String chosen = null;
        for (int i = 1; i <= capacity / 2; i++) {
            String slot = "team-" + i;
            if (!slots.containsKey(slot)) {
                chosen = slot;
                break;
            }
        }
        if (chosen == null) {
            throw ApiException.of("ROOM_FULL", 409);
        }
        Map<String, Object> names = Json.map(room.getMatchmakingNames());
        Map<String, Object> assignments = Json.map(room.getRankedTeamAssignments());
        for (PairMember member : pair.members()) {
            names.put(member.uid(), member.nickname());
            assignments.put(member.uid(), chosen);
            if (!uids.contains(member.uid())) {
                uids.add(member.uid());
            }
        }
        teamIds.add(pair.rankedTeamId());
        slots.put(chosen, pair.rankedTeamId());
        room.setMatchmakingUids(uids);
        room.setMatchmakingNames(names);
        room.setMatchmakingTeamIds(teamIds);
        room.setRankedTeamSlots(slots);
        room.setRankedTeamAssignments(assignments);
        room.setLastActivityAt(System.currentTimeMillis());
        saverRoom.save(room);
        return chosen;
    }

    /**
     * Места напарников в комнате.
     *
     * <p>Одним запросом на пару, а не по месту за раз: строк всего две, но
     * правило «читать пачкой» не знает исключений для маленьких коллекций —
     * именно так и появляются циклы, которые потом растут.
     */
    private void seatPairMembers(String roomId, String slotId, RankedPair pair, long nowMs) {
        Map<String, RoomPlayer> existing = new LinkedHashMap<>();
        for (RoomPlayer player : getterRoom.getPlayers(roomId)) {
            existing.put(player.getUid(), player);
        }
        List<RoomPlayer> seats = new ArrayList<>();
        for (PairMember member : pair.members()) {
            RoomPlayer player = existing.getOrDefault(member.uid(), RoomPlayer.builder()
                    .roomId(roomId)
                    .uid(member.uid())
                    .build());
            player.setName(member.nickname());
            player.setAvatarDataUrl(member.avatarDataUrl() == null ? "" : member.avatarDataUrl());
            player.setTeamId(slotId);
            player.setMemeLoadout(new ArrayList<>(member.loadout()));
            player.setLastSeenAt(nowMs);
            player.setMediaRevision(nowMs);
            seats.add(player);
        }
        saverRoom.savePlayers(seats);
    }

    private Room require(String roomId) {
        return getterRoom.getById(roomId == null ? "" : roomId)
                .orElseThrow(() -> ApiException.of("ROOM_NOT_FOUND", 404));
    }

    /**
     * Строка комнаты → запись порта.
     *
     * <p>Язык подбора читается лесенкой {@code matchmakingLanguage →
     * divisionLanguage}: у комнат, заведённых до появления первой колонки, она
     * пуста, и без запасной такая комната не совпала бы ни с одной заявкой.
     */
    private static TicketRoom ticket(Room room) {
        String matchmakingLanguage = Divisions.normalize(room.getMatchmakingLanguage() == null
                ? room.getDivisionLanguage() : room.getMatchmakingLanguage());
        return new TicketRoom(
                room.getId(),
                room.getCreatedBy(),
                Boolean.TRUE.equals(room.getRanked()),
                Boolean.TRUE.equals(room.getIsPrivate()),
                Boolean.TRUE.equals(room.getManagedMatchmaking()),
                room.isClosed(),
                room.getPhase(),
                room.getGameMode(),
                room.effectiveMaxPlayers(),
                Divisions.normalize(room.getDivisionLanguage()),
                matchmakingLanguage,
                List.copyOf(room.getMatchmakingUids() == null ? List.of() : room.getMatchmakingUids()),
                List.copyOf(room.getMatchmakingTeamIds() == null ? List.of() : room.getMatchmakingTeamIds()),
                room.getMatchmakingDeadline() == null ? 0L : room.getMatchmakingDeadline(),
                (int) Json.num(room.getRatingTarget(), NEUTRAL_RATING),
                Boolean.TRUE.equals(room.getMatchmakingReady()),
                Boolean.TRUE.equals(room.getMatchmakingExpired()));
    }
}
