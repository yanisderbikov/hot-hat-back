package ru.hothat.lobby.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.lobby.api.dto.MatchTicketState;
import ru.hothat.lobby.api.dto.MatchTicketView;
import ru.hothat.lobby.domain.TicketMatching;
import ru.hothat.profile.spi.PlayerCardDirectory;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.rating.spi.SeasonStandingPort;
import ru.hothat.room.spi.RoomTicketPort;
import ru.hothat.sabotage.spi.SabotageArmoryPort;
import ru.hothat.team.api.dto.GameMode;
import ru.hothat.team.spi.RankedTeamPort;
import ru.hothat.team.spi.TeamPreflightPort;
import ru.hothat.util.Divisions;
import ru.hothat.util.Seasons;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Общий ход двух подач заявки: быстрой и рейтинговой.
 *
 * <p>Отдельный класс, а не вызов чужого сценария: сценарию запрещено звать
 * сценарий (§7.2), а порядок шагов у обоих один — проверить снаряжение, выбрать
 * язык, найти комнату или завести её, посмотреть, собрался ли состав. Разница
 * между ними умещается в три места, и все три названы аргументами.
 *
 * <p>Записи в комнату идут через порт её области ({@link RoomTicketPort}):
 * автокомната — обычная комната, и второй писатель у её строки означал бы ровно
 * ту болезнь, ради лечения которой всё затевалось. Здесь остаётся отбор, а
 * правила отбора — в {@link TicketMatching}, где их можно проверить без базы.
 */
@Component
@RequiredArgsConstructor
class MatchTicketBroker {

    /**
     * Столько комнат подбора просматривается за одну подачу.
     *
     * <p>Предел достался от прежнего движка. Раньше его платил каждый опрос
     * каждого ищущего игрока раз в три секунды; теперь — только сама подача,
     * потому что опрос стал чтением своей заявки.
     */
    private static final int SCAN_LIMIT = 80;

    /** Общий английский: единственный язык, на котором смешиваются дивизионы. */
    private static final String INTERNATIONAL = "en";

    private final RoomTicketPort rooms;
    /** Обойма и квота диверсий — у своей области; в оболочке учётки их больше нет. */
    private final SabotageArmoryPort armory;
    /** Имя, аватар и дивизион — у карточки игрока. */
    private final PlayerCardPort cards;
    /** Пара и её готовность принадлежат области команды: спрашиваем её порт. */
    private final TeamPreflightPort preflights;
    /** Вес пары при сведении — очки её команды в таблице текущего сезона. */
    private final SeasonStandingPort standings;

    /**
     * Подать заявку.
     *
     * @param requestedLanguage язык быстрой игры; {@code null} — язык своего
     *                          дивизиона. У рейтинговой заявки не спрашивается
     *                          вовсе: там язык всегда родной дивизион пары
     */
    MatchTicketView open(HotHatUser user, boolean ranked, GameMode requestedMode,
                         int requestedMaxPlayers, String requestedLanguage) {
        armory.requireLoadout(user.uid());
        // Обойму держит область диверсий, имя и дивизион — карточка: это две
        // области и две таблицы, и спрашиваются они порознь.
        PlayerCardPort.Card selfCard = cards.card(user.uid()).orElse(null);

        String nativeDivision = Divisions.normalize(selfCard == null ? null : selfCard.divisionLanguage());
        String gameLanguage = ranked ? nativeDivision
                : Divisions.normalize(requestedLanguage == null ? nativeDivision : requestedLanguage,
                        nativeDivision);
        // Быстрая игра идёт либо на языке своего дивизиона, либо на общем английском.
        if (!ranked && !gameLanguage.equals(nativeDivision) && !INTERNATIONAL.equals(gameLanguage)) {
            throw ApiException.of("QUICK_LANGUAGE_INVALID", 409);
        }
        String gameMode = Seasons.mode(requestedMode.wireValue());
        int maxPlayers = TicketMatching.normalizeSize(requestedMaxPlayers);

        RankedTeamPort.TeamSummary team = null;
        RoomTicketPort.RankedPair pair = null;
        if (ranked) {
            TeamPreflightPort.LaunchablePreflight preflight =
                    preflights.requireLaunchablePreflight(user, gameMode);
            team = preflight.team();
            pair = pair(team);
            if (!user.uid().equals(preflight.initiatorUid())) {
                throw ApiException.of("PREFLIGHT_CAPTAIN_ONLY", 403);
            }
        }
        if ("sabotage".equals(gameMode) && !ranked
                && !armory.entitlement(user.uid(), user.email()).allowed()) {
            throw ApiException.of("SABOTAGE_LIMIT_REACHED", 402);
        }

        long now = System.currentTimeMillis();
        List<String> memberUids = ranked ? team.memberUids() : List.of(user.uid());
        int teamRating = ranked
                ? standings.currentSeason(team.teamId(), gameMode, nativeDivision).points() : 0;
        TicketMatching.Wanted wanted = new TicketMatching.Wanted(ranked, gameMode, maxPlayers,
                gameLanguage, nativeDivision, memberUids, ranked ? team.teamId() : null, teamRating);

        RoomTicketPort.TicketRoom room = place(user, wanted, pair, selfCard, team, gameMode, now);

        int joined = room.applicantUids().size();
        long deadline = room.deadlineMs();
        int capacity = room.maxPlayers();
        boolean ready = room.ready() || joined == capacity;

        if (deadline > 0 && now > deadline && !ready) {
            rooms.expire(room.roomId());
            if (ranked) {
                // Комната не собралась в срок: пара обязана увидеть провал у
                // себя на экране, а не остаться ждать вечно.
                preflights.reportSearch(team.teamId(), null, false, true, null, true);
            }
            return new MatchTicketView(room.roomId(), MatchTicketState.EXPIRED, room.roomId(), null,
                    joined, capacity, deadline, requestedMode, requestedLanguage);
        }
        if (ready && !room.ready()) {
            room = rooms.markReady(room.roomId());
        }
        if (ranked) {
            preflights.reportSearch(team.teamId(), room.roomId(), ready, true, joined, false);
        }
        return new MatchTicketView(room.roomId(),
                ready ? MatchTicketState.MATCHED : MatchTicketState.SEARCHING,
                room.roomId(), blankToNull(room.hostUid()), joined, capacity, deadline,
                GameMode.fromWire(gameMode), gameLanguage);
    }

    /**
     * Ввести пару в названную заранее комнату.
     *
     * <p>Комната здесь не подбирается: о ней договорились на предматчевой
     * подготовке, и сверяются четыре вещи — та ли это комната, жива ли она и
     * рейтинговая ли, тот ли режим и тот ли дивизион.
     */
    String joinRanked(HotHatUser user, String roomId, GameMode requestedMode) {
        String gameMode = Seasons.mode(requestedMode.wireValue());
        TeamPreflightPort.LaunchablePreflight preflight =
                preflights.requireLaunchablePreflight(user, gameMode);
        RankedTeamPort.TeamSummary team = preflight.team();
        if (!user.uid().equals(preflight.initiatorUid())) {
            throw ApiException.of("PREFLIGHT_CAPTAIN_ONLY", 403);
        }
        if (!"room".equals(preflight.intent()) || !roomId.equals(preflight.requestedRoomId())) {
            throw ApiException.of("PREFLIGHT_ROOM_MISMATCH", 409);
        }
        RoomTicketPort.TicketRoom room = rooms.ticketRoom(roomId).orElse(null);
        if (room == null || room.closed() || !"setup".equals(room.phase()) || !room.ranked()) {
            throw ApiException.of("RANKED_ROOM_UNAVAILABLE", 409);
        }
        if (!gameMode.equals(room.gameMode())) {
            throw ApiException.of("RANKED_ROOM_MODE_MISMATCH", 409);
        }
        if (!room.divisionLanguage().equals(Divisions.normalize(team.divisionLanguage()))) {
            throw ApiException.of("RANKED_ROOM_DIVISION_MISMATCH", 409);
        }
        RoomTicketPort.TicketRoom seated = rooms.seatPair(roomId, pair(team));
        preflights.reportSearch(team.teamId(), roomId, true, true, null, false);
        // Идентификатор берём из ответа порта, а не из адреса: расхождение
        // здесь важнее экономии одной строки.
        return seated.roomId();
    }

    /**
     * Куда встала заявка: в свою прежнюю комнату, в чужую свободную или в
     * только что заведённую.
     */
    private RoomTicketPort.TicketRoom place(HotHatUser user, TicketMatching.Wanted wanted,
                                            RoomTicketPort.RankedPair pair,
                                            PlayerCardPort.Card selfCard,
                                            RankedTeamPort.TeamSummary team,
                                            String gameMode, long nowMs) {
        Map<String, RoomTicketPort.TicketRoom> open = new LinkedHashMap<>();
        List<TicketMatching.Option> options = new ArrayList<>();
        for (RoomTicketPort.TicketRoom room : rooms.openTicketRooms(SCAN_LIMIT)) {
            open.put(room.roomId(), room);
            options.add(option(room));
        }

        Optional<TicketMatching.Option> standing = TicketMatching.alreadyStanding(options, wanted);
        if (standing.isPresent()) {
            // Заявка уже стоит: ничего не пишем и никого не переселяем.
            return open.get(standing.get().roomId());
        }
        Optional<TicketMatching.Option> best = TicketMatching.bestFit(options, wanted, nowMs);
        if (best.isPresent()) {
            return wanted.ranked()
                    ? rooms.seatPair(best.get().roomId(), pair)
                    : rooms.addApplicant(best.get().roomId(),
                            new RoomTicketPort.Applicant(user.uid(), nickOf(selfCard)));
        }

        String roomDivision = wanted.ranked()
                ? Divisions.normalize(team.divisionLanguage()) : wanted.divisionLanguage();
        return rooms.openManagedRoom(new RoomTicketPort.NewManagedRoom(
                user.uid(),
                wanted.ranked(),
                gameMode,
                wanted.maxPlayers(),
                roomDivision,
                wanted.ranked() ? roomDivision : wanted.gameLanguage(),
                wanted.ranked()
                        ? standings.currentSeason(team.teamId(), gameMode, roomDivision).points() : null,
                nowMs + TicketMatching.SEARCH_WINDOW_MS,
                wanted.ranked() ? null : new RoomTicketPort.Applicant(user.uid(), nickOf(selfCard)),
                pair));
    }

    /** Пара со снаряжением и именами: обоймы и карточки — по одному запросу на двоих. */
    private RoomTicketPort.RankedPair pair(RankedTeamPort.TeamSummary team) {
        Map<String, List<String>> loadouts = armory.requireLoadouts(team.memberUids());
        Map<String, PlayerCardPort.Card> memberCards = cards.cards(team.memberUids());
        List<RoomTicketPort.PairMember> members = new ArrayList<>(team.memberUids().size());
        for (String uid : team.memberUids()) {
            PlayerCardPort.Card card = memberCards.get(uid);
            members.add(new RoomTicketPort.PairMember(uid, nickOf(card),
                    card == null ? null : card.avatarDataUrl(),
                    loadouts.getOrDefault(uid, List.of())));
        }
        return new RoomTicketPort.RankedPair(team.teamId(), team.name(), members);
    }

    private static TicketMatching.Option option(RoomTicketPort.TicketRoom room) {
        return new TicketMatching.Option(room.roomId(), room.ranked(), room.privateRoom(),
                room.managed(), room.closed(), room.gameMode(), room.maxPlayers(),
                room.divisionLanguage(), room.matchmakingLanguage(), room.applicantUids(),
                room.rankedTeamIds(), room.deadlineMs(), room.ratingTarget());
    }

    /** Заглушку имени называет область профиля, а не копия строки здесь. */
    private static String nickOf(PlayerCardPort.Card card) {
        return card == null || card.nickname() == null
                ? PlayerCardDirectory.PLACEHOLDER_NICKNAME : card.nickname();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
