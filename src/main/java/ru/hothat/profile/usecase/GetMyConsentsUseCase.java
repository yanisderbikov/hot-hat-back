package ru.hothat.profile.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.MyConsentsResponseDTO;
import ru.hothat.profile.store.ProfileStore;

/**
 * Показать, какие правовые документы игрок уже принял.
 *
 * <p>Гость видит свои согласия так же, как зарегистрированный: играть без
 * принятых правил нельзя никому, а значит и знать о них должны оба.
 *
 * <p>Ответ собирается из журнала согласий — строки на документ, — а не из
 * поля {@code jsonb} в учётке. Разница видна на вопросе «принял ли он
 * нынешнюю редакцию»: по журналу это строка с нужной версией, по полю —
 * разбор карты в памяти после чтения всей учётки.
 */
@Service
@RequiredArgsConstructor
public class GetMyConsentsUseCase {

    private final ProfileStore profiles;
    private final ProfileViews views;

    @PreAuthorize("hasAnyRole('USER','GUEST')")
    public MyConsentsResponseDTO run(HotHatUser user) {
        return views.consents(profiles.consents(user.uid()));
    }
}
