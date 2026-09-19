package ru.hothat.media.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.media.domain.MemeAssetKey;
import ru.hothat.media.store.MemeFileStorage;
import ru.hothat.media.store.MemeStore;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Снять свой мем с публикации.
 *
 * <p>Право автора, а не администратора: сегодня кнопка «Удалить» есть только
 * у админа ({@code app-core.js:5889}), и человек, выложивший неудачный ролик,
 * убрать его не может вовсе. Админское снятие никуда не девается — оно живёт
 * по своему адресу в {@code /api/v2/admin/memes} и не спрашивает авторства.
 *
 * <p>Порядок шагов важен: сначала карточка, потом файлы. В обратном порядке
 * сбой на середине оставил бы в библиотеке мем, ролика у которого больше нет,
 * — а это чёрный экран у всей комнаты в момент выстрела.
 *
 * <p>Проверка авторства живёт внутри сценария, а не в предикате на классе:
 * ответ на вопрос «ваш ли это мем» требует чтения самой строки, и предикат
 * уровня класса прочитал бы её вторым запросом.
 */
@Service
@RequiredArgsConstructor
public class WithdrawMyMemeUseCase {

    private final MemeStore memes;
    private final MemeFileStorage files;
    private final Clock clock;

    @PreAuthorize("hasRole('USER')")
    public void run(HotHatUser user, String memeId) {
        MemeStore.Card meme = memes.find(memeId)
                .orElseThrow(() -> ApiException.of("MEME_NOT_FOUND", 404));
        if (MemeAssetKey.isBuiltin(memeId) || meme.builtin()) {
            // Встроенный мем не принадлежит никому из игроков: он посеян
            // вместе с игрой и лежит в обойме по умолчанию у всех.
            throw ApiException.of("BUILTIN_MEME_PROTECTED", 403);
        }
        if (!user.uid().equals(meme.ownerUid())) {
            throw ApiException.of("NOT_MEME_AUTHOR", 403);
        }
        List<String> orphaned = memes.withdraw(memeId, Instant.now(clock));
        files.forget(orphaned);
    }
}
