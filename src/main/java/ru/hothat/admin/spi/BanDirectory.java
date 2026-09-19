package ru.hothat.admin.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.admin.store.ModerationStore;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Реализация {@link BanPort}: живёт у владельца таблицы. */
@Component
@RequiredArgsConstructor
public class BanDirectory implements BanPort {

    private final ModerationStore moderation;

    @Override
    public boolean banned(String uid) {
        return moderation.activeBan(uid).isPresent();
    }

    @Override
    public Optional<Ban> activeBan(String uid) {
        return moderation.activeBan(uid).map(BanDirectory::view);
    }

    @Override
    public Map<String, Ban> activeBans(Collection<String> uids) {
        Map<String, Ban> result = new LinkedHashMap<>();
        moderation.activeBans(uids).forEach((uid, ban) -> result.put(uid, view(ban)));
        return result;
    }

    private static Ban view(ModerationStore.Ban ban) {
        return new Ban(ban.uid(), ban.reason(), ban.byUid(), ban.bannedAtMs());
    }
}
