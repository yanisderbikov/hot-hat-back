package ru.hothat.game.domain.weapon;

import ru.hothat.config.ApiException;
import ru.hothat.game.domain.LoadoutRules;
import ru.hothat.game.domain.MatchPlayer;
import ru.hothat.game.domain.MatchState;
import ru.hothat.game.domain.MemeQueue;
import ru.hothat.game.domain.ReplacementClip;
import ru.hothat.game.domain.SabotageEvent;
import ru.hothat.game.domain.TurnIds;
import ru.hothat.game.domain.TurnRules;
import ru.hothat.game.domain.Weapon;
import ru.hothat.game.domain.WeaponRegistry;
import ru.hothat.game.domain.WeaponType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Реестр обработчиков оружия: вид оружия → что с ним делать.
 *
 * <p>Именно он заменяет тринадцать адресов вида {@code /sabotage/tomato}
 * и {@code switch} по строке в теле запроса. Одиннадцати видам оружия хватает
 * общего обработчика — они отличаются только строками каталога; своё поведение
 * есть у трёх: мем сжигает патрон из обоймы, накладка несёт координаты,
 * Подмена сжигает снятый клип.
 */
public final class WeaponHandlers {

    private static final Map<WeaponType, WeaponHandler> HANDLERS = handlers();

    private WeaponHandlers() {
    }

    private static Map<WeaponType, WeaponHandler> handlers() {
        Map<WeaponType, WeaponHandler> handlers = new EnumMap<>(WeaponType.class);
        for (Weapon weapon : WeaponRegistry.all()) {
            handlers.put(weapon.type(), WeaponHandlers::standard);
        }
        handlers.put(WeaponType.MEME, WeaponHandlers::meme);
        handlers.put(WeaponType.OBJECT, WeaponHandlers::overlay);
        handlers.put(WeaponType.POOP, WeaponHandlers::overlay);
        handlers.put(WeaponType.MEGA_TEXT, WeaponHandlers::overlay);
        handlers.put(WeaponType.REPLACEMENT, WeaponHandlers::replacement);
        return Map.copyOf(handlers);
    }

    public static WeaponHandler of(WeaponType type) {
        WeaponHandler handler = HANDLERS.get(type);
        if (handler == null) {
            throw new IllegalStateException("Нет обработчика оружия " + type);
        }
        return handler;
    }

    // ───────────────────────── обработчики ─────────────────────────

    /** Оружие без собственных правил: помидор, крокодил, голоса, негатив, маска, пук. */
    private static SabotageDecision standard(SabotageContext context) {
        long duration = duration(context, 0);
        return SabotageDecision.of(event(context, duration, null, null, null), lockUntil(context, duration));
    }

    /** Мем: тратится не только заряд, но и конкретный ролик из обоймы. */
    private static SabotageDecision meme(SabotageContext context) {
        MemeMedia media = context.command().media();
        String memeId = context.command().memeId();
        if (media == null || memeId == null || memeId.isBlank()) {
            throw ApiException.of("MEME_NOT_LOADED", 400);
        }
        MatchPlayer attacker = context.attacker();
        MemeQueue queue = LoadoutRules.queue(attacker);
        if (!queue.loaded(memeId)) {
            throw ApiException.of("MEME_NOT_LOADED", 403);
        }
        if (!queue.ready(memeId)) {
            // Ролик отстрелян и ждёт своей очереди: заряд его не возвращает.
            throw ApiException.of("MEME_IN_RESERVE", 409);
        }
        long duration = media.clampedDurationMs();
        SabotageEvent base = event(context, duration, memeId, null, null);
        SabotageEvent withMedia = new SabotageEvent(base.id(), base.type(), memeId, null, null,
                base.attackerUid(), base.attackerName(), base.targetUid(), base.createdAtMs(), duration,
                base.gameNumber(), null, null, media.title(), media.src(), media.poster(),
                media.mediaPath(), media.posterPath(), media.storageProvider(), null, null);
        return new SabotageDecision(withMedia, lockUntil(context, duration), true, null);
    }

    /** Накладка: объект, какахи и мега текст ставятся в точку сцены. */
    private static SabotageDecision overlay(SabotageContext context) {
        long duration = duration(context, 0);
        double x = clampToScene(context.command().x());
        double y = clampToScene(context.command().y());
        return SabotageDecision.of(event(context, duration, null, null, null).withPoint(x, y),
                lockUntil(context, duration));
    }

    /** Подмена: показывает вместо объясняющего клип, снятый в прошлом ходу. */
    private static SabotageDecision replacement(SabotageContext context) {
        MatchState state = context.state();
        String clipId = context.command().clipId();
        if (clipId == null || clipId.isBlank()) {
            throw ApiException.of("REPLACEMENT_CLIP_INVALID", 400);
        }
        ReplacementClip clip = state.getClips().get(clipId);
        if (clip == null || !clip.attackerUid().equals(context.command().attackerUid())) {
            throw ApiException.of("REPLACEMENT_CLIP_INVALID", 404);
        }
        if (!clip.ready()) {
            throw ApiException.of("REPLACEMENT_NOT_READY", 409);
        }
        if (clip.gameNumber() != state.getGameNumber()) {
            throw ApiException.of("REPLACEMENT_CLIP_INVALID", 409);
        }
        if (!state.activeRoster().contains(clip.targetUid())) {
            // Снятый игрок сейчас не объясняет: подменять нечего.
            throw ApiException.of("REPLACEMENT_WRONG_TARGET", 409);
        }
        if (clip.recordedTurnId() != null && clip.recordedTurnId().equals(state.getTurnId())) {
            throw ApiException.of("REPLACEMENT_SAME_TURN", 409);
        }
        long duration = duration(context, 0);
        SabotageEvent base = event(context, duration, null, clipId, clip.recordedTurnId());
        SabotageEvent aimed = base.withTarget(clip.targetUid());
        return new SabotageDecision(aimed, lockUntil(context, duration), false, clipId);
    }

    // ───────────────────────── общее ─────────────────────────

    private static SabotageEvent event(SabotageContext context, long durationMs, String memeId,
                                       String clipId, String recordedTurnId) {
        MatchState state = context.state();
        Weapon weapon = WeaponRegistry.of(context.command().type());
        String targetUid = weapon.targetsExplainer() ? state.getExplainerUid() : null;
        return new SabotageEvent(TurnIds.sabotage(context.random()), weapon.type().code(), memeId, clipId,
                recordedTurnId, context.attacker().getUid(), context.attacker().displayName(), targetUid,
                context.nowMs(), durationMs, state.getGameNumber(), null, null,
                null, null, null, null, null, null, null, null);
    }

    /**
     * Длительность эффекта. Крокодил держится до конца хода, поэтому считается
     * от дедлайна; секунда снизу — чтобы эффект не был мгновенным на самом
     * излёте хода.
     */
    private static long duration(SabotageContext context, long fromMedia) {
        Weapon weapon = WeaponRegistry.of(context.command().type());
        if (weapon.durationMs() == Weapon.DURATION_FROM_MEDIA) {
            return fromMedia;
        }
        if (weapon.durationMs() != Weapon.DURATION_TO_TURN_END) {
            return weapon.durationMs();
        }
        long deadline = TurnRules.deadline(context.state());
        long until = deadline > 0 ? deadline
                : context.nowMs() + Math.round(TurnRules.durationSeconds(context.state()) * 1000);
        return Math.max(1000, until - context.nowMs());
    }

    /** Эффект не переживает ход: иначе он тянулся бы на чужой. */
    private static long lockUntil(SabotageContext context, long durationMs) {
        long deadline = TurnRules.deadline(context.state());
        long effective = deadline > 0 ? deadline : context.nowMs() + durationMs;
        return Math.min(effective, context.nowMs() + durationMs);
    }

    /** Накладка не должна уезжать за край сцены. */
    private static double clampToScene(Double value) {
        double point = value == null ? 0.5 : value;
        return Math.max(0.02, Math.min(0.98, point));
    }
}
