package ru.hothat.realtime.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.team.store.TeamStore;
import ru.hothat.team.api.dto.PreflightSessionView;
import ru.hothat.team.usecase.GetPreflightSessionUseCase;
import ru.hothat.team.usecase.TeamMembershipGuard;

/**
 * Показать проверку готовности слушателю канала
 * {@code /ws/v2/team/{teamId}/preflight}.
 *
 * <p>Сценарий один и тот же для обоих кадров: и для приветственного, и для
 * каждого обновления канал спрашивает одно — «как выглядит проверка для этого
 * участника сейчас».
 *
 * <p>Снимок собирает тот же {@link GetPreflightSessionUseCase}, что отвечает
 * на {@code GET /api/v2/team/me/preflight}, вместе с правилом «протухшую
 * проверку сервер не отдаёт»: пять минут считает сервер, у клиента часы могут
 * уехать.
 *
 * <p>Право — членство в команде, и проверяется оно на <b>каждом</b> кадре тем
 * же {@link TeamMembershipGuard}, что стоит на семи адресах команды. Проверок
 * две, и вторая не лишняя: сначала «у слушателя есть подтверждённая команда»,
 * потом «она та самая, что названа в адресе». Без второй участник одной пары
 * подписался бы на канал чужой — и получал бы снимок своей проверки под чужим
 * адресом, то есть тихо не то, что просил.
 *
 * <p>Идентификатор команды в адресе оставлен, хотя сценарий берёт команду по
 * личности: это ключ рассылки. Событие о смене готовности называет команду, а
 * не игрока, и без ключа пришлось бы перебирать все открытые соединения.
 */
@Service
@RequiredArgsConstructor
public class StreamPreflightUseCase {

    private final TeamMembershipGuard teamAuthz;
    private final GetPreflightSessionUseCase preflightSession;

    @PreAuthorize("hasRole('USER')")
    public PreflightSessionView run(HotHatUser user, String teamId) {
        TeamStore.TeamRow team = teamAuthz.requireActiveTeam(user);
        if (!teamId.equals(team.teamId())) {
            // Тот же код, что у адресов команды: «команда не найдена». Сказать
            // «это чужая команда» значило бы подтвердить, что она существует.
            throw ApiException.of("TEAM_NOT_FOUND", 404);
        }
        return preflightSession.run(user).session();
    }
}
