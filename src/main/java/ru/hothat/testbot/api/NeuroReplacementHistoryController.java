package ru.hothat.testbot.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import ru.hothat.testbot.api.dto.NeuroJobResponseDTO;
import ru.hothat.testbot.api.dto.NeuroDetailedResponseDTO;
import ru.hothat.testbot.api.dto.NeuroReadinessResponseDTO;
import ru.hothat.testbot.api.dto.NeuroProviderStatusResponseDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestPart;
import ru.hothat.testbot.usecase.NeuroScenePlan;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.testbot.usecase.NeuroReplacementPilotService;
import java.util.List;
import java.util.Map;

/** Owner-only read-only history; unlike room endpoints it works after a game ends. */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/admin/neuro-replacements")
@PreAuthorize("hasRole('OWNER')")
@SecurityRequirement(name = "Bearer")
public class NeuroReplacementHistoryController {
    private final NeuroReplacementPilotService pilot;

    @GetMapping("/readiness")
    @ApiResponse(responseCode = "200", description = "Готовность архива и базы перед платной отправкой",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = NeuroReadinessResponseDTO.class)))
    public Map<String,Boolean> readiness(@AuthenticationPrincipal HotHatUser owner) {
        return pilot.readiness();
    }

    @GetMapping
    @ApiResponse(responseCode = "200", description = "История всех попыток владельца, независимо от комнаты",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = NeuroJobResponseDTO.class))))
    public List<Map<String,Object>> history(@AuthenticationPrincipal HotHatUser owner) {
        return pilot.history(owner.uid());
    }


    /**
     * Simple 25-second video upload UI. One owner-confirmed payment action,
     * all subsequent stages are the saved GenAPI pipeline; no extra Kling key.
     */
    @GetMapping("/studio/upload/config")
        @ApiResponse(responseCode = "200", description = "Бесплатный расчёт стоимости и настроек загрузки видео Нейроподмены",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroDetailedResponseDTO.UploadConfig.class)))
    public Map<String,Object> uploadConfig(@AuthenticationPrincipal HotHatUser owner) {
        return pilot.uploadConfig();
    }

    @GetMapping("/studio/upload/by-test/{testId}")
        @ApiResponse(responseCode = "200", description = "Найти исходную попытку по idempotent UUID без нового заказа",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroJobResponseDTO.class)))
    public Map<String,Object> uploadByTestId(@AuthenticationPrincipal HotHatUser owner,
                                             @Parameter(description = "Идемпотентный UUID одной попытки; повторная отправка не создаёт второй заказ") @PathVariable String testId) {
        return pilot.uploadByTestId(owner.uid(),testId);
    }

    /** FREE recheck of the original paid Kling ID, then continue the owner's
     * original authorized pipeline only after Kling is confirmed completed.
     * Does not generate or pay for any second Kling task.
     */
    @PostMapping("/studio/upload/{jobId}/resume-timeout")
        @ApiResponse(responseCode = "202", description = "Бесплатно проверить прежний оплаченный запрос и продолжить только при готовом результате",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroJobResponseDTO.class)))
    public ResponseEntity<Map<String,Object>> resumeTimedOutUpload(
            @AuthenticationPrincipal HotHatUser owner,
            @Parameter(description = "Сохранённый ID задания Нейроподмены, не создаёт новый платный запрос") @PathVariable String jobId) {
        return ResponseEntity.accepted().body(
                pilot.resumeTimedOutUpload(owner.uid(),jobId));
    }

    @PostMapping(value="/studio/upload", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiResponse(responseCode = "202", description = "Single owner-approved 20-30s webcam MP4/WebM with voice, <=10s paid GenAPI pipeline",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroJobResponseDTO.class)))
    public ResponseEntity<Map<String,Object>> uploadVideo(
            @AuthenticationPrincipal HotHatUser owner,
            @Parameter(description = "Идемпотентный UUID одной попытки; повторная отправка не создаёт второй заказ") @RequestParam("testId") String testId,
            @Parameter(description = "Явное согласие владельца на оплату; по умолчанию false") @RequestParam(name="consented",defaultValue="false") boolean consented,
            @RequestPart("video") MultipartFile video) {
        return ResponseEntity.accepted().body(
                pilot.startUploadedVideo(owner.uid(),testId,consented,video));
    }

    @PostMapping(value="/studio/video-character/plan",
                 consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public NeuroScenePlan videoCharacterPlan(@AuthenticationPrincipal HotHatUser owner,
            @Parameter(description = "Точное текстовое задание движений и произнесённых фраз") @RequestParam("assignment") String assignment) {
        return NeuroScenePlan.planForCharacter(assignment);
    }

    @PostMapping(value="/studio/plan", consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ApiResponse(responseCode="200",description="Бесплатный расчёт нового студийного задания до оплаты")
    public NeuroScenePlan studioPlan(@AuthenticationPrincipal HotHatUser owner,
                                    @Parameter(description = "Точное текстовое задание движений и произнесённых фраз") @RequestParam("assignment") String assignment) {
        return NeuroScenePlan.plan(assignment);
    }

    @GetMapping("/studio/video-character/capability")
    @ApiResponse(responseCode = "200", description = "Бесплатная проверка доступности полного видеоперсонажа и причины запрета платного запуска",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroDetailedResponseDTO.VideoCharacterCapability.class)))
    public Map<String,Object> videoCharacterCapability(@AuthenticationPrincipal HotHatUser owner) {
        return pilot.videoCharacterCapability();
    }

    @PostMapping(value="/studio/video-character/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiResponse(responseCode = "200", description = "Подготовить и просмотреть восьмисекундный MP4 образец с синхронным голосом без платных API запросов",
            content = @Content(mediaType = "video/mp4", schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> videoCharacterPreview(@AuthenticationPrincipal HotHatUser owner,
                                                         @RequestPart("video") MultipartFile video,
                                                         @RequestPart("voice") MultipartFile voice,
                                                         @Parameter(description = "Смещение начала бесплатного видеопримера в секундах") @RequestParam(name="startSeconds",defaultValue="0") double startSeconds) {
        byte[] clip=pilot.prepareVideoCharacterPreview(video,voice,startSeconds);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=hot-hat-video-character-reference.mp4")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(clip);
    }

    @PostMapping(value="/studio/archive", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
        @ApiResponse(responseCode = "200", description = "Сохранение исходной камеры и голоса в архиве владельца",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroJobResponseDTO.class)))
    public Map<String,Object> archiveStudio(@AuthenticationPrincipal HotHatUser owner,
            @Parameter(description = "Идемпотентный UUID одной попытки; повторная отправка не создаёт второй заказ") @RequestParam("testId") String testId,
            @RequestPart("video") MultipartFile video,
            @RequestPart("voice") MultipartFile voice) {
        return pilot.archiveStudioReference(owner.uid(),testId,video,voice);
    }

    @PostMapping(value="/studio", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiResponse(responseCode = "202", description = "Вручную записанная камера и голос, новый idempotent тест без игровой комнаты",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroJobResponseDTO.class)))
    public ResponseEntity<Map<String,Object>> studio(@AuthenticationPrincipal HotHatUser owner,
                                    @Parameter(description = "Идемпотентный UUID одной попытки; повторная отправка не создаёт второй заказ") @RequestParam("testId") String testId,
                                    @Parameter(description = "Явное согласие владельца на оплату; по умолчанию false") @RequestParam(name="consented",defaultValue="false") boolean consented,
                                    @Parameter(description = "Точное текстовое задание движений и произнесённых фраз") @RequestParam("assignment") String assignment,
                                    @RequestPart("video") MultipartFile video,
                                    @RequestPart("voice") MultipartFile voice) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(pilot.startStudio(owner.uid(),testId,consented,video,voice,assignment));
    }

    @PostMapping(value="/{jobId}/video-character/start",
                 consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ApiResponse(responseCode = "202", description = "Explicitly approved new direct Kling video-character/Omni task; never repeats the old GenAPI order",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroJobResponseDTO.class)))
    public ResponseEntity<Map<String,Object>> startVideoCharacter(
            @AuthenticationPrincipal HotHatUser owner,
            @Parameter(description = "Сохранённый ID задания Нейроподмены, не создаёт новый платный запрос") @PathVariable String jobId,
            @Parameter(description = "Идемпотентный UUID одной попытки; повторная отправка не создаёт второй заказ") @RequestParam("testId") String testId,
            @Parameter(description = "Точное текстовое задание движений и произнесённых фраз") @RequestParam("assignment") String assignment,
            @Parameter(description = "Смещение начала бесплатного видеопримера в секундах") @RequestParam(name="startSeconds",defaultValue="0") double startSeconds,
            @Parameter(description = "Явное согласие владельца на оплату; по умолчанию false") @RequestParam(name="consented",defaultValue="false") boolean consented) {
        return ResponseEntity.accepted().body(pilot.startVideoCharacterFromArchive(
                owner.uid(),jobId,testId,startSeconds,assignment,consented));
    }

    @GetMapping("/{jobId}/video-character/acting")
    @ApiResponse(responseCode = "200", description = "Already purchased Kling 3 Omni scene for owner inspection before Lip Sync",
            content = @Content(mediaType = "video/mp4", schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> paidVideoCharacterActing(
            @AuthenticationPrincipal HotHatUser owner,@Parameter(description = "Сохранённый ID задания Нейроподмены, не создаёт новый платный запрос") @PathVariable String jobId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(pilot.paidCharacterActing(owner.uid(),jobId));
    }

    @PostMapping("/{jobId}/video-character/approve")
    @ApiResponse(responseCode = "200", description = "Owner verifies new gestures and allows only voice/lip sync paid stages",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroJobResponseDTO.class)))
    public Map<String,Object> approveCharacterVideo(
            @AuthenticationPrincipal HotHatUser owner,@Parameter(description = "Сохранённый ID задания Нейроподмены, не создаёт новый платный запрос") @PathVariable String jobId,
            @Parameter(description = "Явное согласие владельца на оплату; по умолчанию false") @RequestParam(name="consented",defaultValue="false") boolean consented) {
        return pilot.approveCharacterActing(owner.uid(),jobId,consented);
    }

    @PostMapping(value="/{jobId}/video-character/preview",
                 consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ApiResponse(responseCode = "200", description = "Бесплатно подготовить восьмисекундного видеоперсонажа из уже сохранённых видео и голоса, без новых записей и платных GenAPI запросов",
            content = @Content(mediaType = "video/mp4", schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> archivedVideoCharacterPreview(
            @AuthenticationPrincipal HotHatUser owner,
            @Parameter(description = "Сохранённый ID задания Нейроподмены, не создаёт новый платный запрос") @PathVariable String jobId,
            @Parameter(description = "Смещение начала бесплатного видеопримера в секундах") @RequestParam(name="startSeconds",defaultValue="0") double startSeconds) {
        byte[] sample=pilot.prepareArchivedVideoCharacterPreview(
                owner.uid(),jobId,startSeconds);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=hot-hat-archived-video-character-8s.mp4")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(sample);
    }

    @GetMapping("/{jobId}")
        @ApiResponse(responseCode = "200", description = "Статус сохранённого задания Нейроподмены",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroJobResponseDTO.class)))
    public Map<String,Object> job(@AuthenticationPrincipal HotHatUser owner,
                                  @Parameter(description = "Сохранённый ID задания Нейроподмены, не создаёт новый платный запрос") @PathVariable String jobId) {
        return pilot.statusByJob(owner.uid(),jobId);
    }

    @GetMapping("/{jobId}/original")
    @ApiResponse(responseCode = "200", description = "Полная исходная запись до Нейроподмены; только владелец",
            content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> original(@AuthenticationPrincipal HotHatUser owner,
                                            @Parameter(description = "Сохранённый ID задания Нейроподмены, не создаёт новый платный запрос") @PathVariable String jobId) {
        byte[] bytes=pilot.originalVideo(owner.uid(),jobId);
        String mime=pilot.originalMime(owner.uid(),jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .contentType(MediaType.parseMediaType(mime))
                .body(bytes);
    }

    @GetMapping("/{jobId}/reference-clip")
    @ApiResponse(responseCode = "200", description = "Точный бесплатный 7.5-секундный видеореференс внешности, отправленный Kling",
            content = @Content(mediaType = "video/mp4",
                    schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> referenceClip(
            @AuthenticationPrincipal HotHatUser owner,
            @Parameter(description = "ID существующего задания Нейроподмены") @PathVariable String jobId) {
        byte[] bytes=pilot.identityReferenceClip(owner.uid(),jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=hot-hat-kling-identity-reference.mp4")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(bytes);
    }

    @GetMapping("/{jobId}/starting-frame")
    @ApiResponse(responseCode = "200", description = "Точный стартовый JPEG, приложенный к запросу GenAPI; только владелец",
            content = @Content(mediaType = "image/jpeg",
                    schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> startingFrame(
            @AuthenticationPrincipal HotHatUser owner,
            @Parameter(description = "ID сохранённой попытки") @PathVariable String jobId) {
        byte[] bytes=pilot.identityStartFrame(owner.uid(),jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=hot-hat-neuro-start-frame.jpg")
                .contentType(MediaType.IMAGE_JPEG)
                .body(bytes);
    }

    @GetMapping("/{jobId}/diagnostics/report")
    @ApiResponse(responseCode = "200", description = "Полный бесплатный отчёт о попытке: этапы, ID и цены GenAPI, промпт, ошибки; без платного POST",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = NeuroDetailedResponseDTO.DiagnosticReport.class)))
    public ResponseEntity<Map<String,Object>> diagnosticReport(
            @AuthenticationPrincipal HotHatUser owner,
            @Parameter(description = "ID существующего задания Нейроподмены") @PathVariable String jobId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=hot-hat-neuro-diagnostics.json")
                .body(pilot.diagnosticReport(owner.uid(),jobId));
    }

    @GetMapping("/{jobId}/provider-status")
    @ApiResponse(responseCode = "200", description = "Бесплатная проверка ранее созданных запросов GenAPI",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = NeuroProviderStatusResponseDTO.class)))
    public Map<String,Object> providerStatus(@AuthenticationPrincipal HotHatUser owner,
                                             @Parameter(description = "Сохранённый ID задания Hot Hat") @PathVariable String jobId) {
        return pilot.inspectProvider(owner.uid(),jobId);
    }

    /** SCRUM-38: one historical paid Kling, new paid voice only by consent. */
    @GetMapping("/{jobId}/voice-rescue/quote")
    @ApiResponse(responseCode="200", description="Бесплатный расчёт завершения уже оплаченного Kling без новых видеозаказов",
            content=@Content(mediaType="application/json",
                    schema=@Schema(implementation=NeuroDetailedResponseDTO.VoiceRescueQuote.class)))
    public Map<String,Object> voiceRescueQuote(
            @AuthenticationPrincipal HotHatUser owner,
            @Parameter(description="Сохранённый ID оплаченной попытки, доступный только владельцу")
            @PathVariable String jobId) {
        return pilot.voiceRescueQuote(owner.uid(),jobId);
    }

    @PostMapping("/{jobId}/voice-rescue/voice")
    @ApiResponse(responseCode="200", description="Явно оплатить только новый короткий TTS и перенос голоса, затем прослушать WAV; Kling/LipSync не заказывать",
            content=@Content(mediaType="application/json",
                    schema=@Schema(implementation=NeuroJobResponseDTO.class)))
    public Map<String,Object> startVoiceRescue(
            @AuthenticationPrincipal HotHatUser owner,
            @Parameter(description="Сохранённый ID оплаченной попытки, доступный только владельцу")
            @PathVariable String jobId,
            @Parameter(description="Явное отдельное согласие владельца на оплату только выбранного этапа")
            @RequestParam(name="consented",defaultValue="false") boolean consented) {
        return pilot.startVoiceRescue(owner.uid(),jobId,consented);
    }

    @GetMapping("/{jobId}/rescue-voice-preview")
    @ApiResponse(responseCode="200", description="Прослушать готовый короткий WAV после переноса голоса без платных запросов",
            content=@Content(mediaType="audio/wav",
                    schema=@Schema(type="string",format="binary")))
    public ResponseEntity<byte[]> voiceRescuePreview(
            @AuthenticationPrincipal HotHatUser owner,
            @Parameter(description="Сохранённый ID оплаченной попытки, доступный только владельцу")
            @PathVariable String jobId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=hot-hat-rescue-voice.wav")
                .contentType(MediaType.parseMediaType("audio/wav"))
                .body(pilot.voiceRescuePreview(owner.uid(),jobId));
    }

    /** Free repair of a proven 404 BEFORE any paid Lip Sync POST. */
    @PostMapping("/{jobId}/voice-rescue/reset-media-404")
    @ApiResponse(responseCode="200", description="Бесплатно снять блокировку после исправленной ошибки 404 временной ссылки оплаченного Kling, без нового запроса GenAPI",
            content=@Content(mediaType="application/json",
                    schema=@Schema(implementation=NeuroJobResponseDTO.class)))
    public Map<String,Object> resetVoiceRescueMedia404(
            @AuthenticationPrincipal HotHatUser owner,
            @Parameter(description="Сохранённый ID оплаченной попытки, доступный только владельцу")
            @PathVariable String jobId) {
        return pilot.resetVoiceRescueMedia404(owner.uid(),jobId);
    }

    @PostMapping("/{jobId}/voice-rescue/lipsync")
    @ApiResponse(responseCode="200", description="После прослушивания отдельно оплатить только Lip Sync для старого Kling ID, без нового Kling/TTS/Chatterbox",
            content=@Content(mediaType="application/json",
                    schema=@Schema(implementation=NeuroJobResponseDTO.class)))
    public Map<String,Object> startLipSyncRescue(
            @AuthenticationPrincipal HotHatUser owner,
            @Parameter(description="Сохранённый ID оплаченной попытки, доступный только владельцу")
            @PathVariable String jobId,
            @Parameter(description="Явное отдельное согласие владельца на оплату только выбранного этапа")
            @RequestParam(name="consented",defaultValue="false") boolean consented) {
        return pilot.startLipSyncRescue(owner.uid(),jobId,consented);
    }

    @PostMapping("/{jobId}/recover")
    @ApiResponse(responseCode = "200", description = "Бесплатно восстановить уже оплаченный Lip Sync MP4 по сохранённым ID, без новых запросов генерации",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = NeuroJobResponseDTO.class)))
    public Map<String,Object> recover(@AuthenticationPrincipal HotHatUser owner,
                                      @Parameter(description = "ID уже оплаченного задания Нейроподмены с Lip Sync")
                                      @PathVariable String jobId) {
        return pilot.recoverPaidOutput(owner.uid(), jobId);
    }

    @PostMapping("/{jobId}/resume")
    @ApiResponse(responseCode = "200", description = "Продолжить задание после отказа провайдера 402",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = NeuroJobResponseDTO.class)))
    public Map<String,Object> resume(@AuthenticationPrincipal HotHatUser owner,
                                      @Parameter(description = "Сохранённый ID задания Нейроподмены после отказа 402")
                                      @PathVariable String jobId,
                                      @Parameter(description = "Явное согласие владельца на оплату только недостающего Lip Sync")
                                      @RequestParam(name="consented", defaultValue="false") boolean consented) {
        return pilot.resumeAfterLimit(owner.uid(),jobId,consented);
    }

    @PostMapping("/{jobId}/resume-speech-gate")
    @ApiResponse(responseCode = "200", description = "Продолжить отказавшее на проверке темпа задание с прежними оплаченными TTS/Chatterbox ID; Kling и Lip Sync оплачиваются отдельно",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroJobResponseDTO.class)))
    public Map<String,Object> resumeSpeechGate(@AuthenticationPrincipal HotHatUser owner,
                                               @Parameter(description = "Существующий ID задания. Новый заказ не создаётся.")
                                               @PathVariable String jobId,
                                               @Parameter(description = "Отдельное согласие на оплату только ещё не выполненных Kling и Lip Sync")
                                               @RequestParam(name="consented", defaultValue="false") boolean consented) {
        return pilot.resumeAfterSpeechGate(owner.uid(), jobId, consented);
    }

    @GetMapping("/{jobId}/video-character/media-preflight")
    @ApiResponse(responseCode = "200", description = "Бесплатная проверка скачивания GenAPI обоих публичных файлов MP4 и JPG через HTTPS без токена авторизации",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroDetailedResponseDTO.ReferenceMediaPreflight.class)))
    public Map<String,Object> referenceMediaPreflight(
            @AuthenticationPrincipal HotHatUser owner,@Parameter(description = "Сохранённый ID задания Нейроподмены, не создаёт новый платный запрос") @PathVariable String jobId) {
        return pilot.inspectCharacterProviderMedia(owner.uid(),jobId);
    }

    @PostMapping("/{jobId}/resume-reference-file-422")
    @ApiResponse(responseCode = "200", description = "Одно платное продолжение того же задания после определённого GenAPI FILE_ERROR 422, только после бесплатной проверки обоих файлов",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroJobResponseDTO.class)))
    public Map<String,Object> resumeReferenceMedia422(
            @AuthenticationPrincipal HotHatUser owner,@Parameter(description = "Сохранённый ID задания Нейроподмены, не создаёт новый платный запрос") @PathVariable String jobId,
            @Parameter(description = "Явное согласие владельца на оплату; по умолчанию false") @RequestParam(name="consented",defaultValue="false") boolean consented) {
        return pilot.resumeVideoReferenceMedia422(owner.uid(),jobId,consented);
    }

    @PostMapping("/{jobId}/resume-reference-start-image-422")
    @ApiResponse(responseCode = "200", description = "Повтор только определённого отказа GenAPI 422 из-за отсутствующего start_image_url; прежнее задание, явное согласие, без двойной оплаты",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroJobResponseDTO.class)))
    public Map<String,Object> resumeReferenceStartImage422(
            @AuthenticationPrincipal HotHatUser owner,
            @Parameter(description = "Сохранённый ID задания Нейроподмены, не создаёт новый платный запрос") @PathVariable String jobId,
            @Parameter(description = "Явное согласие владельца на оплату; по умолчанию false") @RequestParam(name="consented",defaultValue="false") boolean consented) {
        return pilot.resumeVideoReferenceStartImage422(owner.uid(),jobId,consented);
    }

    @PostMapping("/{jobId}/resume-kling-422")
    @ApiResponse(responseCode = "200", description = "Однократно продолжить после определённого отказа 422 с JSON HTTPS MP4. Старые TTS/Chatterbox ID сохраняются.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroJobResponseDTO.class)))
    public Map<String,Object> resumeKling422(@AuthenticationPrincipal HotHatUser owner,
                                              @Parameter(description = "Сохранённый ID задания Нейроподмены, не создаёт новый платный запрос") @PathVariable String jobId,
                                              @Parameter(description = "Явное согласие владельца на оплату; по умолчанию false") @RequestParam(name="consented", defaultValue="false") boolean consented) {
        return pilot.resumeAfterKlingValidation(owner.uid(),jobId,consented);
    }

    @PostMapping("/{jobId}/resume-verified-kling-final")
    @ApiResponse(responseCode = "200", description = "Строго однократный подтверждённый платный тест Kling+LipSync существующего задания после бесплатной проверки видеовхода",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroJobResponseDTO.class)))
    public Map<String,Object> resumeVerifiedKlingFinal(@AuthenticationPrincipal HotHatUser owner,
                                                        @Parameter(description = "Сохранённый ID задания Нейроподмены, не создаёт новый платный запрос") @PathVariable String jobId,
                                                        @Parameter(description = "Явное согласие владельца на оплату; по умолчанию false") @RequestParam(name="consented", defaultValue="false") boolean consented) {
        return pilot.resumeVerifiedKlingFinal(owner.uid(),jobId,consented);
    }

    @PostMapping("/{jobId}/resume-minimal-kling-once")
    @ApiResponse(responseCode = "200", description = "Одна подтверждённая попытка минимального JSON Kling с сохранёнными TTS и Chatterbox ID",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroJobResponseDTO.class)))
    public Map<String,Object> resumeMinimalKlingOnce(@AuthenticationPrincipal HotHatUser owner,
                                                      @Parameter(description = "Сохранённый ID задания Нейроподмены, не создаёт новый платный запрос") @PathVariable String jobId,
                                                      @Parameter(description = "Явное согласие владельца на оплату; по умолчанию false") @RequestParam(name="consented", defaultValue="false") boolean consented) {
        return pilot.resumeMinimalKlingOnce(owner.uid(),jobId,consented);
    }

    @GetMapping("/{jobId}/kling-preflight")
    @ApiResponse(responseCode = "200", description = "Бесплатно проверить сохранённый MP4 и HTTPS ссылку для Kling без создания нового GenAPI запроса",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroDetailedResponseDTO.KlingReferencePreflight.class)))
    public Map<String,Object> klingPreflight(@AuthenticationPrincipal HotHatUser owner,
                                              @Parameter(description = "Сохранённый ID задания Нейроподмены, не создаёт новый платный запрос") @PathVariable String jobId) {
        return pilot.inspectKlingReference(owner.uid(),jobId);
    }


    @GetMapping("/{jobId}/paid-kling/quality")
    @ApiResponse(responseCode = "200", description = "Бесплатное сравнение оплаченного Kling с оригиналом по SSIM, без GenAPI POST",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroDetailedResponseDTO.KlingActingQuality.class)))
    public Map<String,Object> paidKlingQuality(@AuthenticationPrincipal HotHatUser owner,
                                                @Parameter(description = "Сохранённый ID задания Нейроподмены, не создаёт новый платный запрос") @PathVariable String jobId) {
        return pilot.paidActingQuality(owner.uid(),jobId);
    }

    @PostMapping("/{jobId}/resume-reviewed-acting")
    @ApiResponse(responseCode = "200", description = "Продолжение только Lip Sync после просмотра и явного одобрения движений в уже оплаченном Kling MP4",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroJobResponseDTO.class)))
    public Map<String,Object> resumeReviewedActing(@AuthenticationPrincipal HotHatUser owner,
                                                    @Parameter(description = "Сохранённый ID задания Нейроподмены, не создаёт новый платный запрос") @PathVariable String jobId,
                                                    @Parameter(description = "Явное согласие владельца на оплату; по умолчанию false") @RequestParam(name="consented",defaultValue="false") boolean consented) {
        return pilot.resumeReviewedActing(owner.uid(),jobId,consented);
    }

    @PostMapping("/{jobId}/resume-lipsync-422")
    @ApiResponse(responseCode = "200", description = "Единичное платное продолжение Lip Sync после подтверждённого отказа GenAPI 422 sync_mode; старые ID Kling/TTS/Chatterbox не оплачиваются повторно",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NeuroJobResponseDTO.class)))
    public Map<String,Object> resumeLipSyncMode422(@AuthenticationPrincipal HotHatUser owner,
                                                    @Parameter(description = "Сохранённый ID задания Нейроподмены, не создаёт новый платный запрос") @PathVariable String jobId,
                                                    @Parameter(description = "Явное согласие владельца на оплату; по умолчанию false") @RequestParam(name="consented",defaultValue="false") boolean consented) {
        return pilot.resumeAfterLipSyncMode422(owner.uid(),jobId,consented);
    }

    @GetMapping("/{jobId}/paid-kling")
    @ApiResponse(responseCode = "200", description = "Просмотр и скачивание уже оплаченного MP4 Kling до синхронизации губ, без нового платного запроса",
            content = @Content(mediaType = "video/mp4", schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> paidKling(@AuthenticationPrincipal HotHatUser owner,
                                            @Parameter(description = "Сохранённый ID задания Нейроподмены, не создаёт новый платный запрос") @PathVariable String jobId) {
        byte[] video=pilot.paidKlingPreview(owner.uid(),jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=hot-hat-paid-kling.mp4")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(video);
    }

    @GetMapping("/{jobId}/video")
    @ApiResponse(responseCode = "200", description = "Архивный MP4 Нейроподмены с видео и голосом",
            content = @Content(mediaType = "video/mp4",
                    schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> video(@AuthenticationPrincipal HotHatUser owner,
                                       @Parameter(description = "ID готового задания с финальным MP4") @PathVariable String jobId) {
        byte[] bytes = pilot.archivedVideo(owner.uid(), jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=hot-hat-neuro.mp4")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(bytes);
    }
}
