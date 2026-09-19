package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.admin.store.ModerationStore;
import ru.hothat.auth.spi.AccountPort;
import ru.hothat.auth.spi.IdentityCommandPort;
import ru.hothat.common.api.ErrorCode;
import ru.hothat.config.HotHatUser;

import java.util.Map;

/**
 * Снять блокировку с игрока.
 *
 * <p>Заменяет {@code POST /api/admin} с {@code action=unban_user}.
 *
 * <p>Строка блокировки не удаляется, а закрывается: {@code lifted_at} и
 * {@code lifted_by}. Раньше снятие делало {@code DELETE}, и на вопрос
 * «сколько раз этого человека блокировали» ответить было нечем — история
 * стиралась вместе с фактом.
 *
 * <p>Автор снятия теперь сохраняется строкой {@code v2.moderation_action}, а
 * не строкой журнала приложения: до появления таблицы имя администратора
 * оставалось только в логе и уходило с первой же ротацией.
 *
 * <p>Поколение токенов поднимается и здесь, хотя блокировка снимается. Это не
 * симметрия ради симметрии: пока человек был забанен, его старые токены
 * отвергались, и вернуть их к жизни значило бы воскресить сессии, которых он
 * не открывал заново. Пусть входит.
 */
@Service
@RequiredArgsConstructor
public class LiftBanUseCase {

    private final ModerationStore moderation;
    private final AccountPort accounts;
    private final IdentityCommandPort identities;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void run(HotHatUser admin, String uid) {
        if (accounts.account(uid).isEmpty()) {
            throw ErrorCode.BAN_TARGET_NOT_FOUND.raise();
        }
        boolean lifted = moderation.liftBan(uid, admin.uid());
        identities.revokeAccess(uid, IdentityCommandPort.REASON_BAN);
        moderation.record(admin.uid(), ModerationStore.ACTION_LIFT_BAN,
                ModerationStore.SUBJECT_PLAYER, uid, Map.of("wasBanned", lifted));
    }
}
