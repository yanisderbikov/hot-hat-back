package ru.hothat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Идентификатор запроса: рождается в фильтре, живёт в атрибуте, заголовке и MDC — и только пока идёт запрос. */
class RequestLogFilterTest {

    @Test
    @DisplayName("Один и тот же идентификатор в атрибуте запроса, заголовке ответа и MDC внутри цепочки")
    void requestIdIsSharedByAttributeHeaderAndMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/profile/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenInChain = new String[1];
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req,
                                   jakarta.servlet.http.HttpServletResponse res) {
                seenInChain[0] = MDC.get(RequestLogFilter.MDC_KEY);
            }
        });

        new RequestLogFilter().doFilter(request, response, chain);

        String id = RequestLogFilter.requestId(request);
        assertThat(id).matches("[0-9a-f]{8}");
        assertThat(response.getHeader(RequestLogFilter.REQUEST_ID_HEADER)).isEqualTo(id);
        assertThat(seenInChain[0]).isEqualTo(id);
        // Поток вернётся в пул: чужой запрос не должен унаследовать наш идентификатор.
        assertThat(MDC.get(RequestLogFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Токен в строке запроса маскируется: рукопожатие /ws/v2 несёт его параметром")
    void tokenInQueryIsMasked() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/v2/lobby");
        request.setQueryString("token=eyJhbGciOiJIUzUxMiJ9.secret&x=1");

        assertThat(RequestLogFilter.pathWithQuery(request)).isEqualTo("/ws/v2/lobby?token=…&x=1");
    }

    @Test
    @DisplayName("Без строки запроса — просто адрес")
    void plainPathStaysPlain() {
        assertThat(RequestLogFilter.pathWithQuery(new MockHttpServletRequest("GET", "/api/v2/profile/me")))
                .isEqualTo("/api/v2/profile/me");
    }
}
