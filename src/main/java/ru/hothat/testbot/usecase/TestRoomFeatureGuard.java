package ru.hothat.testbot.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.hothat.common.api.ErrorCode;
import ru.hothat.app.spi.FeatureFlagPort;

/**
 * Тестовая комната закрыта флагом {@code bot_enabled}.
 *
 * <p>Проверка обязана жить на сервере: интерфейс обходится дописыванием
 * {@code ?test=1} в адрес. Раньше она стояла в контроллере — там ей не место,
 * это предусловие сценария, а не разбор запроса.
 */
@Component
@RequiredArgsConstructor
public class TestRoomFeatureGuard {

    private static final String BOT_FEATURE = "bot_enabled";

    private final FeatureFlagPort features;

    public void requireEnabled() {
        if (!features.enabled(BOT_FEATURE)) {
            throw ErrorCode.FEATURE_DISABLED.raise();
        }
    }
}
