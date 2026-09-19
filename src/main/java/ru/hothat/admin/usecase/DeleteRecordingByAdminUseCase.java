package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.admin.port.RecordingArchivePort;
import ru.hothat.common.api.ErrorCode;
import ru.hothat.config.HotHatUser;

/**
 * Удалить запись решением администратора.
 *
 * <p>Заменяет {@code POST /api/recordings} с {@code action=admin_delete}.
 *
 * <p>Строка записи остаётся и получает состояние {@code deleted}, а исчезает
 * файл. Так и было, и это правильно: на запись ссылаются сообщения переписки,
 * и снос строки оставил бы в чужом чате карточку, ведущую в никуда.
 *
 * <p>Ошибка удаления файла не отменяет операцию: администратор просил убрать
 * запись из обращения, и она из обращения уходит. Осиротевший объект подметёт
 * плановая сверка хранилища; молча оставить запись доступной было бы хуже.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteRecordingByAdminUseCase {

    private final RecordingArchivePort recordings;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void run(HotHatUser admin, String recordingId) {
        RecordingArchivePort.Archived recording = recordings.find(recordingId)
                .orElseThrow(ErrorCode.RECORDING_NOT_FOUND::raise);
        recordings.withdraw(recording);
        log.info("Запись {} удалена администратором {}", recording.recordingId(), admin.uid());
    }
}
