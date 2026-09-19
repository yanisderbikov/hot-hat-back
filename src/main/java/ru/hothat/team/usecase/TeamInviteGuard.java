package ru.hothat.team.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.team.store.TeamStore;

/**
 * Право отвечать на приглашение принадлежит приглашённому.
 *
 * <p>Проверка вынесена из сценария в отдельное правило, потому что двум
 * сценариям — согласию и отказу — нужен один и тот же ответ на вопрос «твоё ли
 * это приглашение», и повторять его в каждом значило бы завести два места,
 * которые со временем разойдутся.
 *
 * <p>Чужое, отвеченное и несуществующее приглашение дают один и тот же
 * ответ: по идентификатору нельзя узнать, существует ли оно и кому адресовано.
 *
 * <p>В плане это предикат {@code @teamInviteAuthz.isInvitee} из
 * {@code ru.hothat.security.authz}; пакета безопасности ещё нет, поэтому
 * предусловие стоит внутри сценария.
 */
@Component("teamInviteAuthz")
@RequiredArgsConstructor
public class TeamInviteGuard {

    private final TeamStore teams;

    /**
     * Проверяет право и заодно отдаёт само приглашение вместе с командой:
     * сценарию согласия оно нужно, чтобы назвать в ответе напарника и команду,
     * а читать одни и те же строки дважды — по разу на проверку и на ответ —
     * незачем.
     */
    public TeamStore.InviteRow requirePendingInvitee(HotHatUser user, String inviteId) {
        return teams.invite(inviteId)
                .filter(invite -> user.uid().equals(invite.inviteeUid()))
                .filter(invite -> TeamStore.PENDING.equals(invite.status()))
                .orElseThrow(() -> ApiException.of("INVITE_NOT_FOUND", 404));
    }
}
