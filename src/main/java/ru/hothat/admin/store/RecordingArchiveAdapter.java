package ru.hothat.admin.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.hothat.admin.port.ObjectStoragePort;
import ru.hothat.admin.port.RecordingArchivePort;
import ru.hothat.model.media.GameRecording;
import ru.hothat.recording.spi.RecordingFilePort;
import ru.hothat.repository.GetterMedia;
import ru.hothat.repository.SaverMedia;

import java.time.Clock;
import java.util.Optional;

/**
 * Переходник консоли к архиву записей.
 *
 * <p>Ссылки подписывает сама область записей — через {@link RecordingFilePort}.
 * Строку архива консоль пока читает сама: паспорт партии в {@code v2} и
 * прежняя таблица записей ещё живут рядом, и переключать источник заодно с
 * переездом подписи значило бы менять две вещи разом.
 *
 * <p>{@link #withdraw} перечитывает строку по идентификатору. Второго запроса
 * к базе это не стоит: сценарий уже прочитал её в той же транзакции, и строка
 * приезжает из кеша сессии. Зато сущность не путешествует через порт.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordingArchiveAdapter implements RecordingArchivePort {

    private final GetterMedia getterMedia;
    private final SaverMedia saverMedia;
    private final RecordingFilePort files;
    private final ObjectStoragePort storage;
    private final Clock clock;

    @Override
    public Optional<Archived> find(String recordingId) {
        return getterMedia.getRecording(recordingId).map(RecordingArchiveAdapter::view);
    }

    @Override
    public PlaybackUrls signUrls(Archived recording, String downloadName) {
        // Подписывает область записей: срок ссылки и тип содержимого — её
        // правила, и второй копии этих значений здесь быть не должно.
        RecordingFilePort.PlaybackUrls urls = files.presign(recording.objectPath(), downloadName);
        return new PlaybackUrls(urls.watchUrl(), urls.downloadUrl(), urls.expiresAtMs());
    }

    @Override
    public void withdraw(Archived recording) {
        // Сначала файл, потом строка: неудача сноса файла не должна оставить
        // запись доступной, а осиротевший объект подметёт сверка хранилища.
        storage.delete(recording.objectPath());
        GameRecording row = getterMedia.getRecording(recording.recordingId()).orElseThrow();
        row.setStatus("deleted");
        row.setDeletedAtMs(clock.millis());
        row.setObjectPath(null);
        row.setExpiresAtMs(null);
        saverMedia.saveRecording(row);
    }

    private static Archived view(GameRecording row) {
        return new Archived(
                row.getId(),
                row.getRoomId(),
                row.getGameNumber() == null ? 0 : row.getGameNumber(),
                row.getObjectPath());
    }
}
