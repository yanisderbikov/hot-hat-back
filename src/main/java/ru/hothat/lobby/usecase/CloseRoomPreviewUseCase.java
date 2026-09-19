package ru.hothat.lobby.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;

/**
 * Закрыть превью комнаты.
 *
 * <p>Заменяет {@code deleteDoc spectators/{uid}} из браузера
 * ({@code app-core.js:4852}).
 *
 * <p>Комнату здесь не проверяем намеренно: уборка за собой обязана работать и
 * тогда, когда комната уже закрылась, — иначе место наблюдателя оставалось бы
 * висеть именно в тех случаях, ради которых уборка и нужна. Отсутствие места
 * тоже не ошибка: закрывать нечего, ответ тот же.
 */
@Service
@RequiredArgsConstructor
public class CloseRoomPreviewUseCase {

    private final RoomPreviewSeats seats;

    @PreAuthorize("hasAnyRole('GUEST','USER')")
    @Transactional
    public void run(HotHatUser user, String roomId) {
        seats.close(roomId, user.uid());
    }
}
