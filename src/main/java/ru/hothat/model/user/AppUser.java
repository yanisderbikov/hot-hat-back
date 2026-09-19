package ru.hothat.model.user;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Оболочка учётки: то немногое, чей кластер ещё не переезжал на схему v2.
 *
 * <p>Было тридцать шесть колонок и шесть разных владельцев в одной строке.
 * Личность, карточка игрока, присутствие, согласия и блокировка уехали в
 * одиннадцать таблиц схемы {@code v2} (миграция V13); команда — в
 * {@code v2.ranked_team_member} (V11); обойма мемов и квота диверсий — в
 * {@code v2.player_default_loadout} и {@code v2.sabotage_entitlement} (V16).
 * Осталась одна вещь: история слов рейтинговых партий, чей кластер
 * ({@code words}) ещё не переезжал.
 *
 * <p><b>Полей личности здесь нет намеренно.</b> Колонки в таблице пока
 * остались — их снимет отдельная миграция, когда уйдут последние читатели, —
 * но из сущности они убраны, чтобы обращение к ним не компилировалось.
 * Молча прочитать устаревший ник дороже, чем не собрать проект: ровно так
 * данные и расходятся.
 *
 * <p>Ник, аватар, дивизион и язык спрашивают у
 * {@code profile.spi.PlayerCardPort}; почту, род учётки и права — у
 * {@code auth.spi.AccountPort}; блокировку — у {@code admin.spi.BanPort};
 * обойму и квоту диверсий — у {@code sabotage.spi.SabotageArmoryPort}.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @Column(nullable = false, updatable = false, length = 160)
    private String uid;

    /** Слова, уже попадавшиеся игроку в рейтинговых партиях: их не повторяют. */
    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "ranked_word_history", nullable = false, columnDefinition = "jsonb")
    private List<String> rankedWordHistory = new ArrayList<>();

    @Column(name = "ranked_word_history_updated_at")
    private Instant rankedWordHistoryUpdatedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
