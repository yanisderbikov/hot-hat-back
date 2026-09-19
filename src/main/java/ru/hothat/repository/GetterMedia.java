package ru.hothat.repository;

import ru.hothat.model.media.GameRecording;

import java.util.List;
import java.util.Optional;

/**
 * Чтения медиа, оставшиеся у прежних таблиц: только записи партий.
 *
 * <p>Библиотека мемов отсюда ушла вместе со старой поверхностью: её ведёт
 * {@code v2.meme}, читает область {@code /api/v2/media} своим хранилищем, а
 * старая таблица {@code meme_library} осталась без читателя и без писателя.
 */
public interface GetterMedia {

    Optional<GameRecording> getRecording(String recordingId);

    /**
     * Записи по списку идентификаторов — одним запросом.
     *
     * <p>Появился ради страницы переписки: в ней могут лежать несколько
     * поделённых записей, и карточка каждой берёт название и длительность из
     * самой записи. Поштучный {@link #getRecording(String)} в цикле по
     * странице был бы тем же веером, от которого лечили список друзей.
     *
     * <p>Пустой список в базу не идёт.
     */
    List<GameRecording> getRecordings(List<String> recordingIds);

    List<GameRecording> getRecentRecordings(int limit);

    List<GameRecording> getRecordingsSavedBy(String uid, int limit);

    List<GameRecording> getExpiredRecordings(long now, int limit);
}
