package ru.hothat.game.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class YoutubeMemeSearchControllerTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void parsesSearchFeedWithTitlesDurationsAndThumbnails() throws Exception {
        String output = "WARNING: extractor preamble\\n" + "{\"entries\":[{\"id\":\"JkPLWQK93fE\",\"title\":\"Кот орёт\",\"duration\":7},{\"id\":\"aqz-KE-bpKQ\",\"title\":\"Длинный ролик\",\"duration\":184},{\"id\":\"not-a-youtube-video-id\",\"title\":\"Invalid\"}]}";
        List<YoutubeMemeSearchController.VideoResult> items =
                YoutubeMemeSearchController.parseResults(json, output);
        assertEquals(2, items.size());
        assertEquals("Кот орёт", items.get(0).title());
        assertEquals(7_000L, items.get(0).durationMs());
        assertEquals(184_000L, items.get(1).durationMs());
        assertEquals("https://i.ytimg.com/vi/JkPLWQK93fE/hqdefault.jpg", items.get(0).thumbnail());
    }

    @Test
    void capsResultsAtTwentyAndRejectsGarbageOutput() throws Exception {
        StringBuilder output = new StringBuilder("{\"entries\":[");
        for (int i = 0; i < 30; i++) {
            if (i != 0) output.append(',');
            output.append("{\"id\":\"aaaaaaaaaaa\",\"title\":\"Video\"}");
        }
        output.append("]}");
        assertEquals(20, YoutubeMemeSearchController.parseResults(json, output.toString()).size());
        assertThrows(IllegalStateException.class,
                () -> YoutubeMemeSearchController.parseResults(json, "timeout: no JSON"));
    }
    @Test
    void parsingSupportsUpToEightyOneSearchHitsForAdditionalPages() throws Exception {
        StringBuilder output = new StringBuilder("{\"entries\":[");
        for (int i = 0; i < 50; i++) {
            if (i != 0) output.append(',');
            output.append("{\"id\":\"aaaaaaaaaaa\",\"title\":\"Video\"}");
        }
        output.append("]}");
        assertEquals(41,
            YoutubeMemeSearchController.parseResults(json, output.toString(), 41).size());
    }

    @Test
    void labelsAgeGatedAndAuthenticationRequiredEmbedsWithoutHidingThemFromSearch() throws Exception {
        var videos = YoutubeMemeSearchController.parseResults(json, "{\"entries\":[{\"id\":\"JkPLWQK93fE\",\"title\":\"Restricted\",\"duration\":204,\"age_limit\":18},{\"id\":\"aqz-KE-bpKQ\",\"title\":\"Available\",\"duration\":46,\"age_limit\":0},{\"id\":\"aaaaaaaaaaa\",\"title\":\"Private\",\"availability\":\"needs_auth\"}]}");
        assertEquals(3, videos.size());
        assertTrue(videos.get(0).embedRestricted());
        assertFalse(videos.get(1).embedRestricted());
        assertTrue(videos.get(2).embedRestricted());
    }

}
