package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.MatchStartIntent;
import ru.hothat.game.api.dto.StartMatchRequestDTO;
import ru.hothat.game.api.dto.StartedMatchResponseDTO;
import ru.hothat.game.domain.LoadoutRules;
import ru.hothat.game.domain.MatchEngine;
import ru.hothat.game.domain.MatchPhase;
import ru.hothat.game.domain.MatchPlayer;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.port.RoomLifecyclePort;
import ru.hothat.game.port.RoomTeamPort;
import ru.hothat.game.port.WordSubmissionPort;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Начать партию.
 *
 * <p>Сводит воедино то, что раньше делалось в два приёма и в двух местах:
 * серверную проверку готовности ({@code prepare_game}) и клиентскую транзакцию
 * сбора шляпы ({@code collectWordsAndStart}). Разрыв между ними и был дырой:
 * между проверкой и стартом состав успевал измениться, и клиент ловил это
 * сравнением своих снимков — «Список команд изменился. Нажмите ещё раз».
 *
 * <p>Состав собирается по полю {@code teamId} самих игроков, а не по зеркалу в
 * команде: зеркало отставало, и карточка показывала 2/2 там, где на деле был
 * один человек.
 */
@Service
@RequiredArgsConstructor
public class StartMatchUseCase {

    /** Больше тысячи слов шляпа не принимает: партия столько не переживёт. */
    private static final int BAG_LIMIT = 1000;
    /** Меньше пяти слов — это не партия. */
    private static final int MINIMUM_WORDS = 5;
    private static final int MIN_TEAMS = 2;
    private static final int MAX_TEAMS = 5;
    /** В команде ровно двое: объясняющий и угадывающий. */
    private static final int TEAM_SIZE = 2;

    private final MatchStore matchStore;
    private final MatchEngine engine;
    private final MatchViewAssembler views;
    private final RoomTeamPort teamPort;
    private final RoomLifecyclePort rooms;
    private final WordSubmissionPort wordSubmissions;
    private final Clock clock;

    @PreAuthorize("@gameAuthz.isHost(#roomId)")
    @Transactional
    public StartedMatchResponseDTO run(HotHatUser user, String roomId, StartMatchRequestDTO request) {
        MatchSession session = matchStore.open(roomId);
        MatchState state = session.state();
        requirePhase(state, request.intent());

        List<String> order = teamPort.teams(roomId).stream().map(RoomTeamPort.RoomTeamView::teamId).toList();
        if (order.size() < MIN_TEAMS || order.size() > MAX_TEAMS) {
            throw ApiException.of("TEAMS_NOT_READY", 409);
        }

        long now = clock.millis();
        Map<String, List<String>> rosters = new LinkedHashMap<>();
        order.forEach(teamId -> rosters.put(teamId, new ArrayList<>()));
        Map<String, String> names = new LinkedHashMap<>();
        List<MatchPlayer> playing = new ArrayList<>();
        List<MatchPlayer> all = session.players();
        for (MatchPlayer player : all) {
            List<String> roster = player.getTeamId() == null ? null : rosters.get(player.getTeamId());
            if (roster == null || !active(player, now)) {
                continue;
            }
            if (!LoadoutRules.charged(player.getLoadout())) {
                // Имя в коде: игроку на экране показывают, кто именно не готов.
                throw ApiException.of("LOADOUT_REQUIRED:" + player.displayName(), 409);
            }
            roster.add(player.getUid());
            names.put(player.getUid(), player.displayName());
            playing.add(player);
        }
        if (rosters.values().stream().anyMatch(roster -> roster.size() != TEAM_SIZE)) {
            throw ApiException.of("TEAMS_NOT_READY", 409);
        }

        List<String> words = wordSubmissions.collect(roomId, BAG_LIMIT);
        if (words.size() < MINIMUM_WORDS) {
            throw ApiException.of("NOT_ENOUGH_WORDS", 409);
        }

        engine.start(state, order, rosters, names, words);
        for (MatchPlayer player : playing) {
            // Боезапас и обойма выдаются заново: прошлая партия к этой отношения не имеет.
            LoadoutRules.arm(player);
            player.setLastSeenAt(now);
        }
        session.commit();
        order.forEach(teamId -> teamPort.freezeRoster(roomId, teamId, rosters.get(teamId)));
        rooms.onMatchStarted(roomId, state.getGameNumber(), playing.stream().map(MatchPlayer::getUid).toList());

        String roomName = rooms.find(roomId)
                .map(RoomLifecyclePort.RoomLifecycleView::name)
                .orElse(roomId);
        return new StartedMatchResponseDTO(state.getGameNumber(), roomName, order.size(), playing.size(),
                words.size(), Math.max(0, all.size() - playing.size()),
                views.state(state, user.uid()));
    }

    /**
     * Намерение сверяется с фазой. Расхождение значит, что человек нажал на
     * устаревшем экране — например, на кнопке «Начать игру», когда партия уже
     * идёт третий ход.
     */
    private void requirePhase(MatchState state, MatchStartIntent intent) {
        MatchPhase expected = intent == MatchStartIntent.FIRST_GAME ? MatchPhase.SETUP : MatchPhase.FINISHED;
        if (state.getPhase() != expected) {
            throw ApiException.of("GAME_ALREADY_STARTED", 409);
        }
    }

    /** Тест-бот отметок не пишет и считается на месте всегда. */
    private static boolean active(MatchPlayer player, long nowMs) {
        return player.isTestBot()
                || player.getLastSeenAt() >= nowMs - RoomLifecyclePort.RoomSeatView.ACTIVE_WINDOW_MS;
    }
}
