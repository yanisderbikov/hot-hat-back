package ru.hothat.team.store;

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
 * Логотип команды отдельной строкой.
 *
 * <p>Причина та же, что у картинки в переписке: карточка команды читается на
 * каждом экране рейтинга, логотип нужен не всем этим экранам, а строка с ним
 * весит в тысячу раз больше остальных колонок — до 280 КБ.
 */
@Entity
@Table(name = "ranked_team_logo", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class TeamLogo {

    @Id
    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Column(name = "data_url", nullable = false, length = 280000)
    private String dataUrl;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
