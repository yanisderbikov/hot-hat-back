package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.admin.api.dto.ByteUsageView;
import ru.hothat.admin.api.dto.HostMetricsResponseDTO;
import ru.hothat.admin.api.dto.HostServiceStateView;
import ru.hothat.admin.port.HostMetricsPort;
import ru.hothat.config.HotHatUser;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Показать администратору состояние машины прямо сейчас.
 *
 * <p>Раньше эти числа доставались только внутри снимка мониторинга. Свой адрес
 * нужен потому, что снимок — это хранимая история, снимаемая четыре раза в
 * сутки, а загрузка процессора и слушающие порты интересны сию секунду.
 *
 * <p>Ответ один на оба исхода: на не-Linux {@code supported} равен false, а
 * измерения — null. Прежний движок в этом случае отдавал объект другой формы,
 * и клиенту приходилось различать их по наличию ключа (C6).
 *
 * <p>Разбор сырых показаний уехал в порт: подписи и порядок служб те же, что в
 * сводке «здоровье систем» внутри снимка, и берутся они теперь из одного
 * перечисления, а не из двух списков, которым полагалось совпадать.
 */
@Service
@RequiredArgsConstructor
public class GetHostMetricsUseCase {

    private final HostMetricsPort host;
    private final Clock clock;

    @PreAuthorize("hasRole('ADMIN')")
    public HostMetricsResponseDTO run(HotHatUser admin) {
        Optional<HostMetricsPort.HostSnapshot> read = host.read();
        if (read.isEmpty()) {
            return new HostMetricsResponseDTO(false,
                    HostMetricsPort.UNSUPPORTED_REASON,
                    clock.millis(),
                    null, null, null, null, null, null, null, null, null, List.of());
        }
        HostMetricsPort.HostSnapshot snapshot = read.get();
        return new HostMetricsResponseDTO(
                true,
                null,
                snapshot.takenAtMs(),
                bytes(snapshot.disk()),
                bytes(snapshot.memory()),
                snapshot.networkRxBytes(),
                snapshot.networkTxBytes(),
                snapshot.mediaBytes(),
                snapshot.loadAverage(),
                snapshot.cpuCores(),
                snapshot.livekitSockets(),
                snapshot.turnSockets(),
                snapshot.units().stream().map(GetHostMetricsUseCase::unit).toList());
    }

    private static HostServiceStateView unit(HostMetricsPort.UnitState state) {
        return new HostServiceStateView(
                state.unit().label(),
                state.systemdUnit(),
                state.state(),
                state.active(),
                state.port(),
                state.listening());
    }

    private static ByteUsageView bytes(HostMetricsPort.Bytes value) {
        return new ByteUsageView(value.used(), value.total(), value.free());
    }
}
