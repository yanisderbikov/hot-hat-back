package ru.hothat.model.social;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Дружба: ключ — отсортированная пара uid, как в friendLinks. uid_a/uid_b
 * продублированы колонками, чтобы поиск друзей шёл по индексу, а не по JSONB.
 */
@Entity
@Table(name = "friend_link")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FriendLink {

    @Id
    @Column(nullable = false, length = 340)
    private String pair;

    @Builder.Default
    @Type(JsonType.class)
    @Column(name = "member_uids", nullable = false, columnDefinition = "jsonb")
    private List<String> memberUids = new ArrayList<>();

    @Column(name = "uid_a", nullable = false, length = 160)
    private String uidA;

    @Column(name = "uid_b", nullable = false, length = 160)
    private String uidB;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public String other(String uid) {
        return uidA.equals(uid) ? uidB : uidA;
    }
}
