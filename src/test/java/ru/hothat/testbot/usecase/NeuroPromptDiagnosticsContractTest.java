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
}
