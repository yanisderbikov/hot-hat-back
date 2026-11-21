package ru.hothat.media.usecase;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PrivateYoutubeClipSourceTest {
    @Test
    void rejectsNonTrustedPlainHttpAndDoesNotSendAuthToken() {
        assertNull(PrivateYoutubeClipSource.fetch(
                "http://example.com/clip", "test-only-secret", "hAmi3ddwJIg", 0));
    }

    @Test
    void successfullyReceivesRealMp4PayloadThroughAuthorizedLocalTestServer() throws Exception {
        byte[] mp4 = new byte[3000];
        mp4[4]='f';mp4[5]='t';mp4[6]='y';mp4[7]='p';
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
        server.createContext("/clip", exchange -> {
            calls.incrementAndGet();
            assertEquals("test-only-secret", exchange.getRequestHeaders().getFirst("X-Hot-Hat-Clip-Token"));
            assertTrue(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                    .contains("\"videoId\":\"hAmi3ddwJIg\""));
            exchange.getResponseHeaders().set("Content-Type", "video/mp4");
            exchange.sendResponseHeaders(200, mp4.length);
            exchange.getResponseBody().write(mp4);
            exchange.close();
        });
        server.start();
        try {
            assertArrayEquals(mp4, PrivateYoutubeClipSource.fetch(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/clip",
                    "test-only-secret", "hAmi3ddwJIg", 42));
            assertEquals(1,calls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void neverTreatsHtmlOrUnauthorizedResponsesAsVideo() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),1);
        server.createContext("/clip", exchange -> {
            byte[] bytes = "<html>Access denied</html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","text/html");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            assertNull(PrivateYoutubeClipSource.fetch(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/clip",
                    "test-only-secret", "hAmi3ddwJIg", 0));
        } finally {server.stop(0);}
    }
}
