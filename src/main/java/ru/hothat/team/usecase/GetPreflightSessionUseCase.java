package ru.hothat.team.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.team.api.dto.PreflightStatusResponseDTO;
import ru.hothat.team.store.TeamStore;

/**
 * Прочитать текущую проверку готовности пары.
 *
 * <p>Единственный способ для участника увидеть чужие флаги: связь и готовность
 * напарника не приезжают ни с чем другим. Адрес опрашивают, пока идёт
 * подготовка, — раньше ради этого перезапрашивали {@code my_team} целиком,
 * вместе с составом, статистикой сезона и списком приглашений.
 *
 * <p>Протухшую проверку сервер не отдаёт: после пяти минут от старта снимка
 * нет, даже если строка в таблице ещё лежит. Решение об этом принимает
 * сервер, а не клиент, — у клиента часы могут уехать.
 */
@Service
@RequiredArgsConstructor
public class GetPreflightSessionUseCase {

    private final TeamMembershipGuard teamAuthz;
    private final TeamStore teams;
    private final TeamProfileDirectory profileDirectory;
    private final PreflightAssembler assembler;

    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public PreflightStatusResponseDTO run(HotHatUser user) {
        TeamStore.TeamRow team = teamAuthz.requireActiveTeam(user);
        long now = System.currentTimeMillis();
        TeamStore.PreflightRow preflight = teams.preflight(team.teamId())
                .filter(row -> !row.expired(now))
                .orElse(null);
        if (preflight == null) {
            return new PreflightStatusResponseDTO(null);
        }
        TeamProfileDirectory.Snapshot profiles = profileDirectory.load(team.memberUids());
        return new PreflightStatusResponseDTO(assembler.session(preflight, team, profiles, now));
    }
}
