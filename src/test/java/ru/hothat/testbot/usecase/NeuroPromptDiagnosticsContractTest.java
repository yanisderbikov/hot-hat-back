package ru.hothat.testbot.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verify that the owner-facing diagnostics show exactly the acting prompt
 * submitted to GenAPI. Pure local test: no paid provider requests.
 */
class NeuroPromptDiagnosticsContractTest {
    @Test
    void exactPromptShownInReportMatchesSubmittedKlingFields() {
        var service=new NeuroReplacementPilotService(new ObjectMapper());
        try {
            String raw="Пусть игрок возьмет клавиатуру в руки и поставит себе на голову, "+
                    "повернется лицом в камеру и скажет выразительно четко как рэп "+
                    "«мы на яхте провисели две недели» (пауза) "+
                    "«и все это время ты жуешь коктейли», "+
                    "потом положит клавиатуру обратно на стол.";
            NeuroScenePlan scene=NeuroScenePlan.plan(raw);
            String expected=ReflectionTestUtils.invokeMethod(
                    NeuroReplacementPilotService.class,"buildGenApiVisualPrompt",scene);
            Map<String,Object> actual=ReflectionTestUtils.invokeMethod(service,
                    "genApiVisualReferenceFields",scene,
                    "https://hot-hat-back.up.railway.app/api/v2/public/neuro-references/"+
                            "a".repeat(64)+"/input.mp4",
                    "https://hot-hat-back.up.railway.app/api/v2/public/neuro-references/"+
                            "b".repeat(64)+"/input.jpg");
            assertEquals(expected,actual.get("prompt"));
            assertEquals(String.valueOf(scene.durationSeconds()),actual.get("duration"));
            assertTrue(expected.contains("@Element1"));
            assertTrue(expected.contains("keyboard"));
            assertTrue(expected.contains("BACKGROUND LOCK"));
            assertTrue(expected.contains("мы на яхте провисели две недели"));
            assertTrue(expected.contains("ты жуешь коктейли"));
            assertEquals(false,actual.get("generate_audio"));
        } finally {
            service.close();
        }
    }

    @Test
    void actualFailedOwnerKeyboardRapPromptFitsGenApiAndKeepsEveryAction() {
        var service=new NeuroReplacementPilotService(new ObjectMapper());
        try {
            // Exact text from the 2026-11-10 failed bot-room test. Its former
            // 2772-character prompt was rejected locally before GenAPI POST.
            String assignment="Пусть игрок возьмет клавиатуру в руки и поставит себе на голову и будет держать ее руками на голове, "+
                    "повернется лицом в камеру и скажет выразительно четко как рекламу - "+
                    "мы на яхте провисели две недели (пауза) и все это время ты жуешь коктейли. "+
                    "пусть эту рекламу он говорит как рэп читает, потом положит клавиатуру обратно в невидимую зону на стол.";
            NeuroScenePlan preview=service.previewGameScene(assignment,8);
            Map<String,Object> body=ReflectionTestUtils.invokeMethod(service,
                    "genApiVisualReferenceFields",preview,
                    "https://hot-hat-back.up.railway.app/api/v2/public/neuro-references/"+
                            "a".repeat(64)+"/input.mp4",
                    "https://hot-hat-back.up.railway.app/api/v2/public/neuro-references/"+
                            "b".repeat(64)+"/input.jpg");
            String providerPrompt=(String)body.get("prompt");
            assertTrue(providerPrompt.length()<=2500,
                    "Free preview must never permit an oversized paid O3 prompt");
            assertEquals(8,preview.durationSeconds());
            assertEquals("reference-to-video",body.get("model"));
            assertEquals("8",body.get("duration"));
            assertTrue(providerPrompt.contains("BACKGROUND LOCK"));
            assertTrue(providerPrompt.contains("@Element1"));
            assertTrue(providerPrompt.contains("ACTION PRIORITY 1"));
            assertTrue(providerPrompt.contains("ACTION PRIORITY 2"));
            assertTrue(providerPrompt.contains("keyboard"));
            assertTrue(providerPrompt.contains(assignment));
            assertTrue(providerPrompt.contains(preview.spokenText()));
            assertTrue(providerPrompt.contains("rap"));
            assertEquals(false,body.get("generate_audio"));
            assertEquals(false,body.get("translate_input"));
        } finally {
            service.close();
        }
    }
}
