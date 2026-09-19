package ru.hothat.media.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.hothat.media.domain.MemeAssetKey;
import ru.hothat.media.store.MemeFileStorage;

import java.net.URI;

/**
 * Найти файл мема в хранилище и назвать его подписанный адрес.
 *
 * <p>Единственный сценарий области без права: адрес открыт всем, потому что
 * его вставляют в {@code <video src>} и {@code <img src>}, а туда браузер
 * заголовок {@code Authorization} не передаёт. Секрета в файле нет — мемы
 * общие; ценность адреса в том, что он постоянный, а подпись хранилища живёт
 * час и обновляется на каждый запрос.
 *
 * <p>В базу сценарий не ходит вовсе: ключ проверяется по форме, и этого
 * достаточно — путь строит сервер, и чужого объекта в нём быть не может.
 * Лишнее чтение карточки стояло бы на пути каждого кадра видео.
 *
 * <p>{@code @PreAuthorize} здесь намеренно отсутствует: аннотация на этом
 * методе означала бы, что публичный адрес перестал быть публичным.
 */
@Service
@RequiredArgsConstructor
public class ResolveMemeFileUseCase {

    private final MemeFileStorage files;

    public URI run(String storageKey) {
        return URI.create(files.presignRedirect(MemeAssetKey.require(storageKey)));
    }
}
