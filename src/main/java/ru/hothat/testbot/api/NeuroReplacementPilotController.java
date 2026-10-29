package ru.hothat.testbot.api;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import ru.hothat.testbot.api.dto.NeuroJobResponseDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.testbot.usecase.NeuroReplacementPilotService;
import ru.hothat.testbot.usecase.TestRoomFeatureGuard;
import ru.hothat.testbot.usecase.TestRoomOwnershipGuard;

import java.util.Map;

/** Paid, opt-in AI webcam-replacement experiment in the owner's own test room. */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/admin/test-rooms/{roomId}/neuro-replacements")
@PreAuthorize("hasRole('OWNER')")
@SecurityRequirement(name = "Bearer")
public class NeuroReplacementPilotController {

    private final NeuroReplacementPilotService pilot;
    private final TestRoomOwnershipGuard rooms;
    private final TestRoomFeatureGuard features;

    private void check(HotHatUser owner, String roomId) {
        features.requireEnabled();
        rooms.requireOwnedTestRoom(owner, roomId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiResponse(responseCode = "202", description = "Создано долговечное задание с сохранёнными исходниками",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = NeuroJobResponseDTO.class)))
    public ResponseEntity<Map<String, Object>> start(
            @AuthenticationPrincipal HotHatUser owner,
            @Parameter(description = "Тестовая комната, принадлежащая владельцу")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Отдельное согласие на платную обработку видео и голоса")
            @RequestParam(name = "consented", defaultValue = "false") boolean consented,
            @Parameter(description = "Начало выбранного десятисекундного фрагмента в исходной записи")
            @RequestParam(name = "windowStartSeconds", defaultValue = "0") double windowStartSeconds,
            @RequestPart("video") MultipartFile video,
            @RequestPart("voice") MultipartFile voice) {
        check(owner, roomId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(pilot.start(roomId, owner.uid(), consented, video, voice, windowStartSeconds));
    }

    @GetMapping("/{jobId}")
    @ApiResponse(responseCode = "200", description = "Статус генерации и идентификаторы оплаченных этапов",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = NeuroJobResponseDTO.class)))
    public Map<String, Object> status(
            @AuthenticationPrincipal HotHatUser owner,
            @Parameter(description = "Тестовая комната, принадлежащая владельцу")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Постоянный ID задания Hot Hat")
            @PathVariable @NotBlank String jobId) {
        check(owner, roomId);
        return pilot.status(roomId, owner.uid(), jobId);
    }

    @GetMapping("/{jobId}/video")
    @ApiResponse(responseCode = "200", description = "Готовый AI MP4, доступен только владельцу",
            content = @Content(mediaType = "video/mp4",
                    schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> video(
            @AuthenticationPrincipal HotHatUser owner,
            @Parameter(description = "Тестовая комната, принадлежащая владельцу")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Постоянный ID задания Hot Hat")
            @PathVariable @NotBlank String jobId) {
        check(owner, roomId);
        byte[] bytes = pilot.video(roomId, owner.uid(), jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(bytes);
    }
}
