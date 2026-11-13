package ru.hothat.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;

import java.util.Map;

/** Only the product OWNER can trigger paid off-turn chat. */
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v2/room/{roomId}/ai-players/{botUid}/smalltalk")
@PreAuthorize("hasRole('OWNER')")
public class AiVoiceSmallTalkController {
    private final AiVoiceSmallTalkService service;

    @GetMapping
    public ResponseEntity<Map<String,Object>> status(
            @AuthenticationPrincipal HotHatUser user,
            @PathVariable @RoomId String roomId,@PathVariable String botUid){
        return ResponseEntity.ok(service.status(user,roomId,botUid));
    }
    @PostMapping
    public ResponseEntity<Map<String,Object>> start(
            @AuthenticationPrincipal HotHatUser user,
            @PathVariable @RoomId String roomId,@PathVariable String botUid){
        return ResponseEntity.ok(service.start(user,roomId,botUid));
    }
    @DeleteMapping
    public ResponseEntity<Map<String,Object>> stop(
            @AuthenticationPrincipal HotHatUser user,
            @PathVariable @RoomId String roomId,@PathVariable String botUid){
        return ResponseEntity.ok(service.stop(user,roomId,botUid));
    }
}
