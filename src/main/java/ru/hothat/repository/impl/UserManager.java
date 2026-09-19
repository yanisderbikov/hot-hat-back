package ru.hothat.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.hothat.model.user.AppUser;
import ru.hothat.repository.GetterUser;
import ru.hothat.repository.SaverUser;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Оболочка учётки: обойма мемов, квота диверсий, история слов.
 *
 * <p>Было семь репозиториев и двадцать методов — учётки, ники, согласия,
 * заявки, блокировки, refresh-токены и ссылки восстановления. Шесть таблиц из
 * семи переехали в схему v2 и обслуживаются своими хранилищами; здесь остался
 * один репозиторий и три метода.
 */
@Component
@AllArgsConstructor
@Slf4j
class UserManager implements GetterUser, SaverUser {

    private final AppUserRepo appUserRepo;

    @Override
    public Optional<AppUser> getByUid(String uid) {
        return wrap("getByUid", () -> appUserRepo.findById(uid));
    }

    @Override
    public List<AppUser> getByUids(List<String> uids) {
        if (uids == null || uids.isEmpty()) {
            return List.of();
        }
        // findAllById — один запрос с «uid in (…)»; поштучный getByUid в цикле
        // и есть тот веер, ради которого метод появился.
        return wrap("getByUids", () -> appUserRepo.findAllById(uids));
    }

    @Override
    public AppUser save(AppUser user) {
        user.setUpdatedAt(Instant.now());
        return wrap("save", () -> appUserRepo.save(user));
    }

    private <T> T wrap(String operation, java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (Exception e) {
            log.error("UserManager.{} failed", operation, e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
