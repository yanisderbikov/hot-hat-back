package ru.hothat.admin.port;

import ru.hothat.admin.domain.HostUnit;

import java.util.List;
import java.util.Optional;

/**
 * Машина, на которой работает бекенд, глазами консоли администратора.
 *
 * <p>Ответ типизирован, потому что читателей у него два — карточка «состояние
 * машины» прямо сейчас и раздел VPS внутри снимка расхода, — и разбирать одну
 * и ту же карту в двух местах значило бы дважды написать одни и те же ключи.
 *
 * <p>Пустой {@link #read()} — это не сбой, а ответ: метрики берутся из
 * {@code /proc}, которого нет на macOS. Отличить «нет метрик» от нулей
 * обязательно, иначе консоль покажет машину с нулевым диском.
 */
public interface HostMetricsPort {

    /** Причина, по которой метрик не бывает; показывается администратору как есть. */
    String UNSUPPORTED_REASON = "Метрики хоста доступны только на Linux (/proc)";

    /** Снимок машины; пусто — считать нечего. */
    Optional<HostSnapshot> read();

    /** Всё, что известно о машине в один момент. */
    record HostSnapshot(long takenAtMs,
                        Bytes disk,
                        Bytes memory,
                        Long networkRxBytes,
                        Long networkTxBytes,
                        Long mediaBytes,
                        Double loadAverage,
                        Integer cpuCores,
                        Long livekitSockets,
                        Long turnSockets,
                        List<UnitState> units) {
    }

    /** Занято из сколького; {@code free} посчитан, а не додуман. */
    record Bytes(long used, long total, long free) {
    }

    /**
     * Состояние одной службы вместе с её портом.
     *
     * <p>{@code listening} и {@code port} необязательны: у службы может не
     * быть ожидаемого порта, и это не то же самое, что «порт молчит».
     */
    record UnitState(HostUnit unit, String systemdUnit, String state, boolean active,
                     Integer port, Boolean listening) {

        /** Служба в порядке, когда и systemd доволен, и порт слушает. */
        public boolean healthy() {
            return active && (listening == null || listening);
        }
    }
}
