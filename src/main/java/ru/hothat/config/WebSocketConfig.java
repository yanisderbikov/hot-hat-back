package ru.hothat.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import ru.hothat.realtime.ws.ChannelHandshakeAuthenticator;
import ru.hothat.realtime.ws.ConferenceSocketHandler;
import ru.hothat.realtime.ws.DirectChatSocketHandler;
import ru.hothat.realtime.ws.LobbySocketHandler;
import ru.hothat.realtime.ws.MemeLibrarySocketHandler;
import ru.hothat.realtime.ws.PreflightSocketHandler;
import ru.hothat.realtime.ws.RecorderSocketHandler;
import ru.hothat.realtime.ws.RoomSocketHandler;
import ru.hothat.realtime.ws.SocialSocketHandler;

import java.util.List;

/**
 * Каналы живых обновлений. Личность на входе проверяет
 * {@link ChannelHandshakeAuthenticator} — один и тот же для всех каналов:
 * токен приходит параметром запроса, потому что браузерный WebSocket не
 * позволяет задать заголовок {@code Authorization} при рукопожатии.
 *
 * <p>Семь именованных каналов §9 плана плюс восьмой — видео-чат, — и только они. У каждого есть предмет,
 * названный адресом, право, принадлежащее области-владельцу, и пара кадров со
 * своей формой — той же, что у соответствующего ответа HTTP. Универсального
 * шлюза с подпиской по пути-строке здесь больше нет: он резал права по одному
 * uid, отчего слова чужой команды читались штатным запросом, и ушёл вместе с
 * последним подписчиком — экраном комнаты, который теперь живёт кадром
 * {@code /ws/v2/room}.
 *
 * <p><b>Адрес канала — это его предмет.</b> Переменных пути у сокетов нет:
 * Spring отдаёт обработчику целый URI, и нужный сегмент разбирает сам
 * обработчик. Поэтому в шаблонах стоит {@code *} — один сегмент, не больше.
 *
 * <p><b>Список источников тот же, что у HTTP.</b> Раньше здесь стояла звезда,
 * то есть страница с любого домена могла открыть сокет с чужим токеном и
 * читать чужую переписку (D8 аудита). Теперь оба транспорта берут один и тот
 * же {@code allowed.origins}: разойтись им больше нечем.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final LobbySocketHandler lobbySocketHandler;
    private final RoomSocketHandler roomSocketHandler;
    private final SocialSocketHandler socialSocketHandler;
    private final DirectChatSocketHandler directChatSocketHandler;
    private final PreflightSocketHandler preflightSocketHandler;
    private final MemeLibrarySocketHandler memeLibrarySocketHandler;
    private final RecorderSocketHandler recorderSocketHandler;
    private final ConferenceSocketHandler conferenceSocketHandler;
    private final ChannelHandshakeAuthenticator handshakeAuthenticator;

    /** Тот же список, что у CORS в {@link WebSecurityConfig}: одно окружение — одни источники. */
    @Value("${allowed.origins}")
    private List<String> allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Витрина главной: единственный канал, открытый гостю. Предмета в
        // адресе нет — она одна на всех, а кому какая комната видна, решает
        // сценарий чтения.
        register(registry, lobbySocketHandler, "/ws/v2/lobby");
        register(registry, roomSocketHandler, "/ws/v2/room/*");
        // Предмет канала — сам слушатель, поэтому идентификатора в адресе нет
        // и быть не должно: он приехал бы от клиента, и первым вопросом было
        // бы «а свой ли он».
        register(registry, socialSocketHandler, "/ws/v2/me/social");
        register(registry, directChatSocketHandler, "/ws/v2/chat/*");
        register(registry, preflightSocketHandler, "/ws/v2/team/*/preflight");
        register(registry, memeLibrarySocketHandler, "/ws/v2/media/memes");
        // Номер партии и подпись съёмки приезжают строкой запроса: адрес канала
        // назван планом, и в нём только комната.
        register(registry, recorderSocketHandler, "/ws/v2/machine/recorder/rooms/*");
        // Восьмой канал — видео-чат: состав, лента и заведённая из него комната
        // одним кадром, вместо двух опросов, которыми жила страница в dev.
        register(registry, conferenceSocketHandler, "/ws/v2/conference/*");
    }

    private void register(WebSocketHandlerRegistry registry,
                          WebSocketHandler handler, String path) {
        registry.addHandler(handler, path)
                .addInterceptors(handshakeAuthenticator)
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
    }
}
