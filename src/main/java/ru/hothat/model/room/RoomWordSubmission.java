package ru.hothat.model.room;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** rooms/{id}/wordSubmissions/{uid}: слова, сданные игроком в шляпу. */
@Entity
@Table(name = "room_word_submission")
@IdClass(RoomWordSubmissionId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomWordSubmission {

    @Id
    @Column(name = "room_id", nullable = false, length = 80)
    private String roomId;

    @Id
    @Column(name = "submission_id", nullable = false, length = 120)
    private String submissionId;

    @Builder.Default
    @Type(JsonType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> words = new ArrayList<>();

    @Builder.Default
    @Column(name = "word_count", nullable = false)
    private Integer count = 0;

    @Builder.Default
    @Column(name = "is_test_bot_submission", nullable = false)
    private Boolean isTestBotSubmission = Boolean.FALSE;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
