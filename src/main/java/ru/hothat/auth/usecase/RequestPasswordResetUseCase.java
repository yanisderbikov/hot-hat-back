package ru.hothat.auth.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.auth.api.dto.PasswordResetAcceptedResponseDTO;
import ru.hothat.auth.api.dto.RequestPasswordResetRequestDTO;
import ru.hothat.auth.store.IdentityStore;
import ru.hothat.config.HotHatProperties;

/**
 * Запросить ссылку восстановления пароля.
 *
 * <p>Ответ одинаков для существующего и несуществующего адреса: иначе форма
 * превращается в проверялку, зарегистрирован ли e-mail. Гость сюда попасть не
 * может — пароля у него нет.
 *
 * <p>Саму ссылку не логируем. Токен хранится в базе хешем, поэтому строка
 * журнала была бы единственной копией открытым текстом: кто читает логи, тот
 * меняет пароль любому запросившему (CWE-532). Отправителя писем в этой фазе
 * нет, и молчать об этом нельзя — заявка выглядела бы обслуженной.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestPasswordResetUseCase {

    private final IdentityStore identities;
    private final HotHatProperties properties;

    @PreAuthorize("permitAll()")
    @Transactional
    public PasswordResetAcceptedResponseDTO run(RequestPasswordResetRequestDTO request) {
        identities.byEmail(request.email())
                .filter(IdentityStore.Account::passwordSet)
                .ifPresent(account -> {
                    String token = identities.openPasswordReset(account.uid(), null);
                    // Ссылка собирается, но никуда не уходит: отправителя нет.
                    // Переменная нужна, чтобы это было видно в коде, а не
                    // выглядело забытым вызовом.
                    String link = properties.publicSiteUrl() + "/reset-password?token=" + token;
                    log.warn("Восстановление пароля запрошено, ссылка длиной {} символов выпущена, "
                            + "но отправить её нечем: почтовый отправитель не подключён. "
                            + "Саму ссылку в журнал не пишем намеренно — выдайте её человеку "
                            + "другим каналом.", link.length());
                });
        return new PasswordResetAcceptedResponseDTO(true);
    }
}
