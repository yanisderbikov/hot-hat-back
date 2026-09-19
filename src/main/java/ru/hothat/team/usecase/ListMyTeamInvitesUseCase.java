package ru.hothat.team.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.team.api.dto.TeamInviteView;
import ru.hothat.team.api.dto.TeamInvitesResponseDTO;
import ru.hothat.team.store.TeamStore;
import ru.hothat.util.Divisions;

import java.util.ArrayList;
import java.util.List;

/**
 * Показать приглашения в команды, ждущие ответа.
 *
 * <p>Читает только приглашения, их команды и профили тех, кто зовёт. Раньше
 * этот список приезжал полем в ответе {@code my_team}, а тот заодно поднимал
 * свою команду, профили её участников, две строки рейтинга и снимок
 * префлайта — всё это уходило в мусор, потому что страница портала
 * спрашивала ответ ради одного массива приглашений.
 *
 * <p>Имя зовущего берётся из его карточки: копии ника в приглашении в v2
 * нет вовсе. Она и была источником путаницы — между приглашением и ответом
 * ник могли сменить, и экран показывал прежний.
 */
@Service
@RequiredArgsConstructor
public class ListMyTeamInvitesUseCase {

    private final TeamStore teams;
    private final TeamProfileDirectory profileDirectory;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public TeamInvitesResponseDTO run(HotHatUser user) {
        List<TeamStore.InviteRow> invites = teams.pendingInvites(user.uid(), TeamReadLimits.INVITES);

        // Профили зовущих — одной пачкой на весь список, а не по одному в цикле.
        List<String> captainUids = new ArrayList<>(invites.size());
        for (TeamStore.InviteRow invite : invites) {
            captainUids.add(invite.captainUid());
        }
        TeamProfileDirectory.Snapshot profiles = profileDirectory.load(captainUids);

        List<TeamInviteView> items = new ArrayList<>(invites.size());
        for (TeamStore.InviteRow invite : invites) {
            String captainUid = invite.captainUid();
            items.add(new TeamInviteView(
                    invite.inviteId(),
                    invite.team().teamId(),
                    invite.team().name(),
                    captainUid,
                    profiles.nickname(captainUid),
                    profiles.avatarDataUrl(captainUid),
                    DivisionLanguage.fromWire(Divisions.normalize(invite.team().divisionLanguage())),
                    invite.createdAtMs()));
        }
        return new TeamInvitesResponseDTO(items, null, TeamReadLimits.INVITES);
    }
}
