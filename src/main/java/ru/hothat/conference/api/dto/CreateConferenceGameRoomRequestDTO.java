package ru.hothat.conference.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import ru.hothat.conference.domain.ConferenceRules;

import java.util.List;

/**
 * Кого сажать в игровую комнату.
 *
 * <p>Список называет хозяин по тому, кого он видит в звонке: сервер знает
 * состав видео-чата, но не знает, кто из состава сейчас действительно на
 * связи. Чужих в списке сервер отбросит, себя хозяин получит в любом случае.
 * Форму элементов здесь не проверяют: строка, не совпавшая ни с одним
 * участником, отбрасывается так же, как чужой игрок.
 */
@Schema(description = "Заявка на игровую комнату из видео-чата")
public record CreateConferenceGameRoomRequestDTO(

        @Schema(description = "Кто сядет за стол: участники, которые сейчас в звонке; пусто — только хозяин",
                example = "[\"Qk3xZaTb9mNpR2sVuWyA1cEfGhJk\"]", nullable = true)
        @Size(max = ConferenceRules.MAX_PARTICIPANTS, message = "В звонке не больше шестнадцати человек.")
        List<String> participantUids) {

    public List<String> participantUidsOrEmpty() {
        return participantUids == null ? List.of() : participantUids;
    }
}
