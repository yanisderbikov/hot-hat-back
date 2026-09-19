package ru.hothat.media.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.HotHatUser;
import ru.hothat.media.api.dto.PosterUploadTicketResponseDTO;
import ru.hothat.media.api.dto.RequestPosterUploadTicketRequestDTO;
import ru.hothat.media.domain.MemeAssetKind;
import ru.hothat.media.domain.MemeAssetLimits;

/**
 * Выдать билет на загрузку заставки.
 *
 * <p>Отдельный сценарий, а не ветка в загрузке ролика: заставка
 * необязательна, и её неудача не должна отменять публикацию мема — клиент
 * просто обходится первым кадром, нарисованным у себя.
 */
@Service
@RequiredArgsConstructor
public class IssuePosterUploadTicketUseCase {

    private final MemeUploadTickets tickets;
    private final MemeFileUrls urls;

    @PreAuthorize("hasRole('USER')")
    public PosterUploadTicketResponseDTO run(HotHatUser user, RequestPosterUploadTicketRequestDTO request) {
        MemeUploadTickets.Ticket ticket = tickets.issue(user.uid(), request.memeId(), MemeAssetKind.POSTER,
                request.contentType(), request.sizeBytes(), request.divisionLanguage());
        return new PosterUploadTicketResponseDTO(
                ticket.uploadUrl(),
                request.contentType(),
                ticket.storageKey(),
                urls.stable(ticket.storageKey()),
                ticket.expiresAtMs(),
                MemeAssetLimits.MAX_POSTER_BYTES);
    }
}
