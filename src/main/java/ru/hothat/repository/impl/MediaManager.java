package ru.hothat.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import ru.hothat.model.media.GameRecording;
import ru.hothat.repository.GetterMedia;
import ru.hothat.repository.SaverMedia;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@AllArgsConstructor
@Slf4j
class MediaManager implements GetterMedia, SaverMedia {

    private final GameRecordingRepo gameRecordingRepo;

    @Override
    public Optional<GameRecording> getRecording(String recordingId) {
        if (recordingId == null || recordingId.isBlank()) {
            return Optional.empty();
        }
        return wrap("getRecording", () -> gameRecordingRepo.findById(recordingId));
    }

    @Override
    public List<GameRecording> getRecordings(List<String> recordingIds) {
        if (recordingIds.isEmpty()) {
            return List.of();
        }
        // findAllById — один запрос с «id in (…)»; поштучное чтение в цикле по
        // странице переписки и было тем веером, ради которого метод появился.
        return wrap("getRecordings", () -> gameRecordingRepo.findAllById(recordingIds));
    }

    @Override
    public List<GameRecording> getRecentRecordings(int limit) {
        return wrap("getRecentRecordings", () -> gameRecordingRepo.findAllByOrderByStartedAtMsDesc(Limit.of(limit)));
    }

    @Override
    public List<GameRecording> getRecordingsSavedBy(String uid, int limit) {
        return wrap("getRecordingsSavedBy", () -> gameRecordingRepo.findSavedBy(uid, limit));
    }

    @Override
    public List<GameRecording> getExpiredRecordings(long now, int limit) {
        return wrap("getExpiredRecordings",
                () -> gameRecordingRepo.findByExpiresAtMsLessThanEqual(now, Limit.of(limit)));
    }

    @Override
    public GameRecording saveRecording(GameRecording recording) {
        recording.setUpdatedAt(Instant.now());
        return wrap("saveRecording", () -> gameRecordingRepo.save(recording));
    }

    private <T> T wrap(String operation, java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (Exception e) {
            log.error("MediaManager.{} failed", operation, e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
