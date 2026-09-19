package ru.hothat.media.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.media.usecase.ResolveMemeFileUseCase;

/**
 * Перенаправляет на файл мема в хранилище.
 *
 * <p>Заменяет {@code GET /api/media?path=…}. Разница не косметическая: путь
 * перестал быть строкой в параметре и стал структурой адреса. Обязательный
 * префикс {@code memes/} теперь выражен литеральным сегментом маршрута —
 * запрос за пределы папки мемов не отвергается проверкой, а просто не
 * попадает в этот метод.
 *
 * <p>Адрес открыт всем намеренно: его подставляют в {@code <video src>}, а
 * туда браузер заголовок {@code Authorization} не передаёт. Секрета в файлах
 * нет — библиотека мемов общая для всех вошедших. Ответ живёт в кеше пять
 * минут, а подпись хранилища — час: клиент хранит у себя постоянную ссылку и
 * получает свежую подпись при каждом обращении.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/media/files")
@Tag(name = "Media · файлы", description = "Постоянные адреса файлов мемов")
// Пустой @SecurityRequirements — это и есть объявление «операция открыта»:
// у остальных областей замок стоит из @SecurityRequirement(name = "Bearer"),
// и без явной пометки открытый адрес выглядел бы в спецификации забытым.
@SecurityRequirements
public class MemeFileController {

    private final ResolveMemeFileUseCase resolveMemeFile;

    @Operation(summary = "Перейти к файлу мема",
            description = "Отвечает 302 на подписанную ссылку хранилища. Адрес постоянный, "
                    + "подпись — нет, поэтому редирект, а не сама ссылка в карточке.")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Location — подписанная ссылка на файл",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "MEDIA_PATH_INVALID: путь не похож на файл мема",
                    content = @Content),
            @ApiResponse(responseCode = "503", description = "S3_MEDIA_STORAGE_NOT_CONFIGURED: хранилище "
                    + "не настроено", content = @Content)})
    @GetMapping("/memes/{*objectPath}")
    public ResponseEntity<Void> redirect(
            @Parameter(description = "Остаток пути внутри папки мемов: дивизион, игрок, мем и имя файла",
                    example = "/ru/Qk3xZaTb9mNpR2sVuWyA1cEfGhJk/meme-9f31ab77c204/video.webm")
            @PathVariable String objectPath) {
        // Захваченный хвост приходит с ведущей косой чертой, а сегмент memes
        // остаётся в маршруте — ключ объекта собирается обратно здесь и только
        // здесь, чтобы сценарий получил его в том же виде, в каком он лежит
        // в карточке мема.
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(resolveMemeFile.run("memes" + objectPath))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .build();
    }
}
