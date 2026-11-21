package ru.hothat.media.usecase;

import org.junit.jupiter.api.Test;
import ru.hothat.config.ApiException;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

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

    @SuppressWarnings("unchecked")
    @Test
    void alreadyPreparedNativeMp4RemainsAvailableAfterBotRefusalForAnotherOffset() throws Exception {
        String videoId = "jKPLWQK93fE";
        PrepareYoutubeMemeClipUseCase service = new PrepareYoutubeMemeClipUseCase();

        // Seed exactly the states created by a successful conversion followed
        // by a refused second 10-second offset, without hitting YouTube in CI.
        Field sharedField = PrepareYoutubeMemeClipUseCase.class.getDeclaredField("shared");
        sharedField.setAccessible(true);
        Map<String, Object> shared = (Map<String, Object>) sharedField.get(service);
        Class<?> clipType = Class.forName(PrepareYoutubeMemeClipUseCase.class.getName() + "$SharedClip");
        Constructor<?> clipConstructor = clipType.getDeclaredConstructor(CompletableFuture.class, long.class);
        clipConstructor.setAccessible(true);
        byte[] prepared = new byte[]{1, 2, 3, 4, 5, 6};
        shared.put(videoId + ":0",
                clipConstructor.newInstance(CompletableFuture.completedFuture(prepared),
                        System.currentTimeMillis() + 60_000));

        Field deniedField = PrepareYoutubeMemeClipUseCase.class.getDeclaredField("denied");
        deniedField.setAccessible(true);
        Map<String, Object> denied = (Map<String, Object>) deniedField.get(service);
        Class<?> denialType = Class.forName(PrepareYoutubeMemeClipUseCase.class.getName() + "$DeniedClip");
        Constructor<?> denialConstructor = denialType.getDeclaredConstructor(String.class, long.class);
        denialConstructor.setAccessible(true);
        denied.put(videoId, denialConstructor.newInstance("YOUTUBE_BOT_CONFIRMATION_REQUIRED",
                System.currentTimeMillis() + 60_000));

        assertArrayEquals(prepared, service.run(videoId, 0.0),
                "An already prepared MP4 must be served even after a later segment fails");
        assertThrows(ApiException.class, () -> service.run(videoId, 10.0),
                "A new offset must still respect the original video's cooldown");
    }

}
