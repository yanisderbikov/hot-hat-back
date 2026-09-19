package ru.hothat.lobby.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.room.spi.RoomTicketPort;
import ru.hothat.team.spi.RankedTeamPort;

import java.util.List;

/**
 * Снять заявку на подбор.
 *
 * <p>Заменяет {@code portal cancel_matchmake}. Отличие одно и важное: у
 * заявки появился владелец. Сегодня отмена принимает любой идентификатор
 * комнаты и вычёркивает вызывающего оттуда, а комнату, оставшуюся пустой,
 * закрывает — то есть посторонний может закрыть пустующую служебную комнату
 * подбора.
 *
 * <p>Рейтинговая заявка снимается вместе с напарником: пара занимает слот
 * команды целиком, и оставить в комнате половину пары значило бы держать
 * место, на которое всё равно никого не посадить.
 *
 * <p>Заявки нет вовсе — не ошибка: отменять нечего, ответ тот же. Отмену зовут
 * из обработчика закрытия вкладки, и заставлять его разбирать отказ было бы
 * жестоко.
 */
@Service
@RequiredArgsConstructor
public class CancelMatchTicketUseCase {

    private final MatchTicketDirectory tickets;
    private final RoomTicketPort rooms;
    /**
     * Команду спрашиваем у её области: карточка игрока про напарника больше
     * не знает, а разлучать пару нельзя ни при входе, ни при выходе.
     */
    private final RankedTeamPort rankedTeams;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void run(HotHatUser user, String ticketId) {
        if (!tickets.isMine(ticketId, user.uid())) {
            return;
        }
        RankedTeamPort.TeamSummary team = rankedTeams.teamOf(user.uid()).orElse(null);
        rooms.withdraw(ticketId,
                team == null ? List.of(user.uid()) : team.memberUids(),
                team == null ? null : team.teamId());
    }
}
