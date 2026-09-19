package ru.hothat.media.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.HotHatUser;
import ru.hothat.media.api.dto.RequestVideoUploadTicketRequestDTO;
import ru.hothat.media.api.dto.VideoUploadTicketResponseDTO;
import ru.hothat.media.domain.MemeAssetKind;
import ru.hothat.media.domain.MemeAssetLimits;

/**
 * Выдать билет на загрузку ролика.
 *
 * <p>Путь объекта строит сервер: в нём дивизион, идентификатор игрока и
 * идентификатор мема. Клиент не может ни выбрать чужую папку, ни угадать
 * её — владение потом читается прямо из этого пути, и на нём же держатся
 * уборка осиротевших файлов и снятие мема.
 *
 * <p>Тип содержимого возвращается тот же, что прислал клиент: ссылку подписали
 * именно им, и заголовок {@code Content-Type} при PUT обязан совпасть с ним
 * буква в букву.
 */
@Service
@RequiredArgsConstructor
public class IssueVideoUploadTicketUseCase {

    private final MemeUploadTickets tickets;
    private final MemeFileUrls urls;

    @PreAuthorize("hasRole('USER')")
    public VideoUploadTicketResponseDTO run(HotHatUser user, RequestVideoUploadTicketRequestDTO request) {
        MemeUploadTickets.Ticket ticket = tickets.issue(user.uid(), request.memeId(), MemeAssetKind.VIDEO,
                request.contentType(), request.sizeBytes(), request.divisionLanguage());
        return new VideoUploadTicketResponseDTO(
                ticket.uploadUrl(),
                request.contentType(),
                ticket.storageKey(),
                urls.stable(ticket.storageKey()),
                ticket.expiresAtMs(),
                MemeAssetLimits.MAX_VIDEO_BYTES);
    }
}
