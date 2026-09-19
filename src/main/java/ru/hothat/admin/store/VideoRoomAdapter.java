package ru.hothat.admin.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.hothat.admin.port.VideoRoomPort;
import ru.hothat.common.livekit.LiveKitClient;

/**
 * Переходник к видеослужбе.
 *
 * <p>Отказ видеослужбы не роняет сценарий и не откатывает уже случившееся
 * решение администратора: бан записан, комната закрыта, а до LiveKit нам
 * просто не дозвониться. Участник без токена туда всё равно не вернётся —
 * проверка отвергнет его на входе.
 *
 * <p>Разные уровни записи в журнал не случайны. Выставление участника,
 * который уже сам ушёл, — обычное дело и шумит зря; неудачное гашение целой
 * комнаты означает висящую видеокомнату, и о ней стоит знать.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoRoomAdapter implements VideoRoomPort {

    private final LiveKitClient liveKit;

    @Override
    public void evict(String roomId, String uid) {
        try {
            liveKit.removeParticipant(roomId, uid);
        } catch (RuntimeException e) {
            log.debug("Участник {} уже покинул LiveKit: {}", uid, e.getMessage());
        }
    }

    @Override
    public void close(String roomId) {
        try {
            liveKit.deleteRoom(roomId);
        } catch (RuntimeException e) {
            log.warn("Комната LiveKit {} не удалена: {}", roomId, e.getMessage());
        }
    }
}
