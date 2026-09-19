package ru.hothat.admin.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import ru.hothat.admin.api.dto.ConfigurationKeyView;
import ru.hothat.admin.api.dto.ConfigurationReadinessResponseDTO;
import ru.hothat.config.HotHatUser;
import ru.hothat.admin.port.ObjectStoragePort;

import java.util.List;

/**
 * Показать администратору, что настроено в окружении.
 *
 * <p>Заменяет блок {@code env} из открытого {@code GET /api/health} (A13).
 * Значений здесь нет и не будет — только признак «задано»: смысл вопроса в
 * том, не забыли ли выставить переменную, а не в том, что в ней лежит.
 *
 * <p>Проба живости остаётся открытой и продолжает отвечать «жив»: карта
 * интеграций сервиса анонимному читателю ни к чему.
 */
@Service
@RequiredArgsConstructor
public class CheckConfigurationReadinessUseCase {

    private final ObjectStoragePort storage;

    @Value("${livekit.url:}")
    private String livekitUrl;

    @Value("${livekit.api-key:}")
    private String livekitApiKey;

    @Value("${livekit.api-secret:}")
    private String livekitApiSecret;

    @Value("${turn.urls:}")
    private String turnUrls;

    @Value("${turn.secret:}")
    private String turnSecret;

    @Value("${hot-hat.admin-emails:}")
    private String adminEmails;

    @Value("${hot-hat.admin-uids:}")
    private String adminUids;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${allowed.origins:}")
    private String allowedOrigins;

    @Value("${hot-hat.cron-secret:}")
    private String cronSecret;

    @PreAuthorize("hasRole('ADMIN')")
    public ConfigurationReadinessResponseDTO run(HotHatUser admin) {
        List<ConfigurationKeyView> keys = List.of(
                key("LIVEKIT_URL", set(livekitUrl), "Подключение к серверу видеосвязи"),
                key("LIVEKIT_API_KEY", set(livekitApiKey), "Выдача токенов видеосвязи"),
                key("LIVEKIT_API_SECRET", set(livekitApiSecret), "Подпись токенов видеосвязи и записей"),
                key("JWT_SECRET", set(jwtSecret), "Подпись своих токенов доступа"),
                key("ALLOWED_ORIGINS", set(allowedOrigins), "Список сайтов, которым разрешён запрос"),
                key("TURN_URL", set(turnUrls), "Обход NAT: без него не соединятся часть игроков"),
                key("TURN_SECRET", set(turnSecret), "Временные учётные данные TURN"),
                key("ADMIN_ACCESS", set(adminEmails) || set(adminUids), "Кто входит в админку"),
                key("S3_STORAGE", storage.configured(), "Мем-видео и записи партий"),
                key("CRON_SECRET", set(cronSecret), "Плановая уборка комнат и записей"));
        boolean ready = keys.stream().allMatch(ConfigurationKeyView::configured);
        return new ConfigurationReadinessResponseDTO(
                "hot-hat-back", System.getProperty("java.version"), ready, keys);
    }

    private static ConfigurationKeyView key(String name, boolean configured, String purpose) {
        return new ConfigurationKeyView(name, configured, purpose);
    }

    private static boolean set(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
