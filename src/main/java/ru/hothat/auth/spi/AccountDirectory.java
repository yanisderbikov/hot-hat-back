package ru.hothat.auth.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.auth.store.IdentityStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Реализация {@link AccountPort}: живёт у владельца таблицы, как и положено. */
@Component
@RequiredArgsConstructor
public class AccountDirectory implements AccountPort {

    private final IdentityStore identities;

    @Override
    public Optional<Account> account(String uid) {
        return identities.byUid(uid).map(AccountDirectory::view);
    }

    @Override
    public Map<String, Account> accounts(Collection<String> uids) {
        Map<String, Account> result = new LinkedHashMap<>();
        identities.byUids(uids).forEach((uid, account) -> result.put(uid, view(account)));
        return result;
    }

    @Override
    public List<Account> members(int limit) {
        List<Account> result = new ArrayList<>();
        for (IdentityStore.Account account : identities.members(limit)) {
            result.add(view(account));
        }
        return result;
    }

    @Override
    public Optional<String> ownerUid() {
        return identities.ownerUid();
    }

    @Override
    public long countAccounts() {
        return identities.countAccounts();
    }

    @Override
    public long countRegisteredBetween(Instant from, Instant to) {
        return identities.countMembersRegisteredBetween(from, to);
    }

    private static Account view(IdentityStore.Account account) {
        return new Account(account.uid(), account.email(), account.guest(), account.tokenVersion(),
                account.passwordSet(), account.memberSinceMs(), account.admin(), account.owner());
    }
}
