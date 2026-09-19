package ru.hothat.game.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.api.dto.MatchDepartureResponseDTO;
import ru.hothat.game.api.dto.MatchDisconnectionResponseDTO;
import ru.hothat.game.usecase.ReportDepartureUseCase;
import ru.hothat.game.usecase.ReportDisconnectionUseCase;

/**
 * Игрок сообщает, что его больше нет.
 *
 * <p>Два адреса вместо одного действия {@code sync_game_presence} с причиной в
 * теле, потому что права у них одинаковые, а последствия — разные. Уход
 * (закрытая вкладка, потеря соединения с видеосвязью) только гасит отметку и
 * ставит паузу. Разрыв связи — единственная ветка, которой позволено закрыть
 * комнату, когда из обычной партии разошлись все.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/game/{roomId}/players/me")
@Tag(name = "Game · уход", description = "Уход игрока и разрыв связи")
@SecurityRequirement(name = "Bearer")
@PreAuthorize("@gameAuthz.isMember(#roomId)")
public class MatchDepartureController {

    private final ReportDepartureUseCase reportDeparture;
    private final ReportDisconnectionUseCase reportDisconnection;

    @Operation(summary = "Сообщить об уходе",
            description = "Гасит отметку присутствия немедленно, до опроса видеосвязи: её список "
                    + "отдаёт ушедшего ещё несколько секунд. Комнату не закрывает никогда.")
    @ApiResponse(responseCode = "200", description = "Отметка об уходе принята")
    @PostMapping("/departure")
    public ResponseEntity<MatchDepartureResponseDTO> departure(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId) {
        return ResponseEntity.ok(reportDeparture.run(user, roomId));
    }

    @Operation(summary = "Сообщить о разрыве связи",
            description = "Сверяет состав с видеосвязью. Если из обычной партии исчезли разом все, "
                    + "комната закрывается: доигрывать её некому. Рейтинговая партия вместо этого "
                    + "заканчивается техническим завершением.")
    @ApiResponse(responseCode = "200", description = "Итог доклада")
    @PostMapping("/disconnection")
    public ResponseEntity<MatchDisconnectionResponseDTO> disconnection(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната партии") @PathVariable @RoomId String roomId) {
        return ResponseEntity.ok(reportDisconnection.run(user, roomId));
    }
}
