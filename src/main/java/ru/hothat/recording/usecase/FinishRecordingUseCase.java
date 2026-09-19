package ru.hothat.recording.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.HotHatUser;
import ru.hothat.recording.api.dto.FinishedRecordingResponseDTO;
import ru.hothat.recording.api.dto.RecordingFinishOutcome;
import ru.hothat.recording.api.dto.RecordingStatus;
import ru.hothat.recording.domain.RecordingKey;
import ru.hothat.recording.spi.RecordingLifecyclePort;

/**
 * Завершить запись партии по воле игрока.
 *
 * <p>Это остановка «сверху»: партия кончилась, рекордер не успел подготовиться,
 * старт сорвался и прогретую запись надо убрать. Остановку «снизу», по концу
 * церемонии, делает сам рекордер машинным адресом, и там своя учётка и своя
 * причина завершения. Третья дорога, без человека вовсе, — техническое
 * поражение и уборка заброшенных комнат; адреса у неё нет и не будет.
 *
 * <p>Вся работа — у {@link RecordingFinisher}: три дороги обязаны вести себя
 * одинаково, а одинаково они себя ведут только тогда, когда это один код.
 */
@Service
@RequiredArgsConstructor
public class FinishRecordingUseCase {

    private final RecordingFinisher finisher;

    @PreAuthorize("hasRole('USER')")
    public FinishedRecordingResponseDTO run(HotHatUser user, String roomId, int gameNumber) {
        RecordingKey key = new RecordingKey(roomId, gameNumber);
        RecordingLifecyclePort.FinishResult result = finisher.finish(key, user.uid(), null, true);
        return new FinishedRecordingResponseDTO(outcome(result.outcome()), gameNumber,
                result.recordingId(),
                result.stage() == null ? null : RecordingStatus.fromWire(result.stage()));
    }

    private static RecordingFinishOutcome outcome(RecordingLifecyclePort.Finish finish) {
        return switch (finish) {
            case FINISHED -> RecordingFinishOutcome.FINISHED;
            case ALREADY_FINISHED -> RecordingFinishOutcome.ALREADY_FINISHED;
            case NOT_STARTED -> RecordingFinishOutcome.NOT_STARTED;
            case RECORDING_DISABLED -> RecordingFinishOutcome.RECORDING_DISABLED;
        };
    }
}
