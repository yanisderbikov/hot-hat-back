package ru.hothat.team.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.realtime.spi.RealtimeChangeBus;
import ru.hothat.team.store.TeamStore;
import ru.hothat.team.usecase.TeamMembershipGuard;
import ru.hothat.team.usecase.TeamReadLimits;

/**
 * Реализация порта готовности: живёт у владельца данных.
 *
 * <p>Соседи ходят сюда, а не в {@code TeamStore}: доступ к таблицам остаётся
 * у области, и никто, кроме неё, не может завести проверке второго хозяина.
 */
@Component
@RequiredArgsConstructor
public class TeamPreflightGate implements TeamPreflightPort {

    private final TeamStore teams;
    private final TeamMembershipGuard teamAuthz;
    /**
     * Новости подбора — это новости проверки: найденная комната и число
     * собравшихся показываются на том же экране, что и готовность.
     */
    private final RealtimeChangeBus realtime;

    @Override
    @Transactional(readOnly = true)
    public LaunchablePreflight requireLaunchablePreflight(HotHatUser user, String gameMode) {
        TeamStore.TeamRow team = teamAuthz.requireActiveTeam(user);
        long now = System.currentTimeMillis();
        TeamStore.PreflightRow preflight = teams.preflight(team.teamId())
                .filter(row -> !row.expired(now))
                .orElseThrow(() -> ApiException.of("TEAM_PREFLIGHT_NOT_READY", 409));
        if (!preflight.gameMode().equals(gameMode) || !launchable(team, preflight, now)) {
            // Один код на все причины: пара всё равно смотрит на собственный
            // снимок, и разбирать «кто именно не готов» подбору нечем.
            throw ApiException.of("TEAM_PREFLIGHT_NOT_READY", 409);
        }
        return new LaunchablePreflight(
                RankedTeamDirectory.summary(team),
                preflight.initiatorUid(),
                preflight.intent(),
                preflight.requestedRoomId(),
                preflight.gameMode());
    }

    @Override
    @Transactional
    public void reportSearch(String teamId, String targetRoomId, boolean roomReady,
                             boolean searchStarted, Integer searchCount, boolean failed) {
        teams.reportSearch(teamId, targetRoomId, roomReady, searchStarted, searchCount, failed);
        realtime.preflightChanged(teamId);
    }

    /**
     * Пара готова, когда у обоих свежа связь и оба нажали «готов». Считается
     * здесь, а не хранится: свежесть — это возраст отметки времени, и у
     * колонки такого значения нет.
     */
    private static boolean launchable(TeamStore.TeamRow team, TeamStore.PreflightRow preflight, long nowMs) {
        for (String uid : team.memberUids()) {
            TeamStore.ParticipantRow row = preflight.participant(uid);
            if (row == null || !row.freshMedia(nowMs, TeamReadLimits.MEDIA_FLAG_TTL_MS) || !row.ready()) {
                return false;
            }
        }
        return !team.memberUids().isEmpty();
    }
}
