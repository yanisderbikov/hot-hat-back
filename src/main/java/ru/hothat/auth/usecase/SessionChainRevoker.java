package ru.hothat.auth.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.hothat.auth.store.IdentityStore;

import java.util.UUID;

/**
 * Гашение цепочки при повторном предъявлении refresh-токена.
 *
 * <p>Отдельный бин с отдельной транзакцией, а не приватный метод сценария, —
 * из-за того, чем реюз кончается. Кончается он ответом 401, то есть
 * исключением, а исключение откатывает транзакцию сценария вместе со всем,
 * что в ней сделано: сервер ответил бы «сессия истекла», а украденная цепочка
 * осталась бы жива. Проверено вживую на прежнем движке — до появления такого
 * бина дочерний токен после реюза продолжал обмениваться.
 *
 * <p>{@code REQUIRES_NEW} даёт отзыву собственную транзакцию: она коммитится
 * до того, как исключение уйдёт наверх. Приватным методом того же класса это
 * недостижимо — вызов внутри объекта идёт мимо прокси.
 */
@Component
@RequiredArgsConstructor
public class SessionChainRevoker {

    private final IdentityStore identities;

    /**
     * Гасит и всю цепочку ротаций, и поколение токенов владельца: без второго
     * украденный access-токен работал бы ещё свои пятнадцать минут.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeEverything(String uid, UUID familyId) {
        identities.closeFamily(familyId, IdentityStore.REASON_REUSE);
        identities.revokeAccess(uid, IdentityStore.REASON_REUSE);
    }
}
