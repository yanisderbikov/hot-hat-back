package ru.hothat.repository;

import ru.hothat.model.media.GameRecording;

/**
 * Записи медиа, оставшиеся у прежних таблиц: только запись партии.
 *
 * <p>Мем отсюда убран вместе со старой поверхностью: публикация и снятие
 * живут в {@code /api/v2/media} и пишут в {@code v2.meme}.
 */
public interface SaverMedia {

    GameRecording saveRecording(GameRecording recording);
}
