package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.HostActivityResponseDTO;
import ru.hothat.room.api.dto.ReportHostActivityRequestDTO;
import ru.hothat.room.domain.HostHandoverPolicy;
import ru.hothat.room.domain.RoomPhase;

/**
 * Хозяин подтверждает, что он ещё за экраном.
 *
 * <p>Комната не в наборе — отметка не записывается, и это не ошибка: экран
 * шлёт её по движению мыши и не обязан следить за фазой. Старый
 * {@code setup_host_activity} отвечал в этом случае {@code {active:false}} без
 * остальных полей, и клиент различал ветки по отсутствию ключа. Здесь форма
 * ответа одна.
 *
 * <p>Порог бездействия уезжает в ответе. Сегодня три минуты вписаны литералом
 * и на сервере, и в двух местах фронтенда, а обратный отсчёт на экране
 * считается по клиентской копии: разойдись копии — человек увидел бы таймер,
 * не имеющий отношения к тому, что произойдёт.
 *
 * <p>Комната берётся под замком, хотя меняется одна колонка: у строки нет
 * частичного обновления, и запись отметки уносит в базу все колонки разом
 * (находка B7).
 */
@Service
@RequiredArgsConstructor
public class ReportHostActivityUseCase {

    private final RoomAccessGuard roomAuthz;
    private final SaverRoom saverRoom;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public HostActivityResponseDTO run(HotHatUser user, String roomId,
                                       ReportHostActivityRequestDTO request) {
        Room room = roomAuthz.requireHostRoomForWrite(user, roomId);
        if (!RoomPhase.fromWire(room.getPhase()).isSetup()) {
            return new HostActivityResponseDTO(false, HostHandoverPolicy.IDLE_LIMIT_MS, 0L);
        }
        long now = System.currentTimeMillis();
        room.setHostLastSetupActivityAt(now);
        room.setHostLastSetupActivityKind(request.kind().wireValue());
        saverRoom.save(room);
        return new HostActivityResponseDTO(true, HostHandoverPolicy.IDLE_LIMIT_MS,
                HostHandoverPolicy.IDLE_LIMIT_MS);
    }
}
