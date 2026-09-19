package ru.hothat.realtime.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.hothat.chat.api.dto.ChatMessageView;

import java.util.List;

/**
 * Окно переписки — то, что канал показывает собеседнику прямо сейчас.
 *
 * <p>Общая проекция обоих кадров канала: приветственного и обновляющего.
 * Форма описана один раз, кадры различаются только именем.
 *
 * <p>Сообщение здесь — тот же {@link ChatMessageView}, что отдаёт
 * {@code GET /api/v2/chat/{peerUid}/messages}, и это главное свойство канала.
 * Раньше живая подписка присылала сырой документ переписки
 * ({@code type}/{@code attachment}/{@code roomInviteId}), а история приезжала
 * по HTTP в другой форме, и один и тот же список сообщений рисовался двумя
 * разными ветками кода. Одна проекция на оба источника делает такое
 * расхождение невыразимым.
 *
 * <p>Снимок целиком, а не дельта: переписка короткая (последние тридцать
 * сообщений), а перечитывание надёжнее склейки приращений и не расходится
 * с базой.
 */
@Schema(description = "Последние сообщения переписки")
public record DirectChatWindowView(

        @Schema(description = "Собеседник", example = "2c9d4e7aP3lM5nB8vC1xZ0qW6eR4")
        String peerUid,

        @Schema(description = "Имя собеседника; «Игрок», если карточка не найдена", example = "Petya")
        String peerNickname,

        @Schema(description = "Сообщения от старых к новым — в порядке показа")
        List<ChatMessageView> items,

        @Schema(description = "Курсор следующей страницы. Всегда null, как и у страницы HTTP: "
                + "переходный движок умеет отдать только последние сообщения. Поле сохранено "
                + "именно поэтому — окно канала и страница HTTP обязаны совпадать полем в поле, "
                + "иначе один и тот же список снова придётся разбирать двумя ветками",
                example = "null", nullable = true)
        String nextCursor,

        @Schema(description = "Сколько последних сообщений держит окно", example = "30", type = "integer")
        int limit) {
}
