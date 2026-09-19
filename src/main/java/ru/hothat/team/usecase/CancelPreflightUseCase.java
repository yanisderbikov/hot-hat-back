package ru.hothat.team.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.realtime.spi.RealtimeChangeBus;
import ru.hothat.team.store.TeamStore;

/**
 * Отменить проверку готовности.
 *
 * <p>Ответа нет: отмена ничего не создаёт, а адрес уже назвал и предмет, и
 * исход. Повторная отмена — не ошибка: удалять нечего, состояние то же самое.
 *
 * <p>Начатый поиск соперников этот сценарий не отменяет — за билет подбора
 * отвечает область лобби, и клиент снимает его своим адресом перед отменой.
 */
@Service
@RequiredArgsConstructor
public class CancelPreflightUseCase {

    private final TeamMembershipGuard teamAuthz;
    private final TeamStore teams;
    /** Отмена — тоже новость: напарник обязан увидеть, что подготовки нет. */
    private final RealtimeChangeBus realtime;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void run(HotHatUser user) {
        String teamId = teamAuthz.requireActiveTeam(user).teamId();
        teams.cancelPreflight(teamId);
        realtime.preflightChanged(teamId);
    }
}
