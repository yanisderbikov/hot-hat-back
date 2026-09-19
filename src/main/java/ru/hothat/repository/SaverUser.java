package ru.hothat.repository;

import ru.hothat.model.user.AppUser;

/**
 * Запись оболочки учётки.
 *
 * <p>Осталась одна операция. Ники, согласия, заявки, блокировки и обе таблицы
 * токенов писали отсюда же — теперь их пишут владельцы своих таблиц в схеме
 * v2, а плановую уборку токенов делает {@code IdentityStore.sweepExpired}.
 */
public interface SaverUser {

    AppUser save(AppUser user);
}
