package ru.hothat.realtime.api;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.hothat.realtime.api.dto.ChannelErrorFrameDTO;
import ru.hothat.realtime.api.dto.ChannelPingFrameDTO;
import ru.hothat.realtime.api.dto.ChannelPongFrameDTO;
import ru.hothat.realtime.api.dto.ChannelUnsubscribeFrameDTO;
import ru.hothat.realtime.api.dto.DirectChatEventDTO;
import ru.hothat.realtime.api.dto.DirectChatHelloDTO;
import ru.hothat.realtime.api.dto.LobbyChannelEventDTO;
import ru.hothat.realtime.api.dto.LobbyChannelHelloDTO;
import ru.hothat.realtime.api.dto.MemeLibraryEventDTO;
import ru.hothat.realtime.api.dto.MemeLibraryHelloDTO;
import ru.hothat.realtime.api.dto.PreflightEventDTO;
import ru.hothat.realtime.api.dto.PreflightHelloDTO;
import ru.hothat.realtime.api.dto.RecorderChannelEventDTO;
import ru.hothat.realtime.api.dto.RecorderChannelHelloDTO;
import ru.hothat.realtime.api.dto.RoomChannelEventDTO;
import ru.hothat.realtime.api.dto.RoomChannelHelloDTO;
import ru.hothat.realtime.api.dto.RoomPreviewEventDTO;
import ru.hothat.realtime.api.dto.SocialChannelEventDTO;
import ru.hothat.realtime.api.dto.SocialChannelHelloDTO;
import ru.hothat.realtime.api.dto.SpotlightFrameDTO;

import java.util.List;

/**
 * Кадры именованных каналов в спецификации.
 *
 * <p>Нужен потому, что springdoc ходит по контроллерам, а у каналов
 * контроллеров нет и заводить их нельзя: пустой класс «ради документации»
 * нарушил бы правило «контроллер — это DTO, один сценарий и ответ». Без этого
 * бина двадцать записей-кадров описаны только в исходниках, и главное
 * обещание каналов — «кадр несёт ту же форму, что REST-ответ» — проверить по
 * спецификации нечем. Ровно на этом обещании и споткнулся перевод переписки:
 * два источника одного списка разошлись формой, а увидеть это было негде.
 *
 * <p>Путей каналы здесь не получают, и это не упущение: в OpenAPI 3.0
 * {@code paths} — это HTTP-операции с методом и статусом, и объявить там
 * {@code CONNECT /ws/v2/lobby} значило бы соврать генератору клиентов, который
 * выпустил бы по такому пути обычный вызов. Каналы названы в описаниях самих
 * кадров, а их адреса — в §9 плана.
 *
 * <p>Живёт в области {@code realtime}, а не в общей настройке Swagger:
 * список кадров меняется вместе с каналами и должен лежать рядом с ними.
 */
@Configuration
public class RealtimeChannelDocs {

    /**
     * Кадры всех семи каналов и трёх служебных.
     *
     * <p>Порядок — тот же, что в §5.15 плана: лобби, комната, социальное,
     * переписка, префлайт, мемы, рекордер, затем служебные. Читающий
     * спецификацию видит их в том порядке, в каком они описаны в плане.
     */
    private static final List<Class<?>> FRAMES = List.of(
            LobbyChannelHelloDTO.class, LobbyChannelEventDTO.class,
            RoomChannelHelloDTO.class, RoomChannelEventDTO.class,
            SocialChannelHelloDTO.class, SocialChannelEventDTO.class,
            DirectChatHelloDTO.class, DirectChatEventDTO.class,
            PreflightHelloDTO.class, PreflightEventDTO.class,
            MemeLibraryHelloDTO.class, MemeLibraryEventDTO.class,
            RecorderChannelHelloDTO.class, RecorderChannelEventDTO.class,
            ChannelPingFrameDTO.class, ChannelPongFrameDTO.class,
            SpotlightFrameDTO.class, RoomPreviewEventDTO.class,
            ChannelUnsubscribeFrameDTO.class, ChannelErrorFrameDTO.class);

    /**
     * Разбирать кадры надо тем же преобразователем, каким собрана вся
     * спецификация, — иначе версия у кадров своя. Безымянный
     * {@code getInstance()} — это преобразователь 3.0, а в нём
     * {@code @Schema(description = …)} рядом с {@code $ref} запрещён и молча
     * пропадает: двадцать девять полей кадров — {@code RoomChannelView.snapshot},
     * {@code SocialChannelView.inbox} и прочие вложенные проекции — оставались
     * голой ссылкой без подписи, хотя в исходнике подпись есть. Версию берём
     * не константой, а у самого springdoc: разъехаться с
     * {@code springdoc.api-docs.version} она тогда не может.
     */
    @Bean
    public OpenApiCustomizer realtimeChannelFrames(SpringDocConfigProperties springDoc) {
        ModelConverters converters = ModelConverters.getInstance(springDoc.isOpenapi31());
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            for (Class<?> frame : FRAMES) {
                // readAll, а не read: кадр несёт вложенные проекции, и без них
                // ссылка $ref в спецификации указывала бы в пустоту.
                for (var entry : converters.readAll(frame).entrySet()) {
                    Schema<?> existing = openApi.getComponents().getSchemas() == null
                            ? null : openApi.getComponents().getSchemas().get(entry.getKey());
                    // Проекции у канала и у адреса HTTP общие, и springdoc уже
                    // положил их сюда сам. Перезаписать значило бы затереть
                    // разобранное им описание своим — оно то же самое, но
                    // собрано другим путём, и молчаливая замена однажды
                    // разошлась бы с ответом контроллера.
                    if (existing == null) {
                        openApi.getComponents().addSchemas(entry.getKey(), entry.getValue());
                    }
                }
            }
        };
    }
}
