package ru.hothat.repository;

import ru.hothat.model.social.FriendLink;
import ru.hothat.model.social.RoomInvite;
import ru.hothat.model.social.SocialInbox;

/**
 * Записи социальных данных, оставшиеся у прежних таблиц.
 *
 * <p>Заявку в друзья, шапку переписки и сообщение отсюда убрали вместе со
 * старой поверхностью: писать их некому — оба сценария живут в {@code /api/v2}
 * и пишут в схему {@code v2}.
 */
public interface SaverSocial {

    FriendLink saveLink(FriendLink link);

    void deleteLink(String pair);

    SocialInbox saveInbox(SocialInbox inbox);

    RoomInvite saveRoomInvite(RoomInvite invite);
}
