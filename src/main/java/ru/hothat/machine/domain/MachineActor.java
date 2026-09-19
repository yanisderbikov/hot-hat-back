package ru.hothat.machine.domain;

/**
 * Машинный актор — тот, у кого нет и не может быть учётной записи человека.
 *
 * <p>Их пятеро, и сегодня каждый удостоверяется по-своему и в своём месте:
 * рекордер — подписью в строке запроса ({@code RecordingStateController:32}),
 * вебхук Egress — подписью в строке запроса и ручным {@code if} в контроллере
 * ({@code RecordingStateController:44}), cron — сравнением строки
 * {@code equals} внутри метода ({@code CleanupController.cronAuthorized}),
 * агент мониторинга — таким же {@code equals} в другом методе
 * ({@code MonitorController.secretOk}). Все четыре маршрута при этом объявлены
 * {@code permitAll} в {@code WebSecurityConfig:58-62}, то есть в декларации
 * прав их нет вовсе.
 *
 * <p>Здесь актор — это роль Spring Security, и она выдаётся ровно одним местом:
 * {@code ru.hothat.machine.security.MachineActorFilter}. Дальше право
 * выражается обычным {@code @PreAuthorize} на сценарии, как у людей.
 */
public enum MachineActor {

    /** Рекордер снаряжается перед съёмкой: получает сцену и токен LiveKit. */
    RECORDER_BOOTSTRAP,

    /** Рекордер снимает партию: читает сцену и отмечает ход съёмки. */
    RECORDER,

    /** LiveKit Egress сообщает о состоянии выгрузки файла. */
    EGRESS,

    /** Внешний планировщик запускает плановую уборку. */
    CRON,

    /** Агент мониторинга просит снять снимок расхода ресурсов. */
    MONITOR_AGENT;

    /** Имя роли в терминах Spring Security: {@code hasRole('CRON')} и так далее. */
    public String authority() {
        return "ROLE_" + name();
    }
}
