package ru.hothat.media.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.HotHatUser;
import ru.hothat.media.api.dto.PlaybackTicketResponseDTO;
import ru.hothat.media.api.dto.RequestPlaybackTicketRequestDTO;
import ru.hothat.media.domain.MemeAssetKey;
import ru.hothat.media.store.MemeFileStorage;

import java.time.Clock;

/**
 * Выдать подписанную ссылку на воспроизведение файла мема.
 *
 * <p>Вызывающий стоит в сигнатуре не для порядка: выдача ссылки на платный
 * объект без личности — дыра, которую нечем закрыть задним числом.
 *
 * <p>HEAD перед каждым воспроизведением не делается: это удваивало бы платные
 * обращения к хранилищу, а отсутствующий файл всё равно проявится сам.
 */
@Service
@RequiredArgsConstructor
public class IssuePlaybackTicketUseCase {

    private final MemeFileStorage files;
    private final Clock clock;

    @PreAuthorize("hasRole('USER')")
    public PlaybackTicketResponseDTO run(HotHatUser user, RequestPlaybackTicketRequestDTO request) {
        String storageKey = MemeAssetKey.require(request.storagePath());
        return new PlaybackTicketResponseDTO(
                files.presignPlayback(storageKey),
                storageKey,
                clock.millis() + MemeFileStorage.PLAYBACK_TTL.toMillis());
    }
}
