package ru.hothat.rating.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import ru.hothat.common.validation.RoomId;

/**
 * Просьба зачесть результат рейтинговой партии.
 *
 * <p>Номера партии в запросе нет намеренно, хотя зачёт идемпотентен по паре
 * «комната + номер партии»: номер берёт из комнаты сервер. Приняв его от
 * клиента, мы позволили бы зачесть чужую или уже сыгранную партию — счёт-то
 * всё равно читается из комнаты.
 *
 * <p>Счёт, состав команд и признак технического завершения тоже не передаются
 * по той же причине: их знает сервер.
 */
@Schema(description = "Запрос на зачёт результата партии")
public record SubmitMatchResultRequestDTO(

        @Schema(description = "Комната, в которой закончилась рейтинговая партия",
                example = "hat-0f3a9c1d7b2e5480", pattern = "^hat-[a-f0-9]{16}$")
        @NotBlank(message = "Нужна игровая комната.")
        @RoomId
        String roomId) {
}
