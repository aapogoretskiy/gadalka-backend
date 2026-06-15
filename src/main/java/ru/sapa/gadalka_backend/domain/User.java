package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false, unique = true)
    private Long telegramId;

    @Column(name = "username")
    private String username;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "fortune_used", nullable = false)
    private boolean fortuneUsed;

    /**
     * Реферальный источник первой регистрации пользователя.
     * Проставляется один раз при создании аккаунта, если пользователь пришёл по реферальной ссылке.
     * Например: "telegram_channel1", "tiktok_video1".
     */
    @Column(name = "referral_source")
    private String referralSource;

    @Column(name = "active_theme_id")
    private Long activeThemeId;

    /**
     * Серверный timestamp момента принятия пользовательского соглашения и политики конфиденциальности.
     * Проставляется при завершении онбординга (создании профиля).
     * Для существующих пользователей — null.
     */
    @Column(name = "terms_accepted_at")
    private OffsetDateTime termsAcceptedAt;

    /**
     * Версия принятых юридических документов (формат YYYY-MM-DD, например "2025-04-28").
     * Позволяет отслеживать, по какой версии документов получено согласие.
     */
    @Column(name = "terms_version", length = 50)
    private String termsVersion;

    /**
     * Флаг бана пользователя.
     * При true — JwtAuthFilter возвращает 403 на все защищённые запросы.
     * Управляется исключительно через AdminController.
     */
    @Column(name = "is_banned", nullable = false)
    private boolean banned;

    /**
     * Время последней активности пользователя в приложении.
     * Обновляется JwtAuthFilter не чаще раза в 5 минут при успешной JWT-аутентификации.
     * Используется в админ-панели для мониторинга активности.
     */
    @Column(name = "last_active_at")
    private OffsetDateTime lastActiveAt;

    /**
     * Признак наличия подписки Telegram Premium у пользователя.
     * Передаётся Telegram в поле initData.user.is_premium при открытии Mini App.
     * Обновляется при каждой аутентификации — отражает актуальное состояние подписки.
     */
    @Column(name = "is_premium", nullable = false)
    private boolean premium;

    /**
     * Количество посещений (сеансов) пользователя в приложении.
     * Инкрементируется синхронно с обновлением lastActiveAt (не чаще раза в 5 минут).
     * Каждый "сеанс" также записывается в таблицу user_visits для аналитики по периодам.
     */
    @Column(name = "visit_count", nullable = false)
    private int visitCount;

    @PrePersist
    void prePersist() {
        if (Objects.isNull(this.createdAt)) {
            this.createdAt = OffsetDateTime.now();
        }
    }
}
