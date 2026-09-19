package ru.hothat.game.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.game.api.dto.ServerClockResponseDTO;
import ru.hothat.game.usecase.ReadServerClockUseCase;

/**
 * Серверные часы для игровых сроков.
 *
 * <p>Заменяет калибровку записью отметки в свою строку и немедленным чтением
 * её обратно — то есть запись в базу ради вопроса «который час».
 *
 * <p>Без токена: часы спрашивает страница записи, которую открывает браузер
 * Egress, и главная страница до входа. Пустой {@code @SecurityRequirements}
 * снимает глобальный замок в спецификации, а право на маршрут объявлено своей
 * цепочкой безопасности области.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/game/clock")
@Tag(name = "Game · часы", description = "Серверное время игровых сроков")
@SecurityRequirements
public class ServerClockController {

    private final ReadServerClockUseCase readClock;

    @Operation(summary = "Прочитать серверное время",
            description = "Все сроки партии — дедлайн хода, конец голосования, перезарядка — заданы "
                    + "в этом времени. По нему клиент вычисляет поправку к своим часам.")
    @ApiResponse(responseCode = "200", description = "Серверное время")
    @GetMapping
    @PreAuthorize("permitAll()")
    public ResponseEntity<ServerClockResponseDTO> clock() {
        return ResponseEntity.ok(readClock.run());
    }
}
