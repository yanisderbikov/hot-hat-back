package ru.hothat.repository;

import ru.hothat.model.user.AppUser;

import java.util.List;
import java.util.Optional;

/**
 * Чтение оболочки учётки: обойма мемов, квота диверсий, история слов.
 *
 * <p>Личности здесь больше нет. Почта, пароль, поколение токенов, ник, аватар,
 * дивизион, присутствие, согласия и блокировка уехали в схему v2 и
 * спрашиваются у портов своих областей — {@code auth.spi.AccountPort},
 * {@code profile.spi.PlayerCardPort}, {@code admin.spi.BanPort}. Вместе с
 * ними ушли поиск по почте, поиск по ключу ника, индекс ников, согласия,
 * счётчики онлайна и регистраций и обе таблицы токенов.
 */
public interface GetterUser {

    Optional<AppUser> getByUid(String uid);

    /**
     * Оболочки названных игроков одним запросом.
     *
     * <p>Существует ради того, чтобы не звать {@link #getByUid(String)} в цикле
     * по составу: у стола на восьмерых это восемь обращений к базе там, где
     * достаточно одного {@code uid in (…)}. Пустой список в базу не идёт.
     */
    List<AppUser> getByUids(List<String> uids);
}
