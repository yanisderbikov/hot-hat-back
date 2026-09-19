package ru.hothat.profile.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.config.HotHatUser;
import ru.hothat.profile.api.dto.ConsumeSabotageEntitlementRequestDTO;
import ru.hothat.profile.api.dto.ConsumedSabotageEntitlementResponseDTO;
import ru.hothat.sabotage.spi.SabotageArmoryPort;

/**
 * Списать начатую партию из квоты диверсий.
 *
 * <p>Заменяет действие {@code consume_sabotage_game}. Списание идёт по ключу
 * «игрок + комната + номер партии», и ключ этот — уникальный индекс журнала
 * списаний, а не склейка строк: повторный запрос за ту же партию не тратит
 * вторую. Клиент шлёт списание сразу после старта партии и легко повторяет
 * его при переподключении.
 *
 * <p>Ответ на вопрос «списалось ли именно сейчас» приходит от самого
 * списания, а не вычитанием остатка до и после. Прежде здесь стояли три
 * обращения — квота, списание, квота снова, — и разница остатков врала при
 * безлимите, где остатка нет вовсе.
 *
 * <p>Транзакция общая: между записью в журнал и ростом счётчика не должна
 * помещаться чужая вкладка того же игрока. Исчерпанная квота — отказ 402
 * {@code SABOTAGE_LIMIT_REACHED}, и журнальная запись откатывается вместе
 * с ним.
 */
@Service
@RequiredArgsConstructor
public class ConsumeSabotageEntitlementUseCase {

    private final SabotageArmoryPort armory;
    private final ProfileViews mapper;

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public ConsumedSabotageEntitlementResponseDTO run(HotHatUser user,
                                                      ConsumeSabotageEntitlementRequestDTO request) {
        boolean consumed = armory.consume(user.uid(), request.roomId(), request.gameNumber());
        return new ConsumedSabotageEntitlementResponseDTO(consumed,
                mapper.sabotageEntitlement(armory.entitlement(user.uid(), user.email())));
    }
}
