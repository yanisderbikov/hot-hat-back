package ru.hothat.team.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import ru.hothat.common.validation.RoomId;

/**
 * Начать проверку готовности пары.
 *
 * <p>Двадцать одного расплющенного ключа старого тела здесь нет: снимок
 * префлайта целиком составляет сервер, а от клиента ему нужно ровно три вещи —
 * зачем, в каком режиме и, если целятся в комнату, в какую.
 *
 * <p>Новая проверка полностью заменяет прежнюю: флаги связи и готовности не
 * переносятся. Так было и раньше, но узнать об этом можно было только из кода.
 */
@Schema(description = "Запрос на проверку готовности пары")
public record StartPreflightRequestDTO(

        @Schema(description = "Зачем проверка: встать в очередь подбора или зайти в конкретную комнату")
        @NotNull(message = "Нужно сказать, зачем проверка.")
        PreflightIntent intent,

        @Schema(description = "Режим, в котором пара собирается играть")
        @NotNull(message = "Нужен режим партии.")
        GameMode gameMode,

        @Schema(description = "Комната, в которую целится пара. Обязательна при intent=room "
                + "и не принимается при intent=quick",
                example = "hat-0f3a9c1d7b2e5480", pattern = "^hat-[a-f0-9]{16}$", nullable = true)
        @RoomId
        String roomId) {

    /**
     * Связка двух полей, которую нельзя выразить на одном из них.
     *
     * <p>Раньше несогласованность проходила молча: при {@code intent=room}
     * без комнаты движок подставлял пустую строку, шёл с ней в хранилище,
     * не находил комнату и отвечал {@code RANKED_ROOM_UNAVAILABLE} —
     * «комната занята» вместо «вы её не назвали». Обратный случай — комната
     * при быстрой игре — просто выбрасывался, и пара уезжала к случайным
     * соперникам, будучи уверенной, что идёт к друзьям.
     *
     * <p>Наружу этот признак не показывается: он не поле запроса, а проверка,
     * и в схеме ему места нет.
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Комнату нужно назвать при intent=room и не передавать при intent=quick.")
    public boolean isRoomMatchingIntent() {
        // Замысла может не быть вовсе: его отсутствие называет @NotNull, а
        // связка полей при этом ничего сказать не может и молчит — иначе одна
        // пустота дала бы две жалобы, вторая из них про поле, которого человек
        // и не заполнял.
        if (intent == null) {
            return true;
        }
        boolean named = roomId != null && !roomId.isBlank();
        return (intent == PreflightIntent.ROOM) == named;
    }
}
