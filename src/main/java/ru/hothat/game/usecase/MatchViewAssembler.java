package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.game.api.dto.AppealView;
import ru.hothat.game.api.dto.AppealWordView;
import ru.hothat.game.api.dto.ArsenalView;
import ru.hothat.game.api.dto.LastTurnView;
import ru.hothat.game.api.dto.MatchStateView;
import ru.hothat.game.api.dto.PauseView;
import ru.hothat.game.api.dto.PlayerAmmoView;
import ru.hothat.game.api.dto.ReplacementClipView;
import ru.hothat.game.api.dto.SabotageEventView;
import ru.hothat.game.api.dto.SabotageLocksView;
import ru.hothat.game.api.dto.TeamScoreView;
import ru.hothat.game.api.dto.TechnicalTerminationView;
import ru.hothat.game.api.dto.TurnView;
import ru.hothat.game.domain.AppealRules;
import ru.hothat.game.domain.GuessedWord;
import ru.hothat.game.domain.LastTurn;
import ru.hothat.game.domain.LoadoutRules;
import ru.hothat.game.domain.MatchPhase;
import ru.hothat.game.domain.MatchPlayer;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.domain.ReplacementClip;
import ru.hothat.game.domain.SabotageEvent;
import ru.hothat.game.domain.SabotageLocks;
import ru.hothat.game.domain.TechnicalTermination;
import ru.hothat.game.domain.TurnRules;
import ru.hothat.game.port.RoomTeamPort;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Сборка проекций партии для ответов.
 *
 * <p>Здесь же исполняется правило «слово видит только объясняющий»: проекция
 * собирается на того, кто спрашивает, и всем остальным поле приходит пустым.
 * Место выбрано намеренно — единственная точка, через которую состояние
 * выходит наружу; спрятать слово в каждом из семи ответов по отдельности
 * означало бы однажды забыть.
 *
 * <p>То же правило у боезапаса, только граница проходит иначе: счётчики
 * зарядов каждого игрока публичны — их рисует плитка каждого, — а содержимое
 * (какие мемы заряжены, что отстреляно, перезарядка, клипы Подмены и сроки
 * своей съёмки) выходит наружу только к владельцу. Чужие счётчики собирает
 * {@link #ammo(List)}, своё — {@link #arsenal(MatchPlayer)} и
 * {@link #clipsOf(MatchState, String)}; третьего пути нет.
 *
 * <p>Третья завеса — история диверсий. Выстрел видят все: он и есть сцена.
 * Съёмка Подмены — нет: событие {@code replacement_record} несёт номер клипа,
 * кого снимают и до какого момента, то есть ровно то, ради чего чужие клипы и
 * чужие сроки съёмки из проекции убраны. Поэтому {@link #events(MatchState,
 * String)} и {@link #lastEvent(MatchState, String)} собираются на слушателя
 * так же, как слово и снаряжение, и обойти две первые завесы через третью
 * нельзя.
 */
@Component
@RequiredArgsConstructor
public class MatchViewAssembler {

    private final RoomTeamPort teams;
    private final Clock clock;

    /** Состояние партии глазами названного участника. */
    public MatchStateView state(MatchState state, String viewerUid) {
        List<TeamScoreView> teamViews = teams.teams(state.getRoomId()).stream()
                .map(team -> new TeamScoreView(team.teamId(), team.name(), team.order(), team.score(),
                        state.rosterOf(team.teamId()).isEmpty() ? team.memberUids() : state.rosterOf(team.teamId())))
                .toList();
        return new MatchStateView(
                state.getRoomId(),
                state.getPhase(),
                state.getGameNumber(),
                state.isSabotageMode() ? "sabotage" : "classic",
                state.isRanked(),
                state.isTestRoom(),
                List.copyOf(state.getTestBotIds()),
                state.getTestOwnerExplainerGameNumber(),
                state.getTestOwnerExplainerTurnNumber(),
                state.getWordsLeft(),
                blankToNull(state.getLastGuessedWord()),
                Math.max(0, state.getLastActionAtMs()),
                state.getCurrentTeamId(),
                List.copyOf(state.getTeamOrder()),
                Map.copyOf(state.getRosters()),
                Map.copyOf(state.getPlayerNames()),
                teamViews,
                turn(state, viewerUid),
                lastTurn(state.getLastTurn()),
                pause(state),
                appeal(state, viewerUid),
                lastEvent(state, viewerUid),
                events(state, viewerUid),
                locks(state, viewerUid),
                termination(state.getTermination()),
                clock.millis());
    }

    /** Ход; вне хода — пусто. */
    public TurnView turn(MatchState state, String viewerUid) {
        if (state.getPhase() != MatchPhase.ACTIVE || state.getTurnId() == null) {
            return null;
        }
        boolean explaining = state.getExplainerUid() != null && state.getExplainerUid().equals(viewerUid);
        return new TurnView(state.getTurnId(), state.getExplainerUid(), state.getExplainerName(),
                state.getGuesserUid(), state.getGuesserName(), TurnRules.deadline(state),
                TurnRules.durationSeconds(state), state.getCurrentTurnScore(), state.getWordsLeft(),
                // Слово знает только объясняющий: иначе ответ сервера выдал бы
                // его тому, кто должен отгадать, и зрителям в записи.
                explaining ? state.getCurrentWord() : null);
    }

    public PauseView pause(MatchState state) {
        if (!state.isPaused()) {
            return PauseView.running();
        }
        return new PauseView(true, state.isHostPaused(), state.getPauseReason(),
                List.copyOf(state.getPauseMissingUids()), List.copyOf(state.getPauseMissingNames()),
                state.getPauseStartedAtMs(), Math.max(0, state.getPausedTurnRemainingMs()),
                Math.max(0, state.getPausedAppealRemainingMs()));
    }

    /** Закрытый ход для экрана итогов; пусто, пока в партии не закрыли ни одного. */
    public LastTurnView lastTurn(LastTurn lastTurn) {
        if (lastTurn == null) {
            return null;
        }
        return new LastTurnView(lastTurn.turnId(), lastTurn.teamId(), lastTurn.score(), lastTurn.finalScore());
    }

    /**
     * Занятость дорожек глазами названного участника: срок съёмки Подмены —
     * только свой. Чужие сроки сказали бы, кто и когда готовит Подмену, то
     * есть ровно то, ради чего чужие клипы не отдаются.
     */
    public SabotageLocksView locks(MatchState state, String viewerUid) {
        SabotageLocks locks = state.getLocks();
        Long recording = viewerUid == null ? null : locks.replacementRecordingByAttacker().get(viewerUid);
        return new SabotageLocksView(locks.videoUntil(), locks.voiceUntil(), locks.crocodileUntil(),
                locks.overlayUntil(), locks.replacementUntil(), locks.replacementTurnId(),
                recording == null ? 0 : Math.max(0, recording));
    }

    /** Голосование; вне апелляции — пусто. */
    public AppealView appeal(MatchState state, String viewerUid) {
        LastTurn lastTurn = state.getLastTurn();
        if (state.getPhase() != MatchPhase.APPEAL || lastTurn == null) {
            return null;
        }
        List<String> mine = state.getAppealVotes().getOrDefault(viewerUid, List.of());
        List<AppealWordView> words = new ArrayList<>();
        for (GuessedWord word : lastTurn.guessedWords()) {
            words.add(new AppealWordView(word.id(), word.word(),
                    AppealRules.votesFor(state, word.id()), mine.contains(word.id())));
        }
        int eligible = AppealRules.eligible(state).size();
        return new AppealView(lastTurn.turnId(), state.getAppealEndsAt(), eligible,
                AppealRules.majority(eligible), words);
    }

    /** Своё снаряжение целиком — с содержимым обоймы; чужому не отдаётся. */
    public ArsenalView arsenal(MatchPlayer player) {
        if (player == null) {
            return null;
        }
        return new ArsenalView(Map.copyOf(player.getArsenal()), List.copyOf(player.getLoadout()),
                List.copyOf(player.getAvailable()), List.copyOf(player.getReserve()),
                List.copyOf(player.getRecycle()), player.getSabotageCooldownUntil());
    }

    /**
     * Счётчики боезапаса всех игроков — то, что рисуют плитки.
     *
     * <p>Ни одного идентификатора мема здесь нет намеренно: из обоймы наружу
     * выходит только факт «пять заряжено», без которого экран настройки не
     * знает, можно ли начинать. Порядок — порядок состава комнаты.
     */
    public List<PlayerAmmoView> ammo(List<MatchPlayer> players) {
        List<PlayerAmmoView> views = new ArrayList<>(players.size());
        for (MatchPlayer player : players) {
            views.add(new PlayerAmmoView(player.getUid(), Map.copyOf(player.getArsenal()),
                    LoadoutRules.charged(player.getLoadout())));
        }
        return List.copyOf(views);
    }

    /**
     * Недавние диверсии глазами названного участника.
     *
     * <p>Съёмка Подмены — событие для двоих: снимаемый по нему включает
     * камеру, снимающий — превью. Третьим лицам оно не отдаётся вовсе, а не
     * приезжает с вырезанным клипом: сцене третьего лица с ним делать нечего
     * ({@code app-core.js:11547}), а сам факт «кто-то снимает объясняющего» —
     * уже подсказка, которую эти завесы и прячут. Выстрелы видны всем.
     */
    public List<SabotageEventView> events(MatchState state, String viewerUid) {
        return state.getRecentEvents().stream()
                .filter(event -> visibleTo(event, viewerUid))
                .map(this::event)
                .toList();
    }

    /**
     * Последняя диверсия глазами названного участника.
     *
     * <p>Если последней была скрытая от него съёмка, последней для него
     * остаётся предыдущая видимая: иначе поле «прыгало» бы в пусто и обратно
     * с каждой съёмкой, а сцена третьего лица не отличила бы «диверсий не
     * было» от «была, но не про тебя».
     */
    public SabotageEventView lastEvent(MatchState state, String viewerUid) {
        SabotageEvent last = state.getLastEvent();
        if (last == null) {
            return null;
        }
        if (visibleTo(last, viewerUid)) {
            return event(last);
        }
        List<SabotageEvent> recent = state.getRecentEvents();
        for (int i = recent.size() - 1; i >= 0; i--) {
            if (visibleTo(recent.get(i), viewerUid)) {
                return event(recent.get(i));
            }
        }
        return null;
    }

    /**
     * Кому событие показывать, решает само событие ({@link SabotageEvent#visibleTo}):
     * тем же правилом пользуется рассылка по каналу данных, и завеса кадра не
     * обходится другим путём доставки.
     */
    private static boolean visibleTo(SabotageEvent event, String viewerUid) {
        return event.visibleTo(viewerUid);
    }

    /**
     * Одно событие как есть — для ответа тому, кто его вызвал. Список и
     * последнее событие для чужих глаз идут через {@link #events} и
     * {@link #lastEvent}: там решается, кому событие показывать.
     */
    public SabotageEventView event(SabotageEvent event) {
        if (event == null) {
            return null;
        }
        return new SabotageEventView(event.id(), event.type(), event.attackerUid(), event.attackerName(),
                event.targetUid(), event.createdAtMs(), event.durationMs(), event.gameNumber(),
                event.memeId(), event.memeTitle(), event.memeSrc(), event.memePoster(),
                event.memeMediaPath(), event.memePosterPath(), event.memeStorageProvider(),
                event.clipId(), event.recordedTurnId(), event.x(), event.y(), event.text(), event.voiceId());
    }

    public ReplacementClipView clip(ReplacementClip clip) {
        if (clip == null) {
            return null;
        }
        return new ReplacementClipView(clip.id(), clip.attackerUid(), clip.targetUid(), clip.recordedTurnId(),
                clip.gameNumber(), clip.createdAtMs(), clip.ready());
    }

    /** Свои клипы — чужие не отдаются: по ним видно, кого готовятся подменить. */
    public List<ReplacementClipView> clipsOf(MatchState state, String attackerUid) {
        return state.getClips().values().stream()
                .filter(clip -> clip.attackerUid().equals(attackerUid))
                .filter(clip -> clip.gameNumber() == state.getGameNumber())
                .map(this::clip)
                .toList();
    }

    public TechnicalTerminationView termination(TechnicalTermination termination) {
        if (termination == null) {
            return null;
        }
        return new TechnicalTerminationView(termination.type(), termination.missingUids(),
                termination.missingNames(), termination.noPenalty(), termination.endedAtMs());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
