package ru.hothat.ai;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** Confirmed server word actions from a match, never active secrets or voice transcripts. */
@Entity @Table(name="ai_voice_word_memory",
    uniqueConstraints=@UniqueConstraint(name="uq_ai_voice_word_action",
        columnNames={"room_id","game_number","turn_id","action_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AiVoiceWordMemory {
    @Id @Column(length=64,nullable=false) private String id;
    @Column(name="room_id",length=80,nullable=false) private String roomId;
    @Column(name="game_number",nullable=false) private Integer gameNumber;
    @Column(name="team_id",length=80,nullable=false) private String teamId;
    @Column(name="turn_id",length=120,nullable=false) private String turnId;
    @Column(name="action_id",length=80,nullable=false) private String actionId;
    @Column(length=160,nullable=false) private String word;
    @Column(length=16,nullable=false) private String outcome;
    @Builder.Default @Column(name="recorded_at",nullable=false) private Instant recordedAt=Instant.now();
}
