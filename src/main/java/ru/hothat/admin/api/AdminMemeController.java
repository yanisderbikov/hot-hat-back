package ru.hothat.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.admin.api.dto.MemeLibraryReconciliationResponseDTO;
import ru.hothat.admin.api.dto.OptimizedMemeResponseDTO;
import ru.hothat.admin.api.dto.SaveOptimizedMemeRequestDTO;
import ru.hothat.admin.usecase.ReconcileMemeLibraryUseCase;
import ru.hothat.admin.usecase.SaveOptimizedMemeUseCase;
import ru.hothat.admin.usecase.WithdrawMemeByAdminUseCase;
import ru.hothat.config.HotHatUser;
import ru.hothat.media.api.validation.MemeId;

/**
 * Правит библиотеку мемов решением администратора.
 *
 * <p>Собирает под один адрес три операции, разбросанные сегодня по трём
 * контроллерам: {@code POST /api/optimize-meme},
 * {@code POST /api/meme-library-sync} (доступный любому вошедшему) и
 * {@code POST /api/admin} с {@code action=delete_meme_alert}.
 *
 * <p>Игроцкие операции над мемами остаются в {@code /api/v2/media}: там автор
 * распоряжается своим, здесь администратор — чужим. Право разное, значит и
 * класс разный.
 *
 * <p>Старые адреса пока живы: фронтенд переедет на эти маршруты отдельно.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/admin/memes")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin · мемы", description = "Библиотека мемов глазами администратора")
@SecurityRequirement(name = "Bearer")
public class AdminMemeController {

    private final SaveOptimizedMemeUseCase saveOptimized;
    private final ReconcileMemeLibraryUseCase reconcileLibrary;
    private final WithdrawMemeByAdminUseCase withdrawMeme;

    @Operation(summary = "Сохранить оптимизированную версию мема",
            description = "Сжатый для мобильных ролик, подготовленный браузером администратора. "
                    + "Метод PUT: повторный прогон оптимизатора заменяет прежнюю версию, "
                    + "а не заводит ещё одну.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Версия сохранена"),
            @ApiResponse(responseCode = "403", description = "BUILTIN_MEME_PROTECTED: встроенный "
                    + "мем менять нельзя", content = @Content),
            @ApiResponse(responseCode = "404", description = "MEME_NOT_FOUND: такого мема нет",
                    content = @Content)})
    @PutMapping("/{memeId}/optimized-variant")
    public ResponseEntity<OptimizedMemeResponseDTO> saveOptimizedVariant(
            @AuthenticationPrincipal HotHatUser admin,
            @Parameter(description = "Мем", example = "meme-9f3a1c7b2e54")
            @PathVariable @MemeId String memeId,
            @Valid @RequestBody SaveOptimizedMemeRequestDTO request) {
        return ResponseEntity.ok(saveOptimized.run(admin, memeId, request));
    }

    @Operation(summary = "Сверить библиотеку с хранилищем",
            description = "Восстанавливает записи по файлам, которые в хранилище есть, а в базе "
                    + "их нет. Нужна после сбоев загрузки, когда файл уже уехал, а метаданные "
                    + "записать не успели.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сверка выполнена"),
            @ApiResponse(responseCode = "503", description = "S3_MEDIA_STORAGE_NOT_CONFIGURED: "
                    + "хранилище не настроено, сверять не с чем", content = @Content)})
    @PostMapping("/library-reconciliations")
    public ResponseEntity<MemeLibraryReconciliationResponseDTO> reconcile(
            @AuthenticationPrincipal HotHatUser admin) {
        return ResponseEntity.ok(reconcileLibrary.run(admin));
    }

    @Operation(summary = "Снять мем с публикации",
            description = "Убирает чужой мем по сигналу игроков вместе с его файлами.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Мема больше нет"),
            @ApiResponse(responseCode = "403", description = "BUILTIN_MEME_PROTECTED: встроенный "
                    + "мем удалить нельзя", content = @Content),
            @ApiResponse(responseCode = "404", description = "MEME_NOT_FOUND: такого мема нет",
                    content = @Content)})
    @DeleteMapping("/{memeId}")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal HotHatUser admin,
            @Parameter(description = "Мем", example = "meme-9f3a1c7b2e54")
            @PathVariable @MemeId String memeId) {
        withdrawMeme.run(admin, memeId);
        return ResponseEntity.noContent().build();
    }
}
