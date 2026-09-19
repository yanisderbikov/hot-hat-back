package ru.hothat.recording.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.config.ApiException;
import ru.hothat.recording.domain.RetentionRules;
import ru.hothat.recording.port.RecordingStoragePort;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Подпись ссылок на файл записи для соседней области.
 *
 * <p>Проверяется то, ради чего порт и заведён: срок ссылки называет область
 * записей, а не тот, кто просит. Пока подписывал прежний движок, у консоли
 * администратора стояла вторая копия и срока, и типа содержимого.
 */
class RecordingFilesTest {

    /** Хранилище, которое запоминает, о чём его попросили. */
    private static final class FakeBucket implements RecordingStoragePort {

        private String objectPath;
        private String downloadName;
        private Duration ttl;

        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public String bucket() {
            return "hot-hat";
        }

        @Override
        public Optional<Long> contentLength(String objectPath) {
            return Optional.of(1L);
        }

        @Override
        public boolean delete(String objectPath) {
            return true;
        }

        @Override
        public PlaybackUrls presign(String objectPath, String downloadName, Duration ttl) {
            this.objectPath = objectPath;
            this.downloadName = downloadName;
            this.ttl = ttl;
            return new PlaybackUrls("https://s3/watch", "https://s3/download", 1_800_000L);
        }
    }

    @Test
    @DisplayName("Ссылки подписываются на срок, который назначила область записей")
    void ttlComesFromTheRecordingArea() {
        FakeBucket bucket = new FakeBucket();

        RecordingFilePort.PlaybackUrls urls =
                new RecordingFiles(bucket).presign("recordings/hat-3f1c-1.mp4", "HOT-HAT-hat-3f1c-1.mp4");

        assertThat(bucket.ttl).isEqualTo(RetentionRules.PLAYBACK_URL_TTL);
        assertThat(bucket.objectPath).isEqualTo("recordings/hat-3f1c-1.mp4");
        assertThat(bucket.downloadName).isEqualTo("HOT-HAT-hat-3f1c-1.mp4");
        assertThat(urls.watchUrl()).isEqualTo("https://s3/watch");
        assertThat(urls.downloadUrl()).isEqualTo("https://s3/download");
    }

    @Test
    @DisplayName("Записи без файла не подписывают: съёмка не дошла до выгрузки")
    void recordingWithoutAFileIsRefused() {
        FakeBucket bucket = new FakeBucket();
        RecordingFiles files = new RecordingFiles(bucket);

        assertThatThrownBy(() -> files.presign(null, "имя.mp4"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("RECORDING_NOT_READY");
        assertThatThrownBy(() -> files.presign("   ", "имя.mp4"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("RECORDING_NOT_READY");
        assertThat(bucket.objectPath).as("в хранилище не ходили вовсе").isNull();
    }
}
