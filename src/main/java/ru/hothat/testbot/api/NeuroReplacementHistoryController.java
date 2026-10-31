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

    @PostMapping(value="/studio/plan", consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ApiResponse(responseCode="200",description="Бесплатный расчёт нового студийного задания до оплаты")
    public NeuroScenePlan studioPlan(@AuthenticationPrincipal HotHatUser owner,
                                    @RequestParam("assignment") String assignment) {
        return NeuroScenePlan.plan(assignment);
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
