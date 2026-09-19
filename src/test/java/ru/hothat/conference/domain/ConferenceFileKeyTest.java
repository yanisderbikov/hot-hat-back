package ru.hothat.conference.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ключ вложения: по нему читается владение, поэтому чужая папка, чужой
 * созвон и рукотворный ключ обязаны отвергаться до обращения к хранилищу.
 */
class ConferenceFileKeyTest {

    private static final String CONFERENCE = "vc-0f3a9c1d7b2e5480";
    private static final String UID = "Qk3xZaTb9mNpR2sVuWyA1cEfGhJk";

    @Test
    @DisplayName("Выданный ключ признаётся своим — с кириллицей и пробелами в имени")
    void issuedKeyBelongsToOwner() {
        String key = ConferenceFileKey.of(CONFERENCE, UID, 1788600000000L, "9f31ab77c204", "мой скрин.png");
        assertThat(key).isEqualTo("conference/" + CONFERENCE + "/" + UID + "/1788600000000-9f31ab77c204-мой скрин.png");
        assertThat(ConferenceFileKey.belongsTo(key, CONFERENCE, UID)).isTrue();
    }

    @Test
    @DisplayName("Ключ чужого созвона или чужого игрока — не свой")
    void foreignKeysRejected() {
        String key = ConferenceFileKey.of(CONFERENCE, UID, 1788600000000L, "9f31ab77c204", "a.png");
        assertThat(ConferenceFileKey.belongsTo(key, "vc-ffffffffffffffff", UID)).isFalse();
        assertThat(ConferenceFileKey.belongsTo(key, CONFERENCE, "someoneElse")).isFalse();
    }

    @Test
    @DisplayName("Рукотворный ключ без случайного хвоста или с обходом папки отвергается")
    void handMadeKeysRejected() {
        assertThat(ConferenceFileKey.belongsTo("conference/" + CONFERENCE + "/" + UID + "/a.png", CONFERENCE, UID))
                .isFalse();
        assertThat(ConferenceFileKey.belongsTo(
                "conference/" + CONFERENCE + "/" + UID + "/1-9f31ab77c204-../../x", CONFERENCE, UID)).isFalse();
        assertThat(ConferenceFileKey.belongsTo("memes/ru/x/y/video.webm", CONFERENCE, UID)).isFalse();
        assertThat(ConferenceFileKey.belongsTo(null, CONFERENCE, UID)).isFalse();
    }

    @Test
    @DisplayName("Имя файла чистится от разделителей пути и управляющих знаков, пустое становится file")
    void nameIsSanitised() {
        assertThat(ConferenceFileKey.safeName("../etc/passwd")).isEqualTo(".._etc_passwd");
        assertThat(ConferenceFileKey.safeName("a\r\nb\t c")).isEqualTo("a b c");
        assertThat(ConferenceFileKey.safeName("   ")).isEqualTo("file");
        assertThat(ConferenceFileKey.safeName(null)).isEqualTo("file");
        assertThat(ConferenceFileKey.safeName("x".repeat(300))).hasSize(160);
    }
}
