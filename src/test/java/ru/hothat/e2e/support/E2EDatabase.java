package ru.hothat.e2e.support;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * База сквозных тестов: та же машина, что у приложения, но своя база.
 *
 * <p>Отдельной схемой обойтись нельзя — миграции пишут {@code v2.} прямо в
 * тексте, — а отдельная база стоит одного {@code CREATE DATABASE} и снимает
 * главный риск: тест не трогает данные, на которых работает разработчик.
 * Flyway разворачивает её сам при первом прогоне, дальше просто сверяет версию.
 *
 * <p>Чистится база <b>перед</b> тестом, а не после: упавший на середине прогон
 * иначе оставил бы за собой мусор, который сломал бы следующий.
 */
public final class E2EDatabase {

    /** Имя намеренно не совпадает с базой приложения: перепутать их нельзя. */
    public static final String NAME = "hot_hat_e2e";

    private static final String HOST_URL = "jdbc:postgresql://localhost:5462/postgres";
    private static final String URL = "jdbc:postgresql://localhost:5462/" + NAME;
    private static final String USER = "hothat";
    private static final String PASSWORD = "hothat";

    private E2EDatabase() {
    }

    public static String url() {
        return URL;
    }

    /** Заводит базу, если её ещё нет. Идемпотентно: повтор ничего не делает. */
    public static void ensureExists() {
        try (Connection connection = java.sql.DriverManager.getConnection(HOST_URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = '" + NAME + "'")) {
                if (rows.next()) {
                    return;
                }
            }
            statement.executeUpdate("CREATE DATABASE " + NAME);
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Сквозным тестам нужен docker-Postgres на 5462 (docker compose up -d postgres): "
                            + e.getMessage(), e);
        }
    }

    /**
     * Опустошает все таблицы, кроме журнала миграций.
     *
     * <p>Список берётся из каталога, а не пишется руками: таблиц девять
     * десятков, и забытая при добавлении новой означала бы протекание данных
     * между тестами. {@code CASCADE} снимает вопрос порядка внешних ключей,
     * {@code RESTART IDENTITY} — накопление счётчиков.
     */
    public static void wipe(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            requireTestDatabase(statement);
            List<String> tables = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery("""
                    SELECT format('%I.%I', schemaname, tablename)
                    FROM pg_tables
                    WHERE schemaname IN ('public', 'v2')
                      AND tablename <> 'flyway_schema_history'
                    """)) {
                while (rows.next()) {
                    tables.add(rows.getString(1));
                }
            }
            if (tables.isEmpty()) {
                return;
            }
            statement.executeUpdate("TRUNCATE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось очистить тестовую базу: " + e.getMessage(), e);
        }
    }

    /**
     * Сеет каталог мемов, которым заряжается обойма тестового игрока.
     *
     * <p>Нужен потому, что {@link #wipe} опустошает и {@code v2.meme}: строку
     * встроенного ролика, посеянную миграцией V18, он уносит вместе со всем
     * остальным. Раньше это было незаметно — обойма принимала любые пять строк,
     * и тест заряжал несуществующие {@code builtin-e2e-N}. Теперь адрес обоймы
     * проверяет, что ролик есть в каталоге, и посев стал обязательным: тест
     * обязан идти тем же путём, что игрок, а игрок выбирает из каталога.
     *
     * <p>{@code origin='builtin'} — не украшение: {@code ck_meme_owner}
     * требует, чтобы владелец был пуст ровно у встроенных, а заводить игрока
     * ради карточки мема тесту незачем.
     */
    public static void seedMemeCatalog(DataSource dataSource, List<String> slugs) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            requireTestDatabase(statement);
            for (String slug : slugs) {
                statement.executeUpdate("""
                        INSERT INTO v2.meme (id, slug, title, duration_ms, division_language,
                                             origin, status, owner_player_id, created_at, published_at)
                        VALUES (md5('meme:%s')::uuid, '%s', '%s', 5000, 'ru',
                                'builtin', 'active', NULL, now(), now())
                        ON CONFLICT DO NOTHING
                        """.formatted(slug, slug, slug));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось посеять каталог мемов: " + e.getMessage(), e);
        }
    }

    /**
     * Предохранитель. {@code TRUNCATE} по ошибке в базе приложения стёр бы
     * рабочие данные молча, поэтому имя базы сверяется перед каждым проходом,
     * а не только при настройке.
     */
    private static void requireTestDatabase(Statement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery("SELECT current_database()")) {
            String actual = rows.next() ? rows.getString(1) : "";
            if (!NAME.equals(actual)) {
                throw new IllegalStateException(
                        "Очистка запрошена не в тестовой базе, а в «" + actual + "» — отказано");
            }
        }
    }

    /**
     * Одна строка из базы. Нужна проверке «секрета нет в ответах»: сравнивать
     * с настоящим хранимым значением надёжнее, чем искать имя поля — поле
     * могли бы и переименовать.
     */
    public static String single(DataSource dataSource, String sql) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        } catch (SQLException e) {
            throw new IllegalStateException("Запрос к тестовой базе не удался: " + e.getMessage(), e);
        }
    }
}
