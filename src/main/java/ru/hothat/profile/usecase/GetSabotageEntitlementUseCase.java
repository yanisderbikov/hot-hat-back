package ru.hothat.profile.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.SabotageEntitlementResponseDTO;
import ru.hothat.sabotage.spi.SabotageArmoryPort;

/**
 * Показать квоту партий с диверсиями.
 *
 * <p>Транзакция без {@code readOnly}: чтение квоты пишет. Друг владельца
 * получает безлимит навсегда именно в этот момент, иначе дружбу пришлось бы
 * перепроверять при каждом заходе на главную; здесь же заводится строка
 * права, если игрок пришёл впервые.
 *
 * <p>Само правило живёт у владельца таблиц — {@link SabotageArmoryPort}. Этот
 * сценарий только называет адрес и переводит ответ в форму контракта.
 */
@Service
@RequiredArgsConstructor
public class GetSabotageEntitlementUseCase {

    private final SabotageArmoryPort armory;
    private final ProfileViews mapper;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public SabotageEntitlementResponseDTO run(HotHatUser user) {
        // Почта нужна квоте ради единственной проверки — «это владелец
        // сервиса»; у самой квоты почты нет и быть не должно.
        return new SabotageEntitlementResponseDTO(
                mapper.sabotageEntitlement(armory.entitlement(user.uid(), user.email())));
    }
}
