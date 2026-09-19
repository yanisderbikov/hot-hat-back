package ru.hothat.repository;

import ru.hothat.model.social.FriendLink;
import ru.hothat.model.social.RoomInvite;
import ru.hothat.model.social.SocialInbox;

import java.util.List;
import java.util.Optional;

/**
 * Чтения социальных данных, оставшиеся у прежних таблиц.
 *
 * <p>Заявки в друзья и переписки отсюда ушли вместе со старой поверхностью:
 * их ведёт схема {@code v2} и читают области {@code /api/v2/friends} и
 * {@code /api/v2/chat} через свои хранилища. Здесь остались связь дружбы
 * (её спрашивает арсенал диверсий), инбокс и приглашения в комнату.
 */
public interface GetterSocial {

    Optional<FriendLink> getLink(String pair);

    List<FriendLink> getLinksOf(String uid, int limit);

    Optional<SocialInbox> getInbox(String uid);

    Optional<RoomInvite> getRoomInvite(String inviteId);

    List<RoomInvite> getRoomInvites(List<String> inviteIds);
}
