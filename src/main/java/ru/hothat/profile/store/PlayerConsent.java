package ru.hothat.profile.store;

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
 * Одно принятое согласие: строка на документ, а не карта версий.
 *
 * <p>Имя класса не {@code LegalConsent}: так называется сущность старой схемы.
 * Таблица — {@code v2.legal_consent}.
 *
 * <p>Сегодня согласия лежат дважды: jsonb {@code legal_versions} в карточке
 * игрока и таблица с тем же jsonb внутри. Принятие второй версии соглашения
 * перезаписывало первую, и «что именно человек принял в марте» ответить было
 * нельзя — а это ровно тот вопрос, ради которого согласие вообще хранят.
 *
 * <p>Набор документов закрыт шестью значениями: старый сервис и так перебирал
 * эти шесть ключей, а любые другие молча выбрасывал, так что произвольная
 * карта выражала только возможность опечатки.
 *
 * <p>Повторное принятие той же версии — не событие: уникальность тройки
 * (игрок, документ, версия) делает запись идемпотентной без чтения перед
 * вставкой.
 *
 * <p>Подтверждения возраста здесь нет: у него нет версии, и живёт оно в
 * {@link PlayerProfile#getAdultConfirmedAt()}.
 */
@Entity
@Table(name = "legal_consent", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class PlayerConsent {

    /** Шесть документов контракта; набор закрыт ограничением базы. */
    static final String AGREEMENT = "agreement";
    static final String PRIVACY = "privacy";
    static final String PERSONAL_DATA = "personal_data";
    static final String COMMUNITY = "community";
    static final String RECORDING = "recording";
    static final String DIVISIONS = "divisions";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    @Column(nullable = false, updatable = false, length = 16)
    private String document;

    /** Дата выпуска документа строкой — так её проставляет фронтенд. */
    @Column(name = "document_version", nullable = false, updatable = false, length = 40)
    private String documentVersion;

    @Builder.Default
    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt = Instant.now();

    /**
     * Доказательная часть: откуда и чем принимали. Хранится ради спора, а не
     * ради экрана, поэтому ни в один ответ не попадает.
     */
    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "user_agent", length = 400)
    private String userAgent;
}
