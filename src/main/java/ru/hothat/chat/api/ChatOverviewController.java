package ru.hothat.chat.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hothat.chat.api.dto.ChatThreadsResponseDTO;
import ru.hothat.chat.api.dto.SocialInboxResponseDTO;
import ru.hothat.chat.usecase.GetMySocialInboxUseCase;
import ru.hothat.chat.usecase.ListMyChatThreadsUseCase;
import ru.hothat.config.HotHatUser;

/**
 * Сводка личных переписок игрока.
 *
 * <p>Заменяет {@code POST /api/portal} с {@code action=social_threads} и живую
 * подписку браузера на документ {@code socialInboxes/{uid}}
 * ({@code realtime-social.js:111}): и то и другое отвечает на один вопрос —
 * «есть ли для меня что-то новое и от кого», просто с разной подробностью.
 * Список переписок открывают, когда чат уже раскрыт; входящие спрашивают,
 * пока он закрыт, ради значка и уведомления.
 *
 * <p>Оба адреса отдают только собственные данные игрока, поэтому уровень прав
 * у класса один — {@code ROLE_USER}, и объявлен он на сценариях.
 *
 * <p>Старые адреса пока живы: фронтенд переедет на эти маршруты отдельно.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/chat")
@Tag(name = "Chat · сводка", description = "Сводка личных переписок игрока")
@SecurityRequirement(name = "Bearer")
public class ChatOverviewController {

    private final ListMyChatThreadsUseCase listThreads;
    private final GetMySocialInboxUseCase getInbox;

    @Operation(summary = "Список моих переписок",
            description = "Друзья, с которыми переписка уже заведена, самые свежие сверху, "
                    + "плюс общее число непрочитанного для значка. Читается первая сотня связей: "
                    + "столько же читал старый адрес.")
    @ApiResponse(responseCode = "200", description = "Список отдан")
    @GetMapping("/threads")
    public ResponseEntity<ChatThreadsResponseDTO> threads(@AuthenticationPrincipal HotHatUser user) {
        return ResponseEntity.ok(listThreads.run(user));
    }

    @Operation(summary = "Мои входящие",
            description = "Одно число для значка и два повода показать уведомление: новое сообщение "
                    + "и новое приглашение в комнату. Версии растут при каждом событии — по ним "
                    + "клиент отличает новое от уже показанного.")
    @ApiResponse(responseCode = "200", description = "Входящие отданы; у игрока без переписок они пустые")
    @GetMapping("/inbox")
    public ResponseEntity<SocialInboxResponseDTO> inbox(@AuthenticationPrincipal HotHatUser user) {
        return ResponseEntity.ok(getInbox.run(user));
    }
}
