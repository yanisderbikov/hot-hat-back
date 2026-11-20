package ru.hothat.game.domain.weapon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class YoutubeMemeClipTest {

    @Test
    void ephemeralClipKeepsExactDurationWithoutLibraryStorage() {
        MemeMedia clip = YoutubeMemeClip.parse("yt:JkPLWQK93fE:142000:10000").orElseThrow();
        assertEquals(10_000, clip.durationMs());
        assertEquals("youtube-stream", clip.storageProvider());
        assertEquals("", clip.mediaPath());
        assertEquals("", clip.posterPath());
        assertTrue(clip.src().contains("start=142"));
        assertTrue(clip.src().contains("end=152"));
        assertTrue(clip.src().contains("controls=0"));
        assertTrue(clip.src().startsWith("https://www.youtube-nocookie.com/embed/JkPLWQK93fE"));
    }

    @Test
    void videoShorterThanTenSecondsUsesItsRealDuration() {
        MemeMedia clip = YoutubeMemeClip.parse("yt:JkPLWQK93fE:0:7000").orElseThrow();
        assertEquals(7000, clip.clampedDurationMs());
        assertTrue(clip.src().contains("end=7"));
    }

    @Test
    void invalidOrUnboundedIdentifiersCannotBecomeExternalUrls() {
        assertTrue(YoutubeMemeClip.parse("yt:../../evil:0:1000").isEmpty());
        assertTrue(YoutubeMemeClip.parse("yt:JkPLWQK93fE:0:10001").isEmpty());
        assertTrue(YoutubeMemeClip.parse("yt:JkPLWQK93fE:0:399").isEmpty());
        assertTrue(YoutubeMemeClip.parse("yt:JkPLWQK93fE:99999999:10000").isEmpty());
        assertTrue(YoutubeMemeClip.parse("yt:JkPLWQK93fE:-1000:1000").isEmpty());
        assertFalse(YoutubeMemeClip.isYoutubeId("builtin-bmw-drugoy-ne-znayu"));
    }
}
