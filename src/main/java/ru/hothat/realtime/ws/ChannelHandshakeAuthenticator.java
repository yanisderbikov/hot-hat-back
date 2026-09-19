package ru.hothat.realtime.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import ru.hothat.config.PrincipalResolver;
import ru.hothat.machine.domain.MachineActor;
import ru.hothat.machine.domain.MachineRoute;
import ru.hothat.machine.domain.MachineScope;
import ru.hothat.machine.security.MachineActorPrincipal;
import ru.hothat.machine.security.MachineCredentialVerifier;
import ru.hothat.util.Ids;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Личность на входе в любой канал живых обновлений.
 *
 * <p>Токен приходит параметром запроса, а не заголовком: браузерный WebSocket
 * не позволяет задать {@code Authorization} при рукопожатии. Приём достался от
 * прежнего документного шлюза; здесь он вынут из конфигурации в отдельный
 * класс, чтобы именованные каналы {@code /ws/v2/**} пользовались одним и тем
 * же, а не завели каждый свой разбор токена.
 *
 * <p>Кроме личности в атрибуты кладётся готовый {@code Authentication}.
 * В потоке сокета контекста безопасности нет — его не создаёт ни один фильтр,
 * — поэтому обработчик канала выставляет контекст сам, и тогда
 * {@code @PreAuthorize} на сценарии работает так же, как на HTTP-запросе.
 * Без этого право канала пришлось бы проверять руками в транспорте.
 *
 * <p>Отказ здесь — это отказ в рукопожатии: соединение не открывается вовсе.
 *
 * <p><b>Удостоверений два, потому что и вызывающих два рода.</b> Игрок и гость
 * приходят с access-токеном, рекордер — с подписью съёмки: за ним нет строки
 * в {@code app_user}, и токена ему выписать не от чего. Разбирать их в одном
 * месте важнее, чем в разных: иначе у машинного канала появился бы свой
 * порядок проверки, а именно из-за таких «своих» порядков четыре машинных
 * адреса HTTP и оказались в {@code permitAll} с ручным {@code if} внутри.
 */
@Component
@RequiredArgsConstructor
public class ChannelHandshakeAuthenticator implements HandshakeInterceptor {

    /** Личность владельца сокета: этот ключ атрибутов читают все обработчики каналов. */
    public static final String USER_ATTRIBUTE = "user";

    /** Контекст безопасности владельца сокета: роли и принципал. */
    public static final String AUTHENTICATION_ATTRIBUTE = "authentication";

    /**
     * Единственный канал, открытый гостю (§8 плана): витрина главной — то,
     * ради чего он и пришёл. Список именно закрытый, а не «всё, кроме
     * запрещённого»: новый канал по умолчанию гостю недоступен, и открыть его
     * можно, только дописав сюда строку.
     */
    private static final String GUEST_CHANNEL = "/ws/v2/lobby";

    /** Начало адреса канала рекордера; предмет — комната, стоящая в конце. */
    private static final String RECORDER_PREFIX = "/ws/v2/machine/recorder/rooms/";

    private final PrincipalResolver principalResolver;
    private final MachineCredentialVerifier machineVerifier;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        URI uri = request.getURI();
        String path = uri.getPath() == null ? "" : uri.getPath();
        boolean allowed = path.startsWith(RECORDER_PREFIX)
                ? recorder(uri, attributes)
                : player(uri, path, attributes);
        if (!allowed) {
            // Отказ надо назвать вслух. Один только возврат false оставляет
            // ответ 200 без апгрейда: и браузер, и журнал прокси видят обрыв
            // соединения, а не отказ, и просроченный токен неотличим от сбоя
            // сервера. Тело здесь не отдать — рукопожатие ещё не сокет, — но
            // код состояния отличает одно от другого.
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
        }
        return allowed;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler handler, Exception exception) {
    }

    /**
     * Игрок или гость: access-токен превращает в личность тот же
     * {@link PrincipalResolver}, что и фильтр HTTP.
     *
     * <p>Гостя дальше режет адрес. Роль {@code ROLE_GUEST} сама по себе не
     * открывает ничего — правило по умолчанию у сценариев {@code hasRole('USER')},
     * — но отказ на рукопожатии лучше отказа в сценарии: соединение не
     * открывается вовсе, а не живёт до первого кадра.
     */
    private boolean player(URI uri, String path, Map<String, Object> attributes) {
        return principalResolver.resolve(queryParam(uri, "token"))
                .filter(user -> !user.guest() || GUEST_CHANNEL.equals(path))
                .map(user -> {
                    attributes.put(USER_ATTRIBUTE, user);
                    attributes.put(AUTHENTICATION_ATTRIBUTE, principalResolver.authentication(user));
                    return true;
                })
                .orElse(false);
    }

    /**
     * Рекордер: подпись съёмки вместо токена.
     *
     * <p>Проверяет её тот же {@link MachineCredentialVerifier}, что и машинные
     * адреса HTTP, и по той же паре «комната и номер партии» — подписью от
     * чужой съёмки эту не открыть. Роль выдаётся здесь, а право дальше
     * выражается обычным {@code @PreAuthorize} на сценарии канала.
     *
     * <p>{@code USER_ATTRIBUTE} не кладётся намеренно: {@code HotHatUser} у
     * рекордера нет и быть не может, а подсунуть на его место чью-нибудь
     * личность значило бы дать машине права человека.
     *
     * <p>Подпись приезжает строкой запроса, а не заголовком, которого требует
     * машинная поверхность HTTP: страницу записи открывает браузер, и заголовок
     * при рукопожатии сокета ему задать нечем. Плата известна — подпись оседает
     * в журнале прокси, — но она узкая: подпись действует на одну партию одной
     * комнаты и вместе с ней истекает.
     */
    private boolean recorder(URI uri, Map<String, Object> attributes) {
        String roomId = lastSegment(uri);
        String gameNumber = queryParam(uri, "gameNumber");
        if (!Ids.ROOM.matcher(roomId).matches() || gameNumber == null || !gameNumber.matches("\\d{1,9}")) {
            return false;
        }
        MachineRoute route = new MachineRoute(MachineActor.RECORDER,
                new MachineScope(roomId, Integer.parseInt(gameNumber)));
        if (!machineVerifier.verify(route, queryParam(uri, "sig"))) {
            return false;
        }
        attributes.put(AUTHENTICATION_ATTRIBUTE, new UsernamePasswordAuthenticationToken(
                new MachineActorPrincipal(route.actor(), route.scope()), null,
                List.of(new SimpleGrantedAuthority(route.actor().authority()))));
        return true;
    }

    private String lastSegment(URI uri) {
        String path = uri.getPath();
        if (path == null) {
            return "";
        }
        return URLDecoder.decode(path.substring(path.lastIndexOf('/') + 1), StandardCharsets.UTF_8)
                .trim().toLowerCase();
    }

    private String queryParam(URI uri, String name) {
        String query = uri.getQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
