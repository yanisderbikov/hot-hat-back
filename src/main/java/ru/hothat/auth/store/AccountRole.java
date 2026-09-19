package ru.hothat.auth.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Кто администратор и кто владелец.
 *
 * <p>Сегодня ответ лежит в настройках приложения: списки {@code admin-uids} и
 * {@code admin-emails} плюс {@code owner-email}. Права, заданные почтой,
 * означают, что смена почты меняет права, а опечатка в списке раздаёт их
 * молча и до перезапуска. Здесь право — строка: у неё есть выдавший и дата,
 * и снять её можно, не трогая конфигурацию.
 *
 * <p>Владелец у сервиса ровно один — это держит частичный уникальный индекс
 * {@code ux_account_role_single_owner}, а не соглашение.
 */
@Entity
@Table(name = "account_role", schema = "v2")
@IdClass(AccountRoleId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class AccountRole {

    /** ADMIN | OWNER — набор закрыт ограничением базы. */
    static final String ADMIN = "ADMIN";
    static final String OWNER = "OWNER";

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    @Id
    @Column(nullable = false, updatable = false, length = 16)
    private String role;

    /**
     * Кто выдал право. Пусто у самого первого владельца — его некому было
     * назначить. Внешнего ключа нет: авторство обязано пережить учётку
     * выдавшего (см. шапку миграции V13).
     */
    @Column(name = "granted_by")
    private UUID grantedBy;

    @Builder.Default
    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt = Instant.now();
}
