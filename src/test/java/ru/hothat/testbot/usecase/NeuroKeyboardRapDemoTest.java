package ru.hothat.testbot.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** SCRUM-60: the exact user-authored keyboard + spoken-rap scene.
 * These tests are free: NO paid provider is called.
 */
class NeuroKeyboardRapDemoTest {
    private final NeuroReplacementPilotService pilot=
            new NeuroReplacementPilotService(new ObjectMapper());
    @AfterEach void close(){pilot.close();}
    private static final String UUID="48d6244e-4a1c-49d0-b5a2-9fa6719e4873";
    private static final String SPOKEN=
            "мы на яхте провисели две недели ... и все это время ты жуешь коктейли";

    @Test
    void configDoesNotChargeAndShowsExactSpeechKeyboardAndWan() {
        {
            Map<String,Object> config=pilot.keyboardRapConfig();
            assertEquals("wan-3",config.get("videoModel"));
            assertEquals("720p",config.get("resolution"));
            assertEquals(SPOKEN,config.get("spokenText"));
            assertTrue((Integer)config.get("durationSeconds")<=10);
            assertTrue((Integer)config.get("durationSeconds")>=8);
            String original=(String)config.get("requestedAssignment");
            assertTrue(original.contains("две недели (пауза) и все это время"));
            assertTrue(original.contains("потом положит клавиатуру обратно"));
            String processed=(String)config.get("processedAssignment");
            assertTrue(processed.contains("Клавиатуры НЕТ в исходной записи"));
            assertTrue(processed.contains("СНИЗУ"));
            assertTrue(processed.contains("НА ГОЛОВУ"));
            assertTrue(processed.contains("РЭП"));
            assertTrue(processed.contains("ТУ ЖЕ клавиатуру"));
            int price=(Integer)config.get("priceEstimateRoubles");
            assertTrue(price>=230&&price<400,"Wan 720p quote includes voice");
        }
    }

    @Test
    void paidStartIsExplicitlyBlockedWithoutOwnerConsent() {
        {
            ResponseStatusException error=assertThrows(ResponseStatusException.class,
                    ()->pilot.startKeyboardRap("owner",UUID,false,null,null));
            assertEquals(HttpStatus.BAD_REQUEST,error.getStatusCode());
            assertTrue(error.getReason().contains("EXPLICIT_PAYMENT_CONSENT"));
        }
    }

    @Test
    void wrongProviderSettingFailsClosedBeforeAnyPaidPost() {
        {
            ReflectionTestUtils.setField(pilot,"newVideoModel","kling-video-o3");
            ResponseStatusException error=assertThrows(ResponseStatusException.class,
                    pilot::keyboardRapConfig);
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE,error.getStatusCode());
            assertEquals("NEURO_KEYBOARD_WAN_NOT_SELECTED_NO_CHARGE",error.getReason());
            ResponseStatusException submitError=assertThrows(ResponseStatusException.class,
                    ()->pilot.startKeyboardRap("owner",UUID,true,null,null));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE,submitError.getStatusCode());
        }
    }

    @Test
    void missingTestJobNeverCreatesANewPaidJobWhenCheckingStatus() {
        {
            ResponseStatusException err=assertThrows(ResponseStatusException.class,
                    ()->pilot.keyboardRapByTestId("owner",UUID));
            assertEquals(HttpStatus.NOT_FOUND,err.getStatusCode());
        }
    }
}
