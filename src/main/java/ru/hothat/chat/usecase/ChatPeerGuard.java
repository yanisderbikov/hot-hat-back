package ru.hothat.chat.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.friend.spi.FriendshipPort;
import ru.hothat.team.spi.RankedTeamPort;

/**
 * Переписываться можно с другом и с напарником по рейтинговой команде.
 *
 * <p>Правило одно на все пять адресов переписки, поэтому и живёт оно в одном
 * месте: иначе «а напарник тоже может» пришлось бы помнить в каждом сценарии,
 * и один из них рано или поздно про это забыл бы.
 *
 * <p>Про дружбу спрашивается read-порт соседней области — своих таблиц дружбы
 * у переписки нет и быть не должно. Про команду читаются старые таблицы:
 * область {@code team} ещё не переехала, и {@code v2.ranked_team_member} пока
 * пуста. Когда переедет, вопрос уйдёт за её {@code TeamMembershipPort}, а
 * здесь изменится один вызов.
 *
 * <p>Профиль читается, а не создаётся: старый движок звал {@code ensureProfile}
 * и тем самым писал в базу на каждом чтении переписки. У игрока без карточки
 * команды всё равно нет, а значит и ответ тот же.
 */
@Component
@RequiredArgsConstructor
public class ChatPeerGuard {

    private final FriendshipPort friendships;
    /**
     * Напарник — это участник моей команды, и спрашивается он у её области.
     * Раньше ответ собирался из колонки в карточке игрока и списка внутри
     * команды: два места на один факт, и при расхождении пара теряла право
     * на собственную переписку.
     */
    private final RankedTeamPort rankedTeams;

    /**
     * Отказ приходит кодом {@code FRIEND_REQUIRED}: 403 — «не друг и не
     * напарник», 409 — «сам себе». Второй статус достался от старой службы
     * и здесь не переписан: менять его, пока по тому же коду отвечает живой
     * {@code /api/portal}, значило бы развести два ответа на одну ошибку.
     *
     * @return uid собеседника в том виде, в каком его понимает хранилище
     */
    public String requirePeer(HotHatUser user, String peerUid) {
        return requirePeer(user.uid(), peerUid);
    }

    /**
     * То же правило, но по одному идентификатору.
     *
     * <p>Существует ради командного порта: приглашение в комнату заводит
     * соседняя область, и требовать от неё оболочку учётки значило бы тащить
     * туда предмет, к вопросу не относящийся. Формула у обеих форм одна — эта.
     */
    public String requirePeer(String selfUid, String peerUid) {
        String peer = peerUid == null ? "" : peerUid;
        if (peer.isEmpty() || peer.equals(selfUid)) {
            throw ApiException.of("FRIEND_REQUIRED", 409);
        }
        if (friendships.areFriends(selfUid, peer)) {
            return peer;
        }
        RankedTeamPort.TeamSummary team = rankedTeams.teamOf(selfUid).orElse(null);
        if (team != null && "active".equals(team.status()) && team.memberUids().contains(peer)) {
            return peer;
        }
        throw ApiException.of("FRIEND_REQUIRED", 403);
    }
}
