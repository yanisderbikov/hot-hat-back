package ru.hothat.team.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.team.store.TeamStore;

/**
 * Отклонить приглашение в команду.
 *
 * <p>Ответа нет: отказ ничего не создаёт, а адрес уже назвал и приглашение,
 * и исход. Последствие при этом не пустое — команда, которая ждала ответа,
 * распускается, и основатель снова свободен вместе с занятым названием.
 *
 * <p>Само приглашение уходит вместе с командой: оно на неё ссылается. Строки
 * «отклонено» в v2 не остаётся, и это намеренно — читают только ждущие
 * ответа, а отказ не показывает ни один экран. Отдельной таблицы имён,
 * которую раньше приходилось чистить вручную, тоже нет: имя занято ровно
 * пока жива команда.
 */
@Service
@RequiredArgsConstructor
public class DeclineTeamInviteUseCase {

    private final TeamStore teams;
    private final TeamInviteGuard inviteGuard;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void run(HotHatUser user, String inviteId) {
        TeamStore.InviteRow invite = inviteGuard.requirePendingInvitee(user, inviteId);

        teams.disband(invite.team().teamId());
    }
}
