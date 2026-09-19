package ru.hothat.support;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Маршруты, объявленные контроллерами, — прочитанные из самих аннотаций.
 *
 * <p>Спецификация складывает операции в отображение «путь → метод», и два
 * одинаковых маршрута в ней неотличимы от одного: springdoc молча оставит
 * последний. Поэтому дубли ищутся не по спецификации, а по объявлениям.
 *
 * <p>Путь собирается тем же правилом, что и в Spring: путь класса плюс путь
 * метода. Аннотации читаются через {@code AnnotatedElementUtils}, поэтому
 * {@code @GetMapping} виден как {@code @RequestMapping(method = GET)} — то же
 * склеивание делает и сам контейнер.
 */
public final class Controllers {

    private Controllers() {
    }

    /** Один адрес: метод HTTP и путь, а также где он объявлен. */
    public record Route(String httpMethod, String path, Class<?> controller, Method handler) {

        /** Так адрес читается в отчёте о падении: {@code POST /api/v2/rooms}. */
        public String address() {
            return httpMethod + " " + path;
        }

        public String declaredAt() {
            return controller.getSimpleName() + "." + handler.getName() + "()";
        }
    }

    private static final List<Class<?>> REST_CONTROLLERS = CompiledClasses.all().stream()
            .filter(type -> AnnotatedElementUtils.hasAnnotation(type, RestController.class))
            .toList();

    private static final List<Route> ROUTES = collectRoutes();

    public static List<Class<?>> restControllers() {
        return REST_CONTROLLERS;
    }

    public static List<Route> routes() {
        return ROUTES;
    }

    /** Только новая поверхность: старые адреса живут по другим правилам. */
    public static List<Route> v2Routes() {
        return ROUTES.stream().filter(route -> route.path().startsWith("/api/v2")).toList();
    }

    /** Контроллеры, у которых хотя бы один адрес лежит в {@code /api/v2}. */
    public static List<Class<?>> v2Controllers() {
        return v2Routes().stream()
                .map(Route::controller)
                .distinct()
                .sorted(Comparator.comparing(Class::getName))
                .toList();
    }

    private static List<Route> collectRoutes() {
        List<Route> routes = new ArrayList<>();
        for (Class<?> controller : REST_CONTROLLERS) {
            List<String> classPaths = pathsOf(AnnotatedElementUtils
                    .findMergedAnnotation(controller, RequestMapping.class));
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils
                        .findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                for (String classPath : classPaths) {
                    for (String methodPath : pathsOf(mapping)) {
                        for (RequestMethod httpMethod : httpMethodsOf(mapping)) {
                            routes.add(new Route(httpMethod.name(),
                                    join(classPath, methodPath), controller, method));
                        }
                    }
                }
            }
        }
        routes.sort(Comparator.comparing(Route::path).thenComparing(Route::httpMethod));
        return List.copyOf(routes);
    }

    /**
     * Пустой список путей — это один пустой путь, а не ноль адресов: у класса
     * без {@code @RequestMapping} метод всё равно объявляет свой адрес.
     */
    private static List<String> pathsOf(RequestMapping mapping) {
        if (mapping == null || mapping.path().length == 0) {
            return List.of("");
        }
        return Arrays.asList(mapping.path());
    }

    /** Отображение без метода отвечает на любой — в спецификации это разные операции. */
    private static List<RequestMethod> httpMethodsOf(RequestMapping mapping) {
        return mapping.method().length == 0
                ? Arrays.asList(RequestMethod.values())
                : Arrays.asList(mapping.method());
    }

    private static String join(String classPath, String methodPath) {
        String path = (trimTrailingSlash(classPath) + withLeadingSlash(methodPath));
        return path.isEmpty() ? "/" : path;
    }

    private static String trimTrailingSlash(String path) {
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static String withLeadingSlash(String path) {
        if (path.isEmpty()) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
