package ru.hothat.realtime.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.WebSocketSession;
import ru.hothat.config.ApiException;
import ru.hothat.realtime.api.dto.RecorderChannelEventDTO;
import ru.hothat.realtime.api.dto.RecorderChannelHelloDTO;
import ru.hothat.realtime.api.dto.RecorderMirrorView;
import ru.hothat.realtime.spi.RealtimeEvents;
import ru.hothat.realtime.usecase.StreamRecorderChannelUseCase;
import ru.hothat.util.Ids;

/**
 * Канал страницы записи — {@code /ws/v2/machine/recorder/rooms/{roomId}}.
 *
 * <p>Заменяет обе подписки зеркала рекордера: документ комнаты и её команды
 * ({@code app-core.js:13913}, {@code :13940}). Они приезжали вразнобой, и
 * между двумя снимками в файл попадала секунда со старым счётом; здесь оба
 * приезжают одним кадром, потому что нарисовать сцену без счёта всё равно
 * нельзя.
 *
 * <p>Единственный канал без личности игрока: за рекордером нет строки в
 * {@code app_user}. Право даёт подпись съёмки, проверенная в рукопожатии тем
 * же {@code MachineCredentialVerifier}, что стоит на машинных адресах HTTP;
 * подпись считается от пары «комната и номер партии», поэтому чужой съёмкой
 * эту не открыть.
 *
 * <p>Номер партии стоит в строке запроса, а не в адресе, потому что адрес
 * канала назван планом (§5.15) и в нём только комната. Ключ рассылки — тоже
 * комната: партия в комнате в каждый момент одна, а несовпадение номеров
 * сценарий отдаёт кадром {@code STALE_GAME}, а не молчанием, — страница должна
 * узнать, что снимает не то, и остановиться.
 *
 * <p>Склейки рассылок здесь нет намеренно, в отличие от витрины лобби:
 * записанное видео не переснять, и кадр, задержанный на две секунды, — это две
 * секунды неверного счёта в файле.
 */
@Component
public class RecorderSocketHandler extends ChannelSocketHandler {

    /** Номер снимаемой партии; тем же именем его получает и сама страница записи. */
    private static final String GAME_NUMBER = "gameNumber";

    private final StreamRecorderChannelUseCase streamRecorder;

    public RecorderSocketHandler(ObjectMapper mapper, StreamRecorderChannelUseCase streamRecorder) {
        super(mapper);
        this.streamRecorder = streamRecorder;
    }

    @Override
    protected String key(WebSocketSession session) {
        String roomId = lastSegment(session.getUri()).trim().toLowerCase();
        if (!Ids.ROOM.matcher(roomId).matches()) {
            throw ApiException.of("ROOM_NOT_FOUND", 404);
        }
        return roomId;
    }

    @Override
    protected Object hello(Subscriber subscriber) {
        return new RecorderChannelHelloDTO(mirror(subscriber));
    }

    @Override
    protected Object event(Subscriber subscriber) {
        return new RecorderChannelEventDTO(mirror(subscriber));
    }

    /**
     * Комната изменилась — шлём странице записи новую сцену.
     *
     * <p>{@code AFTER_COMMIT}: кадр, снятый до фиксации, остался бы в файле
     * навсегда, а откат транзакции переснять его уже не даст.
     * {@code REQUIRES_NEW} — транзакция писателя уже зафиксирована, но ещё
     * связана с потоком, и вложенное чтение присоединилось бы к завершённой.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onRoomChanged(RealtimeEvents.RoomChanged event) {
        broadcast(event.roomId());
    }

    /**
     * Сцена под правами этого сокета.
     *
     * <p>Номер партии читается из адреса на каждом кадре, а не запоминается
     * при подписке: он часть удостоверения — подпись рукопожатия сверена
     * именно с ним, — и держать рядом вторую копию значило бы завести способ
     * их рассогласовать.
     */
    private RecorderMirrorView mirror(Subscriber subscriber) {
        int gameNumber = gameNumber(subscriber);
        return underPrincipal(subscriber, () -> streamRecorder.run(subscriber.key(), gameNumber));
    }

    private int gameNumber(Subscriber subscriber) {
        String raw = queryParam(subscriber.session().getUri(), GAME_NUMBER);
        if (raw == null || !raw.matches("\\d{1,9}")) {
            // Тот же вид номера, что принимает машинный адрес HTTP. Без него
            // сцену не собрать: сверять снимаемую партию не с чем.
            throw ApiException.of("RECORDING_GAME_NUMBER_MISMATCH", 409);
        }
        return Integer.parseInt(raw);
    }
}
