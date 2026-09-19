package ru.hothat.profile.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Карточка игрока: имя, язык, дивизион.
 *
 * <p>{@code displayName} здесь нет — §3.2 плана слил его с ником. Все
 * писатели ставили их одинаково, а отдельно имя менял ровно один метод и
 * порождал состояние «ник Vasya, имя Петя», которое потом лечил
 * {@code resolvedNickname}.
 *
 * <p>{@link #nicknameKey} считает база выражением {@code lower(btrim(nickname))} —
 * тем же, что и {@code Ids.key}. Поэтому поле только читается. Вместе с ним
 * исчезает таблица {@code nickname_index}: «занято ли имя» и «под каким ключом
 * оно лежит» больше не могут разойтись, а с ними уходят починка индексов
 * (57 строк), фолбэки {@code getByNicknameKey}/{@code getNicknamesOfUid} и
 * находка B9 — merge по присвоенному {@code @Id}, переписывавший чужую строку.
 *
 * <p>Строка есть и у гостя: ник {@code Guest######} должен где-то лежать, а
 * согласия гость даёт наравне с участником.
 *
 * <p>{@link #adultConfirmedAt} стоит здесь, а не строкой в журнале согласий,
 * потому что подтверждение возраста — факт о человеке, а не о документе: у
 * него нет версии и его нельзя принять повторно. Так это и объявлено в
 * {@code RecordConsentRequestDTO}.
 *
 * <p>{@link #version} — оптимистическая блокировка (§6.4). Присутствие сюда
 * не входит: пинг раз в минуту не должен конфликтовать со сменой ника, и
 * живёт он отдельной строкой {@link PlayerPresence}.
 */
@Entity
@Table(name = "player_profile", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class PlayerProfile {

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    /** 3–20 знаков латиницей: то же, что проверяет {@code Ids.NICKNAME}. */
    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(name = "nickname_key", length = 20, insertable = false, updatable = false)
    private String nicknameKey;

    @Builder.Default
    @Column(name = "division_language", nullable = false, length = 8)
    private String divisionLanguage = "ru";

    @Builder.Default
    @Column(name = "ui_language", nullable = false, length = 8)
    private String uiLanguage = "ru";

    /**
     * Дивизион выбирается один раз. Повтор отбивается условным
     * {@code UPDATE … WHERE division_locked_at IS NULL}: ноль изменённых строк
     * — это 409 {@code DIVISION_LOCKED}. Сегодня то же правило живёт в двух
     * местах кода сразу.
     */
    @Column(name = "division_locked_at")
    private Instant divisionLockedAt;

    @Column(name = "adult_confirmed_at")
    private Instant adultConfirmedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Long version = 0L;
}
