package ru.hothat.machine.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.ApiException;
import ru.hothat.machine.api.dto.RecorderCeremonyOutcome;
import ru.hothat.machine.api.dto.RecorderCeremonyResponseDTO;
import ru.hothat.model.room.Room;
import ru.hothat.recording.spi.RecordingLifecycle;
import ru.hothat.recording.spi.RecordingLifecyclePort;

/**
 * Рекордер досмотрел церемонию награждения.
 *
 * <p>Это остановка «снизу»: не игрок решил, что хватит, а сама съёмка дошла до
 * конца сюжета. Отдельный сигнал нужен потому, что игрок закрывает вкладку
 * раньше, чем на экране догорают титры, и запись обрывалась бы на середине
 * награждения.
 *
 * <p>Шаг рекордера и остановка съёмки — два разных цикла и две разные строки:
 * отметка церемонии остаётся у сессии рекордера, состояние задания меняет
 * задание. Раньше это была одна строка на шестьдесят три колонки, и сигнал
 * рекордера переписывал заодно всё остальное.
 */
@Service
@RequiredArgsConstructor
public class CompleteRecorderCeremonyUseCase {

    private final RecorderSceneReader reader;
    private final RecordingLifecycle recordings;

    @PreAuthorize("hasRole('RECORDER')")
    public RecorderCeremonyResponseDTO run(String roomId, int gameNumber) {
        RecorderSceneReader.Snapshot snapshot = reader.load(roomId, gameNumber);
        Room room = snapshot.room();
        if (!snapshot.exact() || !"finished".equals(room.getPhase())) {
            throw ApiException.of("RECORDING_CEREMONY_NOT_READY", 409);
        }
        reader.requireRecording(room);
        RecordingLifecyclePort.FinishResult result = recordings.completeCeremony(roomId, gameNumber);
        return switch (result.outcome()) {
            // Стадия отдаётся и на FINISHED: контракт обещает «null, если
            // записи не заводилось», а не «null всегда». Раньше её тут не было
            // просто потому, что движок не возвращал её из остановки.
            case FINISHED -> new RecorderCeremonyResponseDTO(
                    RecorderCeremonyOutcome.FINISHED, result.recordingId(), result.stage());
            case ALREADY_FINISHED -> new RecorderCeremonyResponseDTO(
                    RecorderCeremonyOutcome.ALREADY_FINISHED, result.recordingId(), result.stage());
            case RECORDING_DISABLED -> new RecorderCeremonyResponseDTO(
                    RecorderCeremonyOutcome.RECORDING_DISABLED, null, null);
            case NOT_STARTED -> new RecorderCeremonyResponseDTO(
                    RecorderCeremonyOutcome.NOT_STARTED, null, null);
        };
    }
}
