package ru.hothat.auth.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.auth.api.dto.RegisterAccountRequestDTO;
import ru.hothat.auth.api.dto.RegisteredAccountResponseDTO;
import ru.hothat.auth.store.IdentityStore;
import ru.hothat.config.ApiException;
import ru.hothat.profile.spi.PlayerCardPort;
import ru.hothat.profile.spi.ProfileCommandPort;

/**
 * Завести учётную запись и сразу открыть сессию.
 *
 * <p>Открыт всем по очевидной причине.
 *
 * <p>Одна транзакция на две области: учётка в {@code auth}, карточка игрока в
 * {@code profile} (§7.3, строка «Регистрация»). Разнести их нельзя — учётка
 * без имени непредставима, а событием карточку не завести: это инвариант, а
 * не проекция. Пишет карточку по-прежнему её владелец, через командный порт.
 *
 * <p>Занятость почты и ника проверяется до вставки ради понятной ошибки, но
 * последнее слово — за уникальными индексами базы: разнесённая проверка отдала
 * бы двум одновременным регистрациям один ник.
 *
 * <p>Отказы: {@code EMAIL_TAKEN} и {@code NICKNAME_TAKEN} (409),
 * {@code INVALID_EMAIL} и {@code WEAK_PASSWORD} (400).
 */
@Service
@RequiredArgsConstructor
public class RegisterAccountUseCase {

    private final IdentityStore identities;
    private final PlayerCardPort cards;
    private final ProfileCommandPort profiles;
    private final SessionIssuer issuer;

    @PreAuthorize("permitAll()")
    @Transactional
    public RegisteredAccountResponseDTO run(RegisterAccountRequestDTO request, String userAgent) {
        if (identities.emailTaken(request.email())) {
            throw ApiException.of("EMAIL_TAKEN", 409);
        }
        if (cards.nicknameTaken(request.nickname())) {
            throw ApiException.of("NICKNAME_TAKEN", 409);
        }
        IdentityStore.Account account = identities.openMember(request.email(), request.password());
        // Дивизион при заведении не закрепляется: его выбирает игрок на
        // онбординге. Раньше первое же чтение профиля ставило отметку выбора,
        // и новичок оказывался в русском дивизионе, не выбрав ничего.
        profiles.createCard(account.uid(), request.nickname(), null);
        return new RegisteredAccountResponseDTO(
                issuer.open(account, userAgent, null), issuer.view(account));
    }
}
