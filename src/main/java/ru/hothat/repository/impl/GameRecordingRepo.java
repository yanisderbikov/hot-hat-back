package ru.hothat.repository.impl;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.hothat.model.media.GameRecording;

import java.util.List;

@Repository
interface GameRecordingRepo extends JpaRepository<GameRecording, String> {

    List<GameRecording> findAllByOrderByStartedAtMsDesc(Limit limit);

    List<GameRecording> findByExpiresAtMsLessThanEqual(long now, Limit limit);

    /** savedBy — JSONB-массив uid; выборка «мои записи» идёт по нему. */
    @Query(value = "select * from game_recording where saved_by @> to_jsonb(cast(:uid as text)) limit :max",
            nativeQuery = true)
    List<GameRecording> findSavedBy(@Param("uid") String uid, @Param("max") int max);
}
