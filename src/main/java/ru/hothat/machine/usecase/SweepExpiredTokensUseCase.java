package ru.hothat.machine.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.auth.store.IdentityStore;
import ru.hothat.machine.api.dto.TokenSweepResponseDTO;
import ru.hothat.machine.store.MaintenanceJournal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Уборка протухших токенов.
 *
 * <p>Что убирать, знает область личности — здесь только повод и журнал.
 * Прогон записывается в ту же историю, что уборка комнат и записей: «когда
 * последний раз убирали и чем кончилось» должно отвечаться одним запросом,
 * а не тремя разными способами.
 */
@Service
@RequiredArgsConstructor
public class SweepExpiredTokensUseCase {

    private final IdentityStore identities;
    private final MaintenanceJournal journal;

    @PreAuthorize("hasRole('CRON')")
    @Transactional
    public TokenSweepResponseDTO run() {
        Instant before = Instant.now();
        Optional<Long> run = journal.begin(MaintenanceJournal.TOKEN_SWEEP, "cron");
        if (run.isEmpty()) {
            return new TokenSweepResponseDTO(before.toString());
        }
        long runId = run.get();
        int swept;
        try {
            swept = identities.sweepExpired(before);
        } catch (RuntimeException e) {
            journal.failed(runId, e.getMessage());
            throw e;
        }
        // Поштучно токены не перечисляются: их тысячи, и строка на каждый в
        // истории прогонов стоила бы дороже самой уборки.
        journal.succeeded(runId, swept, swept, List.of());
        return new TokenSweepResponseDTO(before.toString());
    }
}
