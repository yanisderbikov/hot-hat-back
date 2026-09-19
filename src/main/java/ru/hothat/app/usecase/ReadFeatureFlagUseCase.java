package ru.hothat.app.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.app.api.dto.FeatureFlagResponseDTO;
import ru.hothat.app.store.FeatureFlagStore;

/**
 * Сказать, включена ли возможность.
 *
 * <p>Открыто до входа намеренно: экран регистрации тоже прячет и показывает
 * части интерфейса по флагам, а токена там ещё нет. Секрета во флаге нет —
 * он говорит только «эта кнопка сейчас есть», а само действие за кнопкой
 * по-прежнему проверяет права.
 *
 * <p>Перечислить флаги этим адресом нельзя: спрашивают по одному имени, и
 * набор имён закрыт проверкой на входе. Так набор возможностей продукта не
 * утекает списком тому, кто просто открыл главную.
 */
@Service
@RequiredArgsConstructor
public class ReadFeatureFlagUseCase {

    private final FeatureFlagStore flags;

    @PreAuthorize("permitAll()")
    @Transactional(readOnly = true)
    public FeatureFlagResponseDTO run(String name) {
        return new FeatureFlagResponseDTO(name, flags.enabled(name));
    }
}
