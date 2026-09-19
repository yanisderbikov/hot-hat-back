package ru.hothat.realtime.store;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.hothat.admin.usecase.AdminEvictionEvents;
import ru.hothat.chat.spi.DirectChatChangedEvent;
import ru.hothat.game.usecase.MatchEvents;
import ru.hothat.realtime.spi.RealtimeChangeBus;

/**
 * Переводчик доменных событий в сигналы именованных каналов.
 *
 * <p>Каналы {@code /ws/v2} слушают {@link ru.hothat.realtime.spi.RealtimeEvents},
 * а публиковать их обязан тот, кто пишет. Записи комнаты об этом не думают:
 * {@code RoomManager} зовёт {@link RealtimeChangeBus} после каждого сохранения
 * и удаления строк — {@code roomRowChanged} на самой комнате, {@code roomChanged}
 * на местах, составах, чате и словах. Писателей у комнаты тридцать пять, и
 * партия тоже пишет через тот же {@code SaverRoom}; одна публикация на границе
 * надёжнее тридцати пяти по одной, и новый сценарий её не забудет. Пока этого
 * не было, канал комнаты отдавал подписчику приветственный кадр и молчал:
 * игрок не видел, как вошёл другой, пока не обновлял страницу
 * ({@code RoomRealtimeE2ETest} держит это под присмотром). Заявки в друзья и
 * готовность команды публикуют свои области ({@code socialChanged},
 * {@code preflightChanged}).
 *
 * <p>Здесь остаётся то, что в шину само не попадает: события, которые их
 * области публикуют на своём языке — переписка ({@code DirectChatChangedEvent}),
 * партия ({@code MatchEvents}) и админская консоль ({@code AdminEvictionEvents}).
 * У каждого из них есть второй адресат, о котором область не знает: значок в
 * шапке, витрина лобби, канал комнаты. Перевод на язык каналов собран в одном
 * месте, чтобы область не тянула к себе знание о том, кто её слушает.
 *
 * <p>Прежде класс переводил ещё и записи документного шлюза по первому
 * сегменту пути документа; шлюза нет, и та половина ушла вместе с ним.
 *
 * <p>{@code @EventListener}, а не {@code @TransactionalEventListener}:
 * переводить надо <b>внутри</b> транзакции писателя. Событие канала,
 * опубликованное здесь, само дождётся фиксации — его слушатели помечены
 * {@code AFTER_COMMIT}. Перевод после коммита опоздал бы на целую фазу: новое
 * событие публиковалось бы уже вне транзакции, и слушатели канала его
 * пропустили бы.
 */
@Component
@RequiredArgsConstructor
public class LegacyChangeBridge {

    private final RealtimeChangeBus bus;

    /**
     * Новое сообщение переписки — это ещё и значок непрочитанного в шапке.
     *
     * <p>Само окно переписки живёт своим каналом и своим событием; здесь
     * важна вторая половина: у обоих участников изменились входящие, а их
     * показывает {@code /ws/v2/me/social}.
     *
     * <p>Пара разбирается обратно тем же правилом, которым её собирают
     * ({@code Ids.pair}): два идентификатора через подчёркивание, меньший
     * первым. Идентификатор игрока подчёркиваний не содержит, поэтому
     * разделение однозначно.
     */
    @EventListener
    public void onDirectChatChanged(DirectChatChangedEvent event) {
        String pair = event.pair();
        if (pair == null) {
            return;
        }
        int separator = pair.indexOf('_');
        if (separator <= 0 || separator == pair.length() - 1) {
            return;
        }
        bus.socialChanged(pair.substring(0, separator));
        bus.socialChanged(pair.substring(separator + 1));
    }

    /** Диверсия записана в комнату: сцена и мониторы должны её увидеть. */
    @EventListener
    public void onSabotageApplied(MatchEvents.SabotageApplied applied) {
        bus.roomChanged(applied.roomId());
    }

    /** Партия кончилась технически — это смена фазы, а её видит и витрина. */
    @EventListener
    public void onMatchTerminated(MatchEvents.MatchTerminated terminated) {
        bus.roomRowChanged(terminated.roomId());
    }

    /**
     * Игрока забанили: ему это говорит канал социальных событий, а комнате,
     * из которой его выставляют, — канал комнаты.
     */
    @EventListener
    public void onPlayerBanned(AdminEvictionEvents.PlayerBanned banned) {
        bus.socialChanged(banned.uid());
        bus.roomChanged(banned.roomId());
    }

    /** Комнату закрыл администратор: и участникам, и витрине. */
    @EventListener
    public void onRoomClosedByAdmin(AdminEvictionEvents.RoomClosedByAdmin closed) {
        bus.roomRowChanged(closed.roomId());
    }
}
