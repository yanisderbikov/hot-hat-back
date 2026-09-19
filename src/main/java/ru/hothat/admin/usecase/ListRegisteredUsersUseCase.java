package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.admin.api.dto.AdminUserCardView;
import ru.hothat.admin.api.dto.AdminUserPageResponseDTO;
import ru.hothat.admin.api.dto.AdminUserQueryDTO;
import ru.hothat.admin.spi.BanPort;
import ru.hothat.auth.spi.AccountPort;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.util.Divisions;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Перечислить учётные записи для владельца сервиса.
 *
 * <p>Заменяет {@code POST /api/portal} с {@code action=list_users}. Право
 * поднято до владельца и объявлено, а не спрятано в сравнение почты внутри
 * сервиса: адрес отдаёт почту всех аккаунтов, а лежал он под общим матчером
 * {@code authenticated} рядом с отметкой присутствия (A6).
 *
 * <p>Предел — сто вместо пяти тысяч: пять тысяч профилей с почтой в одном
 * ответе — это выгрузка базы, и получаться случайно она не должна.
 *
 * <p>Гостей отбирает сама база: у неё есть частичный индекс по полноценным
 * учёткам, и порядок «новые сверху» тоже берётся оттуда. Раньше страница
 * читалась целиком и сортировалась в памяти, а гости отсеивались после.
 *
 * <p>Три обращения на страницу любой длины: учётки, их карточки и действующие
 * блокировки. Имя и аватар спрашиваются у области профиля — своих копий у
 * учётки больше нет.
 */
@Service
@RequiredArgsConstructor
public class ListRegisteredUsersUseCase {

    private final AccountPort accounts;
    private final PlayerCardPort cards;
    private final BanPort bans;

    @PreAuthorize("hasRole('OWNER')")
    public AdminUserPageResponseDTO run(HotHatUser owner, AdminUserQueryDTO query) {
        int limit = query == null ? AdminUserQueryDTO.DEFAULT_LIMIT : query.limitOrDefault();
        List<AccountPort.Account> members = accounts.members(limit);

        Set<String> uids = new LinkedHashSet<>();
        for (AccountPort.Account account : members) {
            uids.add(account.uid());
        }
        Map<String, PlayerCardPort.Card> known = cards.cards(uids);
        Map<String, BanPort.Ban> banned = bans.activeBans(uids);

        List<AdminUserCardView> items = new ArrayList<>();
        for (AccountPort.Account account : members) {
            // Своя учётка из реестра выпадает: владелец пришёл смотреть на
            // остальных, а кнопка «заблокировать» рядом с собой — ловушка.
            if (account.uid().equals(owner.uid())) {
                continue;
            }
            PlayerCardPort.Card card = known.get(account.uid());
            items.add(new AdminUserCardView(
                    account.uid(),
                    card == null || card.nickname() == null ? account.uid() : card.nickname(),
                    blankToNull(account.email()),
                    card == null ? null : blankToNull(card.avatarDataUrl()),
                    Divisions.normalize(card == null ? null : card.divisionLanguage()),
                    account.registeredAtMs(),
                    banned.containsKey(account.uid())));
        }
        return new AdminUserPageResponseDTO(items, null, limit);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
