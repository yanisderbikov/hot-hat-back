package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.admin.spi.BanPort;
import ru.hothat.auth.spi.AccountPort;

import java.util.Collection;
import java.util.Map;

/**
 * Учётки игроков, которых показывает дашборд, — двумя обращениями на весь
 * список.
 *
 * <p>Появился ради того же веера, что справочники карточек: прежний движок
 * ходил в базу за каждым uid отдельно — дашборд из двадцати комнат по шесть
 * человек стоил сто двадцать обращений, и это был самый тяжёлый экран сервиса.
 *
 * <p>Спрашивает две области, а не одну таблицу: почту и наличие пароля —
 * у {@code auth}, блокировку — у {@code admin}. Раньше на оба вопроса
 * отвечала одна строка {@code app_user}, и «заблокирован» в ней был флагом,
 * который умел разойтись со строкой причины.
 */
@Component
@RequiredArgsConstructor
public class AdminAccountDirectory {

    private final AccountPort accounts;
    private final BanPort bans;

    /** Читает учётки названных игроков; пустой список в базу не идёт. */
    public Snapshot load(Collection<String> uids) {
        Map<String, AccountPort.Account> known = accounts.accounts(uids);
        return new Snapshot(known, bans.activeBans(known.keySet()));
    }

    /** Прочитанные учётки. Запросов больше не делает — только карты в памяти. */
    public static final class Snapshot {

        private final Map<String, AccountPort.Account> byUid;
        private final Map<String, BanPort.Ban> banned;

        private Snapshot(Map<String, AccountPort.Account> byUid, Map<String, BanPort.Ban> banned) {
            this.byUid = byUid;
            this.banned = banned;
        }

        /** Почта; null — учётки нет или почта не сохранена (гость). */
        public String email(String uid) {
            AccountPort.Account account = byUid.get(uid);
            return account == null ? null : blankToNull(account.email());
        }

        /** Учётка заблокирована; у пропавшей — false, как и у прежнего движка. */
        public boolean banned(String uid) {
            return banned.containsKey(uid);
        }

        /** У учётки есть пароль, то есть она не гостевая. */
        public boolean registered(String uid) {
            AccountPort.Account account = byUid.get(uid);
            return account != null && account.passwordSet();
        }

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value;
        }
    }
}
