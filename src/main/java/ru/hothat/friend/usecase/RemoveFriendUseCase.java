package ru.hothat.friend.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.friend.spi.FriendshipEvents;
import ru.hothat.friend.store.FriendshipStore;
import ru.hothat.team.spi.RankedTeamPort;

/**
 * Убрать игрока из друзей.
 *
 * <p>Ответа нет: старый {@code {ok:true}} не нёс сведений, которых не было бы
 * в запросе. Напарника по рейтинговой команде удалить нельзя — это 409
 * {@code TEAMMATE_MUST_REMAIN_FRIEND}, а не тихий отказ.
 *
 * <p>Состав команды читается из старых таблиц намеренно: область {@code team}
 * ещё не переехала, и {@code v2.ranked_team_member} пока пуста. Когда команда
 * переедет, вопрос уйдёт за её {@code TeamMembershipPort}, а здесь изменится
 * одна строка.
 *
 * <p>О разрыве извещаются соседи: переписка держит свои строки участия и без
 * этого сигнала оставляла бы бывшего друга в списке переписок вместе с
 * непрочитанным, которое нечем погасить — открыть такую переписку уже нельзя,
 * {@link ru.hothat.chat.usecase.ChatPeerGuard} отвечает 403. Прежняя служба
 * такого не показывала: она собирала список обходом связей дружбы, и с
 * разрывом связи переписка пропадала сама.
 */
@Service
@RequiredArgsConstructor
public class RemoveFriendUseCase {

    private final FriendshipStore friendships;
    /**
     * Напарника из друзей не выкинуть, и «кто мой напарник» спрашивается у
     * области команды: колонка в карточке игрока перестала быть источником
     * правды, когда у состава остался один хозяин.
     */
    private final RankedTeamPort rankedTeams;
    private final ApplicationEventPublisher events;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void run(HotHatUser user, String friendUid) {
        String target = friendUid == null ? "" : friendUid;
        RankedTeamPort.TeamSummary team = rankedTeams.teamOf(user.uid()).orElse(null);
        if (team != null && team.memberUids().contains(target)) {
            throw ApiException.of("TEAMMATE_MUST_REMAIN_FRIEND", 409);
        }
        if (friendships.unlink(user.uid(), target)) {
            // В той же транзакции: разрыв без уборки у соседа — то самое
            // половинчатое состояние, ради которого событие и заведено.
            events.publishEvent(new FriendshipEvents.Unlinked(user.uid(), target));
        }
    }
}
