package ru.hothat.realtime.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.Set;

/**
 * Единственная дверь, через которую писатель говорит каналам «перечитайте».
 *
 * <p>Существует ради одного свойства, которого нет у голого
 * {@code publishEvent}: <b>повтор внутри транзакции снимается</b>. Одна
 * пересадка игрока пишет место, состав команды и строку комнаты — три
 * сохранения подряд. Без снятия повторов канал комнаты собрал бы и разослал
 * три одинаковых снимка, а витрина лобби — три раза по шесть запросов на
 * каждого зрителя главной. Событие описывает не запись, а факт «этот канал
 * устарел», и повторять его столько раз, сколько было сохранений, бессмысленно.
 *
 * <p>Набор увиденного живёт ресурсом транзакции, а не полем класса: у бина
 * один экземпляр на все потоки, и общий набор перемешал бы чужие записи.
 * Ресурс отвязывается своей же синхронизацией после завершения транзакции —
 * иначе он остался бы висеть на потоке пула и глушил бы события следующего
 * запроса, попавшего на тот же поток.
 *
 * <p>Вне транзакции повторы не снимаются и событие уходит сразу: сравнивать
 * не с чем, а придержать его негде.
 */
@Component
@RequiredArgsConstructor
public class RealtimeChangeBus {

    /** Ключ ресурса транзакции; строка своя, чтобы не столкнуться с чужой. */
    private static final String SEEN_KEY = RealtimeChangeBus.class.getName() + ".seen";

    private final ApplicationEventPublisher events;

    /** Комната изменилась; витрину лобби это не трогает. */
    public void roomChanged(String roomId) {
        if (roomId != null && !roomId.isBlank()) {
            publish(new RealtimeEvents.RoomChanged(roomId));
        }
    }

    /**
     * Изменилась сама строка комнаты: это может поменять и витрину.
     *
     * <p>Отдельный метод, потому что витрину двигает не всякая запись в
     * комнате. Место игрока, сообщение чата и сданные слова меняют комнату,
     * но не список на главной; фаза, название, приватность и счётчик живых —
     * меняют. Разделение здесь дешевле, чем разбор на стороне канала лобби:
     * иначе каждое нажатие «угадал» перестраивало бы витрину всем зрителям
     * главной.
     */
    public void roomRowChanged(String roomId) {
        roomChanged(roomId);
        publish(new RealtimeEvents.LobbyChanged());
    }

    /** Личные заявки, входящие или бан игрока. */
    public void socialChanged(String uid) {
        if (uid != null && !uid.isBlank()) {
            publish(new RealtimeEvents.SocialChanged(uid));
        }
    }

    /** Проверка готовности пары. */
    public void preflightChanged(String teamId) {
        if (teamId != null && !teamId.isBlank()) {
            publish(new RealtimeEvents.PreflightChanged(teamId));
        }
    }

    /** Общая библиотека мемов. */
    public void memeLibraryChanged() {
        publish(new RealtimeEvents.MemeLibraryChanged());
    }

    private void publish(Object event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            events.publishEvent(event);
            return;
        }
        if (seen().add(event)) {
            events.publishEvent(event);
        }
    }

    @SuppressWarnings("unchecked")
    private Set<Object> seen() {
        Set<Object> bound = (Set<Object>) TransactionSynchronizationManager.getResource(SEEN_KEY);
        if (bound != null) {
            return bound;
        }
        Set<Object> fresh = new HashSet<>();
        TransactionSynchronizationManager.bindResource(SEEN_KEY, fresh);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(SEEN_KEY);
            }
        });
        return fresh;
    }
}
