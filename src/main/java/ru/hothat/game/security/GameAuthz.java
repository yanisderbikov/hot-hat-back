package ru.hothat.game.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import ru.hothat.config.ApiException;
import ru.hothat.config.HotHatUser;
import ru.hothat.game.domain.MatchPhase;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.port.RoomLifecyclePort;
import ru.hothat.game.store.MatchStore;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Предикаты прав области партии: {@code @gameAuthz} в {@code @PreAuthorize}.
 *
 * <p>Тонкая обёртка над чтением, без единого бизнес-правила: «этот человек
 * объясняет» — вопрос факта, а не решения. Всё, что похоже на решение
 * («активная команда не атакует сама себя», «голосует не своя команда»),
 * осталось в сценариях и в движке — иначе правило пришлось бы читать в двух
 * местах сразу.
 *
 * <p>Бин с областью запроса и кешем: SpEL считает предикат до входа в метод, и
 * без кеша каждый вызов читал бы комнату второй раз. План (§7.4) называет это
 * прямо.
 *
 * <p>Предикаты уровня комнаты ({@code isMember}, {@code isHost},
 * {@code isMemberOrSpectator}) объявлены здесь же, а не берутся у
 * {@code @roomAuthz}: он принадлежит области комнаты, которая пишется
 * параллельно. Читают они всё равно через {@link RoomLifecyclePort}, так что
 * замена на чужой предикат — правка одной строки в аннотации.
 *
 * <p><b>Почему отказ называет себя, а не возвращается ложью.</b> Простое
 * {@code false} уходит наружу как {@code AccessDeniedException}, а общий
 * обработчик объясняет любой такой отказ кодом {@code ADMIN_REQUIRED} —
 * «Нет прав администратора» ({@code GlobalExceptionHandler.deniedCode}).
 * Человеку, который зашёл в комнату посреди партии и нажал «начать ход», это
 * прямая неправда, а {@code TurnController} к тому же обещает в спецификации
 * совсем другой код — {@code TURN_NOT_YOURS}. Поэтому предикат, разобравшись,
 * что права нет, бросает {@link ApiException} со своим кодом: SpEL пропускает
 * {@code RuntimeException} из вызванного метода наружу без обёртки
 * ({@code MethodReference.throwSimpleExceptionIfPossible}), и ответ собирает
 * тот же обработчик, что и отказы внутри сценариев. Ровно по этой причине
 * область комнаты проверяет права в теле сценария; здесь же
 * {@code @PreAuthorize} остаётся единственным местом, где право объявлено.
 *
 * <p>Отсутствие личности — не то же самое, что нехватка права, и остаётся
 * обычным {@code false}: невошедшему и гостю отвечает общая цепочка, которая
 * различает их между собой.
 */
@Component("gameAuthz")
@RequestScope
@RequiredArgsConstructor
public class GameAuthz {

    private final RoomLifecyclePort rooms;
    private final MatchStore matchStore;

    private final Map<String, MatchState> matches = new HashMap<>();
    private final Map<String, Boolean> answers = new HashMap<>();

    /** Хозяин комнаты: он распоряжается течением партии. */
    public boolean isHost(String roomId) {
        return require("host:" + roomId, "HOST_ONLY", uid -> rooms.isHost(roomId, uid));
    }

    /** Участник комнаты — тот, у кого есть место игрока. */
    public boolean isMember(String roomId) {
        return require("member:" + roomId, "ROOM_MEMBER_ONLY", uid -> rooms.isMember(roomId, uid));
    }

    /** Участник или зритель: состояние партии видят оба. */
    public boolean isMemberOrSpectator(String roomId) {
        return require("watcher:" + roomId, "ROOM_MEMBER_ONLY",
                uid -> rooms.isMember(roomId, uid) || rooms.isSpectator(roomId, uid));
    }

    /**
     * Игрок текущей партии — по замороженным на старте составам.
     *
     * <p>Не то же самое, что участник комнаты: зашедший в середине партии
     * человек сидит в комнате, но ни стрелять, ни голосовать не может.
     *
     * <p>Пока партия не идёт, составов ещё нет, и правом обладает любой
     * участник комнаты: обойму мемов заряжают именно там — до старта. Иначе
     * снарядиться не смог бы никто, а после старта было бы поздно.
     */
    public boolean isPlayer(String roomId) {
        return require("player:" + roomId, "PLAYER_NOT_FOUND", uid -> {
            MatchState state = match(roomId);
            if (!state.getPhase().live()) {
                return rooms.isMember(roomId, uid);
            }
            return state.isPlayer(uid);
        });
    }

    /**
     * Тот, кто объясняет.
     *
     * <p>В карточке хода объясняющего ещё нет — его выбирает сам ход. Поэтому
     * до начала хода правом обладает любой из двоих активной команды: иначе
     * начать ход не смог бы никто.
     */
    public boolean isExplainer(String roomId) {
        return require("explainer:" + roomId, "TURN_NOT_YOURS", uid -> {
            MatchState state = match(roomId);
            if (state.getPhase() == MatchPhase.TURN_INTRO) {
                return state.activeRoster().contains(uid);
            }
            return uid.equals(state.getExplainerUid());
        });
    }

    private MatchState match(String roomId) {
        return matches.computeIfAbsent(roomId, matchStore::read);
    }

    /**
     * Ответ предиката: {@code true} либо названный отказ.
     *
     * @param refusalCode код, которым отказ представится клиенту; текст к нему
     *                    лежит в {@code ErrorMessages.BY_CODE}
     */
    private boolean require(String key, String refusalCode, Predicate<String> answer) {
        String uid = currentUid();
        if (uid == null) {
            return false;
        }
        // Ответ кладётся в кеш до броска: SpEL считает предикат до входа в
        // метод, и без кеша повторный вопрос читал бы комнату второй раз.
        if (answers.computeIfAbsent(key, ignored -> answer.test(uid))) {
            return true;
        }
        throw ApiException.of(refusalCode, 403);
    }

    private String currentUid() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof HotHatUser user)) {
            return null;
        }
        return user.uid();
    }
}
