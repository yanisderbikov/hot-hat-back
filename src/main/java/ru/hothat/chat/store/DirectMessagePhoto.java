package ru.hothat.chat.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Картинка сообщения: отдельная строка, а не поле сообщения.
 *
 * <p>data-URL до 120 000 символов в строке сообщения означал бы, что любое
 * чтение истории поднимает из TOAST все картинки страницы — даже когда
 * рисуется список превью, где картинок не видно. Разделение оставляет строку
 * сообщения маленькой, а картинку берут отдельным чтением и только тогда,
 * когда показывают.
 *
 * <p>Границы размеров те же, что зажимает сегодняшний движок: до 3000 по
 * каждой стороне.
 */
@Entity
@Table(name = "direct_chat_photo", schema = "v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class DirectMessagePhoto {

    @Id
    @Column(name = "message_id", nullable = false, updatable = false)
    private Long messageId;

    @Column(name = "data_url", nullable = false, length = 120000)
    private String dataUrl;

    @Builder.Default
    @Column(nullable = false)
    private Integer width = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer height = 0;

    /** Имя исходного файла; показывать не обязательно. */
    @Column(name = "file_name", length = 80)
    private String fileName;
}
