package ru.hothat.machine.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Запрос с телом, прочитанным заранее.
 *
 * <p>Нужен ровно одному актору — вебхуку LiveKit. Его подпись накрывает тело
 * (claim {@code sha256}), значит фильтр обязан увидеть тело раньше Jackson.
 * Поток тела одноразовый, поэтому дальше по цепочке едет эта обёртка: иначе
 * контроллер получил бы пустой {@code @RequestBody}.
 *
 * <p>Тело вебхука — уведомление о состоянии выгрузки, это единицы килобайт;
 * держать его в памяти целиком безопасно, и размер уже ограничен настройкой
 * загрузки в {@code application.properties}.
 */
class CachedBodyRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    CachedBodyRequest(HttpServletRequest request, byte[] body) {
        super(request);
        this.body = body;
    }

    byte[] body() {
        return body;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream source = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return source.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException("Асинхронное чтение тела вебхуку не нужно");
            }

            @Override
            public int read() {
                return source.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
