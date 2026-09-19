package ru.hothat.api.spec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.support.CompiledClasses;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сущность хранилища не выходит наружу.
 *
 * <p>Стоит одному полю ответа получить тип строки таблицы — и форма ответа
 * перестаёт быть решением: её начинает диктовать схема базы. Переименовали
 * колонку — сломался клиент; добавили служебное поле — оно уехало в браузер
 * вместе с тем, чего игроку видеть не следует. Такую утечку не видно ни в
 * обзоре кода, ни в спецификации: в ней просто появляется схема с именем
 * сущности, неотличимая от честного DTO.
 *
 * <p>Проверяются типы, а не строки импорта: полное имя класса, написанное
 * прямо в объявлении поля, импорта не оставляет.
 */
class DtoIsolationTest {

    /** Пакет данных, вход которым в {@code api.dto} закрыт. */
    private static boolean isStorage(Class<?> type) {
        String packageName = type.getPackageName();
        return packageName.startsWith("ru.hothat.model.")
                || packageName.equals("ru.hothat.model")
                || packageName.endsWith(".store")
                || packageName.contains(".store.");
    }

    @Test
    @DisplayName("Ни один DTO не несёт в себе сущность хранилища")
    void dtosDoNotExposeStorageEntities() {
        Set<String> leaks = new TreeSet<>();

        for (Class<?> dto : CompiledClasses.inPackagesEndingWith("api.dto")) {
            for (Field field : dto.getDeclaredFields()) {
                if (field.isSynthetic()) {
                    continue;
                }
                for (Class<?> referenced : typesIn(field.getGenericType())) {
                    if (isStorage(referenced)) {
                        leaks.add(dto.getName() + "." + field.getName() + " → " + referenced.getName());
                    }
                }
            }
            for (Method method : dto.getDeclaredMethods()) {
                if (method.isSynthetic()) {
                    continue;
                }
                for (Class<?> referenced : typesIn(method.getGenericReturnType())) {
                    if (isStorage(referenced)) {
                        leaks.add(dto.getName() + "." + method.getName() + "() → " + referenced.getName());
                    }
                }
                for (Type parameter : method.getGenericParameterTypes()) {
                    for (Class<?> referenced : typesIn(parameter)) {
                        if (isStorage(referenced)) {
                            leaks.add(dto.getName() + "." + method.getName() + "(…) ← " + referenced.getName());
                        }
                    }
                }
            }
        }

        assertThat(leaks)
                .as("сущности хранилища, просочившиеся в пакеты api.dto")
                .isEmpty();
    }

    @Test
    @DisplayName("Пакеты api.dto вообще существуют — иначе проверка молча пуста")
    void thereAreDtosToCheck() {
        List<Class<?>> dtos = CompiledClasses.inPackagesEndingWith("api.dto");

        // Без этой проверки опечатка в имени пакета превратила бы предыдущий
        // тест в вечно зелёный: пустой список ни на что не жалуется.
        assertThat(dtos).hasSizeGreaterThan(300);
    }

    /** Все классы, из которых собран тип: сам тип, его параметры и элементы массива. */
    private static Set<Class<?>> typesIn(Type type) {
        Set<Class<?>> collected = new LinkedHashSet<>();
        collect(type, collected);
        return collected;
    }

    private static void collect(Type type, Set<Class<?>> collected) {
        if (type instanceof Class<?> raw) {
            if (raw.isArray()) {
                collect(raw.getComponentType(), collected);
            } else {
                collected.add(raw);
            }
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            collect(parameterized.getRawType(), collected);
            for (Type argument : parameterized.getActualTypeArguments()) {
                collect(argument, collected);
            }
            return;
        }
        if (type instanceof GenericArrayType array) {
            collect(array.getGenericComponentType(), collected);
            return;
        }
        if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                collect(bound, collected);
            }
            for (Type bound : wildcard.getLowerBounds()) {
                collect(bound, collected);
            }
            return;
        }
        if (type instanceof TypeVariable<?> variable) {
            for (Type bound : variable.getBounds()) {
                collect(bound, collected);
            }
        }
    }
}
