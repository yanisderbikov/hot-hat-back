package ru.hothat.service.monitor;

import java.util.Map;

/**
 * Метрики машины, на которой работает бекенд. Раньше их отдавал отдельный
 * Python-сервис на VPS, и бекенд ходил к нему по HTTP с общим секретом.
 * Теперь бекенд живёт на той же машине и читает /proc сам — посредник и
 * секрет между своими же процессами не нужны.
 */
public interface HostMetricsService {

    /** Форма ответа сохранена: админка разбирает те же ключи. */
    Map<String, Object> collect();

    /** false на не-Linux: /proc нет, и считать нечего (например, на macOS в разработке). */
    boolean supported();
}
