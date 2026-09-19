package ru.hothat.auth.store;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Чем игрок доказывает, что он это он.
 *
 * <p>Шесть колонок из тридцати шести, что лежали в {@code app_user}: всё
 * остальное было не про личность и уехало в свои таблицы — карточка в
 * {@link ru.hothat.profile.store}, присутствие туда же, блокировка в
 * {@code admin.store}, команда и диверсии в чужие кластеры.
 *
 * <p>{@link #kind} вместо булева {@code guest}: гость и участник — два
 * состояния одной учётки, и апгрейд записывается условным
 * {@code UPDATE … WHERE kind='guest'}. Флагом такой переход выразить было
 * нельзя, поэтому «он ещё гость?» было отдельным чтением, а два одновременных
 * апгрейда проходили оба (дыра A4).
 *
 * <p>Колонки {@code banned} здесь нет. Факт блокировки — строка
 * {@code v2.user_ban}, мгновенный отзыв доступа — {@link #tokenVersion}.
 * Сегодня на один вопрос отвечают три источника правды, и они умеют
 * разойтись.
 *
 * <p>{@code @Version} нет намеренно (§6.4 плана): единственное, что здесь
 * меняется под гонкой, — {@link #tokenVersion}, и он поднимается атомарным
 * {@code UPDATE … SET token_version = token_version + 1}. Сегодня это
 * read-modify-write в двух местах сразу, и одновременные бан и смена пароля
 * теряют один инкремент — то есть отозванная сессия остаётся живой.
 */
@Entity
@Table(name = "user_account", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class UserAccount {

    /** guest | member — набор закрыт ограничением базы. */
    static final String GUEST = "guest";
    static final String MEMBER = "member";

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    @Builder.Default
    @Column(nullable = false, length = 8)
    private String kind = GUEST;

    /** У гостя пусто. Регистр хранится как ввели, сравнивается — без регистра. */
    @Column(length = 320)
    private String email;

    /**
     * BCrypt. У гостя пароля нет, пока он не превратит вход в полноценный
     * аккаунт.
     *
     * <p>{@code @JsonIgnore} перенесён со старой сущности намеренно — это
     * находка B4 аудита. Хеш пароля не должен уезжать в браузер ни одним
     * путём сериализации: ни в ответе, ни в снимке по сокету, ни через
     * случайно попавший в контроллер объект сущности. Аннотация закрывает и
     * обратное направление — задать пароль присланным JSON нельзя.
     */
    @JsonIgnore
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    /**
     * Поколение токенов: растёт при бане, смене пароля и выходе со всех
     * устройств. Фильтр сверяет номер из токена с этим полем, поэтому отзыв
     * срабатывает мгновенно, а не по истечении TTL.
     *
     * <p>{@code @JsonIgnore} по той же причине, что и у пароля (B4): это
     * внутренний счётчик отзыва, клиенту он не нужен ни на чтение, ни на
     * запись, а знание его значения помогает подбирать момент для повторного
     * использования украденного токена.
     */
    @JsonIgnore
    @Builder.Default
    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;

    /**
     * Когда учётка стала полноценной. Это и есть {@code registeredAtMs}
     * контракта: у гостя даты регистрации нет по определению.
     */
    @Column(name = "member_since")
    private Instant memberSince;

    /** Читается ответом «моя учётка»; ни на что, кроме показа, не влияет. */
    @Column(name = "password_updated_at")
    private Instant passwordUpdatedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
