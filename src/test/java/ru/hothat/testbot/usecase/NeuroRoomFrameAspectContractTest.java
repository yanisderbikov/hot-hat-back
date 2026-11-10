package ru.hothat.testbot.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for 2026-11-10 bot-room webcam squashing: 4:3 must not be
 * horizontally stretched to the provider's mandatory 16:9 frame.
 */
class NeuroRoomFrameAspectContractTest {
    @Test
    void fourByThreeWebcamRemainsUndistortedInSixteenByNineStartingJpeg(
            @TempDir Path temp) throws Exception {
        String filter = ReflectionTestUtils.invokeMethod(
                NeuroReplacementPilotService.class,"roomStillAspectSafeFilter");
        assertNotNull(filter);
        assertTrue(filter.contains("force_original_aspect_ratio=decrease"));
        assertTrue(filter.contains("pad=1280:720"));
        assertTrue(filter.contains("setsar=1"));
        BufferedImage source=new BufferedImage(640,480,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<480;y++)
            for(int x=0;x<640;x++)
                source.setRGB(x,y,0xCC3311);
        Path input=temp.resolve("webcam-4by3.png");
        Path output=temp.resolve("start-16by9.jpg");
        assertTrue(ImageIO.write(source,"png",input.toFile()));
        Process ffmpeg=new ProcessBuilder("ffmpeg","-hide_banner","-loglevel","error",
                "-nostdin","-y","-i",input.toString(),"-frames:v","1",
                "-vf",filter,"-q:v","3",output.toString()).start();
        assertTrue(ffmpeg.waitFor(20,TimeUnit.SECONDS));
        assertEquals(0,ffmpeg.exitValue());
        BufferedImage still=ImageIO.read(output.toFile());
        assertNotNull(still);
        assertEquals(1280,still.getWidth());
        assertEquals(720,still.getHeight());
        // 640x480 content becomes 960x720, leaving 160px of neutral padding
        // on each side. Old direct scale filled the whole width and deformed
        // a person from 4:3 to 16:9.
        int side=still.getRGB(40,360)&0xFFFFFF;
        int center=still.getRGB(640,360)&0xFFFFFF;
        assertTrue((side&0xFF)<30 && ((side>>16)&0xFF)<30,
                "padding outside the original webcam frame must be neutral");
        assertTrue(((center>>16)&0xFF)>150,
                "the camera image must fill the center without changing shape");
    }
}
