package ru.hothat.lobby.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.lobby.api.dto.JoinRankedRoomRequestDTO;
import ru.hothat.lobby.api.dto.OpenRankedTicketRequestDTO;
import ru.hothat.lobby.api.dto.RankedRoomEntryResponseDTO;
import ru.hothat.lobby.api.dto.RankedTicketResponseDTO;
import ru.hothat.lobby.usecase.JoinRankedRoomUseCase;
import ru.hothat.lobby.usecase.OpenRankedTicketUseCase;

/**
 * Вход рейтинговой пары в подбор.
 *
 * <p>Заменяет {@code portal matchmake} с {@code ranked=true}
 * ({@code portal.js:127}) и {@code portal ranked_join_room}. Пара — не два
 * игрока, а один участник подбора: она занимает слот команды целиком, и
 * разлучать напарников нельзя.
 *
 * <p>Обе операции доступны капитану — тому, кто начал предматчевую подготовку.
 * Проверка стоит внутри сценария, а не предикатом на классе, и причина
 * названа в {@code OpenRankedTicketUseCase}: ответ на «капитан ли ты» требует
 * чтения подготовки, которое движок и так делает первым шагом, а предикат
 * уровня класса читал бы её вторично на каждом тике опроса.
 *
 * <p>Читается рейтинговая заявка тем же адресом, что и быстрая:
 * {@code GET /api/v2/lobby/tickets/{ticketId}}. Различается их подача, а не
 * наблюдение за ними, и второй адрес чтения был бы вторым описанием одного
 * предмета.
 *
 * <p>Старые действия портала пока живы: фронтенд переедет отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/lobby/ranked")
@Tag(name = "Lobby · рейтинговый подбор", description = "Заявка рейтинговой пары")
@SecurityRequirement(name = "Bearer")
public class RankedTicketController {

    private final OpenRankedTicketUseCase openRankedTicket;
    private final JoinRankedRoomUseCase joinRankedRoom;

    @Operation(summary = "Поставить пару в подбор",
            description = "Требует пройденной предматчевой подготовки: связь проверена у обоих и оба "
                    + "нажали «готов». Пары сводятся по близости рейтинга.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Пара в подборе"),
            @ApiResponse(responseCode = "403", description = "PREFLIGHT_CAPTAIN_ONLY: подбор начинает тот, "
                    + "кто создал подготовку", content = @Content),
            @ApiResponse(responseCode = "409", description = "PREFLIGHT_REQUIRED: подготовка не пройдена; "
                    + "RANKED_TEAM_REQUIRED: нет подтверждённой команды", content = @Content)})
    @PostMapping("/tickets")
    public ResponseEntity<RankedTicketResponseDTO> open(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody OpenRankedTicketRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(openRankedTicket.run(user, request));
    }

    @Operation(summary = "Ввести пару в названную комнату",
            description = "Комната выбрана заранее, на подготовке. Сервер сверяет её с той, о которой "
                    + "договорилась пара, а также режим и дивизион.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Пара за столом"),
            @ApiResponse(responseCode = "403", description = "PREFLIGHT_CAPTAIN_ONLY: вводит пару капитан",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "PREFLIGHT_ROOM_MISMATCH: договаривались о "
                    + "другой комнате; RANKED_ROOM_UNAVAILABLE: комната закрыта или не рейтинговая; "
                    + "RANKED_ROOM_MODE_MISMATCH и RANKED_ROOM_DIVISION_MISMATCH: не тот режим или "
                    + "дивизион; ROOM_FULL: свободного слота команды нет", content = @Content)})
    @PostMapping("/rooms/{roomId}/entry")
    public ResponseEntity<RankedRoomEntryResponseDTO> enter(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Комната, о которой договорилась пара", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId
            String roomId,
            @Valid @RequestBody JoinRankedRoomRequestDTO request) {
        return ResponseEntity.ok(joinRankedRoom.run(user, roomId, request));
    }
}
