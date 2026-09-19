package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.RankedAutostartOutcome;
import ru.hothat.game.api.dto.RankedAutostartResponseDTO;
import ru.hothat.game.domain.LoadoutRules;
import ru.hothat.game.domain.MatchEngine;
import ru.hothat.game.domain.MatchPhase;
import ru.hothat.game.domain.MatchPlayer;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.port.RankedWordPort;
import ru.hothat.game.port.RoomLifecyclePort;
import ru.hothat.game.port.RoomTeamPort;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;
import ru.hothat.util.Divisions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Запустить рейтинговую партию, как только собрался состав.
 *
 * <p>Хозяина в рейтинге нет — комнату собрал подбор, и ждать нажатия не от
 * кого. Поэтому вызов делают все игроки сразу, а партию начинает тот, чей
 * запрос пришёл первым; остальные получают {@code ALREADY_RUNNING}.
 *
 * <p>Слова здесь не приносят игроки: сервер берёт их из пула дивизиона с
 * оглядкой на то, что этим людям уже выпадало. Иначе пара, играющая каждый
 * день, заранее знала бы половину шляпы.
 */
@Service
@RequiredArgsConstructor
public class AutostartRankedMatchUseCase {

    /** Десять слов на игрока — столько же, сколько приносят вручную. */
    private static final int WORDS_PER_PLAYER = 10;
    private static final int MIN_PLAYERS = 4;
    private static final int MAX_PLAYERS = 10;
    private static final int TEAM_SIZE = 2;

    private final MatchStore matchStore;
    private final MatchEngine engine;
    private final MatchViewAssembler views;
    private final RoomTeamPort teamPort;
    private final RoomLifecyclePort rooms;
    private final RankedWordPort rankedWords;

    @PreAuthorize("@gameAuthz.isMember(#roomId)")
    @Transactional
    public RankedAutostartResponseDTO run(HotHatUser user, String roomId) {
        RoomLifecyclePort.RoomLifecycleView room = rooms.find(roomId)
                .orElseThrow(() -> ApiException.of("ROOM_NOT_FOUND", 404));
        if (!room.ranked()) {
            throw ApiException.of("RANKED_ONLY", 409);
        }
        MatchSession session = matchStore.open(roomId);
        MatchState state = session.state();
        int target = Math.max(MIN_PLAYERS, Math.min(MAX_PLAYERS, room.maxPlayers()));
        if (state.getPhase() != MatchPhase.SETUP) {
            return waiting(RankedAutostartOutcome.ALREADY_RUNNING, session, user, 0, target, 0);
        }

        List<MatchPlayer> players = session.players();
        if (players.size() < target) {
            return waiting(RankedAutostartOutcome.WAITING, session, user, players.size(), target, 0);
        }
        List<RoomTeamPort.RoomTeamView> teams = teamPort.teams(roomId);
        if (teams.size() != target / TEAM_SIZE) {
            throw ApiException.of("TEAMS_NOT_READY", 409);
        }

        Map<String, MatchPlayer> byUid = new LinkedHashMap<>();
        players.forEach(player -> byUid.put(player.getUid(), player));
        List<String> order = new ArrayList<>();
        Map<String, List<String>> rosters = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        List<String> allUids = new ArrayList<>();
        for (RoomTeamPort.RoomTeamView team : teams) {
            List<String> roster = new ArrayList<>(new LinkedHashSet<>(team.memberUids()));
            if (roster.size() != TEAM_SIZE) {
                throw ApiException.of("TEAMS_NOT_READY", 409);
            }
            for (String uid : roster) {
                MatchPlayer player = byUid.get(uid);
                if (player == null) {
                    // Кто-то из пары ещё не дошёл до комнаты: ждём дальше.
                    return waiting(RankedAutostartOutcome.WAITING, session, user, players.size(), target, 0);
                }
                if (!LoadoutRules.charged(player.getLoadout())) {
                    throw ApiException.of("LOADOUT_REQUIRED:" + player.displayName(), 409);
                }
                names.put(uid, player.displayName());
            }
            order.add(team.teamId());
            rosters.put(team.teamId(), roster);
            allUids.addAll(roster);
        }
        if (new LinkedHashSet<>(allUids).size() != target) {
            throw ApiException.of("TEAMS_NOT_READY", 409);
        }

        String language = Divisions.normalize(room.divisionLanguage() != null
                ? room.divisionLanguage() : room.gameLanguage());
        int wordCount = target * WORDS_PER_PLAYER;
        List<String> words = rankedWords.generate(wordCount, allUids, language);
        if (words.size() != wordCount) {
            throw ApiException.of("RANKED_WORDS_UNAVAILABLE", 500);
        }

        engine.start(state, order, rosters, names, words);
        allUids.forEach(uid -> LoadoutRules.arm(byUid.get(uid)));
        session.commit();
        order.forEach(teamId -> teamPort.freezeRoster(roomId, teamId, rosters.get(teamId)));
        rooms.onMatchStarted(roomId, state.getGameNumber(), allUids);
        // История выдачи пишется после старта: не начавшейся партии запоминать нечего.
        rankedWords.remember(allUids, words, language);
        return new RankedAutostartResponseDTO(RankedAutostartOutcome.STARTED, players.size(), target,
                state.getGameNumber(), wordCount, views.state(state, user.uid()));
    }

    private RankedAutostartResponseDTO waiting(RankedAutostartOutcome outcome, MatchSession session,
                                               HotHatUser user, int playerCount, int target, int wordCount) {
        return new RankedAutostartResponseDTO(outcome, playerCount, target, session.state().getGameNumber(),
                wordCount, views.state(session.state(), user.uid()));
    }
}
