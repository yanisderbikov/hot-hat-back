package ru.hothat.recording.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.recording.api.dto.RecordingSessionResponseDTO;
import ru.hothat.recording.api.dto.RecordingStartOutcome;
import ru.hothat.recording.domain.RecordingKey;
import ru.hothat.recording.port.EgressControlPort;
import ru.hothat.recording.port.RecordingStoragePort;
import ru.hothat.recording.port.RoomSnapshotPort;
import ru.hothat.recording.store.RecordingStore;

import java.util.UUID;

/**
 * Начать запись партии.
 *
 * <p>Порядок шагов здесь — главное, что изменилось. Раньше вся работа шла в
 * одной транзакции, внутри которой сидел вызов LiveKit с таймаутом двадцать
 * секунд (находка B5). Теперь так: короткая транзакция «застолбить», вызов
 * LiveKit СНАРУЖИ транзакции, короткая транзакция «применить ответ».
 *
 * <p>Второе изменение — идемпотентность. Раньше её изображала секундная
 * блокировка в колонке; теперь это уникальный ключ {@code ux_recording_game}:
 * двух заданий на одну партию не может существовать физически.
 *
 * <p>Выключенная запись и закрытая комната — исходы, а не ошибки: клиент зовёт
 * этот адрес на каждом старте партии, не зная настроек комнаты.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StartRecordingUseCase {

    private final RoomSnapshotPort rooms;
    private final EgressControlPort egress;
    private final RecordingStoragePort storage;
    private final RecordingWrites writes;
    private final RecordingStore store;

    @PreAuthorize("hasRole('USER')")
    public RecordingSessionResponseDTO run(HotHatUser user, String roomId, int requestedGameNumber) {
        RoomSnapshotPort.RoomSnapshot room = rooms.find(roomId)
                .orElseThrow(() -> ApiException.of("ROOM_NOT_FOUND", 404));
        if (!room.recordingEnabled()) {
            return new RecordingSessionResponseDTO(
                    RecordingStartOutcome.RECORDING_DISABLED, requestedGameNumber, null, null);
        }
        if (room.closed()) {
            return new RecordingSessionResponseDTO(
                    RecordingStartOutcome.ROOM_CLOSED, requestedGameNumber, null, null);
        }
        if (!rooms.isMember(roomId, user.uid())) {
            throw ApiException.of("PLAYER_NOT_FOUND", 403);
        }
        // В setup запись прогревается на СЛЕДУЮЩУЮ партию, в игре — на текущую.
        boolean prewarm = room.setup();
        int expected = prewarm ? room.gameNumber() + 1 : room.gameNumber();
        RecordingKey key = new RecordingKey(roomId, requestedGameNumber);
        if (requestedGameNumber != expected || !key.valid()) {
            // Нулевой партии не бывает: раньше номер зажимался в ноль и заводил
            // запись «hat-…-0», у которой не было своей партии.
            throw ApiException.of("RECORDING_GAME_NUMBER_MISMATCH", 409);
        }
        if (!storage.configured()) {
            throw ApiException.of("S3_RECORDING_STORAGE_NOT_CONFIGURED", 503);
        }
        if (!egress.available()) {
            throw ApiException.of("LIVEKIT_NOT_CONFIGURED", 503);
        }

        RecordingWrites.Reservation reservation = writes.reserveStart(key, user.uid(), room, prewarm);
        if (!reservation.fresh()) {
            RecordingStore.Card running = reservation.running();
            return new RecordingSessionResponseDTO(RecordingStartOutcome.ALREADY_RUNNING, key.gameNumber(),
                    key.publicId(),
                    running == null || running.job() == null ? null : blankToNull(running.job().egressId()));
        }

        UUID id = reservation.recordingId();
        EgressControlPort.Snapshot started;
        try {
            // Снаружи транзакции: соединение из пула не держится на время
            // сетевого вызова.
            started = egress.start(key.roomId(), key.gameNumber(), key.objectPath());
        } catch (RuntimeException e) {
            writes.applyStartFailure(id, e.getMessage());
            throw e;
        }
        writes.applyStarted(id, started);
        return new RecordingSessionResponseDTO(RecordingStartOutcome.STARTED, key.gameNumber(),
                key.publicId(), blankToNull(started.egressId()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
