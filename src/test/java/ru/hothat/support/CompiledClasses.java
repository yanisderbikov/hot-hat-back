package ru.hothat.support;

import ru.hothat.Main;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Классы приложения, перечисленные по каталогу сборки.
 *
 * <p>Нужны там, где проверяется свойство всего кода сразу: «ни один DTO не
 * тащит сущность хранилища», «ни один контроллер не забыл объявить охрану».
 * Перечислить их по исходникам нельзя — правило про типы полей, а не про текст.
 *
 * <p>Контекст Spring при этом не поднимается и ни один бин не создаётся:
 * классы загружаются без инициализации, статические блоки не выполняются.
 * Поэтому проверка остаётся быстрой и не требует ни базы, ни окружения.
 */
public final class CompiledClasses {

    private CompiledClasses() {
    }

    private static final List<Class<?>> ALL = load();

    /** Все классы {@code ru.hothat}, попавшие в сборку, в устойчивом порядке. */
    public static List<Class<?>> all() {
        return ALL;
    }

    /** Классы пакета, чьё имя оканчивается на {@code suffix}, например {@code "api.dto"}. */
    public static List<Class<?>> inPackagesEndingWith(String suffix) {
        return ALL.stream()
                .filter(type -> type.getPackageName().endsWith(suffix))
                .toList();
    }

    private static List<Class<?>> load() {
        Path root = classesDirectory();
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".class"))
                    .map(path -> root.relativize(path).toString())
                    .map(name -> name.substring(0, name.length() - ".class".length()))
                    .map(name -> name.replace(File.separatorChar, '.'))
                    .filter(name -> name.startsWith("ru.hothat."))
                    .filter(name -> !name.endsWith("package-info"))
                    .sorted(Comparator.naturalOrder())
                    // Класс нужен как описание типа, а не как рабочий объект:
                    // forName загружает его без инициализации.
                    .<Class<?>>map(CompiledClasses::forName)
                    .toList();
        } catch (Exception failure) {
            throw new IllegalStateException("Не удалось перечислить классы в " + root, failure);
        }
    }

    private static Class<?> forName(String name) {
        try {
            return Class.forName(name, false, CompiledClasses.class.getClassLoader());
        } catch (ClassNotFoundException | NoClassDefFoundError failure) {
            throw new IllegalStateException("Класс " + name + " лежит в сборке, но не загружается", failure);
        }
    }

    private static Path classesDirectory() {
        try {
            Path location = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!Files.isDirectory(location)) {
                throw new IllegalStateException("Ожидался каталог классов, а не архив: " + location);
            }
            return location;
        } catch (Exception failure) {
            throw new IllegalStateException("Не найден каталог собранных классов", failure);
        }
    }
}
