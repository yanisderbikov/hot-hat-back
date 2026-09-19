package ru.hothat.admin.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;

/**
 * Размер своей базы.
 *
 * <p>Единственная метрика расхода, у которой источник — сама база. Спрашивается
 * прямым запросом мимо JPA намеренно: {@code pg_database_size} — это вопрос об
 * инструменте, а не о данных, и сущности ему не нужны.
 *
 * <p>Недоступность базы здесь не исключение, а пустой ответ: сбор снимка
 * обязан дойти до конца и записать, что именно не собралось, — иначе беда с
 * базой лишила бы администратора и всех остальных метрик тоже.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseProbe {

    private static final String SIZE_QUERY = "select pg_database_size(current_database())";

    private final DataSource dataSource;

    /** Занятое базой место в байтах; пусто — база не ответила. */
    public Optional<Long> sizeBytes() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(SIZE_QUERY)) {
            return rows.next() ? Optional.of(rows.getLong(1)) : Optional.empty();
        } catch (Exception e) {
            log.warn("Размер базы недоступен: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
