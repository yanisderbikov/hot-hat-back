package ru.hothat.api.spec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hothat.common.api.ErrorCode;
import ru.hothat.config.ErrorMessages;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Код ошибки без текста показывается игроку как есть.
 *
 * <p>Так устроен ответ: {@code GlobalExceptionHandler} кладёт в поле
 * {@code error} то, что вернул {@link ErrorMessages#resolve}, а тот, не найдя
 * строки, возвращает сам код. Фронтенд показывает это поле как есть — и на
 * экране появляется {@code MEME_SOURCE_FETCH_EMPTY}. Ошибка не в переводе:
 * человеку просто не сказали, что случилось и что делать.
 *
 * <p>Проверяется поведение — {@code resolve()}, а не устройство словаря: текст
 * может лежать в общей таблице, в игровой или подставляться разбором кода с
 * хвостом ({@code LOADOUT_REQUIRED:Вася}), и все три способа одинаково хороши.
 */
class ErrorTextCatalogTest {

    private static final Path SOURCES = Path.of("src/main/java/ru/hothat");

    /**
     * Слои прежнего API. Переезд на {@code /api/v2} идёт областями, и старый
     * срез доживает свой век со своими долгами; правило про тексты введено
     * вместе с новой поверхностью и держится на ней.
     */
    private static final Set<String> LEGACY_LAYERS = Set.of(
            "controller", "service", "dto", "model", "repository");

    /**
     * Долг прежнего слоя, пересчитанный поимённо.
     *
     * <p>Список нужен именно списком, а не числом: пока он записан, новый
     * бестекстовый код не спрячется среди старых, а появление текста у любого
     * из этих кодов заставит вычеркнуть строку — то есть долг может только
     * уменьшаться.
     */
    private static final Set<String> KNOWN_LEGACY_DEBT = Set.of(
            "RECORDING_WEBHOOK_FORBIDDEN", "WORD_ID_REQUIRED");

    /**
     * Код в отказе: {@code ApiException.of("ROOM_NOT_FOUND", 404)} и
     * {@code new ApiException("ROOM_NOT_FOUND", 404)}. Двоеточие обрывает
     * захват — у кодов с хвостом текст ищется по началу.
     */
    private static final Pattern THROWN_CODE = Pattern.compile(
            "(?:new\\s+)?ApiException(?:\\.of)?\\(\\s*\"([A-Z][A-Z0-9_]*)[:\"]");

    @Test
    @DisplayName("У каждого кода ошибки новой поверхности есть текст для человека")
    void everyCodeThrownByTheNewSurfaceHasText() {
        TreeMap<String, Set<String>> textless = new TreeMap<>();

        for (Path source : javaSources()) {
            if (isLegacy(source)) {
                continue;
            }
            for (String code : codesIn(source)) {
                if (!hasText(code)) {
                    textless.computeIfAbsent(code, key -> new TreeSet<>()).add(SOURCES.relativize(source).toString());
                }
            }
        }

        assertThat(textless)
                .as("коды ошибок /api/v2, которые игрок увидит машинной строкой")
                .isEmpty();
    }

    @Test
    @DisplayName("Каждое значение ErrorCode имеет текст: enum поднимается кодом, а не строкой")
    void everyErrorCodeConstantHasText() {
        List<String> textless = Stream.of(ErrorCode.values())
                .map(Enum::name)
                .filter(name -> !hasText(name))
                .toList();

        assertThat(textless)
                .as("значения ErrorCode без строки в ErrorMessages")
                .isEmpty();
    }

    @Test
    @DisplayName("Бестекстовые коды прежнего слоя — ровно те, что уже сосчитаны")
    void legacyDebtDoesNotGrow() {
        Set<String> textless = new TreeSet<>();

        for (Path source : javaSources()) {
            if (!isLegacy(source)) {
                continue;
            }
            codesIn(source).stream().filter(code -> !hasText(code)).forEach(textless::add);
        }

        assertThat(textless)
                .as("прежний слой: список бестекстовых кодов должен только сокращаться")
                .isEqualTo(new TreeSet<>(KNOWN_LEGACY_DEBT));
    }

    /**
     * Текст есть, если {@code resolve} вернул не сам код. Статус берётся 400:
     * на пятисотых {@code resolve} отвечает общей фразой любому коду, и
     * проверка на них ничего бы не значила.
     */
    private static boolean hasText(String code) {
        return !code.equals(ErrorMessages.resolve(code, 400));
    }

    private static boolean isLegacy(Path source) {
        return LEGACY_LAYERS.contains(SOURCES.relativize(source).getName(0).toString());
    }

    private static Set<String> codesIn(Path source) {
        Set<String> codes = new TreeSet<>();
        Matcher matcher = THROWN_CODE.matcher(read(source));
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        return codes;
    }

    private static List<Path> javaSources() {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        } catch (IOException failure) {
            throw new IllegalStateException("Не читаются исходники в " + SOURCES.toAbsolutePath(), failure);
        }
    }

    private static String read(Path source) {
        try {
            return Files.readString(source);
        } catch (IOException failure) {
            throw new IllegalStateException("Не читается " + source, failure);
        }
    }
}
