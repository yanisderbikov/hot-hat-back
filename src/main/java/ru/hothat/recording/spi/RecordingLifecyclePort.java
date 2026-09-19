package ru.hothat.recording.spi;

import java.util.Map;

/**
 * Жизненный цикл записи глазами чужих областей.
 *
 * <p>Кому это нужно. Партия умеет сказать «снимать больше нечего» — и только
 * это. Машинная половина принимает уведомления LiveKit, отмечает шаги
 * рекордера и убирает просроченные файлы. Ни той, ни другой не нужны таблицы
 * записей, и порт ровно об этом: чужая область называет СОБЫТИЕ, а что
 * записать в девять таблиц — решает область записей.
 *
 * <p>Начинать съёмку через порт нельзя намеренно: старт требует прав игрока,
 * проверки хранилища и разговора с LiveKit, и у него есть свой адрес.
 */
public interface RecordingLifecyclePort {

    /** Причины остановки без человека: они попадают в журнал, а не в ответ. */
    String REASON_CEREMONY = "ceremony-complete";
    String REASON_TECHNICAL = "technical-termination";
    String REASON_ABANDONED = "room-abandoned";

    /**
     * Остановить запись без участника: техническое поражение, конец церемонии,
     * уборка заброшенной комнаты.
     */
    FinishResult finishBySystem(String roomId, int gameNumber, String reason);

    /** Рекордер подключился к комнате. */
    void markRecorderReady(String roomId, int gameNumber, String phase, String identity);

    /** Рекордер сообщил, что пошла запись. */
    void markRecorderStarted(String roomId, int gameNumber, String phase, String identity);

    /**
     * Применить уведомление LiveKit о выгрузке.
     *
     * @param deliveryId ключ доставки: по нему повтор отличается от нового
     *                   события. Раньше отличить его было не по чему, и повтор
     *                   применялся второй раз
     */
    WebhookResult applyEgressWebhook(String roomId, int gameNumber, String deliveryId,
                                     String eventName, Map<String, Object> egressInfo);

    /** Убрать файлы записей, которым вышел срок и которых никто не сохранил. */
    SweepResult sweepExpired(int limit);

    /** Исход остановки. {@code stage} — стадия записи после неё, если она известна. */
    record FinishResult(Finish outcome, String recordingId, String stage) {
    }

    enum Finish {
        FINISHED,
        ALREADY_FINISHED,
        NOT_STARTED,
        RECORDING_DISABLED
    }

    /**
     * Исход уведомления.
     *
     * @param liveKitCode сырой код состояния LiveKit; он нужен только ответу
     *                    самому LiveKit и в наши стадии не превращается
     */
    record WebhookResult(Webhook outcome, String recordingId, String stage, int liveKitCode) {
    }

    enum Webhook {
        APPLIED,
        IGNORED_DUPLICATE,
        IGNORED_RECORDING_NOT_FOUND,
        IGNORED_EGRESS_ID_MISMATCH
    }

    /**
     * Итог прогона уборки.
     *
     * @param checked  сколько записей просмотрено
     * @param affected сколько убрано: сохранённые кем-то просматриваются, но не удаляются
     */
    record SweepResult(int checked, int affected, java.util.List<String> retired) {
    }
}
