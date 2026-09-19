package ru.hothat.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Превращение гостя в полноценную учётную запись.
 *
 * <p><b>Поля {@code uid} здесь нет — и это главное отличие от старого
 * {@code POST /api/auth/link}.</b> Тот доказывал личность гостя одним
 * идентификатором из тела (аудит A4): {@code uid} добывался цепочкой
 * «увидел ник Guest###### → публичная проверка ника → заявка в друзья →
 * разрешение ника в uid», после чего чужую брошенную заглушку можно было
 * присвоить, задав ей свою почту и свой пароль. Опаснее того: гонка в окне
 * регистрации оставляла жертве живые токены, которые начинали разрешаться в
 * аккаунт с чужим паролем.
 *
 * <p>Здесь личность берётся из предъявленного Bearer-токена гостя, поэтому
 * присвоить чужую учётку нечем: у нападающего нет её токена, а тело запроса
 * на выбор учётки больше не влияет.
 *
 * <p>Ника в запросе тоже нет: гость уже носит выданный сервером
 * {@code Guest######}, и он остаётся занятым за тем же uid. Выбрать
 * человеческий ник можно потом, в области профиля, — там для этого свой
 * адрес и свой разбор занятости.
 */
@Schema(description = "Апгрейд гостевой учётной записи до полноценной")
public record UpgradeGuestAccountRequestDTO(

        @Schema(description = "Почта будущего аккаунта; должна быть свободна",
                example = "player@example.com", maxLength = 320)
        @NotBlank(message = "Не указан e-mail.")
        @Email(message = "Некорректный e-mail.")
        @Size(max = 320, message = "Слишком длинный e-mail.")
        String email,

        @Schema(description = "Пароль, не короче 6 знаков", example = "s3cret-pass",
                minLength = 6, maxLength = 128, format = "password")
        @NotBlank(message = "Не указан пароль.")
        @Size(min = 6, max = 128, message = "Пароль: от 6 до 128 знаков.")
        String password) {
}
