package ru.hothat.room.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.common.validation.RoomId;
import ru.hothat.config.HotHatUser;
import ru.hothat.room.api.dto.CreateRoomTeamRequestDTO;
import ru.hothat.room.api.dto.RoomTeamDrawResponseDTO;
import ru.hothat.room.api.dto.RoomTeamMembershipResponseDTO;
import ru.hothat.room.api.dto.RoomTeamResponseDTO;
import ru.hothat.room.usecase.CreateRoomTeamUseCase;
import ru.hothat.room.usecase.DeleteRoomTeamUseCase;
import ru.hothat.room.usecase.DrawRoomTeamsUseCase;
import ru.hothat.room.usecase.JoinRoomTeamUseCase;
import ru.hothat.room.usecase.LeaveRoomTeamUseCase;

/**
 * Составы команд внутри комнаты.
 *
 * <p>Заменяет пять клиентских транзакций: {@code addTeam()},
 * {@code deleteTeam()}, {@code joinTeam()}, {@code leaveTeam()}
 * ({@code app-core.js:12511-12760}) и серверную жеребьёвку
 * {@code randomize_teams}. Первые четыре читали комнату, свою строку, строку
 * команды и строку каждого её участника — до четырёх чтений на одно нажатие, —
 * и всё равно оставались уговором: составы писались прямой записью документа.
 *
 * <p>Уровень прав у класса один: любой участник комнаты. Составы в комнате
 * незнакомых людей собирают все вместе, и жеребьёвка намеренно не хозяйская
 * ({@code app-core.js:11843}) — право пересадить всех не должно принадлежать
 * одному.
 *
 * <p>Отсюда и {@code members/me} в путях: посадить в команду чужого нельзя, и
 * адреса для этого не существует.
 *
 * <p>Старые пути пока живы: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/room/{roomId}/teams")
@PreAuthorize("hasRole('USER')")
@Tag(name = "Room · составы", description = "Команды комнаты и рассадка по ним")
@SecurityRequirement(name = "Bearer")
public class RoomTeamController {

    private final CreateRoomTeamUseCase createTeam;
    private final DeleteRoomTeamUseCase deleteTeam;
    private final JoinRoomTeamUseCase joinTeam;
    private final LeaveRoomTeamUseCase leaveTeam;
    private final DrawRoomTeamsUseCase drawTeams;

    @Operation(summary = "Создать команду",
            description = "Команд не больше пяти, названия в одной комнате не повторяются. Место в "
                    + "очереди ходов назначает сервер по порядку создания.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Команда создана"),
            @ApiResponse(responseCode = "409", description = "ROOM_SETUP_ONLY: партия уже началась; "
                    + "TEAM_LIMIT_REACHED: команд уже пять; ROOM_TEAM_NAME_TAKEN: название занято",
                    content = @Content)})
    @PostMapping
    public ResponseEntity<RoomTeamResponseDTO> create(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Valid @RequestBody CreateRoomTeamRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createTeam.run(user, roomId, request));
    }

    @Operation(summary = "Распустить команду",
            description = "Только пустую: живые игроки сначала выходят сами. Мёртвым место не "
                    + "держат — вычищать их отдельной кнопкой было бы лишним шагом.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Команда распущена"),
            @ApiResponse(responseCode = "404", description = "ROOM_TEAM_NOT_FOUND: команды нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "ROOM_SETUP_ONLY: партия уже началась; "
                    + "TEAM_NOT_EMPTY: в команде есть живые игроки", content = @Content)})
    @DeleteMapping("/{teamId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Команда внутри комнаты", example = "team-9f3a2b1c7d8e0f4a")
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,80}$", message = "Некорректная команда.")
            String teamId) {
        deleteTeam.run(user, roomId, teamId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Сесть в команду",
            description = "В команде двое. Пересадка снимает игрока со старого места и сажает на "
                    + "новое одной транзакцией; повторное нажатие на свою же команду ничего не "
                    + "меняет и не отвечает отказом.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Место в команде занято"),
            @ApiResponse(responseCode = "404", description = "ROOM_TEAM_NOT_FOUND: команды нет",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "ROOM_SETUP_ONLY: партия уже началась; "
                    + "TEAM_IS_FULL: в команде уже двое", content = @Content)})
    @PutMapping("/{teamId}/members/me")
    public ResponseEntity<RoomTeamMembershipResponseDTO> join(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Команда внутри комнаты", example = "team-9f3a2b1c7d8e0f4a")
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,80}$", message = "Некорректная команда.")
            String teamId) {
        return ResponseEntity.ok(joinTeam.run(user, roomId, teamId));
    }

    @Operation(summary = "Выйти из команды",
            description = "Остаётесь в комнате без команды. Выход из команды, в которой вы не "
                    + "состоите, ничего не меняет: повтор по разорванной связи не должен отвечать "
                    + "отказом.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Вышли из команды"),
            @ApiResponse(responseCode = "409", description = "ROOM_SETUP_ONLY: составы заморожены "
                    + "до конца партии", content = @Content)})
    @DeleteMapping("/{teamId}/members/me")
    public ResponseEntity<Void> leave(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId,
            @Parameter(description = "Команда внутри комнаты", example = "team-9f3a2b1c7d8e0f4a")
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,80}$", message = "Некорректная команда.")
            String teamId) {
        leaveTeam.run(user, roomId, teamId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Перемешать составы",
            description = "Полностью новая жеребьёвка, включая уже рассаженных: половинчатая "
                    + "выглядела бы как поломка кнопки. Кнопка намеренно не хозяйская. Кому не "
                    + "хватило места за столом, остаются в комнате без команды.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Составы перемешаны"),
            @ApiResponse(responseCode = "409", description = "ROOM_SETUP_ONLY: партия уже началась; "
                    + "TEAMS_NOT_READY: команд меньше двух или больше пяти", content = @Content),
            @ApiResponse(responseCode = "422", description = "NO_ACTIVE_PLAYERS: живых игроков нет",
                    content = @Content)})
    @PostMapping("/draw")
    public ResponseEntity<RoomTeamDrawResponseDTO> draw(
            @AuthenticationPrincipal HotHatUser user,
            @Parameter(description = "Игровая комната", example = "hat-0f3a9c1d7b2e5480")
            @PathVariable @RoomId String roomId) {
        return ResponseEntity.ok(drawTeams.run(user, roomId));
    }
}
