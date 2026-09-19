package ru.hothat.recording.store;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import ru.hothat.common.identity.LegacyIdBridge;
import ru.hothat.recording.domain.EgressStage;
import ru.hothat.recording.domain.RecordingKey;
import ru.hothat.recording.domain.RetentionRules;
import ru.hothat.recording.port.EgressControlPort;
import ru.hothat.recording.port.RoomSnapshotPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Единственная дверь области записей в свои девять таблиц.
 *
 * <p>Главное, ради чего затевался переезд, живёт здесь и должно жить здесь
 * дальше: у пяти жизненных циклов записи ПЯТЬ РАЗНЫХ ПИСАТЕЛЕЙ, и ни один
 * метод этого класса не пишет в две таблицы разных циклов сразу.
 *
 * <ul>
 *   <li>паспорт партии — пишется один раз на старте и один раз на финише
 *       ({@link #freezeResults}), больше никогда;</li>
 *   <li>задание Egress — вебхуки и опрос LiveKit;</li>
 *   <li>отметки рекордера — сама страница-рекордер;</li>
 *   <li>файл в бакете — завершение выгрузки и уборка;</li>
 *   <li>срок хранения и счётчик сохранений — ИГРОКИ.</li>
 * </ul>
 *
 * <p>Раньше все пять лежали в одной строке на шестьдесят три колонки, и это
 * значило ровно то, на что жаловались: вебхук LiveKit переписывал заодно
 * счётчик сохранений, а сохранение записи — состояние Egress. Не сводите их
 * обратно: удобный метод «сохрани мне всю запись» вернёт ту же беду.
 *
 * <p>Наружу отдаются записи ({@link Card} и её части), а не сущности:
 * {@code Recording}, {@code EgressJob} и соседи не публичны и в сигнатуры
 * сценариев не попадают. Здесь же живёт перевод uid ↔ uuid и склейки
 * {@code hat-…-3} в суррогат.
 *
 * <p>Чтений в цикле по коллекции нет ни одного: библиотека на полсотни
 * записей стоит семь запросов — по одному на таблицу, — а не триста пятьдесят.
 */
@Component
@RequiredArgsConstructor
public class RecordingStore {

    private final Recordings recordings;
    private final RecordingParticipants participants;
    private final RecordingTeams teams;
    private final EgressJobs jobs;
    private final EgressEvents events;
    private final RecorderSessions sessions;
    private final RecordingArtifacts artifacts;
    private final RecordingRetentions retentions;
    private final RecordingSaves saves;
    private final RecordingShares shares;
    private final LegacyIdBridge ids;

    /**
     * Нужен ради вставок снимка: строки состава и команд называем мы сами, и
     * обычное сохранение искало бы каждую в базе прежде, чем вставить.
     */
    @PersistenceContext
    private EntityManager entities;

    // ───────────────────────────── чтение ─────────────────────────────

    /** Суррогат записи по её имени снаружи. Чистый расчёт, в базу не ходит. */
    public UUID idOf(RecordingKey key) {
        return ids.recordingId(key.publicId());
    }

    /** Заведена ли запись этой партии. */
    public boolean exists(RecordingKey key) {
        return recordings.existsById(idOf(key));
    }

    public Optional<Card> card(RecordingKey key) {
        return card(idOf(key));
    }

    public Optional<Card> card(UUID id) {
        List<Card> found = cards(List.of(id));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /**
     * Карточки пачкой — по одному запросу на таблицу.
     *
     * <p>Порядок ответа повторяет порядок запроса; записи, которой нет, в
     * ответе просто не будет.
     */
    public List<Card> cards(Collection<UUID> wanted) {
        List<UUID> keys = wanted.stream().distinct().toList();
        if (keys.isEmpty()) {
            return List.of();
        }
        Map<UUID, Recording> passports = new HashMap<>();
        for (Recording row : recordings.findByIdIn(keys)) {
            passports.put(row.getId(), row);
        }
        List<RecordingParticipant> people = participants.findByRecordingIdIn(keys);
        // Обратный перевод игроков одним запросом на всю пачку: иначе полсотни
        // карточек стоили бы полсотни обращений к мосту.
        Map<UUID, String> uids = ids.playerUids(people.stream()
                .map(RecordingParticipant::getPlayerId).toList());
        Map<UUID, List<Participant>> roster = new HashMap<>();
        for (RecordingParticipant row : people) {
            roster.computeIfAbsent(row.getRecordingId(), k -> new ArrayList<>())
                    .add(participant(row, uids.get(row.getPlayerId())));
        }
        Map<UUID, List<RecordingTeam>> lineups = new HashMap<>();
        for (RecordingTeam row : teams.findByRecordingIdIn(keys)) {
            lineups.computeIfAbsent(row.getRecordingId(), k -> new ArrayList<>()).add(row);
        }
        Map<UUID, EgressJob> byJob = new HashMap<>();
        for (EgressJob row : jobs.findByRecordingIdIn(keys)) {
            byJob.put(row.getRecordingId(), row);
        }
        Map<UUID, RecorderSession> bySession = new HashMap<>();
        for (RecorderSession row : sessions.findByRecordingIdIn(keys)) {
            bySession.put(row.getRecordingId(), row);
        }
        Map<UUID, RecordingArtifact> byArtifact = new HashMap<>();
        for (RecordingArtifact row : artifacts.findByRecordingIdIn(keys)) {
            byArtifact.put(row.getRecordingId(), row);
        }
        Map<UUID, RecordingRetention> byRetention = new HashMap<>();
        for (RecordingRetention row : retentions.findByRecordingIdIn(keys)) {
            byRetention.put(row.getRecordingId(), row);
        }

        List<Card> result = new ArrayList<>(keys.size());
        for (UUID id : keys) {
            Recording passport = passports.get(id);
            if (passport == null) {
                continue;
            }
            List<Participant> crew = roster.getOrDefault(id, List.of());
            result.add(new Card(
                    passport(passport),
                    crew,
                    lineup(lineups.getOrDefault(id, List.of()), crew),
                    job(byJob.get(id)),
                    session(bySession.get(id)),
                    artifact(byArtifact.get(id)),
                    retention(byRetention.get(id))));
        }
        return result;
    }

    /** Записи, которые игрок положил к себе: свежие сверху. Один запрос. */
    public List<UUID> savedBy(String uid, int limit) {
        List<UUID> found = new ArrayList<>();
        for (RecordingSave row : saves.findByPlayerIdOrderBySavedAtDesc(
                ids.playerId(uid), Limit.of(Math.max(1, limit)))) {
            found.add(row.getRecordingId());
        }
        return found;
    }

    /** Какие из этих записей игрок держит у себя. Один запрос на весь список. */
    public List<UUID> savedAmong(Collection<UUID> ids0, String uid) {
        if (ids0.isEmpty()) {
            return List.of();
        }
        List<UUID> found = new ArrayList<>();
        for (RecordingSave row : saves.findByRecordingIdInAndPlayerId(ids0, ids.playerId(uid))) {
            found.add(row.getRecordingId());
        }
        return found;
    }

    public boolean isParticipant(UUID recordingId, String uid) {
        return participants.existsByRecordingIdAndPlayerId(recordingId, ids.playerId(uid));
    }

    public boolean isSaved(UUID recordingId, String uid) {
        return saves.existsById(new RecordingSaveId(recordingId, ids.playerId(uid)));
    }

    public boolean isShared(UUID recordingId, String uid) {
        return shares.existsByRecordingIdAndGrantee(recordingId, ids.playerId(uid));
    }

    /** Запись, которой владеет это задание Egress. Поиск по уникальному ключу. */
    public Optional<UUID> byEgressId(String egressId) {
        return egressId == null || egressId.isBlank()
                ? Optional.empty()
                : jobs.findByEgressId(egressId).map(EgressJob::getRecordingId);
    }

    // ─────────────────────── паспорт партии ───────────────────────

    /**
     * Завести запись партии.
     *
     * <p>Пишет паспорт, состав, команды и заводит по одной строке каждому из
     * остальных циклов в их НАЧАЛЬНОМ состоянии. Это не нарушение разделения:
     * циклы разные, но их отсчёт начинается здесь, и дальше каждую строку
     * трогает только её писатель.
     *
     * <p>Срок хранения ставится сразу, а не на финише, как раньше. Запись,
     * старт которой сорвался и которую никто не завершил, тогда оставалась в
     * бакете навсегда — уборке она была не видна.
     */
    public UUID openPassport(RecordingKey key, String startedByUid,
                             RoomSnapshotPort.RoomSnapshot room, boolean prewarm, Instant now) {
        UUID id = idOf(key);
        ids.rememberRecording(key.publicId());
        ids.rememberPlayers(room.seats().stream().filter(seat -> !seat.bot())
                .map(RoomSnapshotPort.Seat::uid).toList());
        ids.rememberPlayers(List.of(startedByUid));

        if (!recordings.existsById(id)) {
            recordings.save(Recording.builder()
                    .id(id)
                    .roomId(key.roomId())
                    .gameNumber(key.gameNumber())
                    .title(trim(room.name() == null || room.name().isBlank() ? room.roomId() : room.name(), 80))
                    .gameMode(trim(room.gameMode() == null ? "classic" : room.gameMode(), 16))
                    .ranked(room.ranked())
                    .privateRoom(room.privateRoom())
                    .testRoom(room.testRoom())
                    .divisionLanguage(trim(room.divisionLanguage(), 8))
                    .gameLanguage(trim(room.gameLanguage(), 8))
                    .wordCount(Math.max(0, room.wordCount()))
                    .startedBy(ids.playerId(startedByUid))
                    .createdAt(now)
                    .build());
        }
        writeRoster(id, room);
        if (!jobs.existsById(id)) {
            jobs.save(EgressJob.builder()
                    .recordingId(id)
                    .state(EgressStage.STARTING.wire())
                    .startRequestedAt(now)
                    .build());
        }
        if (!sessions.existsById(id)) {
            sessions.save(RecorderSession.builder()
                    .recordingId(id)
                    .prewarm(prewarm)
                    .openedAt(now)
                    .build());
        }
        if (!retentions.existsById(id)) {
            retentions.save(RecordingRetention.builder()
                    .recordingId(id)
                    .expiresAt(RetentionRules.expiryFrom(now))
                    .saveCount(0)
                    .build());
        }
        return id;
    }

    /**
     * Заморозить итог партии: счёт команд, победителя и число слов.
     *
     * <p>Зовётся на финише и только на финише. Состав и счёт берутся заново —
     * на старте партии их ещё нет, а прогретая запись заводится вообще до
     * первого хода.
     */
    public void freezeResults(UUID id, RoomSnapshotPort.RoomSnapshot room) {
        recordings.findById(id).ifPresent(row -> {
            row.setWordCount(Math.max(0, room.wordCount()));
            recordings.save(row);
        });
        writeRoster(id, room);
    }

    /**
     * Состав и команды одним куском: обе таблицы описывают ОДИН снимок партии,
     * и половина снимка хуже, чем никакого.
     */
    private void writeRoster(UUID id, RoomSnapshotPort.RoomSnapshot room) {
        int best = room.teams().stream().mapToInt(RoomSnapshotPort.Team::score).max().orElse(0);
        List<RecordingTeam> lineup = new ArrayList<>();
        for (RoomSnapshotPort.Team team : room.teams()) {
            lineup.add(RecordingTeam.builder()
                    .recordingId(id)
                    .roomTeamId(teamId(room.roomId(), team.teamId()))
                    .name(trim(team.name() == null || team.name().isBlank() ? "Команда" : team.name(), 40))
                    .rankedTeamId(team.rankedTeamId() == null || team.rankedTeamId().isBlank()
                            ? null : teamId(room.roomId(), team.rankedTeamId()))
                    .score(team.score())
                    // При ничьей признак стоит у всех, кто набрал лучший счёт:
                    // раньше это выражалось параллельными массивами имён и
                    // идентификаторов, которые расходились именно на ничьей.
                    .winner(!room.teams().isEmpty() && team.score() == best)
                    .build());
        }
        List<RecordingParticipant> roster = new ArrayList<>();
        for (RoomSnapshotPort.Seat seat : room.seats()) {
            roster.add(RecordingParticipant.builder()
                    .recordingId(id)
                    .playerId(ids.playerId(seat.uid()))
                    .nickname(trim(seat.nickname() == null || seat.nickname().isBlank()
                            ? "Игрок" : seat.nickname(), 20))
                    .roomTeamId(seat.teamId() == null || seat.teamId().isBlank()
                            ? null : teamId(room.roomId(), seat.teamId()))
                    .bot(seat.bot())
                    .build());
        }
        // Колонки снимка объявлены неизменяемыми: снимок либо переснимается
        // целиком, либо остаётся прежним. Правка половины строк дала бы
        // состав одной партии со счётом другой.
        teams.deleteByRecordingId(id);
        participants.deleteByRecordingId(id);
        // persist, а не save: у строк снимка ключ назначен нами, и save искал
        // бы каждую в базе по одной, прежде чем вставить.
        lineup.forEach(entities::persist);
        roster.forEach(entities::persist);
    }

    // ─────────────────────── задание Egress ───────────────────────

    /** LiveKit принял запрос: у задания появился идентификатор. */
    public void attachEgress(UUID id, EgressControlPort.Snapshot started, Instant now) {
        jobs.findById(id).ifPresent(job -> {
            EgressStage stage = EgressStage.fromLiveKit(started.liveKitCode(), EgressStage.STARTING);
            if (started.egressId() != null && !started.egressId().isBlank()) {
                job.setEgressId(started.egressId());
            }
            applyStage(job, stage, started.error(), now);
            job.setLastSyncAt(now);
            jobs.save(job);
        });
    }

    /** Старт сорвался: задания не будет, и запись обязана назвать причину. */
    public void failJob(UUID id, String reason, Instant now) {
        jobs.findById(id).ifPresent(job -> {
            applyStage(job, EgressStage.FAILED, reason == null || reason.isBlank()
                    ? "egress start refused" : reason, now);
            jobs.save(job);
        });
    }

    /** Мы попросили LiveKit остановиться; ответ применяется отдельно. */
    public void markStopRequested(UUID id, Instant now) {
        jobs.findById(id).ifPresent(job -> {
            job.setStopRequestedAt(now);
            jobs.save(job);
        });
    }

    /**
     * Применить ответ LiveKit — вебхук или опрос, разницы для задания нет.
     *
     * <p>Пишет ТОЛЬКО строку задания. Файл, если он приехал в том же ответе,
     * кладёт {@link #putArtifact}: у файла свой писатель и своя строка.
     *
     * @return стадия задания после применения
     */
    public EgressStage applyEgressSnapshot(UUID id, EgressControlPort.Snapshot snapshot, Instant now) {
        EgressJob job = jobs.findById(id).orElse(null);
        if (job == null) {
            return EgressStage.STARTING;
        }
        EgressStage was = EgressStage.ofWire(job.getState());
        EgressStage stage = EgressStage.fromLiveKit(snapshot.liveKitCode(), was);
        if (snapshot.egressId() != null && !snapshot.egressId().isBlank()) {
            job.setEgressId(snapshot.egressId());
        }
        applyStage(job, stage, snapshot.error(), now);
        if (snapshot.endedAtMs() != null && snapshot.endedAtMs() > 0) {
            job.setEndedAt(Instant.ofEpochMilli(snapshot.endedAtMs()));
        }
        job.setLastSyncAt(now);
        jobs.save(job);
        return stage;
    }

    /**
     * Перевод стадии с оглядкой на ограничение базы: провал обязан назвать
     * причину, а всё остальное обязано её не иметь. Раньше причина разъезжалась
     * по двум колонкам, и какая заполнена — зависело от шага, на котором
     * сорвалось.
     */
    private static void applyStage(EgressJob job, EgressStage stage, String error, Instant now) {
        job.setState(stage.wire());
        job.setFailureReason(stage == EgressStage.FAILED
                ? trim(error == null || error.isBlank() ? "egress failed" : error, 600)
                : null);
        if (stage == EgressStage.ACTIVE && job.getActiveAt() == null) {
            // ACTIVE — первое состояние, доказывающее, что запись реально пошла.
            job.setActiveAt(now);
        }
        if (stage.finished() && job.getEndedAt() == null) {
            job.setEndedAt(now);
        }
    }

    // ─────────────────────── отметки рекордера ───────────────────────

    public void markRecorderReady(UUID id, String phase, String identity, Instant now) {
        touchSession(id, now, session -> {
            session.setReadyAt(now);
            session.setReadyPhase(trim(phase, 24));
            if (identity != null && !identity.isBlank()) {
                session.setLivekitIdentity(trim(identity, 220));
            }
        });
    }

    public void markRecorderStarted(UUID id, String phase, String identity, Instant now) {
        touchSession(id, now, session -> {
            // Порядок сигналов проверяет база: снимать нельзя раньше
            // готовности. Рекордер, отчитавшийся о первом кадре, готов по
            // определению — сигнал готовности мог просто потеряться в сети.
            if (session.getReadyAt() == null) {
                session.setReadyAt(now);
                session.setReadyPhase(trim(phase, 24));
            }
            session.setStartSignalAt(now);
            session.setStartPhase(trim(phase, 24));
            if (identity != null && !identity.isBlank()) {
                session.setLivekitIdentity(trim(identity, 220));
            }
        });
    }

    public void markCeremonyCompleted(UUID id, Instant now) {
        touchSession(id, now, session -> {
            if (session.getStartSignalAt() == null) {
                // Та же оговорка, что выше: церемония не кончается раньше
                // первого кадра, и база это проверяет.
                if (session.getReadyAt() == null) {
                    session.setReadyAt(now);
                }
                session.setStartSignalAt(now);
            }
            session.setCeremonyCompletedAt(now);
        });
    }

    private void touchSession(UUID id, Instant now, java.util.function.Consumer<RecorderSession> change) {
        RecorderSession session = sessions.findById(id).orElseGet(() -> RecorderSession.builder()
                .recordingId(id).openedAt(now).build());
        change.accept(session);
        sessions.save(session);
    }

    // ─────────────────────────── файл ───────────────────────────

    /** Файл выгружен: путь, размер и длительность приезжают вместе с ним. */
    public void putArtifact(UUID id, String objectPath, String bucket,
                            long sizeBytes, long durationNs, Instant now) {
        RecordingArtifact artifact = artifacts.findById(id).orElseGet(() -> RecordingArtifact.builder()
                .recordingId(id).objectPath(objectPath).createdAt(now).build());
        artifact.setObjectPath(objectPath);
        artifact.setStorageBucket(trim(bucket, 120));
        if (sizeBytes > 0) {
            artifact.setSizeBytes(sizeBytes);
        }
        if (durationNs > 0) {
            artifact.setDurationNs(durationNs);
        }
        artifacts.save(artifact);
    }

    /**
     * Файла больше нет.
     *
     * <p>Мягкое удаление: строка записи переживает свой файл, иначе ссылка на
     * запись в личной переписке указывала бы в пустоту, а карточка в чужой
     * библиотеке исчезала бы без объяснения.
     */
    public void markArtifactDeleted(UUID id, Instant now) {
        artifacts.findById(id).ifPresent(artifact -> {
            artifact.setDeletedAt(now);
            artifacts.save(artifact);
        });
    }

    // ─────────────────── срок хранения и библиотека ───────────────────

    /**
     * Положить запись к себе.
     *
     * @return {@code false} — она уже лежала; счётчик при этом не двигается
     */
    public boolean save(UUID id, String uid) {
        ids.rememberPlayers(List.of(uid));
        UUID player = ids.playerId(uid);
        if (saves.existsById(new RecordingSaveId(id, player))) {
            return false;
        }
        saves.save(RecordingSave.builder().recordingId(id).playerId(player).build());
        // Счётчик и срок двигаются одним условным обновлением: база требует их
        // согласованности, а два отдельных оператора оставили бы строку в
        // состоянии, которого ограничение не допускает.
        retentions.countSave(id);
        return true;
    }

    /**
     * Забрать запись из библиотеки.
     *
     * @return {@code false} — её там и не было
     */
    public boolean forget(UUID id, String uid, Instant now) {
        if (saves.deleteByRecordingIdAndPlayerId(id, ids.playerId(uid)) == 0) {
            return false;
        }
        retentions.countForget(id, RetentionRules.expiryFrom(now));
        return true;
    }

    /** Просроченные и никем не сохранённые. Частичный индекс отвечает целиком. */
    public List<UUID> expired(Instant before, int limit) {
        List<UUID> found = new ArrayList<>();
        for (RecordingRetention row : retentions.findBySaveCountAndExpiresAtLessThanOrderByExpiresAt(
                0, before, Limit.of(Math.max(1, Math.min(200, limit))))) {
            found.add(row.getRecordingId());
        }
        return found;
    }

    /**
     * Снять запись с учёта хранения: срок вышел, файл убран, второй раз она
     * уборке не попадётся.
     *
     * <p>Строка удаляется целиком, а не обнуляется: ограничение базы не
     * допускает записи без срока и без сохранивших, и правильно делает —
     * именно такая жила бы в бакете вечно.
     */
    public void dropRetention(UUID id) {
        retentions.deleteById(id);
    }

    // ─────────────────── журнал уведомлений LiveKit ───────────────────

    /**
     * Записать доставку вебхука.
     *
     * @return {@code true} — доставка новая и её следует применить;
     *         {@code false} — это повтор, который сегодня применялся второй раз
     */
    public boolean journal(String deliveryId, UUID recordingId, String eventName,
                           String egressId, String egressState, boolean applied,
                           Map<String, Object> payload, Instant now) {
        if (events.existsByDeliveryId(deliveryId)) {
            return false;
        }
        events.save(EgressEvent.builder()
                .deliveryId(trim(deliveryId, 120))
                .recordingId(recordingId)
                .eventName(trim(eventName, 60))
                .egressId(trim(egressId, 120))
                .egressState(trim(egressState, 40))
                .applied(applied)
                .payload(payload == null ? Map.of() : payload)
                .receivedAt(now)
                .build());
        return true;
    }

    // ─────────────────────────── записи наружу ───────────────────────────

    /**
     * Карточка записи: пять циклов рядом, но по-прежнему порознь.
     *
     * <p>{@code null} на месте цикла — это не «ноль», а «этого ещё не было»:
     * файла нет, рекордер не приходил, срок снят уборкой.
     */
    public record Card(Passport passport,
                       List<Participant> participants,
                       List<Team> teams,
                       Job job,
                       Session session,
                       Artifact artifact,
                       Retention retention) {

        /**
         * Стадия жизни записи — одна на всю карточку.
         *
         * <p>«Удалён» — свойство ФАЙЛА, а не задания: задание давно завершилось
         * успехом, и переписывать ему стадию значило бы соврать про съёмку.
         */
        public String stage() {
            if (artifact != null && artifact.deletedAtMs() != null) {
                return EgressStage.DELETED;
            }
            return job == null ? EgressStage.STARTING.wire() : job.state();
        }

        public boolean playable() {
            return EgressStage.COMPLETE.wire().equals(stage());
        }

        public int winningScore() {
            return teams.stream().mapToInt(Team::score).max().orElse(0);
        }

        public int totalScore() {
            return teams.stream().mapToInt(Team::score).sum();
        }

        public long sizeBytes() {
            return artifact == null ? 0L : artifact.sizeBytes();
        }

        public long durationNs() {
            return artifact == null ? 0L : artifact.durationNs();
        }

        public Integer saveCount() {
            return retention == null ? 0 : retention.saveCount();
        }

        public Long expiresAtMs() {
            return retention == null ? null : retention.expiresAtMs();
        }
    }

    public record Passport(UUID id, String recordingId, String roomId, int gameNumber, String title,
                           String gameMode, boolean ranked, boolean privateRoom, boolean testRoom,
                           String divisionLanguage, String gameLanguage, int wordCount, long createdAtMs) {
    }

    public record Participant(String uid, String nickname, String teamId, boolean bot) {
    }

    public record Team(String teamId, String name, String rankedTeamId, int score, boolean winner,
                       List<String> memberUids) {
    }

    public record Job(String egressId, String state, long startRequestedAtMs, Long activeAtMs,
                      Long stopRequestedAtMs, Long endedAtMs, Long lastSyncAtMs, String failureReason) {
    }

    public record Session(boolean prewarm, String livekitIdentity, Long readyAtMs, String readyPhase,
                          Long startSignalAtMs, String startPhase, Long ceremonyCompletedAtMs) {
    }

    public record Artifact(String objectPath, String bucket, long sizeBytes, long durationNs, Long deletedAtMs) {
    }

    public record Retention(Long expiresAtMs, int saveCount) {
    }

    // ─────────────────────────── перевод строк ───────────────────────────

    private Passport passport(Recording row) {
        RecordingKey key = new RecordingKey(row.getRoomId() == null ? "" : row.getRoomId(), row.getGameNumber());
        return new Passport(row.getId(), key.publicId(), row.getRoomId(), row.getGameNumber(),
                row.getTitle(), row.getGameMode(), Boolean.TRUE.equals(row.getRanked()),
                Boolean.TRUE.equals(row.getPrivateRoom()), Boolean.TRUE.equals(row.getTestRoom()),
                row.getDivisionLanguage(), row.getGameLanguage(),
                row.getWordCount() == null ? 0 : row.getWordCount(),
                row.getCreatedAt() == null ? 0L : row.getCreatedAt().toEpochMilli());
    }

    /**
     * Обратный перевод игроков — одним запросом на всю пачку.
     *
     * <p>Тест-бот обратного перевода не имеет и не должен: учётки у него нет,
     * а в мосте лежат только люди. Его uid остаётся пустым, а признак
     * {@code bot} говорит читателю, почему.
     */
    private static Participant participant(RecordingParticipant row, String uid) {
        return new Participant(uid == null ? "" : uid, row.getNickname(),
                row.getRoomTeamId() == null ? null : row.getRoomTeamId().toString(),
                Boolean.TRUE.equals(row.getBot()));
    }

    private List<Team> lineup(List<RecordingTeam> rows, List<Participant> roster) {
        Map<String, List<String>> members = new LinkedHashMap<>();
        for (Participant person : roster) {
            if (person.teamId() != null && !person.uid().isEmpty()) {
                members.computeIfAbsent(person.teamId(), k -> new ArrayList<>()).add(person.uid());
            }
        }
        List<Team> result = new ArrayList<>(rows.size());
        for (RecordingTeam row : rows) {
            String teamId = row.getRoomTeamId().toString();
            result.add(new Team(teamId, row.getName(),
                    row.getRankedTeamId() == null ? null : row.getRankedTeamId().toString(),
                    row.getScore() == null ? 0 : row.getScore(),
                    Boolean.TRUE.equals(row.getWinner()),
                    // Состава списком в таблице нет намеренно: он выводится из
                    // recording_participant.room_team_id и потому не может с
                    // ним разойтись.
                    members.getOrDefault(teamId, List.of())));
        }
        return result;
    }

    private static Job job(EgressJob row) {
        return row == null ? null : new Job(row.getEgressId(), row.getState(),
                millis(row.getStartRequestedAt(), 0L), nullableMillis(row.getActiveAt()),
                nullableMillis(row.getStopRequestedAt()), nullableMillis(row.getEndedAt()),
                nullableMillis(row.getLastSyncAt()), row.getFailureReason());
    }

    private static Session session(RecorderSession row) {
        return row == null ? null : new Session(Boolean.TRUE.equals(row.getPrewarm()),
                row.getLivekitIdentity(), nullableMillis(row.getReadyAt()), row.getReadyPhase(),
                nullableMillis(row.getStartSignalAt()), row.getStartPhase(),
                nullableMillis(row.getCeremonyCompletedAt()));
    }

    private static Artifact artifact(RecordingArtifact row) {
        return row == null ? null : new Artifact(row.getObjectPath(), row.getStorageBucket(),
                row.getSizeBytes() == null ? 0L : row.getSizeBytes(),
                row.getDurationNs() == null ? 0L : row.getDurationNs(),
                nullableMillis(row.getDeletedAt()));
    }

    private static Retention retention(RecordingRetention row) {
        return row == null ? null : new Retention(nullableMillis(row.getExpiresAt()),
                row.getSaveCount() == null ? 0 : row.getSaveCount());
    }

    /**
     * Суррогат команды из её сегодняшнего строкового идентификатора.
     *
     * <p>Мост идентификаторов знает только игроков, записи и мемы, а команда
     * комнаты объявлена как uuid. Расчёт той же природы, что в мосте:
     * детерминированный и без обращения к базе.
     */
    private static UUID teamId(String roomId, String teamId) {
        return UUID.nameUUIDFromBytes(("room_team:" + roomId + ":" + teamId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static long millis(Instant value, long fallback) {
        return value == null ? fallback : value.toEpochMilli();
    }

    private static Long nullableMillis(Instant value) {
        return value == null ? null : value.toEpochMilli();
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String clean = value.trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
