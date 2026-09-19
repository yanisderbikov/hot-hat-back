package ru.hothat.model.room;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** rooms/{id}/teams/{teamId}. */
@Entity
@Table(name = "room_team")
@IdClass(RoomTeamId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomTeam {

    @Id
    @Column(name = "room_id", nullable = false, length = 80)
    private String roomId;

    @Id
    @Column(name = "team_id", nullable = false, length = 80)
    private String teamId;

    @Column(length = 120)
    private String name;

    @Builder.Default
    /** Поле называется order, как в документе; колонка — team_order: order в SQL зарезервирован. */
    @Column(name = "team_order", nullable = false)
    private Integer order = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer score = 0;

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "member_uids", nullable = false, columnDefinition = "jsonb")
    private List<String> memberUids = new ArrayList<>();

    @Column(name = "ranked_team_id", length = 80)
    private String rankedTeamId;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
