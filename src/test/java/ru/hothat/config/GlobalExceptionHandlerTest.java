package ru.hothat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import ru.hothat.dto.common.ErrorDTO;

import static org.assertj.core.api.Assertions.assertThat;

/** Тело ошибки: прежние поля на месте, к ним — адрес, статус и идентификатор запроса. */
class GlobalExceptionHandlerTest {

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setAttribute(RequestLogFilter.REQUEST_ID_ATTRIBUTE, "deadbeef");
        return request;
    }

    @Test
    @DisplayName("Отказ сценария: текст из словаря, код, статус, метод, адрес и requestId")
    void apiExceptionCarriesRequestContext() {
        ResponseEntity<ErrorDTO> response = new GlobalExceptionHandler(false)
                .handleApi(ApiException.of("NICKNAME_TAKEN", 409), request("PUT", "/api/v2/profile/me/nickname"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        ErrorDTO body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.error()).isEqualTo("Этот ник уже занят.");
        assertThat(body.code()).isEqualTo("NICKNAME_TAKEN");
        assertThat(body.status()).isEqualTo(409);
        assertThat(body.method()).isEqualTo("PUT");
        assertThat(body.path()).isEqualTo("/api/v2/profile/me/nickname");
        assertThat(body.requestId()).isEqualTo("deadbeef");
        assertThat(body.timestamp()).endsWith("Z");
        assertThat(body.details()).isNull();
        assertThat(body.detail()).isNull();
    }

    @Test
    @DisplayName("Хвост кода срезается как раньше: LOADOUT_REQUIRED:имя → LOADOUT_REQUIRED с именем в тексте")
    void codeTailStaysOutOfPublicCode() {
        ErrorDTO body = new GlobalExceptionHandler(false)
                .handleApi(ApiException.of("LOADOUT_REQUIRED:Дима", 409), request("POST", "/api/v2/game/starts"))
                .getBody();

        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("LOADOUT_REQUIRED");
        assertThat(body.error()).contains("Дима");
    }

    @Test
    @DisplayName("Сбой сервера без флага: общий текст, причины в теле нет")
    void internalErrorHidesCauseByDefault() {
        ErrorDTO body = new GlobalExceptionHandler(false)
                .handleOther(new IllegalStateException("pool exhausted"), request("GET", "/api/v2/profile/me"))
                .getBody();

        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(500);
        assertThat(body.code()).isEqualTo("INTERNAL");
        assertThat(body.error()).isEqualTo("Сервер временно недоступен.");
        assertThat(body.detail()).isNull();
    }

    @Test
    @DisplayName("Сбой сервера с флагом: в detail — исключение и его корень")
    void internalErrorExposesCauseWhenAsked() {
        RuntimeException wrapped = new RuntimeException("commit failed",
                new IllegalArgumentException("column \"nickname\" does not exist"));

        ErrorDTO body = new GlobalExceptionHandler(true)
                .handleOther(wrapped, request("GET", "/api/v2/profile/me"))
                .getBody();

        assertThat(body).isNotNull();
        assertThat(body.detail())
                .isEqualTo("RuntimeException: commit failed ← IllegalArgumentException: column \"nickname\" does not exist");
    }

    @Test
    @DisplayName("Без фильтра (тесты, async) requestId просто отсутствует, а не роняет ответ")
    void missingRequestIdIsNull() {
        ErrorDTO body = new GlobalExceptionHandler(false)
                .handleApi(ApiException.of("ROOM_REQUIRED"), new MockHttpServletRequest("GET", "/api/v2/x"))
                .getBody();

        assertThat(body).isNotNull();
        assertThat(body.requestId()).isNull();
    }
}
