package ru.hothat.recording.spi;

/**
 * Подписи, которыми удостоверяются машинные гости записи.
 *
 * <p>Порт для чужих областей: машинный фильтр обязан уметь проверить подпись
 * рекордера, но не обязан ничего знать про запись, LiveKit и S3.
 *
 * <p>Подписей две и они разные намеренно. Подпись просмотра открывает
 * страницу-рекордер; подпись вебхука открывает адрес, на который LiveKit
 * присылает уведомления. Одна на двоих означала бы, что владелец ссылки на
 * страницу умеет подделывать уведомления о выгрузке.
 */
public interface RecordingSignaturePort {

    /** Подпись, по которой headless-рекордер открывает страницу игры. */
    String viewSignature(String roomId, int gameNumber);

    boolean verifyViewSignature(String roomId, int gameNumber, String signature);

    String egressWebhookSignature(String roomId, int gameNumber);

    boolean verifyEgressWebhookSignature(String roomId, int gameNumber, String signature);
}
