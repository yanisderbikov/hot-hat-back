package ru.hothat.room.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.model.room.Room;
import ru.hothat.repository.SaverRoom;
import ru.hothat.room.api.dto.RecordingPreferenceResponseDTO;
import ru.hothat.room.api.dto.SetRecordingPreferenceRequestDTO;
import ru.hothat.room.domain.RoomPhase;

import java.time.Instant;

/**
 * Включить запись партий в комнате.
 *
 * <p>Право владельца сервиса, а не хозяина комнаты: запись стоит денег у
 * внешнего сервиса, и решать за счёт хозяина не может ни один из игроков.
 *
 * <p>Роль проверяется ролью. Сегодня это проверка по адресу почты внутри
 * сервиса ({@code GameServiceImpl.isOwner}) — одна из семи точек, где правило
 * владельца пересчитывается заново (находка F7). {@code HotHatUser} несёт
 * готовый признак, и роль {@code OWNER}, до сих пор не проверявшаяся нигде,
 * получает здесь своего второго потребителя.
 *
 * <p>Только до старта партии: начатую игру дописать с середины нельзя, а
 * выключить запись на ходу значило бы оборвать файл, который уже пишется.
 *
 * <p>Владелец обязан быть в комнате. Иначе он включал бы запись чужой партии
 * по одному лишь идентификатору из чата, и участники узнали бы об этом из
 * готового файла.
 */
@Service
@RequiredArgsConstructor
public class SetRecordingPreferenceUseCase {

    private final RoomAccessGuard roomAuthz;
    private final SaverRoom saverRoom;

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public RecordingPreferenceResponseDTO run(HotHatUser user, String roomId,
                                              SetRecordingPreferenceRequestDTO request) {
        Room room = roomAuthz.requireRoomForWrite(roomId);
        roomAuthz.requireMember(user, roomId);
        if (!RoomPhase.fromWire(room.getPhase()).isSetup()) {
            throw ApiException.of("GAME_ALREADY_STARTED", 409);
        }
        long now = System.currentTimeMillis();
        room.setRecordGame(Boolean.TRUE.equals(request.enabled()));
        room.setRecordingPreferenceUpdatedAt(Instant.ofEpochMilli(now));
        room.setRecordingPreferenceUpdatedBy(user.uid());
        saverRoom.save(room);
        return new RecordingPreferenceResponseDTO(roomId,
                Boolean.TRUE.equals(room.getRecordGame()), user.uid(), now);
    }
}
