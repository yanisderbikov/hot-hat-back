package ru.hothat.media.usecase;

import org.junit.jupiter.api.Test;
import ru.hothat.config.ApiException;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrepareYoutubeMemeClipUseCaseTest {

    @Test
    void onlyValidatedVideoIdsMayReachExtractor() {
        assertThrows(ApiException.class,
                () -> PrepareYoutubeMemeClipUseCase.buildCommand("https://evil.example/", 0, Path.of("/tmp/x")));
        assertThrows(ApiException.class,
                () -> PrepareYoutubeMemeClipUseCase.buildCommand("notid", 0, Path.of("/tmp/x")));
        assertThrows(ApiException.class,
                () -> PrepareYoutubeMemeClipUseCase.buildCommand("jKPLWQK93fE", Double.NaN, Path.of("/tmp/x")));
        assertThrows(ApiException.class,
                () -> PrepareYoutubeMemeClipUseCase.buildCommand("jKPLWQK93fE", -1, Path.of("/tmp/x")));
        assertThrows(ApiException.class,
                () -> PrepareYoutubeMemeClipUseCase.buildCommand("jKPLWQK93fE", 7201, Path.of("/tmp/x")));
    }

    @Test
    void exact10SecondClipIsExtractedWithoutShellOrArbitraryUrls() {
        List<String> cmd = PrepareYoutubeMemeClipUseCase.buildCommand(
                "jKPLWQK93fE", 42.5, Path.of("/tmp/safe"));
        assertEquals("yt-dlp", cmd.get(0));
        assertEquals("https://www.youtube.com/watch?v=jKPLWQK93fE", cmd.get(cmd.size() - 1));
        assertTrue(cmd.contains("--download-sections"));
        assertTrue(cmd.contains("*42.500-52.500"));
        assertTrue(cmd.contains("--recode-video"));
        assertTrue(cmd.contains("mp4"));
        assertFalse(cmd.contains("sh"));
        assertFalse(cmd.contains("bash"));
    }
    @Test
    void explainsRealRailwayAgeAndBotFailuresInsteadOfGenericImportUnavailable() {
        assertEquals("YOUTUBE_AGE_CONFIRMATION_REQUIRED",
                PrepareYoutubeMemeClipUseCase.classifyExtractorFailure(
                        "ERROR: [youtube] M6vD-iLc_PI: Sign in to confirm your age. This video may be inappropriate for some users."));
        assertEquals("YOUTUBE_BOT_CONFIRMATION_REQUIRED",
                PrepareYoutubeMemeClipUseCase.classifyExtractorFailure(
                        "ERROR: [youtube] brN7JjbWbrA: Sign in to confirm you’re not a bot."));
        assertEquals("YOUTUBE_IMPORT_UNAVAILABLE",
                PrepareYoutubeMemeClipUseCase.classifyExtractorFailure("ERROR: Unable to download video data."));
    }

}
