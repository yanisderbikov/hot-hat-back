package ru.hothat.room.spi;

import ru.hothat.config.HotHatUser;

import java.util.List;

/**
 * Пропуск в видеосвязь комнаты — для соседей.
 *
 * <p>Порт объявлен у владельца комнаты по той же причине, что и
 * {@link RoomDirectoryPort}: кого пускать за стол и в зал, решает область
 * комнаты. До сих пор решал отдельный движок токенов со своей копией допуска,
 * и спрашивали его двое — сама комната и витрина лобби, где карточка комнаты
 * показывает живое видео.
 *
 * <p>Порт ничего не пишет: строку зрителя заводит тот, кто его сажает. Здесь
 * только подпись пропуска для того, кто уже сидит.
 */
public interface RoomVideoPort {

    /**
     * Пропуск игрока.
     *
     * @param participantIdentity участник, за которого просят; пусто — за себя
     */
    VideoTicket playerTicket(HotHatUser user, String roomId, String participantIdentity);

    /**
     * Пропуск зрителя.
     *
     * @param previewSession сессия превью с главной; пусто — зритель смотрит
     *                       из самой комнаты. Идентификатор сессии называет
     *                       сервер: назвавшись чужой сессией, можно было выбить
     *                       из комнаты другого зрителя
     */
    VideoTicket spectatorTicket(HotHatUser user, String roomId, String previewSession);

    /**
     * Выданный пропуск.
     *
     * @param turn учётка ретранслятора; {@code null} — ретранслятор не настроен,
     *             связь держится напрямую
     */
    record VideoTicket(String serverUrl, String participantToken, String participantIdentity,
                       Turn turn) {
    }

    /** Короткоживущая учётка ретранслятора. */
    record Turn(List<String> urls, String username, String credential,
                long ttlSeconds, long expiresAtSeconds) {
    }
}
