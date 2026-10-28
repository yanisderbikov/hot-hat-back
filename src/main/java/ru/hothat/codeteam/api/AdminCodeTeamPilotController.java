package ru.hothat.codeteam.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.codeteam.api.dto.CodeTeamPilotRequestDTO;
import ru.hothat.codeteam.api.dto.CodeTeamPilotResponseDTO;
import ru.hothat.codeteam.usecase.RunCodeTeamPilotUseCase;

/** Закрытая ручная проверка; реальные матчи сюда пока не подключены. */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/admin/code-team/pilot")
@PreAuthorize("hasRole('OWNER')")
public class AdminCodeTeamPilotController {

    private final RunCodeTeamPilotUseCase runPilot;

    @PostMapping
    public ResponseEntity<CodeTeamPilotResponseDTO> run(@Valid @RequestBody CodeTeamPilotRequestDTO request) {
        return ResponseEntity.ok(runPilot.run(request));
    }
}
