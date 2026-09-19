package ru.hothat.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import ru.hothat.model.social.*;
import ru.hothat.repository.GetterSocial;
import ru.hothat.repository.SaverSocial;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@AllArgsConstructor
@Slf4j
class SocialManager implements GetterSocial, SaverSocial {

    private final FriendLinkRepo friendLinkRepo;
    private final SocialInboxRepo socialInboxRepo;
    private final RoomInviteRepo roomInviteRepo;

    @Override
    public Optional<FriendLink> getLink(String pair) {
        return wrap("getLink", () -> friendLinkRepo.findById(pair));
    }

    @Override
    public List<FriendLink> getLinksOf(String uid, int limit) {
        return wrap("getLinksOf", () -> friendLinkRepo.findByUidAOrUidB(uid, uid, Limit.of(limit)));
    }

    @Override
    public Optional<SocialInbox> getInbox(String uid) {
        return wrap("getInbox", () -> socialInboxRepo.findById(uid));
    }

    @Override
    public Optional<RoomInvite> getRoomInvite(String inviteId) {
        if (inviteId == null || inviteId.isBlank()) {
            return Optional.empty();
        }
        return wrap("getRoomInvite", () -> roomInviteRepo.findById(inviteId));
    }

    @Override
    public List<RoomInvite> getRoomInvites(List<String> inviteIds) {
        if (inviteIds.isEmpty()) {
            return List.of();
        }
        return wrap("getRoomInvites", () -> roomInviteRepo.findAllById(inviteIds));
    }

    @Override
    public FriendLink saveLink(FriendLink link) {
        return wrap("saveLink", () -> friendLinkRepo.save(link));
    }

    @Override
    public void deleteLink(String pair) {
        wrap("deleteLink", () -> {
            friendLinkRepo.deleteById(pair);
            return null;
        });
    }

    @Override
    public SocialInbox saveInbox(SocialInbox inbox) {
        inbox.setUpdatedAt(Instant.now());
        return wrap("saveInbox", () -> socialInboxRepo.save(inbox));
    }

    @Override
    public RoomInvite saveRoomInvite(RoomInvite invite) {
        return wrap("saveRoomInvite", () -> roomInviteRepo.save(invite));
    }

    private <T> T wrap(String operation, java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (Exception e) {
            log.error("SocialManager.{} failed", operation, e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
