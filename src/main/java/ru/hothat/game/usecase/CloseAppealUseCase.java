package ru.hothat.game.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.AppealClosingOutcome;
import ru.hothat.game.api.dto.AppealResultResponseDTO;
import ru.hothat.game.api.dto.CloseAppealRequestDTO;
import ru.hothat.game.api.dto.MatchRewardsView;
import ru.hothat.game.domain.AppealClosed;
import ru.hothat.game.domain.LoadoutRules;
import ru.hothat.game.domain.MatchEngine;
import ru.hothat.game.domain.MatchPlayer;
import ru.hothat.game.store.MatchSession;
import ru.hothat.game.store.MatchStore;

import java.util.Map;

/**
 * Подвести итог хода.
 *
 * <p>Одна транзакция делает четыре вещи сразу: отменяет слова, возвращает их в
 * шляпу, начисляет награды всей команде и передаёт очередь. Разорвать её
 * событиями нельзя — плана это касается прямо (§7.3, строка «Закрытие
 * апелляции»): между «слово отменено» и «награда начислена» партия не должна
 * существовать ни секунды.
 *
 * <p>Идемпотентно по {@code turnId}: просьбу шлют все участники разом, как
 * только истекли десять секунд, и повторное начисление наград было бы
 * подарком команде за то, что у неё быстрый интернет.
 */
@Service
@RequiredArgsConstructor
public class CloseAppealUseCase {

    private final MatchStore matchStore;
    private final MatchEngine engine;
    private final MatchViewAssembler views;

    @PreAuthorize("@gameAuthz.isPlayer(#roomId)")
    @Transactional
    public AppealResultResponseDTO run(HotHatUser user, String roomId, CloseAppealRequestDTO request) {
        MatchSession session = matchStore.open(roomId);
        AppealClosed closed = engine.closeAppeal(session.state(), request.turnId());
        if (closed.outcome() == AppealClosed.Outcome.SETTLED) {
            session.addTeamScore(closed.teamId(), closed.teamScoreDelta());
            grantRewards(session, closed);
        }
        session.commit();
        // Пока голосование идёт, наград ещё нет — и пустая карта на их месте
        // читалась бы как «начислили ноль», а это разные вещи.
        MatchRewardsView rewards = closed.outcome() == AppealClosed.Outcome.STILL_OPEN
                ? null : new MatchRewardsView(closed.rewards(), closed.specialRewardsByUid());
        return new AppealResultResponseDTO(outcome(closed.outcome()), closed.finalScore(),
                closed.invalidWordIds(), closed.returnedWords().size(), rewards, closed.matchFinished(),
                views.state(session.state(), user.uid()));
    }

    /**
     * Награды получает вся команда, а не только объясняющий: помидоры и мемы
     * тратятся против чужих ходов, и делить их внутри пары было бы нечестно.
     */
    private void grantRewards(MatchSession session, AppealClosed closed) {
        Map<String, Integer> rewards = closed.rewards();
        int memes = rewards.getOrDefault("meme", 0);
        // Состав читается разом: findPlayer в цикле сходил бы в базу за каждым
        // награждённым отдельно, а после этого карточки всё равно понадобятся
        // сборщику ответа.
        session.players();
        for (String uid : closed.rewardedUids()) {
            MatchPlayer player = session.findPlayer(uid).orElse(null);
            if (player == null) {
                // Игрок вышел из комнаты между ходом и итогами: начислять некому.
                continue;
            }
            rewards.forEach(player::grant);
            closed.specialRewardsByUid().getOrDefault(uid, Map.of()).forEach(player::grant);
            LoadoutRules.grant(player, memes);
        }
    }

    private static AppealClosingOutcome outcome(AppealClosed.Outcome outcome) {
        return switch (outcome) {
            case SETTLED -> AppealClosingOutcome.SETTLED;
            case ALREADY_SETTLED -> AppealClosingOutcome.ALREADY_SETTLED;
            case STILL_OPEN -> AppealClosingOutcome.STILL_OPEN;
        };
    }
}
