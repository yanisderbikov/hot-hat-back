package ru.hothat.testbot.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import ru.hothat.testbot.api.dto.NeuroJobResponseDTO;
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

    @PostMapping(value="/studio/video-character/plan",
                 consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public NeuroScenePlan videoCharacterPlan(@AuthenticationPrincipal HotHatUser owner,
            @RequestParam("assignment") String assignment) {
        return NeuroScenePlan.planForCharacter(assignment);
    }

    @PostMapping(value="/studio/plan", consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ApiResponse(responseCode="200",description="Бесплатный расчёт нового студийного задания до оплаты")
    public NeuroScenePlan studioPlan(@AuthenticationPrincipal HotHatUser owner,
                                    @RequestParam("assignment") String assignment) {
        return NeuroScenePlan.plan(assignment);
    }

    @GetMapping("/studio/video-character/capability")
    @ApiResponse(responseCode="200", description="Бесплатная проверка доступности полного видеоперсонажа и причины запрета платного запуска")
    public Map<String,Object> videoCharacterCapability(@AuthenticationPrincipal HotHatUser owner) {
        return pilot.videoCharacterCapability();
    }

    @PostMapping(value="/studio/video-character/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiResponse(responseCode="200",description="Подготовить и просмотреть восьмисекундный MP4 образец с синхронным голосом без платных API запросов")
    public ResponseEntity<byte[]> videoCharacterPreview(@AuthenticationPrincipal HotHatUser owner,
                                                         @RequestPart("video") MultipartFile video,
                                                         @RequestPart("voice") MultipartFile voice,
                                                         @RequestParam(name="startSeconds",defaultValue="0") double startSeconds) {
        byte[] clip=pilot.prepareVideoCharacterPreview(video,voice,startSeconds);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=hot-hat-video-character-reference.mp4")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(clip);
    }

    @PostMapping(value="/studio/archive", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> archiveStudio(@AuthenticationPrincipal HotHatUser owner,
            @RequestParam("testId") String testId,
            @RequestPart("video") MultipartFile video,
            @RequestPart("voice") MultipartFile voice) {
        return pilot.archiveStudioReference(owner.uid(),testId,video,voice);
    }

    @PostMapping(value="/studio", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiResponse(responseCode = "202", description = "Вручную записанная камера и голос, новый idempotent тест без игровой комнаты")
    public ResponseEntity<Map<String,Object>> studio(@AuthenticationPrincipal HotHatUser owner,
                                    @RequestParam("testId") String testId,
                                    @RequestParam(name="consented",defaultValue="false") boolean consented,
                                    @RequestParam("assignment") String assignment,
                                    @RequestPart("video") MultipartFile video,
                                    @RequestPart("voice") MultipartFile voice) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(pilot.startStudio(owner.uid(),testId,consented,video,voice,assignment));
    }

    @PostMapping(value="/{jobId}/video-character/start",
                 consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ApiResponse(responseCode="202",description="Explicitly approved new direct Kling video-character/Omni task; never repeats the old GenAPI order")
    public ResponseEntity<Map<String,Object>> startVideoCharacter(
            @AuthenticationPrincipal HotHatUser owner,
            @PathVariable String jobId,
            @RequestParam("testId") String testId,
            @RequestParam("assignment") String assignment,
            @RequestParam(name="startSeconds",defaultValue="0") double startSeconds,
            @RequestParam(name="consented",defaultValue="false") boolean consented) {
        return ResponseEntity.accepted().body(pilot.startVideoCharacterFromArchive(
                owner.uid(),jobId,testId,startSeconds,assignment,consented));
    }

    @GetMapping("/{jobId}/video-character/acting")
    @ApiResponse(responseCode="200",description="Already purchased Kling 3 Omni scene for owner inspection before Lip Sync")
    public ResponseEntity<byte[]> paidVideoCharacterActing(
            @AuthenticationPrincipal HotHatUser owner,@PathVariable String jobId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(pilot.paidCharacterActing(owner.uid(),jobId));
    }

    @PostMapping("/{jobId}/video-character/approve")
    @ApiResponse(responseCode="200",description="Owner verifies new gestures and allows only voice/lip sync paid stages")
    public Map<String,Object> approveCharacterVideo(
            @AuthenticationPrincipal HotHatUser owner,@PathVariable String jobId,
            @RequestParam(name="consented",defaultValue="false") boolean consented) {
        return pilot.approveCharacterActing(owner.uid(),jobId,consented);
    }

    @PostMapping(value="/{jobId}/video-character/preview",
                 consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ApiResponse(responseCode="200",
            description="Бесплатно подготовить восьмисекундного видеоперсонажа из уже сохранённых видео и голоса, без новых записей и платных GenAPI запросов")
    public ResponseEntity<byte[]> archivedVideoCharacterPreview(
            @AuthenticationPrincipal HotHatUser owner,
            @PathVariable String jobId,
            @RequestParam(name="startSeconds",defaultValue="0") double startSeconds) {
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
    public Map<String,Object> job(@AuthenticationPrincipal HotHatUser owner,
                                  @PathVariable String jobId) {
        return pilot.statusByJob(owner.uid(),jobId);
    }

    @GetMapping("/{jobId}/original")
    @ApiResponse(responseCode="200",description="Полная исходная запись до Нейроподмены; только владелец")
    public ResponseEntity<byte[]> original(@AuthenticationPrincipal HotHatUser owner,
                                            @PathVariable String jobId) {
        byte[] bytes=pilot.originalVideo(owner.uid(),jobId);
        String mime=pilot.originalMime(owner.uid(),jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .contentType(MediaType.parseMediaType(mime))
                .body(bytes);
    }

    @GetMapping("/{jobId}/provider-status")
    @ApiResponse(responseCode = "200", description = "Бесплатная проверка ранее созданных запросов GenAPI",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = NeuroProviderStatusResponseDTO.class)))
    public Map<String,Object> providerStatus(@AuthenticationPrincipal HotHatUser owner,
                                             @Parameter(description = "Сохранённый ID задания Hot Hat") @PathVariable String jobId) {
        return pilot.inspectProvider(owner.uid(),jobId);
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
    @ApiResponse(responseCode = "200", description = "Продолжить отказавшее на проверке темпа задание с прежними оплаченными TTS/Chatterbox ID; Kling и Lip Sync оплачиваются отдельно")
    public Map<String,Object> resumeSpeechGate(@AuthenticationPrincipal HotHatUser owner,
                                               @Parameter(description = "Существующий ID задания. Новый заказ не создаётся.")
                                               @PathVariable String jobId,
                                               @Parameter(description = "Отдельное согласие на оплату только ещё не выполненных Kling и Lip Sync")
                                               @RequestParam(name="consented", defaultValue="false") boolean consented) {
        return pilot.resumeAfterSpeechGate(owner.uid(), jobId, consented);
    }

    @GetMapping("/{jobId}/video-character/media-preflight")
    @ApiResponse(responseCode="200",description="Бесплатная проверка скачивания GenAPI обоих публичных файлов MP4 и JPG через HTTPS без токена авторизации")
    public Map<String,Object> referenceMediaPreflight(
            @AuthenticationPrincipal HotHatUser owner,@PathVariable String jobId) {
        return pilot.inspectCharacterProviderMedia(owner.uid(),jobId);
    }

    @PostMapping("/{jobId}/resume-reference-file-422")
    @ApiResponse(responseCode="200",description="Одно платное продолжение того же задания после определённого GenAPI FILE_ERROR 422, только после бесплатной проверки обоих файлов")
    public Map<String,Object> resumeReferenceMedia422(
            @AuthenticationPrincipal HotHatUser owner,@PathVariable String jobId,
            @RequestParam(name="consented",defaultValue="false") boolean consented) {
        return pilot.resumeVideoReferenceMedia422(owner.uid(),jobId,consented);
    }

    @PostMapping("/{jobId}/resume-reference-start-image-422")
    @ApiResponse(responseCode="200",
            description="Повтор только определённого отказа GenAPI 422 из-за отсутствующего start_image_url; прежнее задание, явное согласие, без двойной оплаты")
    public Map<String,Object> resumeReferenceStartImage422(
            @AuthenticationPrincipal HotHatUser owner,
            @PathVariable String jobId,
            @RequestParam(name="consented",defaultValue="false") boolean consented) {
        return pilot.resumeVideoReferenceStartImage422(owner.uid(),jobId,consented);
    }

    @PostMapping("/{jobId}/resume-kling-422")
    @ApiResponse(responseCode = "200", description = "Однократно продолжить после определённого отказа 422 с JSON HTTPS MP4. Старые TTS/Chatterbox ID сохраняются.")
    public Map<String,Object> resumeKling422(@AuthenticationPrincipal HotHatUser owner,
                                              @PathVariable String jobId,
                                              @RequestParam(name="consented", defaultValue="false") boolean consented) {
        return pilot.resumeAfterKlingValidation(owner.uid(),jobId,consented);
    }

    @PostMapping("/{jobId}/resume-verified-kling-final")
    @ApiResponse(responseCode = "200", description = "Строго однократный подтверждённый платный тест Kling+LipSync существующего задания после бесплатной проверки видеовхода")
    public Map<String,Object> resumeVerifiedKlingFinal(@AuthenticationPrincipal HotHatUser owner,
                                                        @PathVariable String jobId,
                                                        @RequestParam(name="consented", defaultValue="false") boolean consented) {
        return pilot.resumeVerifiedKlingFinal(owner.uid(),jobId,consented);
    }

    @PostMapping("/{jobId}/resume-minimal-kling-once")
    @ApiResponse(responseCode = "200", description = "Одна подтверждённая попытка минимального JSON Kling с сохранёнными TTS и Chatterbox ID")
    public Map<String,Object> resumeMinimalKlingOnce(@AuthenticationPrincipal HotHatUser owner,
                                                      @PathVariable String jobId,
                                                      @RequestParam(name="consented", defaultValue="false") boolean consented) {
        return pilot.resumeMinimalKlingOnce(owner.uid(),jobId,consented);
    }

    @GetMapping("/{jobId}/kling-preflight")
    @ApiResponse(responseCode = "200", description = "Бесплатно проверить сохранённый MP4 и HTTPS ссылку для Kling без создания нового GenAPI запроса")
    public Map<String,Object> klingPreflight(@AuthenticationPrincipal HotHatUser owner,
                                              @PathVariable String jobId) {
        return pilot.inspectKlingReference(owner.uid(),jobId);
    }


    @GetMapping("/{jobId}/paid-kling/quality")
    @ApiResponse(responseCode= "200", description="Бесплатное сравнение оплаченного Kling с оригиналом по SSIM, без GenAPI POST")
    public Map<String,Object> paidKlingQuality(@AuthenticationPrincipal HotHatUser owner,
                                                @PathVariable String jobId) {
        return pilot.paidActingQuality(owner.uid(),jobId);
    }

    @PostMapping("/{jobId}/resume-reviewed-acting")
    @ApiResponse(responseCode= "200", description="Продолжение только Lip Sync после просмотра и явного одобрения движений в уже оплаченном Kling MP4")
    public Map<String,Object> resumeReviewedActing(@AuthenticationPrincipal HotHatUser owner,
                                                    @PathVariable String jobId,
                                                    @RequestParam(name="consented",defaultValue="false") boolean consented) {
        return pilot.resumeReviewedActing(owner.uid(),jobId,consented);
    }

    @PostMapping("/{jobId}/resume-lipsync-422")
    @ApiResponse(responseCode= "200", description="Единичное платное продолжение Lip Sync после подтверждённого отказа GenAPI 422 sync_mode; старые ID Kling/TTS/Chatterbox не оплачиваются повторно")
    public Map<String,Object> resumeLipSyncMode422(@AuthenticationPrincipal HotHatUser owner,
                                                    @PathVariable String jobId,
                                                    @RequestParam(name="consented",defaultValue="false") boolean consented) {
        return pilot.resumeAfterLipSyncMode422(owner.uid(),jobId,consented);
    }

    @GetMapping("/{jobId}/paid-kling")
    @ApiResponse(responseCode="200",description="Просмотр и скачивание уже оплаченного MP4 Kling до синхронизации губ, без нового платного запроса")
    public ResponseEntity<byte[]> paidKling(@AuthenticationPrincipal HotHatUser owner,
                                            @PathVariable String jobId) {
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
