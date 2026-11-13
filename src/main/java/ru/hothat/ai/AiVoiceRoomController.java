package ru.hothat.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;

import java.util.Map;

/**
 * Room host/owner controls one GPT-Live seat alongside a real teammate.
 * No model inference is performed by these setup endpoints.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/room/{roomId}/ai-players")
@PreAuthorize("hasAnyRole('USER','OWNER','ADMIN')")
public class AiVoiceRoomController {
    private final AiVoiceSeatService seats;

    public record AddRequest(@NotBlank
         @Pattern(regexp="^[A-Za-z0-9_-]{1,80}$") String teamId) {}

    @PostMapping
    public ResponseEntity<Map<String,Object>> add(
            @AuthenticationPrincipal HotHatUser user,
            @PathVariable @RoomId String roomId,
            @Valid @RequestBody AddRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(seats.add(user,roomId,body.teamId()));
    }

    @DeleteMapping("/{aiUid}")
    public ResponseEntity<Map<String,Object>> remove(
            @AuthenticationPrincipal HotHatUser user,
            @PathVariable @RoomId String roomId,
            @PathVariable String aiUid) {
        return ResponseEntity.ok(seats.remove(user,roomId,aiUid));
    }
}
