package ru.hothat.recording.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.HotHatUser;

import java.util.UUID;

/**
 * Убрать запись из своей библиотеки.
 *
 * <p>Ответа нет: старый {@code {ok:true}} не нёс сведений, которых не было бы
 * в запросе. Сам файл не удаляется — уходит только пометка владения; если
 * забрал последний, записи возвращается обычный срок хранения, и её заберёт
 * плановая уборка.
 *
 * <p>Проверка «а была ли она у тебя» стоит здесь не ради формальности.
 * Сегодня {@code remove_saved} не проверяет ничего: любой вошедший может
 * назвать чужой идентификатор, и движок перепишет чужой записи срок хранения —
 * то есть назначит удаление файлу, которого он в глаза не видел. Отказом это
 * не отвечаем: удаление того, чего у тебя не было, — не ошибка, а уже
 * достигнутый итог, и второй вызов DELETE обязан вести себя так же, как первый.
 */
@Service
@RequiredArgsConstructor
public class ForgetRecordingUseCase {

    private final RecordingOwnershipGuard ownershipGuard;
    private final RecordingWrites writes;

    @PreAuthorize("hasRole('USER')")
    public void run(HotHatUser user, String recordingId) {
        UUID id = ownershipGuard.require(recordingId);
        writes.forget(id, user.uid());
    }
}
