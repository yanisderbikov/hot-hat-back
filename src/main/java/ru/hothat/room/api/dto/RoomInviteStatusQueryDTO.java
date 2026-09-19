package ru.hothat.room.api.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * По каким приглашениям спрашивают состояние.
 *
 * <p>Список, а не по одному: страница переписки держит на экране до шестидесяти
 * карточек приглашений и опрашивает их разом ({@code realtime-social.js:71}).
 * Шестьдесят отдельных запросов на каждое открытие чата — это и есть причина,
 * по которой адрес принимает набор.
 *
 * <p>Право здесь общее — «любой вошедший», — и видимость режется построчно
 * внутри проекции: предикат уровня класса к списку из шестидесяти чужих
 * идентификаторов неприменим в принципе. Чужое приглашение отвечает
 * {@code forbidden} и не называет ни комнаты, ни отправителя.
 */
@Schema(description = "Приглашения, состояние которых спрашивают")
public record RoomInviteStatusQueryDTO(

        @Parameter(description = "Идентификаторы приглашений; не больше шестидесяти за раз",
                example = "inv-4b2c8e1a9f6d3057")
        @NotEmpty(message = "Не названо ни одного приглашения.")
        @Size(max = 60, message = "За раз спрашиваем не больше шестидесяти приглашений.")
        List<String> inviteIds) {

    /** Столько же принимает старый адрес: предел живёт в одном месте. */
    public static final int MAX_IDS = 60;
}
