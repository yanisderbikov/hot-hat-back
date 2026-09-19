package ru.hothat.app.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.app.store.FeatureFlagStore;

/** Реализация {@link FeatureFlagPort}: живёт у владельца таблицы. */
@Component
@RequiredArgsConstructor
public class FeatureFlagDirectory implements FeatureFlagPort {

    private final FeatureFlagStore flags;

    @Override
    public boolean enabled(String name) {
        return flags.enabled(name);
    }
}
