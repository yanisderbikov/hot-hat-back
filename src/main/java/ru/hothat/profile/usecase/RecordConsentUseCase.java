package ru.hothat.profile.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.RecordConsentRequestDTO;
import ru.hothat.profile.api.dto.RecordedConsentResponseDTO;
import ru.hothat.profile.store.ProfileStore;

/**
 * Зарегистрировать принятие правовых документов.
 *
 * <p>Раньше запись шла в двух местах сразу — отдельная строка-доказательство
 * и поля профиля, по которым проверялся допуск к игре, — и разойтись им было
 * нечем, кроме сбоя посередине. Теперь место одно: журнал согласий и есть
 * допуск. Подтверждение возраста живёт отметкой в карточке, потому что это
 * факт о человеке, а не о документе: версии у него нет.
 *
 * <p>Момент принятия проставляет сервер, поэтому ответ собирается по
 * сохранённому журналу, а не по телу запроса.
 */
@Service
@RequiredArgsConstructor
public class RecordConsentUseCase {

    private final ProfileStore profiles;
    private final ProfileViews views;

    @PreAuthorize("hasAnyRole('USER','GUEST')")
    @Transactional
    public RecordedConsentResponseDTO run(HotHatUser user, RecordConsentRequestDTO request) {
        long acceptedAt = profiles.recordConsents(
                user.uid(),
                views.versionsOf(request.versions()),
                Boolean.TRUE.equals(request.adultConfirmed()),
                null,
                null);
        return views.recordedConsent(profiles.consents(user.uid()), acceptedAt);
    }
}
