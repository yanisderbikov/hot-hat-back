package ru.hothat.testbot.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;

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

    @Test
    void keyboardVoiceRecoveryRequiresExactlyTheOriginalSavedIdsAndNoAmbiguousPost() throws Exception {
        Class<?> jobType=Class.forName(
                "ru.hothat.testbot.usecase.NeuroReplacementPilotService$Job");
        Constructor<?> ctor=jobType.getDeclaredConstructor(
                String.class,String.class,String.class);
        ctor.setAccessible(true);
        Object job=ctor.newInstance(UUID,"hat-fac7edkeyboard"+UUID.replace("-",""),"owner");
        ReflectionTestUtils.setField(job,"state","failed");
        ReflectionTestUtils.setField(job,"sceneAssignment",
                ReflectionTestUtils.getField(NeuroReplacementPilotService.class,
                        "KEYBOARD_RAP_ASSIGNMENT"));
        ReflectionTestUtils.setField(job,"sourceCameraKey","archived-original.webm");
        ReflectionTestUtils.setField(job,"sourceVoiceKey","archived-voice.webm");
        ReflectionTestUtils.setField(job,"cameraMime","video/webm");
        ReflectionTestUtils.setField(job,"voiceMime","audio/webm");
        Field field=jobType.getDeclaredField("providerRequests");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String,String> saved=(Map<String,String>)field.get(job);
        saved.put("character-mode","genapi-game-o3-reference-10");
        saved.put("__diag.videoModel","wan-3");
        saved.put("tts","58629153");
        saved.put("chatterbox-speech","58629167");
        Method allowed=NeuroReplacementPilotService.class.getDeclaredMethod(
                "keyboardRapVoiceResumeEligible",jobType,String.class);
        allowed.setAccessible(true);
        assertEquals(Boolean.TRUE,allowed.invoke(null,job,"owner"));
        assertEquals(Boolean.FALSE,allowed.invoke(null,job,"not-owner"));
        saved.put("wan-3","already-paid-wan-id");
        assertEquals(Boolean.FALSE,allowed.invoke(null,job,"owner"),
                "never submit a second Wan when an existing video ID exists");
        saved.remove("wan-3");
        ReflectionTestUtils.setField(job,"submittingModel","wan-3");
        assertEquals(Boolean.FALSE,allowed.invoke(null,job,"owner"),
                "never retry ambiguous paid POST");
        ReflectionTestUtils.setField(job,"submittingModel",null);
        saved.remove("chatterbox-speech");
        assertEquals(Boolean.FALSE,allowed.invoke(null,job,"owner"),
                "missing paid voice ID must stop recovery");
        saved.put("chatterbox-speech","58629167");
        saved.put("__diag.videoModel","kling-video-o3");
        assertEquals(Boolean.FALSE,allowed.invoke(null,job,"owner"),
                "never cross-charge Kling instead of Wan");
        assertEquals("58629153",saved.get("tts"));
        assertEquals("58629167",saved.get("chatterbox-speech"));
    }

    @Test
    void keyboardVoiceRecoveryNeedsExplicitConsentEvenForAnOldFailedJob(){
        ResponseStatusException error=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeKeyboardRapFromVoiceTimeout(
                        "owner",UUID,false));
        assertEquals(HttpStatus.BAD_REQUEST,error.getStatusCode());
        assertEquals("NEURO_KEYBOARD_RECOVERY_EXPLICIT_CONSENT_REQUIRED",
                error.getReason());
    }
}
