package ru.hothat.recording.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.recording.domain.RecordingKey;
import ru.hothat.recording.store.RecordingStore;

import java.util.UUID;

/**
 * Право смотреть запись.
 *
 * <p>Складывается из трёх источников: запись лежит у тебя в библиотеке, её
 * тебе открыли в переписке или ты играл в этой партии. Раньше первые два были
 * поиском по jsonb-массивам оператором {@code @>} по всей таблице, третий —
 * поиском строки в третьем массиве.
 *
 * <p>Проверка «а была ли она у тебя» нужна и на удаление. Сегодня
 * {@code remove_saved} не проверяет ничего: любой вошедший может назвать чужой
 * идентификатор, и движок перепишет чужой записи срок хранения — то есть
 * назначит удаление файлу, которого он в глаза не видел.
 */
@Component
@RequiredArgsConstructor
public class RecordingOwnershipGuard {

    private final RecordingStore store;

    /** Суррогат записи по её имени снаружи; 404, если такой записи нет. */
    @Transactional(readOnly = true)
    public UUID require(String recordingId) {
        UUID id = RecordingKey.parse(recordingId).map(store::idOf)
                .orElseThrow(() -> ApiException.of("RECORDING_NOT_FOUND", 404));
        if (store.card(id).isEmpty()) {
            throw ApiException.of("RECORDING_NOT_FOUND", 404);
        }
        return id;
    }

    @Transactional(readOnly = true)
    public UUID requireWatchable(HotHatUser user, String recordingId) {
        UUID id = require(recordingId);
        if (!store.isSaved(id, user.uid()) && !store.isShared(id, user.uid())) {
            throw ApiException.of("RECORDING_NOT_SAVED", 403);
        }
        return id;
    }

    @Transactional(readOnly = true)
    public boolean isInMyLibrary(HotHatUser user, String recordingId) {
        return store.isSaved(require(recordingId), user.uid());
    }

    /** Сохранить у себя можно только запись партии, в которой ты играл. */
    @Transactional(readOnly = true)
    public void requireParticipant(HotHatUser user, UUID recordingId) {
        if (!store.isParticipant(recordingId, user.uid())) {
            throw ApiException.of("RECORDING_PARTICIPANT_ONLY", 403);
        }
    }
}
