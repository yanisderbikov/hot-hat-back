package ru.hothat.service.monitor.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.hothat.admin.store.HostMetricsReader;
import ru.hothat.service.monitor.HostMetricsService;

import java.util.Map;

/**
 * Прежний фасад показаний машины: теперь переходник к {@link HostMetricsReader}.
 *
 * <p>Чтение /proc переехало к владельцу раздела наблюдения. Фасад жив, пока
 * жив старый снимок расхода, который им пользуется.
 */
@Service
@RequiredArgsConstructor
public class HostMetricsServiceImpl implements HostMetricsService {

    private final HostMetricsReader reader;

    @Override
    public Map<String, Object> collect() {
        return reader.collect();
    }

    @Override
    public boolean supported() {
        return reader.supported();
    }
}
