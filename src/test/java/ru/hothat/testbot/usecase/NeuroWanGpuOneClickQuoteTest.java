package ru.hothat.testbot.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** SCRUM-65: no-charge contract for the new Wan/RTX-only mode. */
class NeuroWanGpuOneClickQuoteTest {
    @Test
    void tenSecondWanAndVoiceQuoteNeverIncludesLegacyLipSync() {
        NeuroReplacementPilotService pilot =
                new NeuroReplacementPilotService(new ObjectMapper());
        try {
            Map<String,Object> quote=pilot.keyboardGpuConfig();
            assertEquals("wan-3",quote.get("videoModel"));
            assertEquals("720p",quote.get("resolution"));
            assertEquals(10,quote.get("durationSeconds"));
            assertEquals(10,quote.get("sourceRecordingSeconds"));
            assertEquals(9.75,(Double)quote.get("wanReferenceSeconds"),0.001);
            assertEquals(250,quote.get("priceWanRoubles"));
            assertEquals(250,quote.get("priceEstimateRoubles"));
            assertEquals(1,quote.get("priceVoiceEstimateRoubles"));
            assertEquals(251,quote.get("priceTotalEstimateRoubles"));
            assertEquals(false,quote.get("lipSyncEnabled"));
            assertEquals("Immers RTX 4090",quote.get("mattingBackend"));
            assertEquals(false,quote.get("gpuOnline"),
                    "No GPU heartbeat means the paid start is blocked");
            assertEquals(false,quote.get("providerReady"));
        }finally {
            pilot.close();
        }
    }

    @Test
    void newWanReferenceIsTenSecondsButLegacyKlingReferenceRemainsEight() {
        assertEquals(true,ReflectionTestUtils.invokeMethod(
                NeuroReplacementPilotService.class,"validWanGpuReferenceDuration",9.75));
        assertEquals(false,ReflectionTestUtils.invokeMethod(
                NeuroReplacementPilotService.class,"validWanGpuReferenceDuration",7.5));
        assertEquals(true,ReflectionTestUtils.invokeMethod(
                NeuroReplacementPilotService.class,"validKlingElementDuration",7.5));
        assertEquals(false,ReflectionTestUtils.invokeMethod(
                NeuroReplacementPilotService.class,"validKlingElementDuration",9.75));
    }
}
