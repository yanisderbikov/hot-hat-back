package ru.hothat.machine.security;

import ru.hothat.machine.domain.MachineActor;
import ru.hothat.machine.domain.MachineScope;

/**
 * Личность машинного запроса.
 *
 * <p>Здесь не {@code HotHatUser} и не может им быть: за рекордером, вебхуком,
 * cron и агентом нет строки в {@code app_user}. Сегодняшний обход этого —
 * {@code RecorderStateServiceImpl.recorderCustomToken}, который выписывает
 * JWT на несуществующего пользователя {@code hot-hat-recorder}; в комментарии
 * там прямо написано, что обычный фильтр такой токен не примет.
 *
 * <p>Область съёмки хранится вместе с актором, потому что подпись рекордера
 * даёт право не «вообще», а на одну партию одной комнаты.
 */
public record MachineActorPrincipal(MachineActor actor, MachineScope scope) {

    @Override
    public String toString() {
        return scope.present() ? actor.name() + "@" + scope.roomId() + "#" + scope.gameNumber() : actor.name();
    }
}
