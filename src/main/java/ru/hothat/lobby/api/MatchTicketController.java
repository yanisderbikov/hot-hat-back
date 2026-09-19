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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.lobby.api.dto.MatchTicketResponseDTO;
import ru.hothat.lobby.api.dto.MatchTicketStatusResponseDTO;
import ru.hothat.lobby.api.dto.OpenCasualTicketRequestDTO;
import ru.hothat.lobby.usecase.CancelMatchTicketUseCase;
import ru.hothat.lobby.usecase.GetMyMatchTicketUseCase;
import ru.hothat.lobby.usecase.OpenCasualTicketUseCase;

/**
 * Заявка игрока на подбор комнаты.
 *
 * <p>Заменяет {@code portal matchmake} с {@code ranked=false} и
 * {@code portal cancel_matchmake}. Главное изменение — не в адресах: подбор
 * перестаёт быть побочным эффектом опроса. Сегодня {@code home/home.js:104}
 * зовёт {@code matchmake} каждые три секунды, и каждый вызов заново перебирает
 * комнаты фазы {@code setup} — из-за чего очередной опрос может увести игрока
 * в другую комнату, а первая ждёт его до конца срока. Здесь заявка подаётся
 * один раз, получает собственный адрес, а опрос становится чтением.
 *
 * <p>У заявки появился и владелец: сегодня отмена принимает любой
 * идентификатор комнаты и владение не проверяет вовсе.
 *
 * <p>Только для вошедших: подбор гостю закрыт.
 *
 * <p>Старые действия портала пока живы: фронтенд переедет отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/lobby/tickets")
@Tag(name = "Lobby · заявки", description = "Заявка на подбор быстрой игры")
@SecurityRequirement(name = "Bearer")
public class MatchTicketController {

    private final OpenCasualTicketUseCase openCasualTicket;
    private final GetMyMatchTicketUseCase getMyMatchTicket;
    private final CancelMatchTicketUseCase cancelMatchTicket;

    @Operation(summary = "Подать заявку на быструю игру",
            description = "Один вызов на весь поиск: дальше заявку читают по её адресу.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Заявка подана"),
            @ApiResponse(responseCode = "402", description = "SABOTAGE_LIMIT_REACHED: бесплатные партии "
                    + "с диверсиями кончились", content = @Content),
            @ApiResponse(responseCode = "409", description = "QUICK_LANGUAGE_INVALID: быстрая игра идёт "
                    + "на языке своего дивизиона либо на общем английском; DEFAULT_LOADOUT_REQUIRED: "
                    + "обойма мемов не заряжена", content = @Content)})
    @PostMapping("/casual")
    public ResponseEntity<MatchTicketResponseDTO> openCasual(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @RequestBody OpenCasualTicketRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(openCasualTicket.run(user, request));
    }

    @Operation(summary = "Узнать, что с заявкой",
            description = "Чистое чтение: опрос больше не пересобирает подбор и не переселяет игрока "
                    + "между комнатами.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Состояние заявки"),
            @ApiResponse(responseCode = "404", description = "MATCH_TICKET_NOT_FOUND: заявки нет или она "
                    + "чужая — по идентификатору эти случаи неразличимы", content = @Content)})
    @GetMapping("/{ticketId}")
    public ResponseEntity<MatchTicketStatusResponseDTO> status(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Идентификатор заявки, выданный при её подаче",
                    example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId
            String ticketId) {
        return ResponseEntity.ok(getMyMatchTicket.run(user, ticketId));
    }

    @Operation(summary = "Снять заявку",
            description = "Рейтинговая заявка снимается вместе с напарником: пара занимает слот "
                    + "команды целиком.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Заявки больше нет; её и не было — тот же ответ"),
            @ApiResponse(responseCode = "404", description = "MATCH_TICKET_NOT_FOUND: заявка чужая",
                    content = @Content)})
    @DeleteMapping("/{ticketId}")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Идентификатор своей заявки", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId
            String ticketId) {
        cancelMatchTicket.run(user, ticketId);
        return ResponseEntity.noContent().build();
    }
}
