package ru.hothat.testbot.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SCRUM-59: provider switch for new jobs cannot reinterpret historic paid
 * Kling request IDs. All assertions run locally and cost zero GenAPI credits.
 */
class NeuroWanVideoProviderTest {
    @Test
    void wan720pUsesOnlyDocumentedReferenceFieldsAndKeepsSeparateVoice() {
        NeuroReplacementPilotService pilot =
                new NeuroReplacementPilotService(new ObjectMapper());
        try {
            String assignment = (String)ReflectionTestUtils.getField(
                    NeuroReplacementPilotService.class,"UPLOAD_ASSIGNMENT");
            NeuroScenePlan scene=NeuroScenePlan.plan(assignment);
            String video="https://media.example.test/ref/input.mp4";
            String image="https://media.example.test/still/input.jpg";
            @SuppressWarnings("unchecked")
            Map<String,Object> body=(Map<String,Object>)ReflectionTestUtils.invokeMethod(
                    pilot,"wanReferenceFields",scene,video,image);
            assertNotNull(body);
            assertEquals("720p",body.get("resolution"));
            assertEquals(scene.durationSeconds(),body.get("duration"));
            assertEquals(false,body.get("audio"),
                    "Gemini, Chatterbox and Lip Sync must preserve the spoken voice");
            assertEquals(false,body.get("enable_prompt_expansion"));
            assertEquals(List.of(video),body.get("video_urls"));
            assertEquals(image,body.get("image_url"));
            assertTrue(((String)body.get("prompt")).contains(scene.spokenText()));
            assertFalse(((String)body.get("prompt")).contains("@Element1"));
            assertFalse(body.containsKey("elements"),"Kling elements are not Wan inputs");
            assertFalse(body.containsKey("pro"),"Kling Pro must not be copied to Wan");
            assertFalse(body.containsKey("generate_audio"));
            assertEquals("wan-3",ReflectionTestUtils.invokeMethod(pilot,"newVideoModelId"));
            Map<String,Object> quote=pilot.uploadConfig();
            assertEquals("wan-3",quote.get("videoModel"));
            assertEquals("720p",quote.get("videoResolution"));
            int price=(Integer)quote.get("priceEstimateRoubles");
            assertTrue(price>=250 && price<400, "pre-consent price must use 25 RUB/sec");
            // Explicit env rollback applies to newly created jobs only.
            ReflectionTestUtils.setField(pilot,"newVideoModel","kling-video-o3");
            assertEquals("kling-video-o3",ReflectionTestUtils.invokeMethod(pilot,"newVideoModelId"));
            assertEquals("kling-video-o3",pilot.uploadConfig().get("videoModel"));
        }finally{pilot.close();}
    }

    @Test
    @SuppressWarnings("unchecked")
    void historicalJobsRemainOnKlingEvenWhenNewProviderDefaultsToWan() throws Exception {
        Class<?> jobType=Class.forName(
                "ru.hothat.testbot.usecase.NeuroReplacementPilotService$Job");
        Constructor<?> ctor=jobType.getDeclaredConstructor(
                String.class,String.class,String.class);
        ctor.setAccessible(true);
        Object historic=ctor.newInstance("id","room","owner");
        Field requests=jobType.getDeclaredField("providerRequests");
        requests.setAccessible(true);
        Map<String,String> saved=(Map<String,String>)requests.get(historic);
        saved.put("kling-video-o3","paid-task-57716859");
        Method modelFor=NeuroReplacementPilotService.class.getDeclaredMethod(
                "videoModelFor",jobType);
        modelFor.setAccessible(true);
        assertEquals("kling-video-o3",modelFor.invoke(null,historic));
        assertEquals("paid-task-57716859",saved.get("kling-video-o3"));
        Object newJob=ctor.newInstance("new-id","new-room","owner");
        ((Map<String,String>)requests.get(newJob)).put("__diag.videoModel","wan-3");
        assertEquals("wan-3",modelFor.invoke(null,newJob));
        assertFalse(((Map<String,String>)requests.get(newJob))
                .containsKey("kling-video-o3"));
    }
}
