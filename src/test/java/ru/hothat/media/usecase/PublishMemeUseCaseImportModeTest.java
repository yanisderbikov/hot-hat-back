package ru.hothat.media.usecase;

import org.junit.jupiter.api.Test;
import ru.hothat.config.ApiException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression: the browser captured a 15s meme successfully, then an old
 * source-mode label longer than the DB's VARCHAR(24) blocked publication.
 */
class PublishMemeUseCaseImportModeTest {
    @Test void mapsLegacyYoutubeCaptureModesToStoredLength() {
        assertEquals("youtube-tab-play", PublishMemeUseCase.canonicalImportMode(
                "youtube-tab-raw-capture-playing-source"));
        assertEquals("youtube-tab-crop", PublishMemeUseCase.canonicalImportMode(
                "youtube-tab-raw-then-crop"));
        assertTrue(PublishMemeUseCase.canonicalImportMode(
                "youtube-tab-raw-capture-playing-source").length() <= 24);
        assertTrue(PublishMemeUseCase.canonicalImportMode(
                "youtube-tab-raw-then-crop").length() <= 24);
    }

    @Test void preservesValidModesAndDoesNotMaskUnknownLongValues() {
        assertEquals("file", PublishMemeUseCase.canonicalImportMode("file"));
        assertEquals("direct-url", PublishMemeUseCase.canonicalImportMode("direct-url"));
        assertEquals("tab-area-live-capture", PublishMemeUseCase.canonicalImportMode(
                "tab-area-live-capture"));
        assertNull(PublishMemeUseCase.canonicalImportMode(null));
        assertThrows(ApiException.class, () -> PublishMemeUseCase.canonicalImportMode(
                "unrecognized-video-import-method-very-long"));
    }
}
