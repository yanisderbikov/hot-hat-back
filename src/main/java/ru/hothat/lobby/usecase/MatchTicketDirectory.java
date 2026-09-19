package ru.hothat.lobby.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.config.ApiException;
import ru.hothat.lobby.api.dto.MatchTicketState;
import ru.hothat.lobby.api.dto.MatchTicketView;
import ru.hothat.room.spi.RoomTicketPort;
import ru.hothat.team.api.dto.GameMode;


/**
 * Чья заявка и что с ней сейчас.
 *
 * <p>Один и тот же вопрос задают чтение заявки и её отмена, поэтому ответ на
 * него живёт в одном месте: два разошедшихся ответа означали бы, что отменить
 * можно то, чего не видно.
 *
 * <p>Прежняя отмена владение не проверяла вовсе: она вычёркивала вызывающего
 * из любой названной комнаты и закрывала её, если та осталась пустой, — то
 * есть посторонний мог закрыть пустующую служебную комнату подбора. Здесь
 * право названо: заявка твоя, если твой uid стоит в списке заявок комнаты.
 *
 * <p>Чужая и несуществующая заявка отвечают одинаково: по идентификатору
 * нельзя узнать, идёт ли в этой комнате подбор.
 *
 * <p>Состояние вычисляется чтением и ничего не пишет. Это и есть причина, по
 * которой опрос перестал быть вызовом подбора: раньше каждый опрос заново
 * перебирал комнаты фазы {@code setup} и мог переселить игрока в другую
 * комнату между двумя тиками таймера.
 *
 * <p>Комната читается через порт своей области, а не её таблицу: у строки
 * комнаты один хозяин, и лобби спрашивает его, а не заглядывает через плечо.
 */
@Component
@RequiredArgsConstructor
public class MatchTicketDirectory {

    private final RoomTicketPort rooms;

    /** Заявка вызывающего; чужой или несуществующей — 404. */
    public MatchTicketView requireMine(String ticketId, String viewerUid) {
        RoomTicketPort.TicketRoom room = rooms.ticketRoom(ticketId)
                .orElseThrow(MatchTicketDirectory::notFound);
        requireHolder(room, viewerUid);
        return view(room, System.currentTimeMillis());
    }

    /**
     * Стоит ли за вызывающим такая заявка.
     *
     * @return {@code false} — комнаты нет вовсе, отменять нечего
     * @throws ApiException 404, если комната есть, но заявка в ней чужая
     */
    public boolean isMine(String ticketId, String viewerUid) {
        RoomTicketPort.TicketRoom room = rooms.ticketRoom(ticketId).orElse(null);
        if (room == null) {
            return false;
        }
        requireHolder(room, viewerUid);
        return true;
    }

    private static void requireHolder(RoomTicketPort.TicketRoom room, String viewerUid) {
        if (!room.applicantUids().contains(viewerUid)) {
            throw notFound();
        }
    }

    private static MatchTicketView view(RoomTicketPort.TicketRoom room, long nowMs) {
        int joined = room.applicantUids().size();
        return new MatchTicketView(
                room.roomId(),
                state(room, joined, room.maxPlayers(), nowMs),
                room.roomId(),
                blankToNull(room.hostUid()),
                joined,
                room.maxPlayers(),
                room.deadlineMs(),
                GameMode.fromWire(room.gameMode()),
                room.matchmakingLanguage());
    }

    /**
     * Порядок проверок именно такой.
     *
     * <p>Сначала «всё кончилось»: комната, закрытая по истечении срока или
     * рукой администратора, не должна звать игрока внутрь, даже если состав в
     * ней когда-то собрался. Потом «состав собран» — причём и по флагу, и по
     * числу собравшихся, ровно как считает сам подбор
     * так же, как считает сама подача: флаг ставится следующей записью, а
     * читатель обязан увидеть готовность сразу. И только потом истёкший срок:
     * его подача дописывает в комнату при следующем обращении, а чистое
     * чтение обязано назвать срок истёкшим само.
     */
    private static MatchTicketState state(RoomTicketPort.TicketRoom room, int joined,
                                          int maxPlayers, long nowMs) {
        if (room.expired() || room.closed()) {
            return MatchTicketState.EXPIRED;
        }
        if (room.ready() || joined >= maxPlayers) {
            return MatchTicketState.MATCHED;
        }
        if (room.deadlineMs() > 0 && nowMs > room.deadlineMs()) {
            return MatchTicketState.EXPIRED;
        }
        return MatchTicketState.SEARCHING;
    }

    private static ApiException notFound() {
        return ApiException.of("MATCH_TICKET_NOT_FOUND", 404);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
