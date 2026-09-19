package ru.hothat.recording.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.recording.api.dto.RecorderReadinessResponseDTO;
import ru.hothat.recording.api.dto.RecordingStatus;
import ru.hothat.recording.domain.EgressStage;
import ru.hothat.recording.domain.RecordingKey;
import ru.hothat.recording.store.RecordingStore;

import java.util.Optional;

/**
 * Готов ли рекордер снимать партию.
 *
 * <p>Дешёвый опрос: читает отметки, оставленные машинной половиной, и никуда
 * не ходит — ни в LiveKit, ни в хранилище. Его зовут в цикле, дожидаясь старта
 * партии, и сетевой вызов внутри означал бы обращение к LiveKit каждую секунду.
 *
 * <p>Старт партии ждёт ТОЛЬКО подключения страницы-рекордера. Сигнал ACTIVE от
 * Egress остаётся диагностикой: сделать его обязательным нельзя, иначе игра
 * зависнет на задержке вебхука.
 */
@Service
@RequiredArgsConstructor
public class GetRecorderReadinessUseCase {

    private final RecordingStore store;

    @PreAuthorize("hasRole('USER')")
    public RecorderReadinessResponseDTO run(HotHatUser user, String roomId, int gameNumber) {
        Optional<RecordingStore.Card> found = store.card(new RecordingKey(roomId, gameNumber));
        if (found.isEmpty()) {
            return new RecorderReadinessResponseDTO(false, false, null, null, null, null, null, null, null);
        }
        RecordingStore.Card card = found.get();
        if (!store.isParticipant(card.passport().id(), user.uid())) {
            throw ApiException.of("RECORDING_PARTICIPANT_ONLY", 403);
        }
        RecordingStore.Session session = card.session();
        RecordingStore.Job job = card.job();
        String stage = card.stage();
        boolean failed = EgressStage.FAILED.wire().equals(stage) || EgressStage.DELETED.equals(stage);
        return new RecorderReadinessResponseDTO(
                true,
                session != null && session.readyAtMs() != null && !failed,
                card.passport().recordingId(),
                RecordingStatus.fromWire(stage),
                session == null ? null : blankToNull(session.livekitIdentity()),
                session == null ? null : session.readyAtMs(),
                session == null ? null : session.startSignalAtMs(),
                job == null ? null : job.activeAtMs(),
                job == null ? null : blankToNull(job.failureReason()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
