package ru.hothat.support;

import ru.hothat.team.spi.RankedTeamPort;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Команда игрока так, как её видят соседние области. */
public final class FakeRankedTeams implements RankedTeamPort {

    private TeamSummary team;

    public static FakeRankedTeams empty() {
        return new FakeRankedTeams();
    }

    public static FakeRankedTeams of(String status, String... memberUids) {
        FakeRankedTeams teams = new FakeRankedTeams();
        teams.team = new TeamSummary("team-red", "Пара", "ru", status,
                memberUids.length == 0 ? null : memberUids[0], List.of(memberUids));
        return teams;
    }

    @Override
    public Optional<TeamSummary> teamOf(String uid) {
        return Optional.ofNullable(team).filter(row -> row.memberUids().contains(uid));
    }

    @Override
    public Map<String, TeamSummary> teams(Collection<String> teamIds) {
        return Map.of();
    }

    @Override
    public Map<String, String> logos(Collection<String> teamIds) {
        return Map.of();
    }
}
