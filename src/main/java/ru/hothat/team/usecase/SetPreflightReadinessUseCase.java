package ru.hothat.team.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.realtime.spi.RealtimeChangeBus;
import ru.hothat.team.api.dto.PreflightReadinessResponseDTO;
import ru.hothat.team.api.dto.SetPreflightReadinessRequestDTO;
import ru.hothat.team.store.TeamStore;

/**
 * Поставить или снять свою готовность.
 *
 * <p>Готовность нельзя поставить без подтверждённой связи: ответ 409
 * {@code MEDIA_NOT_READY}. Это не формальность — «готов» здесь означает
 * «меня видно и слышно», и пара, запустившая поиск с потухшей камерой,
 * получила бы техническое поражение.
 *
 * <p>В ответ едет весь снимок, а не записанное значение: нажатие меняет и
 * сводные признаки, по которым включается кнопка запуска.
 */
@Service
@RequiredArgsConstructor
public class SetPreflightReadinessUseCase {

    private final TeamMembershipGuard teamAuthz;
    private final TeamStore teams;
    private final TeamProfileDirectory profileDirectory;
    private final PreflightAssembler assembler;
    /** Готовность напарника — единственное, ради чего открыт канал пары. */
    private final RealtimeChangeBus realtime;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public PreflightReadinessResponseDTO run(HotHatUser user, SetPreflightReadinessRequestDTO request) {
        TeamStore.TeamRow team = teamAuthz.requireActiveTeam(user);
        long now = System.currentTimeMillis();
        TeamStore.PreflightRow preflight = teams.preflight(team.teamId())
                .filter(row -> !row.expired(now))
                .orElseThrow(() -> ApiException.of("PREFLIGHT_REQUIRED", 409));

        boolean ready = Boolean.TRUE.equals(request.ready());
        if (!teams.setReady(preflight.teamId(), user.uid(), ready, TeamReadLimits.MEDIA_FLAG_TTL_MS)) {
            throw ApiException.of("MEDIA_NOT_READY", 409);
        }

        realtime.preflightChanged(team.teamId());
        TeamStore.PreflightRow updated = teams.preflight(team.teamId())
                .orElseThrow(() -> ApiException.of("PREFLIGHT_REQUIRED", 409));
        TeamProfileDirectory.Snapshot profiles = profileDirectory.load(team.memberUids());
        return new PreflightReadinessResponseDTO(assembler.session(updated, team, profiles, now));
    }
}
