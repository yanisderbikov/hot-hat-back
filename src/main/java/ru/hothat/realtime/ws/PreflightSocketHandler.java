package ru.hothat.realtime.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.WebSocketSession;
import ru.hothat.config.ApiException;
import ru.hothat.realtime.api.dto.PreflightEventDTO;
import ru.hothat.realtime.api.dto.PreflightHelloDTO;
import ru.hothat.realtime.spi.RealtimeEvents;
import ru.hothat.realtime.usecase.StreamPreflightUseCase;

import java.util.regex.Pattern;

/**
 * Канал предматчевой проверки — {@code /ws/v2/team/{teamId}/preflight}.
 *
 * <p>Заменяет живую подписку на документ {@code rankedTeamPreflights/{teamId}}
 * ({@code portal.js:137}). Подписчиков у ключа ровно двое — оба участника
 * пары, — поэтому рассылка идёт по двум вкладкам, а не по коллекции
 * неизвестного размера.
 *
 * <p>Команда стоит в адресе, а не берётся по личности, хотя сценарий всё равно
 * спрашивает её у {@code teamAuthz}. Адрес нужен транспорту: событие о смене
 * готовности называет команду, и без ключа рассылка перебирала бы все открытые
 * соединения. Сценарий при этом сверяет, та ли это команда, — подписаться на
 * чужую нельзя.
 */
@Component
public class PreflightSocketHandler extends ChannelSocketHandler {

    /** Хвост адреса, перед которым стоит идентификатор команды. */
    private static final String TAIL = "preflight";

    /** Тот же вид идентификатора, что у команд в адресах HTTP. */
    private static final Pattern TEAM_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final StreamPreflightUseCase streamPreflight;

    public PreflightSocketHandler(ObjectMapper mapper, StreamPreflightUseCase streamPreflight) {
        super(mapper);
        this.streamPreflight = streamPreflight;
    }

    @Override
    protected String key(WebSocketSession session) {
        String teamId = segmentBefore(session.getUri(), TAIL);
        if (!TEAM_ID.matcher(teamId).matches()) {
            throw ApiException.of("TEAM_NOT_FOUND", 404);
        }
        return teamId;
    }

    @Override
    protected Object hello(Subscriber subscriber) {
        return new PreflightHelloDTO(underPrincipal(subscriber,
                () -> streamPreflight.run(subscriber.user(), subscriber.key())));
    }

    @Override
    protected Object event(Subscriber subscriber) {
        return new PreflightEventDTO(underPrincipal(subscriber,
                () -> streamPreflight.run(subscriber.user(), subscriber.key())));
    }

    /**
     * Проверка изменилась — шлём обоим участникам новый снимок.
     *
     * <p>{@code AFTER_COMMIT}: до фиксации напарник увидел бы чужую
     * готовность, которой при откате не станет, — и по ней запустил бы поиск
     * соперников. {@code REQUIRES_NEW} — потому что транзакция писателя уже
     * зафиксирована, но ещё связана с потоком.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onPreflightChanged(RealtimeEvents.PreflightChanged event) {
        broadcast(event.teamId());
    }
}
