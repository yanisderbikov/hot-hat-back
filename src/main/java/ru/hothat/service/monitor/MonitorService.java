package ru.hothat.service.monitor;

import java.util.Map;

/** Снимок расхода ресурсов, письма о превышении и история по дням. */
public interface MonitorService {

    Map<String, Object> collect();

    /** Сохраняет снимок и, если нужно, шлёт письма — вызывается из крона и из админки. */
    Map<String, Object> collectAndPersist(boolean withNotifications);

    Map<String, Object> history(String start, String end);

    /** Счётчик обращений к базе, который фронтенд шлёт для оценки нагрузки. */

    /** Разрешён ли плановый запуск в текущий час (четыре раза в сутки). */
    boolean scheduledWindowAllowed();
}
