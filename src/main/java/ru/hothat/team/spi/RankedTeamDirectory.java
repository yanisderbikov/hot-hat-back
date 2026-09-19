package ru.hothat.team.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.team.store.TeamStore;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Реализация read-порта команды: живёт у владельца данных, как велит §7.3
 * плана.
 *
 * <p>Зависит только от своего хранилища, и это не случайность. Карточку
 * игрока спрашивает и сама команда — правило «участник обязан быть в дивизионе
 * своей команды» без неё не проверить. Если бы проекции жили в одном бине с
 * этой проверкой, кольцо профиль → команда → профиль замкнулось бы на
 * конструкторах. Готовность пары со всеми её предусловиями вынесена
 * в {@link TeamPreflightPort} именно поэтому.
 */
@Component
@RequiredArgsConstructor
public class RankedTeamDirectory implements RankedTeamPort {

    private final TeamStore teams;

    @Override
    @Transactional(readOnly = true)
    public Optional<TeamSummary> teamOf(String uid) {
        return teams.teamOf(uid).map(RankedTeamDirectory::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, TeamSummary> teams(Collection<String> teamIds) {
        Map<String, TeamSummary> result = new LinkedHashMap<>();
        for (Map.Entry<String, TeamStore.TeamRow> entry : teams.teams(teamIds).entrySet()) {
            result.put(entry.getKey(), summary(entry.getValue()));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> logos(Collection<String> teamIds) {
        return teams.logos(teamIds);
    }

    static TeamSummary summary(TeamStore.TeamRow team) {
        return new TeamSummary(
                team.teamId(),
                team.name(),
                team.divisionLanguage(),
                team.status(),
                team.captainUid(),
                team.memberUids());
    }
}
