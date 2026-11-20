package ru.hothat.game.usecase;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import ru.hothat.config.ApiException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** SCRUM-58: genuine native MP4 conversion; no YouTube/network or S3. */
class EphemeralMemeClipStoreTest {

    @Test
    void convertsAndScopesAnOwnedTemporaryVideo() throws Exception {
        Path mp4 = Files.createTempFile("hh-fixture-", ".mp4");
        try {
            Process fixture = new ProcessBuilder(List.of(
                    "ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                    "-f", "lavfi", "-i", "color=c=blue:s=160x90:r=24:d=3",
                    "-f", "lavfi", "-i", "sine=frequency=440:duration=3",
                    "-shortest", "-c:v", "libx264", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-movflags", "+faststart", mp4.toString()))
                    .redirectErrorStream(true).start();
            assertTrue(fixture.waitFor(15, TimeUnit.SECONDS));
            assertEquals(0, fixture.exitValue());
            MockMultipartFile source = new MockMultipartFile(
                    "file", "example.mp4", "video/mp4", Files.readAllBytes(mp4));
            EphemeralMemeClipStore store = new EphemeralMemeClipStore();
            var clip = store.prepare("room-a", "uploader", source, 1, "Example");
            assertTrue(EphemeralMemeClipStore.isLocalId(clip.id()));
            assertTrue(clip.durationMs() >= 1700 && clip.durationMs() <= 2200);
            assertTrue(clip.data().length > 2048);
            assertEquals(clip.id(),store.find("room-a",clip.id()).id());
            assertEquals("ephemeral-mp4",store.mediaForShot("room-a","uploader",clip.id()).storageProvider());
            assertThrows(ApiException.class,()->store.find("other-room",clip.id()));
            assertThrows(ApiException.class,()->store.mediaForShot("room-a","other-player",clip.id()));
        }finally{
            Files.deleteIfExists(mp4);
        }
    }

    @Test
    void refusesNonMp4AndMalformedIds() {
        EphemeralMemeClipStore store = new EphemeralMemeClipStore();
        MockMultipartFile fake = new MockMultipartFile(
                "file","fake.mp4","video/mp4",new byte[]{1,2,3,4,5,6,7,8,9,10,11,12});
        assertThrows(ApiException.class,()->store.prepare("room","uid",fake,0,"bad"));
        assertFalse(EphemeralMemeClipStore.isLocalId("local:../../escape"));
        assertThrows(ApiException.class,()->store.find("room","local:../../escape"));
    }
}
