package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.admin.api.dto.AdminUsageSnapshotResponseDTO;
import ru.hothat.admin.domain.SnapshotAuthor;
import ru.hothat.config.HotHatUser;

/**
 * Снять снимок расхода по кнопке администратора.
 *
 * <p>Заменяет админскую ветку {@code POST /api/monitor}. Второй вход —
 * агент мониторинга по секрету — живёт в {@code /api/v2/machine} и остаётся
 * отдельным адресом: у него другое право входа и другие последствия. Только
 * он рассылает письма о превышении порогов и проверяет окно планового
 * снятия; ручной снимок делает ровно то, о чём просили, — снимает и сохраняет.
 *
 * <p>Снимок называет того, кто его снял. Раньше «кто» не записывалось вовсе, и
 * плановый снимок было не отличить от нажатия кнопки — а различать надо: у них
 * разные последствия.
 *
 * <p>Собственной транзакции здесь нет: снимок ходит по сети за состоянием
 * сайта, API и машины, и держать транзакцию открытой всё это время
 * запрещено (§7.2). Запись снимка транзакционна у двери в таблицы.
 */
@Service
@RequiredArgsConstructor
public class TakeUsageSnapshotByAdminUseCase {

    private final UsageSnapshotCollector collector;
    private final UsageSnapshotMapper mapper;

    @PreAuthorize("hasRole('ADMIN')")
    public AdminUsageSnapshotResponseDTO run(HotHatUser admin) {
        return new AdminUsageSnapshotResponseDTO(mapper.snapshot(
                collector.take(SnapshotAuthor.ADMIN, admin.uid(), false).snapshot()));
    }
}
