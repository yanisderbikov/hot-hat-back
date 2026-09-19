package ru.hothat.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.e2e.support.ApiClient;
import ru.hothat.e2e.support.E2ETest;
import ru.hothat.e2e.support.Friendship;
import ru.hothat.e2e.support.Player;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Смена ника сразу видна другу.
 *
 * <p>Чинилось: ник хранился копиями — в карточке дружбы, в индексе ников, в
 * заявке, — и после переименования друг ещё долго видел старое имя. Теперь имя
 * у игрока одно, и список друзей читает именно его.
 */
class FriendNicknameE2ETest extends E2ETest {

    @Test
    @DisplayName("Друг видит новый ник сразу после переименования, старого в списке не остаётся")
    void renameIsVisibleToFriend() {
        Player vasilisa = register("Vasilisa");
        Player boris = register("Boris");
        Friendship.make(api, vasilisa, boris);

        // До переименования: друг с прежним именем в списке есть.
        assertThat(friendNickname(boris, vasilisa.uid())).isEqualTo("Vasilisa");

        ApiClient.Response renamed = api.put("/api/v2/profile/me/nickname", vasilisa.token(),
                Map.of("nickname", "Marfushka"));
        assertThat(renamed.status()).as(renamed.raw()).isEqualTo(200);
        assertThat(renamed.text("/nickname")).isEqualTo("Marfushka");

        ApiClient.Response friends = api.get("/api/v2/friends", boris.token());
        assertThat(friends.status()).as(friends.raw()).isEqualTo(200);
        assertThat(friendNickname(friends, vasilisa.uid())).isEqualTo("Marfushka");
        // Ни в одном поле ответа старого имени быть не должно: копия ника —
        // это ровно то, из-за чего список показывал вчерашнее имя.
        assertThat(friends.raw()).as("копия старого ника осталась в списке").doesNotContain("Vasilisa");
    }

    @Test
    @DisplayName("Занятый ник не отдают второму игроку")
    void nicknameStaysUnique() {
        // Обратная сторона той же починки: если имя одно, занять чужое нельзя.
        Player vasilisa = register("Vasilisa");
        register("Boris");

        ApiClient.Response refused = api.put("/api/v2/profile/me/nickname", vasilisa.token(),
                Map.of("nickname", "Boris"));

        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.raw()).contains("NICKNAME_TAKEN");
    }

    private String friendNickname(Player viewer, String friendUid) {
        return friendNickname(api.get("/api/v2/friends", viewer.token()), friendUid);
    }

    private String friendNickname(ApiClient.Response friends, String friendUid) {
        for (JsonNode card : friends.at("/items")) {
            if (friendUid.equals(card.get("uid").asText())) {
                return card.get("nickname").asText();
            }
        }
        throw new AssertionError("друга " + friendUid + " нет в списке: " + friends.raw());
    }
}
