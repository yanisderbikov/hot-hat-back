package ru.hothat.recording.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Готов ли рекордер снимать партию.
 *
 * <p>Заменяет {@code POST /api/recordings} с {@code action=ready_status}
 * ({@code app-core.js:12985}). Это самый горячий опрос игрового экрана: пока
 * рекордер не отозвался, партия не начинается вовсе — иначе первый ход не
 * попал бы в файл. Клиент бьёт сюда раз в 0,65–2,5 секунды до 75 секунд.
 *
 * <p>Вопрос задаёт игрок, отвечает — половина рекордера. Сигналы
 * {@code ready} и {@code started} шлёт сама машина в
 * {@code /api/v2/machine/recorder/**}; здесь их только показывают, ничего не
 * меняя. Из-за этого адрес и остаётся GET, хотя его старший брат в машинной
 * области — POST.
 *
 * <p>Числового кода Egress тут нет намеренно: игроку нечего делать с
 * внутренним кодом чужого сервиса, а всё, что из него следует, уже сведено
 * в {@code status} и {@code ready}.
 */
@Schema(description = "Готовность рекордера к съёмке партии")
public record RecorderReadinessResponseDTO(

        @Schema(description = "Запись этой партии заведена. false — старт ещё не звали",
                example = "true", type = "boolean")
        boolean exists,

        @Schema(description = "Рекордер подключился и не упал: партию можно начинать",
                example = "true", type = "boolean")
        boolean ready,

        @Schema(description = "Запись партии; null — записи нет", example = "hat-0f3a9c1d7b2e5480-3",
                nullable = true)
        String recordingId,

        @Schema(description = "Стадия записи; null — записи нет", nullable = true)
        RecordingStatus status,

        @Schema(description = "Личность рекордера в видеокомнате: по ней экран прячет его плитку "
                + "из сетки. null — рекордер ещё не представился",
                example = "hot-hat-recorder-3f1c9a2b", nullable = true)
        String recorderLivekitIdentity,

        @Schema(description = "Когда рекордер отчитался о готовности, мс эпохи; null — ещё не отчитался",
                example = "1757068800000", type = "integer", format = "int64", nullable = true)
        Long recorderReadyAtMs,

        @Schema(description = "Когда рекордер сообщил, что пошла запись, мс эпохи; null — ещё не сообщил",
                example = "1757068803000", type = "integer", format = "int64", nullable = true)
        Long recorderStartSignalAtMs,

        @Schema(description = "Когда LiveKit подтвердил активное задание Egress, мс эпохи; "
                + "null — не подтверждал", example = "1757068802000", type = "integer",
                format = "int64", nullable = true)
        Long egressActiveAtMs,

        @Schema(description = "Почему запись не получилась; null — жалоб нет",
                example = "egress start refused: no worker available", nullable = true)
        String error) {
}
