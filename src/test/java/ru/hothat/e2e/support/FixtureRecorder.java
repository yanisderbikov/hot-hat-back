package ru.hothat.e2e.support;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Пишет слепки кадров канала комнаты в файлы, которыми потом кормятся
 * Node-тесты адаптера на фронте.
 *
 * <p>Каталог берётся из {@code -Dhothat.fixtures.dir}; без свойства файлы
 * ложатся в {@code target/room-frames} бекенда, и полный прогон тестов
 * соседний репозиторий не трогает. Свойство доходит до форка Surefire как
 * пользовательское свойство Maven.
 *
 * <p>Пишутся только кадры — в {@code frames/} и {@code frames/index.json}.
 * Золотой эталон того же каталога ({@code moments/} и {@code index.json},
 * {@code legacyCaptured: true}) снят до сноса документного шлюза и несёт в
 * каждой записи ещё и шесть его документов; пересобрать ту половину нечем, а
 * дописывать в неё свежий кадр нельзя: пара атомарна на прогон (uid, roomId,
 * turnId и времена рождаются заново), и «legacy из прогона A + кадр из прогона
 * B» сравнивать поле в поле бессмысленно. Поэтому золото здесь не трогается
 * ни при каком каталоге, а свой подкаталог перезаписывается целиком — чтобы
 * после переименования момента не остался хвост.
 *
 * <p>Формат — как у {@code JSON.stringify(…, null, 2)}: ключи в порядке
 * сервера, кириллица без экранирования, пустые массивы и объекты без пробела
 * внутри. Так файлы читаются глазами и не дают шумных диффов.
 */
public final class FixtureRecorder {

    public static final String DIR_PROPERTY = "hothat.fixtures.dir";

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Один зритель одного момента: кто смотрел, когда и какой кадр получил.
     *
     * @param http ответы адресов HTTP тому же зрителю в тот же момент либо
     *             {@code null}, если их не снимали ({@code -Dhothat.fixtures.http=true})
     */
    public record ViewerCapture(Player viewer, String seat, List<String> roles, long capturedAtMs,
                                JsonNode frame, JsonNode http) {
    }

    private record MomentEntry(String file, String moment, String description, List<String> viewers) {
    }

    /** Подкаталог кадров; соседние {@code moments/} и {@code index.json} — золото, его не трогаем. */
    private static final String FRAMES_DIR = "frames";

    private final Path root;
    private final Path framesDir;
    private final List<MomentEntry> moments = new ArrayList<>();
    private int counter;

    private FixtureRecorder(Path root) {
        this.root = root;
        this.framesDir = root.resolve(FRAMES_DIR);
    }

    public static FixtureRecorder open() throws IOException {
        String dir = System.getProperty(DIR_PROPERTY,
                System.getProperty("basedir", ".") + "/target/room-frames");
        FixtureRecorder recorder = new FixtureRecorder(Path.of(dir).toAbsolutePath().normalize());
        recorder.prepare();
        return recorder;
    }

    public Path root() {
        return root;
    }

    private void prepare() throws IOException {
        Files.createDirectories(framesDir);
        // Перезаписываем свой подкаталог целиком: переименованный момент иначе
        // оставил бы за собой прежний файл, а индекс о нём бы не знал.
        // Индекс лежит в том же подкаталоге и уходит вместе с остальными.
        try (DirectoryStream<Path> stale = Files.newDirectoryStream(framesDir, "*.json")) {
            for (Path file : stale) {
                Files.delete(file);
            }
        }
    }

    private Path indexFile() {
        return framesDir.resolve("index.json");
    }

    /**
     * Записать момент: один файл, массив по зрителям.
     *
     * @param name имя момента без номера; номер присваивается по порядку вызова
     */
    public Path record(String name, String description, List<ViewerCapture> captures) throws IOException {
        String moment = String.format("%02d-%s", ++counter, name);
        ArrayNode entries = JSON.createArrayNode();
        for (ViewerCapture capture : captures) {
            ObjectNode entry = entries.addObject();
            entry.put("moment", moment);
            entry.put("description", description);
            entry.put("capturedAtMs", capture.capturedAtMs());
            ObjectNode viewer = entry.putObject("viewer");
            viewer.put("uid", capture.viewer().uid());
            viewer.put("nickname", capture.viewer().nickname());
            viewer.put("seat", capture.seat());
            ArrayNode roles = viewer.putArray("roles");
            capture.roles().forEach(roles::add);
            entry.set("frame", capture.frame());
            if (capture.http() != null) {
                entry.set("http", capture.http());
            }
        }
        Path file = framesDir.resolve(moment + ".json");
        write(file, entries);
        moments.add(new MomentEntry(root.relativize(file).toString(), moment, description,
                captures.stream().map(capture -> capture.viewer().uid()).collect(Collectors.toList())));
        return file;
    }

    /**
     * Индекс: паспорт прогона, зрители, легенда идентификаторов и список
     * моментов. Легенда нужна, чтобы Node-тест ссылался на «событие помидора»
     * или «клип», не разбирая кадры.
     */
    public Path finish(String generator, String contract, ObjectNode room, ObjectNode viewers,
                       ObjectNode legend, List<String> notCaptured) throws IOException {
        ObjectNode index = JSON.createObjectNode();
        index.put("generator", generator);
        index.put("generatedAtMs", System.currentTimeMillis());
        // Поле остаётся, чтобы читатель отличал золото (true) от пересъёмки
        // одним взглядом на индекс, не сравнивая пути.
        index.put("legacyCaptured", false);
        index.put("contract", contract);
        index.set("room", room);
        index.set("viewers", viewers);
        index.set("legend", legend);
        ArrayNode list = index.putArray("moments");
        for (MomentEntry entry : moments) {
            ObjectNode node = list.addObject();
            node.put("file", entry.file());
            node.put("moment", entry.moment());
            node.put("description", entry.description());
            ArrayNode uids = node.putArray("viewers");
            entry.viewers().forEach(uids::add);
        }
        ArrayNode missing = index.putArray("notCaptured");
        notCaptured.forEach(missing::add);
        Path file = indexFile();
        write(file, index);
        return file;
    }

    private static void write(Path file, JsonNode content) throws IOException {
        String text = JSON.writer(new JsonStylePrinter()).writeValueAsString(content) + "\n";
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    /**
     * Печать в стиле {@code JSON.stringify(value, null, 2)}: двоеточие без
     * пробела слева, элементы массивов по строкам, пустые {@code []} и
     * {@code {}} без пробела внутри. Стандартный {@link DefaultPrettyPrinter}
     * пишет {@code "key" : value} и {@code [ ]}, и файл выглядел бы чужим
     * в репозитории фронта.
     */
    private static final class JsonStylePrinter extends DefaultPrettyPrinter {

        private static final DefaultIndenter INDENT = new DefaultIndenter("  ", "\n");

        JsonStylePrinter() {
            _arrayIndenter = INDENT;
            _objectIndenter = INDENT;
        }

        private JsonStylePrinter(JsonStylePrinter base) {
            super(base);
        }

        @Override
        public DefaultPrettyPrinter createInstance() {
            return new JsonStylePrinter(this);
        }

        @Override
        public void writeObjectFieldValueSeparator(JsonGenerator g) throws IOException {
            g.writeRaw(": ");
        }

        @Override
        public void writeEndArray(JsonGenerator g, int nrOfValues) throws IOException {
            if (!_arrayIndenter.isInline()) {
                --_nesting;
            }
            if (nrOfValues > 0) {
                _arrayIndenter.writeIndentation(g, _nesting);
            }
            g.writeRaw(']');
        }

        @Override
        public void writeEndObject(JsonGenerator g, int nrOfEntries) throws IOException {
            if (!_objectIndenter.isInline()) {
                --_nesting;
            }
            if (nrOfEntries > 0) {
                _objectIndenter.writeIndentation(g, _nesting);
            }
            g.writeRaw('}');
        }
    }
}
