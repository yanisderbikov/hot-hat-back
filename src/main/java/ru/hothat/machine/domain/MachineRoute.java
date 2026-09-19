package ru.hothat.machine.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор машинного адреса: какой актор его открывает и какой съёмке он
 * принадлежит.
 *
 * <p>Это единственное место, где путь превращается в право. Раньше такого
 * места не было вовсе: каждый машинный метод сам решал, кого пускать, и
 * решения разошлись — cron-уборка комнат пускала ещё и «любого вошедшего»
 * ({@code CleanupController:41}), а уборка записей — только админа
 * ({@code CleanupController:60}), хотя это одна и та же плановая работа.
 *
 * <p>Идентификатор комнаты сверяется тем же выражением, что и {@code @RoomId}:
 * фильтр не должен пускать дальше адрес, который разбор пути потом отвергнет.
 */
public record MachineRoute(MachineActor actor, MachineScope scope) {

    public static final String PREFIX = "/api/v2/machine";

    private static final Pattern RECORDER = Pattern.compile(
            "^/api/v2/machine/recorder/rooms/(hat-[a-f0-9]{16})/games/(\\d{1,9})/([a-z-]+)$");
    private static final Pattern EGRESS = Pattern.compile(
            "^/api/v2/machine/webhooks/livekit-egress/rooms/(hat-[a-f0-9]{16})/games/(\\d{1,9})$");
    private static final Pattern MAINTENANCE = Pattern.compile(
            "^/api/v2/machine/maintenance/[a-z-]+$");
    private static final Pattern USAGE = Pattern.compile(
            "^/api/v2/machine/usage-snapshots$");

    /** Снаряжение — единственный под-ресурс рекордера со своей ролью. */
    private static final String BOOTSTRAP_SEGMENT = "sessions";

    /**
     * @return разбор адреса или {@code null}, если это не машинный адрес —
     * тогда фильтр не выдаёт ролей, и запрос отсекает {@code authenticated()}.
     */
    public static MachineRoute of(String path) {
        if (path == null || !path.startsWith(PREFIX)) {
            return null;
        }
        Matcher recorder = RECORDER.matcher(path);
        if (recorder.matches()) {
            MachineScope scope = new MachineScope(recorder.group(1), Integer.parseInt(recorder.group(2)));
            boolean bootstrap = BOOTSTRAP_SEGMENT.equals(recorder.group(3));
            return new MachineRoute(bootstrap ? MachineActor.RECORDER_BOOTSTRAP : MachineActor.RECORDER, scope);
        }
        Matcher egress = EGRESS.matcher(path);
        if (egress.matches()) {
            return new MachineRoute(MachineActor.EGRESS,
                    new MachineScope(egress.group(1), Integer.parseInt(egress.group(2))));
        }
        if (MAINTENANCE.matcher(path).matches()) {
            return new MachineRoute(MachineActor.CRON, MachineScope.NONE);
        }
        if (USAGE.matcher(path).matches()) {
            return new MachineRoute(MachineActor.MONITOR_AGENT, MachineScope.NONE);
        }
        return null;
    }
}
