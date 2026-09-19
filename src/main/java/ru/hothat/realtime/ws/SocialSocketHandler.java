package ru.hothat.realtime.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.WebSocketSession;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.realtime.api.dto.SocialChannelEventDTO;
import ru.hothat.realtime.api.dto.SocialChannelHelloDTO;
import ru.hothat.realtime.spi.RealtimeEvents;
import ru.hothat.realtime.usecase.StreamSocialChannelUseCase;

/**
 * Канал шапки портала — {@code /ws/v2/me/social}.
 *
 * <p>Заменяет четыре живые подписки: входящие заявки, свои заявки, входящие
 * сообщения и бан ({@code realtime-social.js:156}, {@code :158}, {@code :159},
 * {@code :161}). Держится он на каждой странице портала, поэтому и назван
 * «своим»: предмет канала — сам слушатель.
 *
 * <p>Идентификатора в адресе нет и быть не должно. Он взялся бы из строки
 * запроса, то есть от клиента, и первый же вопрос был бы «а свой ли он» —
 * лишняя проверка там, где ответ уже известен из рукопожатия. Ключ рассылки —
 * личность владельца сокета.
 */
@Component
public class SocialSocketHandler extends ChannelSocketHandler {

    private final StreamSocialChannelUseCase streamSocial;

    public SocialSocketHandler(ObjectMapper mapper, StreamSocialChannelUseCase streamSocial) {
        super(mapper);
        this.streamSocial = streamSocial;
    }

    @Override
    protected String key(WebSocketSession session) {
        Object user = session.getAttributes().get(ChannelHandshakeAuthenticator.USER_ATTRIBUTE);
        if (!(user instanceof HotHatUser known)) {
            throw ApiException.of("AUTH_REQUIRED", 401);
        }
        return known.uid();
    }

    @Override
    protected Object hello(Subscriber subscriber) {
        return new SocialChannelHelloDTO(underPrincipal(subscriber, () -> streamSocial.run(subscriber.user())));
    }

    @Override
    protected Object event(Subscriber subscriber) {
        return new SocialChannelEventDTO(underPrincipal(subscriber, () -> streamSocial.run(subscriber.user())));
    }

    /**
     * У игрока изменились заявки, входящие или бан — шлём ему новый снимок.
     *
     * <p>{@code AFTER_COMMIT}: до фиксации значок непрочитанного показал бы
     * письмо, которого при откате не станет. {@code REQUIRES_NEW} — потому что
     * транзакция писателя уже зафиксирована, но ещё связана с потоком, и
     * вложенное чтение присоединилось бы к завершённой.
     *
     * <p>Рассылка идёт только сокетам названного игрока: чужие входящие не
     * читает никто, и перебирать ради этого все открытые соединения незачем.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onSocialChanged(RealtimeEvents.SocialChanged event) {
        broadcast(event.uid());
    }
}
