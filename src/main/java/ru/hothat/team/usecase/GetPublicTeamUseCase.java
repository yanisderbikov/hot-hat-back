package ru.hothat.team.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.team.api.dto.PublicTeamMemberView;
import ru.hothat.team.api.dto.PublicTeamResponseDTO;
import ru.hothat.team.api.dto.TeamStatus;
import ru.hothat.team.store.TeamStore;
import ru.hothat.util.Divisions;

import java.util.ArrayList;
import java.util.List;

/**
 * Показать публичную карточку чужой команды.
 *
 * <p>Пять чтений вместо одиннадцати. Старый {@code publicTeamProfile} звал на
 * каждого участника целый {@code publicPlayerProfile}, а тот поднимал профиль,
 * его собственную команду и две строки его личного рейтинга — четыре обращения
 * на человека ради аватара и ника, которые единственные и попадали на экран.
 * Здесь профили обоих участников читаются одной пачкой, а за своей командой и
 * своими рейтингами игрока есть его собственный публичный профиль.
 */
@Service
@RequiredArgsConstructor
public class GetPublicTeamUseCase {

    private final TeamStore teams;
    private final TeamProfileDirectory profileDirectory;
    private final TeamSeasonStatsReader statsReader;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public PublicTeamResponseDTO run(String teamId) {
        TeamStore.TeamRow team = teams.team(teamId)
                .orElseThrow(() -> ApiException.of("TEAM_NOT_FOUND", 404));

        TeamProfileDirectory.Snapshot profiles = profileDirectory.load(team.memberUids());
        List<PublicTeamMemberView> members = new ArrayList<>(team.members().size());
        for (TeamStore.MemberRow member : team.members()) {
            members.add(new PublicTeamMemberView(
                    member.uid(),
                    profiles.nickname(member.uid()),
                    profiles.avatarDataUrl(member.uid()),
                    profiles.divisionLanguage(member.uid())));
        }
        return new PublicTeamResponseDTO(
                team.teamId(),
                team.name(),
                teams.logo(team.teamId()).orElse(null),
                DivisionLanguage.fromWire(Divisions.normalize(team.divisionLanguage())),
                TeamStatus.fromWire(team.status()),
                members,
                statsReader.read(team.teamId(), team.divisionLanguage()));
    }
}
