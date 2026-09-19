package ru.hothat.admin.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Блокировка игрока: история, а не флаг.
 *
 * <p>Имя класса не {@code UserBan}: так называется сущность старой схемы.
 * Таблица — {@code v2.user_ban}.
 *
 * <p>Сегодня бан — это булев {@code app_user.banned} плюс строка причины,
 * которую заводили не всегда, плюс поколение токенов: три источника правды на
 * один вопрос. Снятие бана строку удаляло, поэтому «сколько раз этого
 * человека банили» не знал никто. Здесь снятие — это {@link #liftedAt}, и
 * история остаётся.
 *
 * <p>Ключ суррогатный, потому что банов у игрока со временем много; «забанен
 * ли он сейчас» отвечает частичный уникальный индекс
 * {@code ux_user_ban_active}, а не колонка.
 *
 * <p>{@link #bannedBy} и {@link #liftedBy} — авторство и потому без внешнего
 * ключа (см. шапку миграции V13). Самобан запрещён ограничением базы: он
 * выглядит как опечатка в чужом uid, но стоит администратору доступа в
 * собственную панель.
 */
@Entity
@Table(name = "user_ban", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class PlayerBan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    @Column(nullable = false, updatable = false, length = 300)
    private String reason;

    @Column(name = "banned_by", nullable = false, updatable = false)
    private UUID bannedBy;

    @Builder.Default
    @Column(name = "banned_at", nullable = false, updatable = false)
    private Instant bannedAt = Instant.now();

    @Column(name = "lifted_at")
    private Instant liftedAt;

    /** Ставится вместе с датой снятия: этого требует ограничение базы. */
    @Column(name = "lifted_by")
    private UUID liftedBy;

    /** Действует ли блокировка прямо сейчас. */
    boolean active() {
        return liftedAt == null;
    }
}
