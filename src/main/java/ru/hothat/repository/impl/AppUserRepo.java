package ru.hothat.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.hothat.model.user.AppUser;

/**
 * Оболочка учётки: обойма мемов, квота диверсий, история слов.
 *
 * <p>Собственных запросов не осталось: поиск по почте, поиск по ключу ника,
 * счётчик онлайна и счётчик регистраций уехали в схему v2 вместе с колонками,
 * по которым искали. Здесь хватает того, что даёт {@code JpaRepository}.
 */
@Repository
interface AppUserRepo extends JpaRepository<AppUser, String> {
}
