package ru.hothat.auth.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.auth.api.dto.AccountKind;
import ru.hothat.auth.api.dto.AccountView;
import ru.hothat.auth.api.dto.SessionTokensView;
import ru.hothat.auth.store.IdentityStore;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.auth.spi.AccessTokenPort;

/**
 * Выдача пары токенов и сборка учётки для ответа — в одном месте.
 *
 * <p>Существует по той же причине, по какой раньше существовал переходный
 * переводчик: пару выдают шесть сценариев (вход, регистрация, гость, обмен,
 * апгрейд и восстановление), и разойтись шести ответам о том же человеке
 * нельзя. Признак администратора считается одной формулой — той, что живёт в
 * {@code IdentityStore}, — а род учётки одним правилом.
 *
 * <p>Ник берётся у области профиля, а не из учётки: имени в
 * {@code v2.user_account} нет вовсе, и это намеренно — имя принадлежит
 * карточке игрока и меняется её сценариями.
 */
@Component
@RequiredArgsConstructor
public class SessionIssuer {

    private final IdentityStore identities;
    private final AccessTokenPort jwt;
    private final PlayerCardPort cards;

    /** Новая цепочка: вход, регистрация, гость, апгрейд. */
    public SessionTokensView open(IdentityStore.Account account, String userAgent, String ip) {
        return tokens(account, identities.openSession(account.uid(), userAgent, ip));
    }

    /** Продолжение цепочки: обмен refresh-токена. */
    public SessionTokensView rotate(IdentityStore.Account account, String presentedToken,
                                    String userAgent, String ip) {
        return tokens(account, identities.rotateSession(presentedToken, userAgent, ip));
    }

    /**
     * Учётка для ответа.
     *
     * <p>{@code displayName} повторяет ник, и это не небрежность: колонки
     * {@code display_name} в новой схеме нет. Она была вторым именем того же
     * человека, расходилась с первым при каждой смене ника и ни разу не
     * показывалась отдельно. Поле контракта остаётся — его читает фронтенд, —
     * но источник у имени теперь один.
     */
    public AccountView view(IdentityStore.Account account) {
        String nickname = cards.card(account.uid())
                .map(PlayerCardPort.Card::nickname)
                .orElse(null);
        return new AccountView(
                account.uid(),
                account.email(),
                nickname,
                nickname,
                account.guest() ? AccountKind.GUEST : AccountKind.USER,
                account.admin());
    }

    private SessionTokensView tokens(IdentityStore.Account account, String refreshToken) {
        String access = jwt.createAccessToken(
                account.uid(), account.email(), account.tokenVersion(), account.guest());
        return new SessionTokensView(access, SessionTokensView.BEARER, refreshToken, jwt.accessTtlSeconds());
    }
}
