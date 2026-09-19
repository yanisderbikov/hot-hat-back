package ru.hothat.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.support.CompiledClasses;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Новые области не зависят от прежнего слоя {@code ru.hothat.service}.
 *
 * <p>Смысл правила в направлении зависимости. Прежний слой сносится целиком, и
 * снос обязан быть удалением файлов, а не переписыванием половины продукта:
 * значит, ссылаться позволено только в одну сторону — старое на новое.
 * Обратную ссылку не видно ни на код-ревью, ни в тестах области: она
 * компилируется и работает, а обнаруживается в день, когда легаси удаляют.
 *
 * <p>Проверяются классы сборки, а не текст исходников: имя чужого класса
 * попадает в пул констант и от импорта, и от полного имени в сигнатуре, и от
 * вызова статического метода. Правило про связи, а не про строку {@code import}.
 *
 * <p>Контекст Spring не поднимается: читаются байты .class.
 */
class LegacyServiceIsolationTest {

    /** Внутреннее имя прежнего слоя: так его записывает пул констант. */
    private static final String LEGACY = "ru/hothat/service/";

    /** Пакеты, которым ссылаться на прежний слой позволено: они сами прежние. */
    private static final List<String> LEGACY_LAYERS = List.of("ru.hothat.service.", "ru.hothat.controller.");

    /**
     * Оставшийся долг, поимённо.
     *
     * <p>Списком, а не числом: новая зависимость не спрячется среди старых, а
     * переезд последнего должника заставит вычеркнуть строку — то есть список
     * может только сокращаться. Конфигурация сокетов из него уже ушла:
     * документный шлюз снесён вместе с подписками экрана комнаты, и
     * {@code WebSocketConfig} прежний слой больше не упоминает.
     *
     * <ul>
     *   <li>{@code LegacyBotEngineAdapter} — движок тест-ботов: он рассаживает
     *       ботов по местам комнаты и ведёт ход партии прямой записью в чужие
     *       кластеры, и переехать раньше них не может.</li>
     * </ul>
     */
    private static final Set<String> KNOWN_DEBT = Set.of(
            "ru.hothat.testbot.store.LegacyBotEngineAdapter");

    @Test
    @DisplayName("На прежний слой ru.hothat.service ссылаются только он сам, старые контроллеры и учтённый долг")
    void newAreasDoNotDependOnLegacyServices() {
        Set<String> offenders = new TreeSet<>();

        for (Class<?> type : CompiledClasses.all()) {
            String name = type.getName();
            if (LEGACY_LAYERS.stream().anyMatch(name::startsWith)) {
                continue;
            }
            if (mentionsLegacy(type)) {
                // Вложенные классы отвечают за своего хозяина: лямбда области
                // ссылается на легаси ровно тогда, когда ссылается и класс.
                offenders.add(name.contains("$") ? name.substring(0, name.indexOf('$')) : name);
            }
        }

        assertThat(offenders)
                .as("классы новых областей, которые всё ещё зовут ru.hothat.service")
                .isEqualTo(new TreeSet<>(KNOWN_DEBT));
    }

    /** Имя чужого класса лежит в пуле констант как текст — его и ищем. */
    private static boolean mentionsLegacy(Class<?> type) {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream bytes = LegacyServiceIsolationTest.class.getResourceAsStream(resource)) {
            if (bytes == null) {
                throw new IllegalStateException("Не найден файл класса " + resource);
            }
            // ISO-8859-1: байты в символы один к одному, поиск идёт по ним, а
            // не по разобранной структуре — от кодировки он не зависит.
            return new String(bytes.readAllBytes(), StandardCharsets.ISO_8859_1).contains(LEGACY);
        } catch (Exception failure) {
            throw new IllegalStateException("Не прочитан класс " + type.getName(), failure);
        }
    }
}
