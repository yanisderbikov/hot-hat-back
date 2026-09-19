package ru.hothat.team.store;

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
 * Готовность одного напарника к рейтинговой партии.
 *
 * <p>Одна строка вместо трёх параллельных jsonb ({@code ready}, {@code media},
 * {@code media_at}) с uid в ключе. Клиент читал их по одному ключу трижды, и
 * при расхождении ключей строка получалась про разных людей.
 *
 * <p>{@code @Version} здесь намеренно нет: каждый пишет только свою строку,
 * конфликтовать не с кем.
 *
 * <p>Правило «готов только при подтверждённой связи» перенесено в ограничение
 * базы: сегодня оно живёт в двух местах кода сразу, и разойтись им ничто не
 * мешает. Свежесть связи считает сервер — у клиента часы уезжают, — поэтому
 * рядом с признаком лежит отметка времени, без которой проверить срок нечем.
 */
@Entity
@Table(name = "team_preflight_participant", schema = "v2")
@IdClass(TeamPreflightParticipantId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class TeamPreflightParticipant {

    @Id
    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    private UUID playerId;

    /** Поколение проверки: строка прошлой сессии не считается готовностью. */
    @Column(name = "session_seq", nullable = false)
    private Integer sessionSeq;

    @Builder.Default
    @Column(name = "media_ok", nullable = false)
    private Boolean mediaOk = Boolean.FALSE;

    @Column(name = "media_ok_at")
    private Instant mediaOkAt;

    @Builder.Default
    @Column(nullable = false)
    private Boolean ready = Boolean.FALSE;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
