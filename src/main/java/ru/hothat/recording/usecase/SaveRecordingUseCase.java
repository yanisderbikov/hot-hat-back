package ru.hothat.recording.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.recording.api.dto.SavedRecordingResponseDTO;
import ru.hothat.recording.domain.EgressStage;
import ru.hothat.recording.store.RecordingStore;

import java.util.UUID;

/**
 * Положить запись к себе в библиотеку.
 *
 * <p>Сохранение только помечает владение и снимает срок хранения. Остановкой
 * записи оно не является: страница-рекордер сама завершает съёмку после
 * церемонии награждения.
 *
 * <p>Пишутся ДВЕ строки одного цикла — отметка владения и счётчик сохранений,
 * — и ни одна чужая. Раньше это же действие переписывало всю строку записи
 * целиком, вместе с состоянием Egress и отметками рекордера: два игрока,
 * сохранившие запись одновременно, откатывали друг другу состояние съёмки.
 *
 * <p>Повторное сохранение — не ошибка, а тот же самый итог, и ответ у него
 * тот же. Счётчик при этом не двигается: он считает игроков, а не нажатия.
 */
@Service
@RequiredArgsConstructor
public class SaveRecordingUseCase {

    private final RecordingOwnershipGuard ownershipGuard;
    private final RecordingWrites writes;
    private final RecordingStore store;
    private final RecordingCards cards;

    @PreAuthorize("hasRole('USER')")
    public SavedRecordingResponseDTO run(HotHatUser user, String recordingId) {
        UUID id = ownershipGuard.require(recordingId);
        ownershipGuard.requireParticipant(user, id);
        RecordingStore.Card card = store.card(id)
                .orElseThrow(() -> ApiException.of("RECORDING_NOT_FOUND", 404));
        String stage = card.stage();
        if (EgressStage.FAILED.wire().equals(stage) || EgressStage.DELETED.equals(stage)) {
            throw ApiException.of("RECORDING_NOT_AVAILABLE", 409);
        }
        writes.save(id, user.uid());
        return new SavedRecordingResponseDTO(true, cards.card(store.card(id).orElse(card)));
    }
}
