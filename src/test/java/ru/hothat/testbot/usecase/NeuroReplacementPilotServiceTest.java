package ru.hothat.testbot.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.List;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeuroReplacementPilotServiceTest {
    private final NeuroReplacementPilotService pilot = new NeuroReplacementPilotService(new ObjectMapper());

    @AfterEach
    void close() {
        pilot.close();
    }

    @Test
    void simpleUploadPlanFitsTenSecondsWithAllGesturesAndExactTwoRussianPhrases() {
        Map<String,Object> plan=pilot.uploadConfig();
        int seconds=(Integer)plan.get("durationSeconds");
        assertTrue(seconds>=8 && seconds<=10,"Entire scene fits ten seconds");
        assertEquals("А вы всё ещё кипятите? ... Тогда мы идём к вам!",
                plan.get("spokenText"));
        String script=(String)plan.get("assignment");
        for(String action:new String[]{"палец","кулаком","помахать","встать","уйти"})
            assertTrue(script.contains(action),action);
        assertEquals(16*1024*1024,plan.get("maximumSourceBytes"));
        assertEquals(true,plan.get("priceIsEstimate"));
    }

    @Test
    void prerecordedTwentyFiveSecondVideoProducesLocalVoiceWithoutGenApiCharge() throws Exception {
        java.nio.file.Path mp4=java.nio.file.Files.createTempFile("neuro-25s-source-", ".mp4");
        try{
            Process make=new ProcessBuilder("ffmpeg","-hide_banner","-loglevel","error",
                    "-nostdin","-y","-f","lavfi","-i",
                    "color=c=gray:s=320x180:r=12",
                    "-f","lavfi","-i","sine=frequency=330:sample_rate=24000",
                    "-t","25","-c:v","mpeg4","-q:v","10","-c:a","aac",
                    "-shortest",mp4.toString()).redirectErrorStream(true).start();
            assertTrue(make.waitFor(30,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0,make.exitValue());
            byte[] source=java.nio.file.Files.readAllBytes(mp4);
            byte[] voice=ReflectionTestUtils.invokeMethod(pilot,
                    "transcodeMedia",source,"mp4","wav",false,0.0);
            assertTrue(voice.length>25000 && voice.length<3*1024*1024);
            assertEquals("RIFF",new String(voice,0,4,java.nio.charset.StandardCharsets.US_ASCII));
            // No call to GenAPI is needed to extract the user's voice.
        }finally{
            java.nio.file.Files.deleteIfExists(mp4);
        }
    }

    @Test
    void klingO3EightSecondIdentityVideoMustBe1080pH264Aac24fpsBeforePayment()
            throws Exception {
        java.nio.file.Path mp4=java.nio.file.Files.createTempFile("neuro-kling-identity-",".mp4");
        try{
            Process ffmpeg=new ProcessBuilder("ffmpeg","-hide_banner","-loglevel","error",
                    "-nostdin","-y",
                    "-f","lavfi","-i","color=c=gray:s=1920x1080:r=24",
                    "-f","lavfi","-i","sine=frequency=350:sample_rate=48000",
                    "-t","8","-threads","1","-filter_threads","1",
                    "-c:v","libx264","-threads:v","1",
                    "-preset","ultrafast","-pix_fmt","yuv420p",
                    "-c:a","aac","-movflags","+faststart",mp4.toString()).start();
            assertTrue(ffmpeg.waitFor(45,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0,ffmpeg.exitValue());
            byte[] valid=java.nio.file.Files.readAllBytes(mp4);
            assertDoesNotThrow(()->ReflectionTestUtils.invokeMethod(
                    pilot,"verifyKlingElementReference",valid));
            byte[] bad=new byte[26_000];
            assertThrows(Exception.class,()->ReflectionTestUtils.invokeMethod(
                    pilot,"verifyKlingElementReference",bad),
                    "Corrupt media is caught LOCALLY, before GenAPI can charge.");
        }finally{
            java.nio.file.Files.deleteIfExists(mp4);
        }
    }

    @Test
    void chromeLiveWebmWithoutDurationHeaderIsAcceptedFromActualPacketTimestamps() throws Exception {
        // Chrome MediaRecorder emits streaming WebM with NO format.duration.
        // Real owner example was 1280x720, ~21 fps, 30 seconds, Opus audio.
        java.nio.file.Path webm=java.nio.file.Files.createTempFile("neuro-chrome-live-",".webm");
        try {
            Process live=new ProcessBuilder("ffmpeg","-hide_banner","-loglevel","error",
                    "-nostdin","-y",
                    "-f","lavfi","-i","color=c=gray:s=320x180:r=12",
                    "-f","lavfi","-i","sine=frequency=330:sample_rate=24000",
                    "-t","25","-c:v","libvpx","-deadline","realtime",
                    "-cpu-used","8","-b:v","100k",
                    "-c:a","libopus","-f","webm","-live","1","pipe:1")
                    .redirectOutput(webm.toFile()).start();
            assertTrue(live.waitFor(35,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0,live.exitValue());

            byte[] original=java.nio.file.Files.readAllBytes(webm);
            Object checked=ReflectionTestUtils.invokeMethod(pilot,"probeMp4",original);
            var durationGetter=checked.getClass().getDeclaredMethod("seconds");
            durationGetter.setAccessible(true);
            double seconds=(Double)durationGetter.invoke(checked);
            assertTrue(seconds>=24.8 && seconds<=25.3,
                    "Missing WebM container duration must be recovered from packets: "+seconds);
            var videoGetter=checked.getClass().getDeclaredMethod("video");
            videoGetter.setAccessible(true);
            var audioGetter=checked.getClass().getDeclaredMethod("audio");
            audioGetter.setAccessible(true);
            assertEquals(true,videoGetter.invoke(checked));
            assertEquals(true,audioGetter.invoke(checked));
            // This is fully local: no GenAPI POST, no money spent.
        }finally{
            java.nio.file.Files.deleteIfExists(webm);
        }
    }

    @Test
    void simpleUploadNeverChargesWithoutExplicitOwnerConsent() {
        var error=assertThrows(ResponseStatusException.class,()->
                pilot.startUploadedVideo("owner","e6b8de6b-3008-4bf4-a71c-a0bd9d2a5c9a",
                        false,null));
        assertEquals(HttpStatus.BAD_REQUEST,error.getStatusCode());
        assertTrue(error.getReason().contains("CONSENT"));
    }

    @Test
    void genApiOnlyCapabilityActivatesWithoutAnOfficialKlingApiKey() {
        ReflectionTestUtils.setField(pilot,"enabled",true);
        ReflectionTestUtils.setField(pilot,"key","existing-genapi-key");
        ReflectionTestUtils.setField(pilot,"klingApiKey","");
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        var store=mock(ru.hothat.common.storage.ObjectStore.class);
        when(store.configured()).thenReturn(true);
        pilot.setJdbcTemplate(jdbc);
        pilot.setObjectStore(store);
        Map<String,Object> status=pilot.videoCharacterCapability();
        assertEquals(true,status.get("paidGenerationEnabled"));
        assertEquals(false,status.get("officialKlingApiConfigured"));
        assertEquals("reference-to-video",status.get("referenceMode"));
        assertEquals(false,status.get("persistentKlingVideoElementCreated"));
        assertEquals("genapi-o3-video-reference",status.get("variant"));
        assertEquals(84,status.get("costEstimateRoublesPerSecond"));
        assertEquals(false,status.get("providerDurationVerified"));
    }

    @Test
    void genApiReferenceUsesVideoCharacterElementAndFirstFrameWithoutVideoMotionEdit() {
        var scene=NeuroScenePlan.planForCharacter(
                "Повернуться прямо в камеру, сказать «А вы всё ещё кипятите?», "+
                "сказать «Тогда мы идём к вам!», показать большой палец, "+
                "погрозить кулаком, встать и уйти из кадра.");
        String videoUrl="https://hot-hat-back.up.railway.app/api/v2/public/"+
                "neuro-references/abc123/input.mp4";
        String stillUrl="https://hot-hat-back.up.railway.app/api/v2/public/"+
                "neuro-references/def456/input.jpg";
        Map<String,Object> fields=ReflectionTestUtils.invokeMethod(pilot,
                "genApiVisualReferenceFields",scene,videoUrl,stillUrl);
        assertEquals("reference-to-video",fields.get("model"));
        assertEquals(stillUrl,fields.get("start_image_url"),
                "GenAPI 422 requires start_image_url whenever reference-to-video");
        assertFalse(fields.containsKey("video_url"),
                "Top-level video_url belongs to VIDEO-to-video, not image/element reference-to-video");
        assertEquals(1,((List<?>)fields.get("elements")).size());
        assertEquals(Map.of("video_url",videoUrl),((List<?>)fields.get("elements")).get(0),
                "Keep the archived 8s video as a character identity element");
        assertEquals(scene.durationSeconds()+"",fields.get("duration"));
        assertEquals(true,fields.get("pro"));
        assertEquals(false,fields.get("generate_audio"));
        assertFalse(fields.containsKey("keep_audio"),
                "Keep-audio is a video-edit/reference mode field, not image/element mode");
        assertFalse(fields.containsKey("element_list"));
        assertTrue(fields.containsKey("start_image_url"));
        assertFalse(fields.containsKey("klingApiKey"));
        assertFalse(fields.containsKey("video-to-video-reference"));
        assertTrue(fields.get("prompt").toString().contains("@Element1"));
        assertFalse(fields.get("prompt").toString().contains("@Video1"));
        assertTrue(fields.get("prompt").toString().contains("not the source gestures"));
        assertTrue(fields.get("prompt").toString().length()<=2500);
        assertThrows(IllegalArgumentException.class,
                ()->ReflectionTestUtils.invokeMethod(pilot,
                    "genApiVisualReferenceFields",scene,"http://untrusted.test/input.mp4",stillUrl));
        assertThrows(IllegalArgumentException.class,
                ()->ReflectionTestUtils.invokeMethod(pilot,
                    "genApiVisualReferenceFields",scene,videoUrl,null));
    }

    @Test
    void exactGenApiStartFrame422CanOnlyBeRetriedWithSeparateConsent() {
        ResponseStatusException noConsent=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeVideoReferenceStartImage422("owner-1",
                        "25e45c7a-64a1-45ad-b080-973e9ad20009",false));
        assertEquals(HttpStatus.BAD_REQUEST,noConsent.getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ResponseStatusException.class,
                    ()->pilot.resumeVideoReferenceStartImage422("owner-1",
                        "25e45c7a-64a1-45ad-b080-973e9ad20009",true))
                        .getStatusCode());
    }

    @Test
    @SuppressWarnings({"unchecked","rawtypes"})
    void rejectedReferenceStartImage422OnSameJobCannotBeDoubleClaimed() throws Exception {
        ReflectionTestUtils.setField(pilot,"enabled",true);
        ReflectionTestUtils.setField(pilot,"key","test-existing-genapi-key");
        JdbcTemplate db=mock(JdbcTemplate.class);
        pilot.setJdbcTemplate(db);
        var archive=mock(ru.hothat.common.storage.ObjectStore.class);
        when(archive.configured()).thenReturn(true);
        pilot.setObjectStore(archive);
        String id="25e45c7a-64a1-45ad-b080-973e9ad20009", owner="owner-1";
        ResultSet row=mock(ResultSet.class);
        when(row.getString(1)).thenReturn(id);
        when(row.getString(2)).thenReturn("hat-fac7edcharacter25e45c7a64a145adb080973e9ad20009");
        when(row.getString(3)).thenReturn(owner);
        when(row.getString(4)).thenReturn("failed");
        when(row.getString(6)).thenReturn(
                "{\"character-mode\":\"genapi-o3-video-identity-reference\"}");
        when(row.getString(8)).thenReturn(
                "GenAPI kling-video-o3 submission: GENAPI_HTTP_422 start_image_url: "+
                "Поле start image url обязательно для заполнения");
        when(row.getString(10)).thenReturn("saved-camera.webm");
        when(row.getString(11)).thenReturn("saved-voice.webm");
        when(row.getString(12)).thenReturn("video/webm");
        when(row.getString(13)).thenReturn("audio/webm");
        when(row.getString(15)).thenReturn("kling-video-o3");
        when(row.getString(16)).thenReturn(
                "Сказать «А вы всё ещё кипятите?», потом «Тогда мы идём к вам!», "+
                "показать большой палец, кулак, встать и уйти из кадра.");
        when(row.getInt(17)).thenReturn(0);
        when(db.query(anyString(),any(RowMapper.class),eq(id)))
                .thenAnswer(call->List.of(((RowMapper)call.getArgument(1)).mapRow(row,0)));
        // Someone on another Railway worker claimed the only eligible retry.
        when(db.update(anyString(),eq(id),eq(owner))).thenReturn(0);
        ResponseStatusException denied=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeVideoReferenceStartImage422(owner,id,true));
        assertEquals(HttpStatus.CONFLICT,denied.getStatusCode());
        assertTrue(denied.getReason().contains("ALREADY_CLAIMED"));
        verify(db,times(1)).update(anyString(),eq(id),eq(owner));
    }

    @Test
    @SuppressWarnings({"unchecked","rawtypes"})
    void publicJpegPreflightRejects401BeforeAnyPaidGenApiPost() throws Exception {
        java.net.http.HttpClient client=mock(java.net.http.HttpClient.class);
        NeuroReplacementPilotService service=new NeuroReplacementPilotService(
                new ObjectMapper(),client);
        try{
            ReflectionTestUtils.setField(service,"referencePublicOrigin",
                    "https://hot-hat-back.up.railway.app");
            java.net.http.HttpResponse<java.io.InputStream> blocked=mock(
                    java.net.http.HttpResponse.class);
            when(blocked.statusCode()).thenReturn(401);
            when(blocked.body()).thenReturn(new java.io.ByteArrayInputStream(
                    new byte[]{'<','h','t','m','l','>'}));
            when(client.send(any(java.net.http.HttpRequest.class),
                    any(java.net.http.HttpResponse.BodyHandler.class)))
                    .thenReturn(blocked);
            String url="https://hot-hat-back.up.railway.app"+
                    "/api/v2/public/neuro-references/"+"a".repeat(64)+"/input.jpg";
            Exception refused=assertThrows(Exception.class,
                    ()->ReflectionTestUtils.invokeMethod(service,
                            "checkGenApiMediaDownload",url,"image/jpeg"));
            String reason=String.valueOf(refused)+
                    String.valueOf(refused.getCause())+
                    (refused.getCause()==null?"":String.valueOf(refused.getCause().getCause()));
            assertTrue(reason.contains("HTTP_401"),
                    "HTTP 401 on JPG MUST block paid request: "+reason);
            verify(client,times(1)).send(any(java.net.http.HttpRequest.class),
                    any(java.net.http.HttpResponse.BodyHandler.class));
        }finally{service.close();}
    }

    @Test
    @SuppressWarnings({"unchecked","rawtypes"})
    void publicJpegPreflightAllowsFull200JpegWithoutAuthAndRejectsBrokenMagic() throws Exception {
        java.net.http.HttpClient client=mock(java.net.http.HttpClient.class);
        NeuroReplacementPilotService service=new NeuroReplacementPilotService(
                new ObjectMapper(),client);
        try{
            ReflectionTestUtils.setField(service,"referencePublicOrigin",
                    "https://hot-hat-back.up.railway.app");
            java.net.http.HttpResponse<java.io.InputStream> response=mock(
                    java.net.http.HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(
                    Map.of("Content-Type",List.of("image/jpeg")),(key,value)->true));
            byte[] jpeg=new byte[3000];
            jpeg[0]=(byte)0xff;jpeg[1]=(byte)0xd8;jpeg[2]=(byte)0xff;
            when(response.body()).thenAnswer(call->
                    new java.io.ByteArrayInputStream(jpeg));
            java.net.http.HttpResponse<Void> head=mock(java.net.http.HttpResponse.class);
            when(head.statusCode()).thenReturn(200);
            when(head.headers()).thenReturn(java.net.http.HttpHeaders.of(
                    Map.of("Content-Type",List.of("image/jpeg")),(key,value)->true));
            when(client.send(any(java.net.http.HttpRequest.class),
                    any(java.net.http.HttpResponse.BodyHandler.class)))
                    .thenAnswer(call->{
                        java.net.http.HttpRequest req=call.getArgument(0);
                        return "HEAD".equals(req.method())?head:response;
                    });
            String url="https://hot-hat-back.up.railway.app"+
                    "/api/v2/public/neuro-references/"+"b".repeat(64)+"/input.jpg";
            ReflectionTestUtils.invokeMethod(service,
                    "checkGenApiMediaDownload",url,"image/jpeg");
            jpeg[1]=0;
            Exception invalid=assertThrows(Exception.class,
                    ()->ReflectionTestUtils.invokeMethod(service,
                            "checkGenApiMediaDownload",url,"image/jpeg"));
            String details=String.valueOf(invalid)+
                    String.valueOf(invalid.getCause())+
                    (invalid.getCause()==null?"":String.valueOf(invalid.getCause().getCause()));
            assertTrue(details.contains("INVALID_OR_SHORT_FILE"),details);
        }finally{service.close();}
    }

    @Test
    @SuppressWarnings({"unchecked","rawtypes"})
    void publicMediaGet200ButHead401BlocksPaidKlingBeforeSubmission() throws Exception {
        java.net.http.HttpClient client=mock(java.net.http.HttpClient.class);
        NeuroReplacementPilotService service=new NeuroReplacementPilotService(
                new ObjectMapper(),client);
        try{
            ReflectionTestUtils.setField(service,"referencePublicOrigin",
                    "https://hot-hat-back.up.railway.app");
            java.net.http.HttpResponse<java.io.InputStream> get=mock(
                    java.net.http.HttpResponse.class);
            when(get.statusCode()).thenReturn(200);
            when(get.headers()).thenReturn(java.net.http.HttpHeaders.of(
                    Map.of("Content-Type",List.of("image/jpeg")),(key,value)->true));
            byte[] jpeg=new byte[3000];
            jpeg[0]=(byte)0xff;jpeg[1]=(byte)0xd8;jpeg[2]=(byte)0xff;
            when(get.body()).thenAnswer(call->new java.io.ByteArrayInputStream(jpeg));
            java.net.http.HttpResponse<Void> head=mock(java.net.http.HttpResponse.class);
            when(head.statusCode()).thenReturn(401);
            when(client.send(any(java.net.http.HttpRequest.class),
                    any(java.net.http.HttpResponse.BodyHandler.class)))
                    .thenAnswer(call->{
                        java.net.http.HttpRequest req=call.getArgument(0);
                        return "HEAD".equals(req.method())?head:get;
                    });
            String url="https://hot-hat-back.up.railway.app"+
                    "/api/v2/public/neuro-references/"+"c".repeat(64)+"/input.jpg";
            Exception refused=assertThrows(Exception.class,
                    ()->ReflectionTestUtils.invokeMethod(service,
                            "checkGenApiMediaDownload",url,"image/jpeg"));
            String details=String.valueOf(refused)+String.valueOf(refused.getCause());
            assertTrue(details.contains("HEAD_REJECTED_image_jpeg_HTTP_401"),details);
            var requests=org.mockito.ArgumentCaptor.forClass(java.net.http.HttpRequest.class);
            verify(client,times(2)).send(requests.capture(),
                    any(java.net.http.HttpResponse.BodyHandler.class));
            assertEquals(List.of("GET","HEAD"),
                    requests.getAllValues().stream().map(java.net.http.HttpRequest::method).toList());
        }finally{service.close();}
    }

    @Test
    @SuppressWarnings({"unchecked","rawtypes"})
    void media422RetryRequiresSecondExplicitConsentAndFreePreflight() throws Exception {
        ResponseStatusException denied=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeVideoReferenceMedia422("owner-1",
                        "25e45c7a-64a1-45ad-b080-973e9ad20009",false));
        assertEquals(HttpStatus.BAD_REQUEST,denied.getStatusCode());
        ReflectionTestUtils.setField(pilot,"enabled",true);
        ReflectionTestUtils.setField(pilot,"key","simulation-only");
        JdbcTemplate db=mock(JdbcTemplate.class);
        pilot.setJdbcTemplate(db);
        var archive=mock(ru.hothat.common.storage.ObjectStore.class);
        when(archive.configured()).thenReturn(true);
        pilot.setObjectStore(archive);
        ResultSet row=mock(ResultSet.class);
        String id="25e45c7a-64a1-45ad-b080-973e9ad20009";
        when(row.getString(1)).thenReturn(id);
        when(row.getString(2)).thenReturn("hat-fac7edcharacter25e45c7a64a145adb080973e9ad20009");
        when(row.getString(3)).thenReturn("owner-1");
        when(row.getString(4)).thenReturn("failed");
        when(row.getString(6)).thenReturn(
                "{\"character-mode\":\"genapi-o3-video-identity-reference\"}");
        when(row.getString(8)).thenReturn(
                "GenAPI kling-video-o3 submission: GENAPI_HTTP_422 Ошибка файла. "+
                "Это возможно из-за отсутствия доступа к файлу.");
        when(row.getString(10)).thenReturn("source-camera.webm");
        when(row.getString(11)).thenReturn("source-voice.webm");
        when(row.getString(12)).thenReturn("video/webm");
        when(row.getString(13)).thenReturn("audio/webm");
        when(row.getString(15)).thenReturn("kling-video-o3");
        when(row.getString(16)).thenReturn("Сказать привет, показать кулак и встать.");
        when(row.getInt(17)).thenReturn(1);
        when(db.query(anyString(),any(RowMapper.class),eq(id)))
                .thenAnswer(call->List.of(((RowMapper)call.getArgument(1)).mapRow(row,0)));
        ResponseStatusException blocked=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeVideoReferenceMedia422("owner-1",id,true));
        assertEquals(HttpStatus.CONFLICT,blocked.getStatusCode());
        assertTrue(blocked.getReason().contains("PREFLIGHT_FAILED"));
        verify(db,never()).update(anyString(),eq(id),eq("owner-1"));
    }

    @Test
    void videoCharacter3RequiresExplicitConsentAndSameOfficialKlingAccount() {
        ResponseStatusException noConsent=assertThrows(ResponseStatusException.class,
                ()->pilot.startVideoCharacterFromArchive("owner-1",
                    "f8011911-f38d-4706-92cb-546049d42d0d",
                    "cc7a8726-23c0-49be-81df-e87a25e4c596",0,
                    "Произнести «Привет!» и показать большой палец.",false));
        assertEquals(HttpStatus.BAD_REQUEST,noConsent.getStatusCode());
        ResponseStatusException noKlingKey=assertThrows(ResponseStatusException.class,
                ()->pilot.startVideoCharacterFromArchive("owner-1",
                    "f8011911-f38d-4706-92cb-546049d42d0d",
                    "cc7a8726-23c0-49be-81df-e87a25e4c596",0,
                    "Произнести «Привет!» и показать большой палец.",true));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,noKlingKey.getStatusCode());
        Map<String,Object> readiness=pilot.videoCharacterCapability();
        assertEquals(false,readiness.get("paidGenerationEnabled"));
        assertEquals(false,readiness.get("officialKlingApiConfigured"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void officialKlingCharacterAndOmniHaveTwoDistinctDocumentedPayloads() {
        String id="cc7a8726-23c0-49be-81df-e87a25e4c596";
        String ref="https://hot-hat-back.up.railway.app/api/v2/public/neuro-references/"+
                   "123456abcdef/input.mp4";
        Map<String,Object> character=ReflectionTestUtils.invokeMethod(
                pilot,"klingVideoCharacterFields",ref,id);
        assertEquals("video_refer",character.get("reference_type"));
        assertFalse(character.containsKey("video_url"));
        Map<String,Object> media=(Map<String,Object>)character.get("element_video_list");
        assertEquals(ref,((Map<String,Object>)((List<?>)media.get("refer_videos")).get(0)).get("video_url"));
        NeuroScenePlan scene=NeuroScenePlan.plan(
            "Повернуться к камере, произнести «Привет!», показать большой палец.");
        Map<String,Object> omni=ReflectionTestUtils.invokeMethod(
                pilot,"klingOmniCharacterFields",123L,
                "https://hot-hat-back.up.railway.app/api/v2/public/neuro-references/still/input.jpg",
                scene,id);
        assertEquals("kling-v3-omni",omni.get("model_name"));
        assertEquals("pro",omni.get("mode"));
        assertEquals(false,omni.get("multi_shot"));
        assertFalse(omni.containsKey("video_list"),"Old source movements must not be used");
        List<?> elements=(List<?>)omni.get("element_list");
        assertEquals(123L,((Map<?,?>)elements.get(0)).get("element_id"));
        assertTrue(omni.get("prompt").toString().contains("<<<element_1>>>"));
        assertTrue(omni.get("prompt").toString().contains("<<<image_1>>>"));
    }

    @Test
    void ownerMustApproveNewGesturesBeforeLipSyncCanBePurchased() {
        ResponseStatusException noConsent=assertThrows(ResponseStatusException.class,
                ()->pilot.approveCharacterActing("owner-1",
                        "f8011911-f38d-4706-92cb-546049d42d0d",false));
        assertEquals(HttpStatus.BAD_REQUEST,noConsent.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(ResponseStatusException.class,
                ()->pilot.approveCharacterActing("owner-1",
                        "f8011911-f38d-4706-92cb-546049d42d0d",true)).getStatusCode());
    }

    @Test
    void paidRequestRequiresExplicitConsent() {
        ReflectionTestUtils.setField(pilot, "enabled", true);
        ReflectionTestUtils.setField(pilot, "key", "local-test-token");
        MockMultipartFile video = new MockMultipartFile("video", "camera.webm", "video/webm", new byte[]{1});
        MockMultipartFile voice = new MockMultipartFile("voice", "voice.webm", "audio/webm", new byte[]{1});

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> pilot.start("hat-0123456789abcdef", "owner-1", false, video, voice));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsUnsupportedMediaBeforeAnyPaidJobIsCreated() {
        ReflectionTestUtils.setField(pilot, "enabled", true);
        ReflectionTestUtils.setField(pilot, "key", "local-test-token");
        MockMultipartFile video = new MockMultipartFile("video", "evil.exe", "application/octet-stream", new byte[]{1});
        MockMultipartFile voice = new MockMultipartFile("voice", "voice.webm", "audio/webm", new byte[]{1});

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> pilot.start("hat-0123456789abcdef", "owner-1", true, video, voice));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void featureCanBeDisabledWithoutUnsettingGenApiCredentials() {
        ReflectionTestUtils.setField(pilot, "enabled", false);
        ReflectionTestUtils.setField(pilot, "key", "local-test-token");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> pilot.start("hat-0123456789abcdef", "owner-1", true, null, null));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void unknownJobsAreNotExposed() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> pilot.status("hat-0123456789abcdef", "owner-1", "not-his-job"));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void extractsNestedGenApiFailureInsteadOfGenericError() throws Exception {
        ObjectMapper json = new ObjectMapper();
        var response = json.readTree("{\"status\":\"error\",\"data\":{\"error\":{\"message\":\"Unsupported video format\"}}}");
        String message = ReflectionTestUtils.invokeMethod(pilot, "errorCode", response);
        assertEquals("Unsupported video format", message);
    }

    @Test
    void providerFailureDetailsKeepFullModerationOrValidationWordingWithoutSecrets() {
        String exact = "Параметры сформированы некорректно. Пожалуйста, убедитесь в " +
                "правильности введенных данных и отсутствии неприемлемого контента. " +
                "Диагностика завершена.";
        String safe = ReflectionTestUtils.invokeMethod(pilot,
                "safeProviderStatusMessage",exact);
        assertEquals(exact,safe,"The former 110-character limit hid the important ending");

        String secret = exact+" https://private.example.test/input.mp4?Signature=SECRET123 "+
                "Bearer=secret-token";
        String scrubbed=ReflectionTestUtils.invokeMethod(pilot,
                "safeProviderStatusMessage",secret);
        assertTrue(scrubbed.contains("неприемлемого контента"),scrubbed);
        assertFalse(scrubbed.contains("SECRET123"));
        assertFalse(scrubbed.contains("private.example.test"));
        assertFalse(scrubbed.contains("secret-token"));
        assertTrue(scrubbed.length()<=480);
    }

    @Test
    void doesNotInventProviderFailureReason() throws Exception {
        ObjectMapper json = new ObjectMapper();
        var response = json.readTree("{\"status\":\"error\"}");
        String message = ReflectionTestUtils.invokeMethod(pilot, "errorCode", response);
        assertEquals("", message);
    }

    @Test
    void usesTheSameKlingVideoO3ModelAsTheSuccessfulManualTest() {
        assertEquals("kling-video-o3",
                ReflectionTestUtils.getField(NeuroReplacementPilotService.class, "VIDEO_MODEL_ID"));
        String prompt = (String) ReflectionTestUtils.getField(
                NeuroReplacementPilotService.class, "VIDEO_PROMPT");
        assertTrue(prompt.contains("THUMBS-UP"));
        assertTrue(prompt.contains("GOODBYE WAVE"));
        assertTrue(prompt.contains("STANDS UP"));
        assertTrue(prompt.contains("WALKS AWAY"));
        assertTrue(prompt.contains("APPEARANCE AND SET REFERENCE ONLY"));
        assertFalse(prompt.contains("Edit the supplied webcam video"));
    }

    @Test
    void lipSyncUsesGenApiAcceptedModeForAlreadyAlignedFullLengthAudio() {
        assertEquals("cut_off", ReflectionTestUtils.getField(
                NeuroReplacementPilotService.class, "LIPSYNC_SYNC_MODE"),
                "GenAPI rejected silence with HTTP 422; the local FFmpeg output is padded to exactly the video duration");
    }

    @Test
    void klingO3ReferenceVideoUsesExplicitFifteenSecondActingDuration() {
        Map<?, ?> fields = ReflectionTestUtils.invokeMethod(pilot, "klingEditFields");
        assertEquals("reference-to-video", fields.get("model"),
                "edit-video copies head grooming instead of creating ordered gestures");
        assertEquals("15", fields.get("duration"));
        assertEquals("true", fields.get("pro"));
        assertEquals("false", fields.get("keep_audio"));
        assertEquals("false", fields.get("generate_audio"));
        assertEquals("16:9", fields.get("aspect_ratio"));
        assertTrue(((String)fields.get("prompt")).length() <= 2500,
                "Kling reference prompt may not exceed provider limit");
    }

    @Test
    void voiceScriptHasOnlyTheRequestedCatchphraseAndExpressiveSourceTts() {
        String phrase=(String) ReflectionTestUtils.getField(
                NeuroReplacementPilotService.class,"SPOKEN_TEXT");
        assertTrue(phrase.contains("А вы всё ещё кипятите?"));
        assertTrue(phrase.contains("Тогда мы идём к вам!"));
        assertFalse(phrase.contains("не бойся"));
        assertEquals("gemini-tts",ReflectionTestUtils.getField(
                NeuroReplacementPilotService.class,"TTS_MODEL_ID"));
        assertTrue(((String)ReflectionTestUtils.getField(
                NeuroReplacementPilotService.class,"SPEECH_STYLE")).contains("unhurriedly"));
    }

    @Test
    void rejectsOutOfRangeObservationWindowBeforePaidWork() {
        ReflectionTestUtils.setField(pilot, "enabled", true);
        ReflectionTestUtils.setField(pilot, "key", "local-test-token");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> pilot.start("hat-0123456789abcdef", "owner-1", true, null, null, 29.5));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsNonFiniteObservationWindowBeforePaidWork() {
        ReflectionTestUtils.setField(pilot, "enabled", true);
        ReflectionTestUtils.setField(pilot, "key", "local-test-token");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> pilot.start("hat-0123456789abcdef", "owner-1", true, null, null, Double.NaN));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void refusesPaidWorkWhenDurableDatabaseOrArchiveIsUnavailable() {
        ReflectionTestUtils.setField(pilot, "enabled", true);
        ReflectionTestUtils.setField(pilot, "key", "local-test-token");
        MockMultipartFile video = new MockMultipartFile("video", "camera.webm", "video/webm", new byte[]{1, 2});
        MockMultipartFile voice = new MockMultipartFile("voice", "voice.webm", "audio/webm", new byte[]{1, 2});

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> pilot.start("hat-0123456789abcdef", "owner-1", true, video, voice));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void interruptedJobCannotBeBilledAgainInTheSameRoom() throws Exception {
        ReflectionTestUtils.setField(pilot, "enabled", true);
        ReflectionTestUtils.setField(pilot, "key", "local-test-token");
        Class<?> jobClass = Class.forName(NeuroReplacementPilotService.class.getName() + "$Job");
        var ctor = jobClass.getDeclaredConstructor(String.class, String.class, String.class);
        ctor.setAccessible(true);
        Object oldJob = ctor.newInstance("existing-hot-hat-id", "hat-0123456789abcdef", "owner-1");
        ReflectionTestUtils.setField(oldJob, "state", "interrupted");
        Map<String, Object> byRoom = (Map<String, Object>) ReflectionTestUtils.getField(pilot, "byRoom");
        byRoom.put("hat-0123456789abcdef", oldJob);

        // Even new, valid uploaded media must not authorize a second POST.
        MockMultipartFile video = new MockMultipartFile("video", "camera.webm", "video/webm", new byte[]{1, 2});
        MockMultipartFile voice = new MockMultipartFile("voice", "voice.webm", "audio/webm", new byte[]{1, 2});
        Map<String, Object> result = pilot.start("hat-0123456789abcdef", "owner-1", true, video, voice);
        assertEquals("existing-hot-hat-id", result.get("jobId"));
        assertEquals("interrupted", result.get("status"));
        assertEquals(1, byRoom.size());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void databaseCheckpointBeatsStaleCachedProcessingStateInHistoryAndStatus() throws Exception {
        String room = "hat-a1ece4688a6e8389";
        String id = "paid-job-previously-completed";
        String owner = "owner-1";
        Class<?> jobType = Class.forName(NeuroReplacementPilotService.class.getName() + "$Job");
        var ctor = jobType.getDeclaredConstructor(String.class, String.class, String.class);
        ctor.setAccessible(true);
        Object stale = ctor.newInstance(id, room, owner);
        ReflectionTestUtils.setField(stale, "state", "processing");
        ReflectionTestUtils.setField(stale, "stage", "Видео и озвучка");
        Map<String, Object> byRoom = (Map<String, Object>) ReflectionTestUtils.getField(pilot, "byRoom");
        byRoom.put(room, stale);

        JdbcTemplate db = mock(JdbcTemplate.class);
        pilot.setJdbcTemplate(db);
        ResultSet saved = mock(ResultSet.class);
        when(saved.getString(1)).thenReturn(id);
        when(saved.getString(2)).thenReturn(room);
        when(saved.getString(3)).thenReturn(owner);
        when(saved.getString(4)).thenReturn("ready");
        when(saved.getString(5)).thenReturn("Готово");
        when(saved.getString(6)).thenReturn("{\"lipsync\":\"567890\"}");
        when(saved.getString(9)).thenReturn("neuro-replacements/owner-1/result.mp4");
        when(saved.getTimestamp(10)).thenReturn(Timestamp.from(Instant.parse("2026-10-29T13:16:56Z")));

        when(db.query(anyString(), any(RowMapper.class), eq(owner)))
                .thenAnswer(call -> List.of(((RowMapper) call.getArgument(1)).mapRow(saved, 0)));
        when(db.query(anyString(), any(RowMapper.class), eq(id), eq(owner)))
                .thenAnswer(call -> List.of(((RowMapper) call.getArgument(1)).mapRow(saved, 0)));

        Map<String, Object> archived = pilot.history(owner).get(0);
        assertEquals("ready", archived.get("status"));
        assertEquals("Готово", archived.get("stage"));
        assertEquals(true, archived.get("archived"));
        Map<String, Object> direct = pilot.status(room, owner, id);
        assertEquals("ready", direct.get("status"));
        assertEquals("Готово", direct.get("stage"));
    }
    @Test
    void ownerHistoryIncludesEveryDynamicAssignmentAndExactSpokenText() throws Exception {
        String owner = "owner-1";
        JdbcTemplate db = mock(JdbcTemplate.class);
        pilot.setJdbcTemplate(db);
        ResultSet saved = mock(ResultSet.class);
        String script = "Смотрит в камеру и говорит Привет всем друзьям и хорошего дня!";
        when(saved.getString(1)).thenReturn("neuro-script-01");
        when(saved.getString(2)).thenReturn("hat-a1ece4688a6e8389");
        when(saved.getString(3)).thenReturn(owner);
        when(saved.getString(4)).thenReturn("ready");
        when(saved.getString(5)).thenReturn("Готово");
        when(saved.getString(6)).thenReturn("{\"lipsync\":\"already-paid\"}");
        when(saved.getString(9)).thenReturn("neuro-replacements/owner-1/neuro-script-01.mp4");
        when(saved.getString(11)).thenReturn(script);
        when(saved.getTimestamp(10)).thenReturn(Timestamp.from(Instant.parse("2026-10-30T13:16:56Z")));
        when(db.query(anyString(), any(RowMapper.class), eq(owner)))
                .thenAnswer(call -> List.of(((RowMapper)call.getArgument(1)).mapRow(saved,0)));

        Map<String,Object> row = pilot.history(owner).get(0);
        assertEquals("custom",row.get("sceneType"));
        assertEquals(script,row.get("sceneAssignment"));
        assertEquals("Привет всем друзьям и хорошего дня!",row.get("spokenText"));
        assertEquals(true,row.get("backgroundLocked"));
        assertEquals(true,row.get("archived"));
        assertTrue((Integer)row.get("durationSeconds")<=10);
        assertEquals("ready",row.get("status"));
    }

    @Test
    void dynamicSceneIsPassedToKlingWithoutChangingLegacyPilotFields() {
        NeuroScenePlan scene = NeuroScenePlan.plan(
                "Этот игрок должен сказать - а вы все еще кипятите? пауза тогда мы идем к вам "
                + "и показывает большой палец, затем грозит кулаком, встаёт и уходит.");
        Map<String, String> dynamicFields = ReflectionTestUtils.invokeMethod(
                pilot, "klingEditFields", scene);
        Map<String, String> legacyFields = ReflectionTestUtils.invokeMethod(
                pilot, "klingEditFields");
        assertEquals(String.valueOf(scene.durationSeconds()), dynamicFields.get("duration"));
        assertTrue(scene.durationSeconds() >= 4 && scene.durationSeconds() <= 10);
        assertTrue(dynamicFields.get("prompt").contains("BACKGROUND LOCK"));
        assertTrue(dynamicFields.get("prompt").contains("грозит кулаком"));
        assertEquals("image-to-video", dynamicFields.get("model"));
        assertEquals("15", legacyFields.get("duration"), "Old paid jobs must not be rewritten");
    }

    @Test
    void invalidLipSyncMode422CannotTriggerAnyPaidReplayWithoutOwnerConsent() {
        ResponseStatusException refused=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeAfterLipSyncMode422("owner-1",
                        "f8011911-f38d-4706-92cb-546049d42d0d",false));
        assertEquals(HttpStatus.BAD_REQUEST,refused.getStatusCode());
    }

    @Test
    @SuppressWarnings({"unchecked","rawtypes"})
    void invalidLipSyncMode422FailsClosedWhenPaidKlingQualityCannotBeVerified() throws Exception {
        ReflectionTestUtils.setField(pilot,"enabled",true);
        ReflectionTestUtils.setField(pilot,"key","simulation-only");
        var archive=mock(ru.hothat.common.storage.ObjectStore.class);
        when(archive.configured()).thenReturn(true);
        pilot.setObjectStore(archive);
        JdbcTemplate db=mock(JdbcTemplate.class);
        pilot.setJdbcTemplate(db);
        ResultSet row=mock(ResultSet.class);
        String id="f8011911-f38d-4706-92cb-546049d42d0d",owner="owner-1";
        when(row.getString(1)).thenReturn(id);
        when(row.getString(2)).thenReturn("hat-fac7ed44e374eef2");
        when(row.getString(3)).thenReturn(owner);
        when(row.getString(4)).thenReturn("failed");
        when(row.getString(6)).thenReturn("{\"tts\":\"57019817\",\"chatterbox-speech\":\"57019832\",\"kling-video-o3\":\"57020589\"}");
        when(row.getString(8)).thenReturn("GENAPI_HTTP_422 sync_mode: Значение поля sync mode некорректно.");
        when(row.getString(10)).thenReturn("existing-camera");
        when(row.getString(11)).thenReturn("existing-voice");
        when(row.getString(12)).thenReturn("video/webm");
        when(row.getString(13)).thenReturn("audio/webm");
        when(row.getString(15)).thenReturn("lipsync");
        when(row.getString(16)).thenReturn(
                "Повернуться к веб-камере и смотреть прямо в камеру. "+
                "Выразительно произнести «А вы всё ещё кипятите?», выдержать паузу, "+
                "затем выразительно произнести «Тогда мы идём к вам!», "+
                "показать большой палец прямо в камеру. "+
                "Затем угрожающе погрозить кулаком прямо в камеру, встать со стула и уйти из кадра.");
        when(db.query(anyString(),any(RowMapper.class),eq(id))).thenAnswer(call->
                List.of(((RowMapper)call.getArgument(1)).mapRow(row,0)));
        when(db.update(anyString(),eq(id),eq(owner))).thenReturn(0);
        ResponseStatusException doubleClick=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeAfterLipSyncMode422(owner,id,true));
        assertEquals(HttpStatus.BAD_GATEWAY,doubleClick.getStatusCode(),
                "Never charge Lip Sync when the old paid Kling cannot be quality-checked");
        verify(db,never()).update(anyString(),eq(id),eq(owner));
    }

    @Test
    void general422OrOtherJobsCannotUseLipSyncSpecificRecovery() throws Exception {
        ReflectionTestUtils.setField(pilot,"enabled",true);
        ReflectionTestUtils.setField(pilot,"key","simulation-only");
        var archive=mock(ru.hothat.common.storage.ObjectStore.class);
        when(archive.configured()).thenReturn(true);
        pilot.setObjectStore(archive);
        JdbcTemplate db=mock(JdbcTemplate.class);
        pilot.setJdbcTemplate(db);
        ResultSet row=mock(ResultSet.class);
        when(row.getString(1)).thenReturn("job-with-unknown-422");
        when(row.getString(2)).thenReturn("hat-fac7ed44e374eef2");
        when(row.getString(3)).thenReturn("owner-1");
        when(row.getString(4)).thenReturn("failed");
        when(row.getString(6)).thenReturn("{\"tts\":\"57019817\",\"chatterbox-speech\":\"57019832\",\"kling-video-o3\":\"57020589\"}");
        when(row.getString(8)).thenReturn("GENAPI_HTTP_422 video_url: invalid URL");
        when(row.getString(15)).thenReturn("lipsync");
        when(db.query(anyString(),any(RowMapper.class),eq("job-with-unknown-422")))
                .thenAnswer(call->List.of(((RowMapper)call.getArgument(1)).mapRow(row,0)));
        ResponseStatusException rejected=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeAfterLipSyncMode422("owner-1","job-with-unknown-422",true));
        assertEquals(HttpStatus.CONFLICT,rejected.getStatusCode());
        verify(db,never()).update(anyString(),anyString(),anyString());
    }

    @Test
    void speechGateResumeRequiresExplicitConsentBeforeReadingOrCharging() {
        ResponseStatusException e=assertThrows(ResponseStatusException.class,
                () -> pilot.resumeAfterSpeechGate("owner-1","old-paid-job",false));
        assertEquals(HttpStatus.BAD_REQUEST,e.getStatusCode());
    }

    @Test
    @SuppressWarnings({"unchecked","rawtypes"})
    void speechGateResumeWithOldIdsRejectsDuplicateDatabaseClaimBeforeAnyPaidPost() throws Exception {
        ReflectionTestUtils.setField(pilot,"enabled",true);
        ReflectionTestUtils.setField(pilot,"key","simulation-only");
        ru.hothat.common.storage.ObjectStore archive=mock(ru.hothat.common.storage.ObjectStore.class);
        when(archive.configured()).thenReturn(true);
        pilot.setObjectStore(archive);
        JdbcTemplate db=mock(JdbcTemplate.class);
        pilot.setJdbcTemplate(db);
        ResultSet row=mock(ResultSet.class);
        String id="f3c3190a-8f68-4a38-95df-26dc3fe17f0e", owner="owner-1";
        when(row.getString(1)).thenReturn(id);
        when(row.getString(2)).thenReturn("hat-50698835685110f4");
        when(row.getString(3)).thenReturn(owner);
        when(row.getString(4)).thenReturn("failed");
        when(row.getString(5)).thenReturn("Ошибка генерации");
        when(row.getString(6)).thenReturn("{\"tts\":\"57010833\",\"chatterbox-speech\":\"57010840\"}");
        when(row.getString(8)).thenReturn(
                "NEURO_SPEECH_TOO_FAST_OR_SILENT voiced=5.2s lastSpeech=6.9s");
        when(row.getString(10)).thenReturn("camera-already-in-S3");
        when(row.getString(11)).thenReturn("voice-already-in-S3");
        when(row.getString(12)).thenReturn("video/webm");
        when(row.getString(13)).thenReturn("audio/webm");
        when(row.getString(16)).thenReturn(
                "Повернуться к веб-камере и смотреть прямо в камеру. "+
                "Выразительно произнести «А вы всё ещё кипятите?», выдержать паузу, "+
                "затем выразительно произнести «Тогда мы идём к вам!», "+
                "показать большой палец прямо в камеру. "+
                "Затем угрожающе погрозить кулаком прямо в камеру, встать со стула и уйти из кадра.");
        when(db.query(anyString(),any(RowMapper.class),eq(id)))
                .thenAnswer(call -> List.of(((RowMapper)call.getArgument(1)).mapRow(row,0)));
        // A competing browser/device already claimed this job. Absolutely no
        // second paid POST may be sent, even with explicit consent.
        when(db.update(anyString(),eq(id),eq(owner))).thenReturn(0);
        ResponseStatusException duplicate=assertThrows(ResponseStatusException.class,
                () -> pilot.resumeAfterSpeechGate(owner,id,true));
        assertEquals(HttpStatus.CONFLICT,duplicate.getStatusCode());
        assertTrue(String.valueOf(duplicate.getReason()).contains("ALREADY_CLAIMED"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void klingJsonReferenceUsesSignedUrlAndRealBooleanFields() {
        String assignment="Говорит Привет всем друзьям, затем показывает большой палец!";
        NeuroScenePlan plan=NeuroScenePlan.plan(assignment);
        Map<String,Object> fields=ReflectionTestUtils.invokeMethod(
                pilot,"klingJsonReferenceFields",plan,
                "https://signed.example.test/input.jpg");
        assertEquals("https://signed.example.test/input.jpg",fields.get("start_image_url"));
        assertEquals("image-to-video",fields.get("model"));
        assertEquals(true,fields.get("pro"));
        assertFalse(fields.containsKey("generate_audio"),"Documented default is false");
        assertFalse(fields.containsKey("keep_audio"),"Documented default is false");
        assertFalse(fields.containsKey("aspect_ratio"),"Documented default is 16:9");
        assertEquals(String.valueOf(plan.durationSeconds()),fields.get("duration"));
        assertFalse(fields.containsKey("video_url"),"Video reference copies motions");
        assertTrue(String.valueOf(fields.get("prompt")).contains("@Image1"));
    }

    @Test
    void nestedGenApiValidationErrorIsNotLostOn422() throws Exception {
        var data = new ObjectMapper().readTree(
                "{\"detail\":[{\"loc\":[\"body\",\"video_url\"],\"type\":\"missing\","+
                "\"msg\":\"Field required\"}]}");
        String reason=ReflectionTestUtils.invokeMethod(pilot,"validationDetails",data);
        assertTrue(reason.contains("Field required"));
        assertTrue(reason.contains("video_url"));
    }

    @Test
    void documentedGenApi422ErrorsValidationRevealsRejectedFieldsWithoutSignedUrls() throws Exception {
        var data = new ObjectMapper().readTree("""
                {"error":true,"errors_validation":{
                  "model":["Unknown generation mode"],
                  "video_url":["Cannot download https://private.example.test/?Signature=SECRET123"],
                  "duration":["Must be between 3 and 15 seconds"]
                }}
                """);
        String diagnostic=ReflectionTestUtils.invokeMethod(pilot,"validationDetails",data);
        assertTrue(diagnostic.contains("model: Unknown generation mode"),diagnostic);
        assertTrue(diagnostic.contains("video_url: Cannot download"),diagnostic);
        assertTrue(diagnostic.contains("duration: Must be between 3 and 15"),diagnostic);
        assertFalse(diagnostic.contains("private.example.test"),diagnostic);
        assertFalse(diagnostic.contains("SECRET123"),diagnostic);
    }

    @Test
    void documentedGenApi422NestedErrorsRemainActionable() throws Exception {
        var data = new ObjectMapper().readTree("""
                {"error":true,"errors_validation":{"body":{
                  "model":{"errors":[{"loc":["body","model"],"msg":"Unsupported generation mode"}]},
                  "video_url":["Invalid input format"]
                }}}
                """);
        String diagnostic=ReflectionTestUtils.invokeMethod(pilot,"validationDetails",data);
        assertTrue(diagnostic.contains("model: Unsupported generation mode"),diagnostic);
        assertTrue(diagnostic.contains("video_url: Invalid input format"),diagnostic);
    }

    @Test
    void documentedGenApi422EmptyValidationMapDoesNotPretendFieldIsKnown() throws Exception {
        var data = new ObjectMapper().readTree("""
                {"error":true,"errors_validation":{}}
                """);
        String diagnostic=ReflectionTestUtils.invokeMethod(pilot,"validationDetails",data);
        assertTrue(diagnostic.contains("without field descriptions"),diagnostic);
    }

    @Test
    void definitiveKling422RequiresNewConsentAndRefusesStaleClaim() throws Exception {
        ResponseStatusException declined=assertThrows(ResponseStatusException.class,
                () -> pilot.resumeAfterKlingValidation("owner-1","old-422",false));
        assertEquals(HttpStatus.BAD_REQUEST,declined.getStatusCode());

        ReflectionTestUtils.setField(pilot,"enabled",true);
        ReflectionTestUtils.setField(pilot,"key","simulation");
        var archive=mock(ru.hothat.common.storage.ObjectStore.class);
        when(archive.configured()).thenReturn(true);
        pilot.setObjectStore(archive);
        JdbcTemplate db=mock(JdbcTemplate.class);
        pilot.setJdbcTemplate(db);
        ResultSet saved=mock(ResultSet.class);
        when(saved.getString(1)).thenReturn("old-422");
        when(saved.getString(2)).thenReturn("hat-50698835685110f4");
        when(saved.getString(3)).thenReturn("owner-1");
        when(saved.getString(4)).thenReturn("failed");
        when(saved.getString(5)).thenReturn("Ошибка генерации");
        when(saved.getString(6)).thenReturn("{\"tts\":\"57010833\",\"chatterbox-speech\":\"57010840\"}");
        when(saved.getString(8)).thenReturn("GENAPI_HTTP_422 provider rejected request");
        when(saved.getString(10)).thenReturn("camera-key");
        when(saved.getString(11)).thenReturn("voice-key");
        when(saved.getString(12)).thenReturn("video/webm");
        when(saved.getString(13)).thenReturn("audio/wav");
        when(saved.getString(15)).thenReturn("kling-video-o3");
        when(saved.getString(16)).thenReturn(
                "Говорит Привет всем друзьям и показывает большой палец.");
        when(db.query(anyString(),any(RowMapper.class),eq("old-422")))
                .thenAnswer(call -> List.of(((RowMapper)call.getArgument(1)).mapRow(saved,0)));
        when(db.update(anyString(),eq("old-422"),eq("owner-1"))).thenReturn(0);
        ResponseStatusException duplicate=assertThrows(ResponseStatusException.class,
                () -> pilot.resumeAfterKlingValidation("owner-1","old-422",true));
        assertEquals(HttpStatus.CONFLICT,duplicate.getStatusCode());
        assertTrue(String.valueOf(duplicate.getReason()).contains("ALREADY_CLAIMED"));
    }

    @Test
    @SuppressWarnings({"unchecked","rawtypes"})
    void freeVideoReferencePreflightNeverOrdersGenApiWhenArchiveIsMissing() throws Exception {
        JdbcTemplate db=mock(JdbcTemplate.class);
        pilot.setJdbcTemplate(db);
        ResultSet rs=mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("reference-test");
        when(rs.getString(2)).thenReturn("hat-50698835685110f4");
        when(rs.getString(3)).thenReturn("owner-1");
        when(rs.getString(4)).thenReturn("failed");
        when(rs.getString(6)).thenReturn("{}");
        when(rs.getString(16)).thenReturn("Говорит Привет всем друзьям и уходит.");
        when(db.query(anyString(),any(RowMapper.class),eq("reference-test")))
                .thenAnswer(call -> List.of(((RowMapper)call.getArgument(1)).mapRow(rs,0)));
        var outcome=pilot.inspectKlingReference("owner-1","reference-test");
        assertEquals("MISSING_ARCHIVED_REFERENCE",outcome.get("check"));
        assertEquals(0,outcome.get("paidCalls"));
        assertEquals(false,outcome.get("safeToRetry"));
        ResponseStatusException denied=assertThrows(ResponseStatusException.class,
                () -> pilot.inspectKlingReference("other-owner","reference-test"));
        assertEquals(HttpStatus.NOT_FOUND,denied.getStatusCode());
    }

    @Test
    void finalKlingTestRequiresConsentAndCannotRunOnAnotherJob() {
        ResponseStatusException noConsent=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeVerifiedKlingFinal("owner-1",
                        "f3c3190a-8f68-4a38-95df-26dc3fe17f0e",false));
        assertEquals(HttpStatus.BAD_REQUEST,noConsent.getStatusCode());
        ReflectionTestUtils.setField(pilot,"enabled",true);
        ReflectionTestUtils.setField(pilot,"key","test-only");
        var archive=mock(ru.hothat.common.storage.ObjectStore.class);
        when(archive.configured()).thenReturn(true);
        pilot.setObjectStore(archive);
        pilot.setJdbcTemplate(mock(JdbcTemplate.class));
        ResponseStatusException other=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeVerifiedKlingFinal("owner-1","not-the-original-paid-job",true));
        assertEquals(HttpStatus.CONFLICT,other.getStatusCode());
    }

    @Test
    void minimalKlingRetryRequiresFreshConsentAndIsLimitedToOriginalJob() {
        ResponseStatusException missing=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeMinimalKlingOnce("owner-1",
                        "f3c3190a-8f68-4a38-95df-26dc3fe17f0e",false));
        assertEquals(HttpStatus.BAD_REQUEST,missing.getStatusCode());
        ReflectionTestUtils.setField(pilot,"enabled",true);
        ReflectionTestUtils.setField(pilot,"key","test-only");
        var archive=mock(ru.hothat.common.storage.ObjectStore.class);
        when(archive.configured()).thenReturn(true);
        pilot.setObjectStore(archive);
        pilot.setJdbcTemplate(mock(JdbcTemplate.class));
        ResponseStatusException other=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeMinimalKlingOnce("owner-1","different-paid-job",true));
        assertEquals(HttpStatus.CONFLICT,other.getStatusCode());
    }

    @Test
    @SuppressWarnings({"unchecked","rawtypes"})
    void minimalKlingRetryCannotChargeWhenAnotherTabHasAlreadyClaimed() throws Exception {
        ReflectionTestUtils.setField(pilot,"enabled",true);
        ReflectionTestUtils.setField(pilot,"key","test-only");
        var archive=mock(ru.hothat.common.storage.ObjectStore.class);
        when(archive.configured()).thenReturn(true);
        pilot.setObjectStore(archive);
        JdbcTemplate db=mock(JdbcTemplate.class);
        pilot.setJdbcTemplate(db);
        ResultSet saved=mock(ResultSet.class);
        String jobId="f3c3190a-8f68-4a38-95df-26dc3fe17f0e";
        when(saved.getString(1)).thenReturn(jobId);
        when(saved.getString(2)).thenReturn("hat-50698835685110f4");
        when(saved.getString(3)).thenReturn("owner-1");
        when(saved.getString(4)).thenReturn("failed");
        when(saved.getString(5)).thenReturn("Ошибка генерации");
        when(saved.getString(6)).thenReturn("{\"tts\":\"57010833\",\"chatterbox-speech\":\"57010840\"}");
        when(saved.getString(8)).thenReturn("GENAPI_HTTP_422 model: invalid mode");
        when(saved.getString(10)).thenReturn("camera");
        when(saved.getString(11)).thenReturn("voice");
        when(saved.getString(12)).thenReturn("video/webm");
        when(saved.getString(13)).thenReturn("audio/webm");
        when(saved.getString(15)).thenReturn("kling-video-o3");
        when(saved.getString(16)).thenReturn("Говорит Привет всем друзьям и уходит.");
        when(saved.getInt(17)).thenReturn(3);
        when(db.query(anyString(),any(RowMapper.class),eq(jobId)))
                .thenAnswer(call -> List.of(((RowMapper)call.getArgument(1)).mapRow(saved,0)));
        // A second browser won the atomic claim; the first must NOT start
        // another provider task. Preflight can also refuse before CAS.
        when(db.update(anyString(),eq(jobId),eq("owner-1"))).thenReturn(0);
        // Simulate the already-passing, free S3/MP4 preflight, then force
        // the DB CAS to lose. No paid provider POST must be reachable.
        NeuroReplacementPilotService subject=org.mockito.Mockito.spy(pilot);
        org.mockito.Mockito.doReturn(Map.of("check","REFERENCE_READY_PROVIDER_SCHEMA_NOT_PROVEN"))
                .when(subject).inspectKlingReference("owner-1",jobId);
        var result=assertThrows(ResponseStatusException.class,
                ()->subject.resumeMinimalKlingOnce("owner-1",jobId,true));
        assertEquals(HttpStatus.CONFLICT,result.getStatusCode());
        assertTrue(String.valueOf(result.getReason()).contains("ALREADY_CLAIMED"));
        org.mockito.Mockito.verify(db).update(anyString(),eq(jobId),eq("owner-1"));
    }

    @Test
    void studioRejectsInvalidIdAndRequiresNewPaymentConsent() {
        var sample = new MockMultipartFile("video","camera.webm","video/webm",new byte[]{1});
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,
                ()->pilot.startStudio("owner-1","bad-uuid",true,sample,sample,
                        "Говорит Привет всем друзьям.")).getStatusCode());
        ReflectionTestUtils.setField(pilot,"enabled",true);
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,
                ()->pilot.startStudio("owner-1","aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                        false,sample,sample,"Говорит Привет всем друзьям.")).getStatusCode());
    }

    @Test
    void sourceReferenceHasCleanMp4SuffixWithoutAnySignedS3Query() throws Exception {
        var db=mock(JdbcTemplate.class);
        var store=mock(ru.hothat.common.storage.ObjectStore.class);
        when(store.configured()).thenReturn(true);
        pilot.setJdbcTemplate(db);
        pilot.setObjectStore(store);
        ReflectionTestUtils.setField(pilot,"referencePublicOrigin",
                "https://hot-hat-back.up.railway.app");
        Class<?> jobType=Class.forName(
                "ru.hothat.testbot.usecase.NeuroReplacementPilotService$Job");
        var ctor=jobType.getDeclaredConstructor(String.class,String.class,String.class);
        ctor.setAccessible(true);
        Object job=ctor.newInstance("test-123","hat-aabbccddeeff0011","owner-1");
        String clean=ReflectionTestUtils.invokeMethod(pilot,
                "publishSceneCameraReference",job,new byte[300]);
        assertTrue(clean.matches(
                "https://hot-hat-back[.]up[.]railway[.]app/api/v2/public/neuro-references/[a-f0-9]{64}/input[.]mp4"),
                clean);
        assertFalse(clean.contains("?"),"S3 query params caused the real GenAPI 422");
        assertFalse(clean.contains("X-Amz"),"Never forward S3 signatures to GenAPI");
        org.mockito.Mockito.verify(store).put(
                eq("neuro-replacements/owner-1/test-123-kling-reference.mp4"),
                any(byte[].class),eq("video/mp4"));
    }

    @Test
    void newImageToVideoUsesOnlyOnePrivateCameraFrameWithShortLivedCleanJpegUrl() throws Exception {
        JdbcTemplate db=mock(JdbcTemplate.class);
        var store=mock(ru.hothat.common.storage.ObjectStore.class);
        when(store.configured()).thenReturn(true);
        pilot.setJdbcTemplate(db);
        pilot.setObjectStore(store);
        ReflectionTestUtils.setField(pilot,"referencePublicOrigin",
                "https://hot-hat-back.up.railway.app");
        Class<?> jobType=Class.forName(NeuroReplacementPilotService.class.getName()+"$Job");
        var ctor=jobType.getDeclaredConstructor(String.class,String.class,String.class);
        ctor.setAccessible(true);
        Object job=ctor.newInstance("test-jpg","hat-aabbccddeeff0011","owner-1");
        String url=ReflectionTestUtils.invokeMethod(pilot,"cleanGenApiInputUrl",job,
                "neuro-replacements/owner-1/test-jpg-kling-still.jpg","jpg");
        assertTrue(url.matches("https://hot-hat-back[.]up[.]railway[.]app/"+
                "api/v2/public/neuro-references/[a-f0-9]{64}/input[.]jpg"),url);
        assertFalse(url.contains("?"));
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(ResponseStatusException.class,
                ()->pilot.publicKlingStill("not-a-token")).getStatusCode());
    }

    @Test
    void reviewedActingCannotSpendWithoutExplicitConsent() {
        ResponseStatusException denied=assertThrows(ResponseStatusException.class,
                ()->pilot.resumeReviewedActing("owner-1","some-job",false));
        assertEquals(HttpStatus.BAD_REQUEST,denied.getStatusCode());
    }

    @Test
    void lipSyncInputAlsoUsesCleanWavUrlWithoutS3Signature() throws Exception {
        JdbcTemplate db=mock(JdbcTemplate.class);
        var store=mock(ru.hothat.common.storage.ObjectStore.class);
        when(store.configured()).thenReturn(true);
        pilot.setJdbcTemplate(db);
        pilot.setObjectStore(store);
        ReflectionTestUtils.setField(pilot,"referencePublicOrigin",
                "https://hot-hat-back.up.railway.app");
        Class<?> jobType=Class.forName(
                "ru.hothat.testbot.usecase.NeuroReplacementPilotService$Job");
        var ctor=jobType.getDeclaredConstructor(String.class,String.class,String.class);
        ctor.setAccessible(true);
        Object job=ctor.newInstance("test-abc","hat-aabbccddeeff0011","owner-1");
        String clean=ReflectionTestUtils.invokeMethod(pilot,"cleanGenApiInputUrl",
                job,"neuro-replacements/owner-1/test-abc-lipsync-voice.wav","wav");
        assertTrue(clean.matches(
                "https://hot-hat-back[.]up[.]railway[.]app/api/v2/public/neuro-references/[a-f0-9]{64}/input[.]wav"),clean);
        assertFalse(clean.contains("?"),"signed S3 query causes provider file validation errors");
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(ResponseStatusException.class,
                ()->pilot.publicVoiceReference("not-a-token")).getStatusCode());
    }

}
