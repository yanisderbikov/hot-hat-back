package ru.hothat.team.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.profile.api.dto.DivisionLanguage;
import ru.hothat.team.api.dto.AcceptedTeamInviteResponseDTO;
import ru.hothat.team.store.TeamStore;
import ru.hothat.util.Divisions;

import java.util.List;

/**
 * Принять приглашение в команду.
 *
 * <p>Согласие подтверждает команду обоим: она становится активной, и оба
 * участника выходят из состояния ожидания. Это и есть причина, по которой
 * согласие и отказ разведены на два адреса, — у них разные последствия и
 * разные ответы, а не разное значение флага в теле.
 *
 * <p>Строка напарника в составе появляется ровно здесь. До согласия его в
 * команде нет вовсе, поэтому одного и того же человека могут звать сразу
 * несколько пар, и уникальность «одна команда на игрока» им не мешает.
 */
@Service
@RequiredArgsConstructor
public class AcceptTeamInviteUseCase {

    private final TeamStore teams;
    private final TeamInviteGuard inviteGuard;
    /** Дивизион игрока — у карточки: своей копии у команды нет. */
    private final PlayerCardPort cards;
    private final TeamProfileDirectory profileDirectory;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public AcceptedTeamInviteResponseDTO run(HotHatUser user, String inviteId) {
        // Приглашение читается до ответа: ответ обязан назвать, с кем игрок
        // теперь в команде, а после записи имя капитана пришлось бы искать
        // заново.
        TeamStore.InviteRow invite = inviteGuard.requirePendingInvitee(user, inviteId);
        TeamStore.TeamRow team = invite.team();
        String partnerUid = invite.captainUid();

        String divisionLanguage = Divisions.normalize(cards.card(user.uid())
                .map(PlayerCardPort.Card::divisionLanguage).orElse(null));
        if (teams.teamOf(user.uid()).isPresent()) {
            throw ApiException.of("ALREADY_IN_TEAM", 409);
        }
        if (!divisionLanguage
                .equals(Divisions.normalize(team.divisionLanguage()))) {
            throw ApiException.of("TEAM_DIVISION_MISMATCH", 409);
        }

        teams.acceptInvite(inviteId, user.uid());

        TeamProfileDirectory.Snapshot profiles = profileDirectory.load(List.of(partnerUid));
        return new AcceptedTeamInviteResponseDTO(
                inviteId,
                team.teamId(),
                team.name(),
                partnerUid,
                profiles.nickname(partnerUid),
                DivisionLanguage.fromWire(Divisions.normalize(team.divisionLanguage())));
    }
}
