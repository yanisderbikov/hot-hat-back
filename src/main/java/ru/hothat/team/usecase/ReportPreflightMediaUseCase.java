package ru.hothat.team.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.realtime.spi.RealtimeChangeBus;
import ru.hothat.team.api.dto.PreflightMediaResponseDTO;
import ru.hothat.team.api.dto.ReportPreflightMediaRequestDTO;
import ru.hothat.team.store.TeamStore;

/**
 * Отчитаться о своей камере и микрофоне.
 *
 * <p>Самый частый вызов области: клиент повторяет его, пока идёт подготовка,
 * потому что флаг протухает за 25 секунд. Отсюда и ответ размером в три поля —
 * возить в нём весь состав с никами значило бы платить за то, что уже приехало
 * по адресу чтения.
 *
 * <p>Пропавшая связь снимает и готовность: играть вслепую нельзя. Правило
 * держит и ограничение базы {@code ck_team_preflight_participant_ready} —
 * раньше оно жило в двух местах кода сразу, и разойтись им ничто не мешало.
 *
 * <p>Команду сценарий берёт из личности, а не из тела: старое действие
 * принимало {@code teamId} от клиента и при пустом значении находило команду
 * само. Правило одно, и живёт оно на сервере.
 */
@Service
@RequiredArgsConstructor
public class ReportPreflightMediaUseCase {

    private final TeamMembershipGuard teamAuthz;
    private final TeamStore teams;
    /** Связь напарника видна только через канал: его экран ждёт события. */
    private final RealtimeChangeBus realtime;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public PreflightMediaResponseDTO run(HotHatUser user, ReportPreflightMediaRequestDTO request) {
        TeamStore.TeamRow team = teamAuthz.requireActiveTeam(user);
        long now = System.currentTimeMillis();
        boolean live = teams.preflight(team.teamId())
                .filter(row -> !row.expired(now))
                .isPresent();
        if (!live) {
            // Отчёт без проверки некуда класть: строка участника существует
            // только внутри проверки и уходит вместе с ней.
            throw ApiException.of("PREFLIGHT_REQUIRED", 409);
        }
        teams.reportMedia(team.teamId(), user.uid(), Boolean.TRUE.equals(request.mediaOk()));
        realtime.preflightChanged(team.teamId());
        return new PreflightMediaResponseDTO(
                Boolean.TRUE.equals(request.mediaOk()),
                now,
                TeamReadLimits.MEDIA_FLAG_TTL_MS);
    }
}
