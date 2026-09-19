package ru.hothat.room.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.config.HotHatUser;
import ru.hothat.room.api.dto.RoomInviteStatusQueryDTO;
import ru.hothat.room.api.dto.RoomInviteStatusesResponseDTO;
import ru.hothat.room.usecase.ReadRoomInviteStatusesUseCase;

/**
 * Состояние выданных приглашений в комнаты.
 *
 * <p>Заменяет {@code room_invite_statuses} из портала. Адрес принимает до
 * шестидесяти идентификаторов сразу ({@code realtime-social.js:71}): страница
 * переписки держит на экране столько карточек и опрашивает их разом.
 *
 * <p>Отдельный класс от принятия приглашения — не по предмету, а по правам.
 * Предикат одного получателя к списку из шестидесяти чужих идентификаторов
 * неприменим в принципе, поэтому чтение живёт под общим правом «любой
 * вошедший», а видимость режется построчно внутри проекции. Принятие —
 * под правом адресата, и у него свой класс.
 *
 * <p>Литеральный сегмент {@code invites} при разборе пути побеждает образец
 * {@code {roomId}}, так что снимок комнаты сюда не попадает.
 *
 * <p>Старый путь пока жив: фронтенд переедет на этот маршрут отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/room/invites")
@PreAuthorize("hasRole('USER')")
@Tag(name = "Room · приглашения", description = "Состояние выданных приглашений в комнаты")
@SecurityRequirement(name = "Bearer")
public class RoomInviteStatusController {

    private final ReadRoomInviteStatusesUseCase readInviteStatuses;

    @Operation(summary = "Показать состояние приглашений",
            description = "Построчные состояния в том же порядке, в каком спрашивали. Чужое "
                    + "приглашение отвечает forbidden и не называет ни комнаты, ни отправителя: "
                    + "по номеру нельзя узнать, чьё оно. Приглашение живо, пока комната в наборе и "
                    + "её поколение партии совпадает с зафиксированным при отправке.")
    @ApiResponse(responseCode = "200", description = "Состояния приглашений")
    @GetMapping
    public ResponseEntity<RoomInviteStatusesResponseDTO> statuses(
            @AuthenticationPrincipal HotHatUser user,
            @Valid @ParameterObject RoomInviteStatusQueryDTO query) {
        return ResponseEntity.ok(readInviteStatuses.run(user, query));
    }
}
