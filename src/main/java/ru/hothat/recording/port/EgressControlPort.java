package ru.hothat.recording.port;

import java.util.Optional;

/**
 * Задание Egress глазами области записей.
 *
 * <p>Порт нужен ради одного правила: сетевой вызов LiveKit идёт ВНЕ
 * транзакции. Раньше он делался внутри неё, и транзакция держала соединение
 * из пула на все двадцать секунд таймаута при пуле в десять соединений —
 * находка B5. Порт делает границу видимой: всё, что здесь, — снаружи
 * транзакции, а её открывает сценарий уже с готовым ответом.
 */
public interface EgressControlPort {

    /** Настроен ли LiveKit: без ключей снимать нечем. */
    boolean available();

    /** Запросить съёмку. Бросает, если LiveKit отказал: старт без задания бессмыслен. */
    Snapshot start(String roomId, int gameNumber, String objectPath);

    /**
     * Остановить съёмку. Пусто — LiveKit не ответил ничем полезным; повтор
     * безопасен: уже завершённое задание он подтверждает, зависшее закрывает.
     */
    Optional<Snapshot> stop(String egressId);

    /** Спросить состояние задания, ничего не меняя. */
    Optional<Snapshot> describe(String egressId);

    /**
     * Ответ LiveKit, приведённый к нашим понятиям.
     *
     * @param liveKitCode сырой код состояния; отрицательный — кода не было
     * @param endedAtMs   когда задание кончилось; {@code null} — ещё не кончилось
     */
    record Snapshot(String egressId, int liveKitCode, long durationNs, long sizeBytes,
                    String filename, String error, Long endedAtMs) {
    }
}
