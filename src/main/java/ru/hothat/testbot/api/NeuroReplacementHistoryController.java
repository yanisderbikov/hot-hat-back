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
