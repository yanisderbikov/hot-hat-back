package ru.hothat.service.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.hothat.service.cleanup.RoomCleanupService;
import ru.hothat.service.monitor.MonitorService;
import ru.hothat.service.recording.RecordingService;

import java.util.Map;

/**
 * Замена секции crons из vercel.json. Расписания вынесены в настройки, чтобы
 * dev-контур мог убирать мусор чаще прода.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledMaintenance {

    private final RoomCleanupService roomCleanupService;
    private final RecordingService recordingService;
    private final MonitorService monitorService;

    // Частая уборка критична для 30-секундного TTL тестовых комнат.
    // Отдельный interval не зависит от старого CLEANUP_ROOMS_CRON в Railway:
    // устаревшее env-значение cron ранее оставляло тестовые комнаты на часы.
    @Scheduled(fixedDelayString = "${cleanup.rooms.interval-ms:10000}",
               initialDelayString = "${cleanup.rooms.initial-delay-ms:3000}")
    public void cleanupRooms() {
        try {
            Map<String, Object> result = roomCleanupService.sweep();
            log.info("Уборка комнат: {}", result);
        } catch (RuntimeException e) {
            log.error("Уборка комнат не выполнена", e);
        }
    }

    @Scheduled(cron = "${cleanup.recordings.cron}")
    public void cleanupRecordings() {
        try {
            Map<String, Object> result = recordingService.cleanupExpired(100);
            log.info("Уборка записей: {}", result);
        } catch (RuntimeException e) {
            log.error("Уборка записей не выполнена", e);
        }
    }

    /** Снимок расхода ресурсов и письма о превышении — четыре раза в сутки. */
    @Scheduled(cron = "${monitor.collect.cron}")
    public void collectUsage() {
        try {
            monitorService.collectAndPersist(true);
        } catch (RuntimeException e) {
            log.error("Снимок мониторинга не снят", e);
        }
    }
}
