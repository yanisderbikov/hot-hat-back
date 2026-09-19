package ru.hothat.config;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import ru.hothat.auth.spi.AccountPort;
import ru.hothat.auth.spi.AccessTokenPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Единственное место, где access-токен превращается в пользователя. Им
 * пользуются и HTTP-фильтр, и рукопожатие WebSocket — иначе две проверки
 * прав со временем разойдутся.
 *
 * <p>Гость получает личность наравне с игроком, но с другой ролью. Раньше
 * {@code resolve} возвращал по гостевому токену пустоту (аудит A7), и от этого
 * гостевая сессия была декорацией: refresh обновлялся бесконечно, а любой
 * защищённый адрес отвечал 401 — включая {@code GET /api/v2/auth/me}, где
 * сервер сам придумал гостю имя и обязан его назвать. Теперь личность есть, а
 * поверхность режет роль: {@code ROLE_GUEST} вместо {@code ROLE_USER}.
 *
 * <p><b>Блокировка здесь больше не читается, и это не упущение.</b> Бан
 * поднимает поколение токенов в той же транзакции, что заводит строку
 * блокировки, поэтому любой ранее выданный access-токен отвергается проверкой
 * поколения — двумя строками ниже. Отдельный запрос в таблицу банов на каждый
 * запрос к серверу отвечал бы на тот же вопрос второй раз и стоил бы третьего
 * похода в базу на разбор токена.
 *
 * <p>Права спрашиваются у области {@code auth} и только у неё. Раньше формул
 * «кто администратор» было две — здесь и в старом движке входа, — и они уже
 * различались (аудит A5).
 */
@Component
@RequiredArgsConstructor
public class PrincipalResolver {

    private final AccessTokenPort accessTokens;
    private final AccountPort accounts;

    public Optional<HotHatUser> resolve(String token) {
        if (token == null || token.isBlank() || !accessTokens.isValid(token)) {
            return Optional.empty();
        }
        AccountPort.Account account = accounts.account(accessTokens.getUid(token)).orElse(null);
        if (account == null) {
            return Optional.empty();
        }
        if (accessTokens.getTokenVersion(token) != account.tokenVersion()) {
            return Optional.empty();
        }
        return Optional.of(new HotHatUser(
                account.uid(), account.email(), null,
                account.admin(), account.owner(), account.guest()));
    }

    /**
     * Роли пользователя одной лестницей.
     *
     * <p>Ими пользуются оба входа — HTTP-фильтр и рукопожатие сокета. Пока
     * список строился в фильтре, у сокета ролей не было вовсе, и любое
     * {@code @PreAuthorize} на сценарии канала отвергло бы своего же
     * участника. Две копии списка разошлись бы ещё хуже: права игрока
     * зависели бы от того, каким транспортом он пришёл.
     */
    public UsernamePasswordAuthenticationToken authentication(HotHatUser principal) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (principal.guest()) {
            // Ровно одна роль и никаких надстроек над ней: ROLE_GUEST ничего не
            // открывает сама по себе, потому что правило по умолчанию —
            // hasRole('USER'). Гостю доступно только то, где 'GUEST' написан
            // явно в @PreAuthorize сценария, и расширить эту поверхность нельзя,
            // не написав слово GUEST — это проверяется грепом (§8 плана).
            authorities.add(new SimpleGrantedAuthority("ROLE_GUEST"));
            return new UsernamePasswordAuthenticationToken(principal, null, authorities);
        }
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (principal.admin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        if (principal.owner()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_OWNER"));
        }
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }
}
