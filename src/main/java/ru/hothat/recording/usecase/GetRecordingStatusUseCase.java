package ru.hothat.recording.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.recording.api.dto.RecordingStatusResponseDTO;
import ru.hothat.recording.domain.RecordingKey;
import ru.hothat.recording.store.RecordingStore;

import java.util.Optional;

/**
 * Состояние записи текущей партии.
 *
 * <p>Одна форма ответа на оба случая: записи нет — {@code exists=false} и
 * пустая карточка. Право смотреть — у участника партии: карточка несёт состав,
 * счёт и названия команд, и посторонним она не полагается.
 */
@Service
@RequiredArgsConstructor
public class GetRecordingStatusUseCase {

    private final RecordingStore store;
    private final EgressRefresher refresher;
    private final RecordingCards cards;

    @PreAuthorize("hasRole('USER')")
    public RecordingStatusResponseDTO run(HotHatUser user, String roomId, int gameNumber) {
        RecordingKey key = new RecordingKey(roomId, gameNumber);
        Optional<RecordingStore.Card> found = store.card(key);
        if (found.isEmpty()) {
            return new RecordingStatusResponseDTO(false, false, null);
        }
        RecordingStore.Card card = found.get();
        if (!store.isParticipant(card.passport().id(), user.uid())) {
            throw ApiException.of("RECORDING_PARTICIPANT_ONLY", 403);
        }
        RecordingStore.Card fresh = refresher.refresh(key, card);
        return new RecordingStatusResponseDTO(true,
                store.isSaved(fresh.passport().id(), user.uid()),
                cards.card(fresh));
    }
}
