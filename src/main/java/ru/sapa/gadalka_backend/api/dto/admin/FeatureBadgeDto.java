package ru.sapa.gadalka_backend.api.dto.admin;

import java.time.LocalDateTime;

/**
 * Отметки «Новинка» / «Хит» для одной платной функции.
 *
 * <p>{@code newSince} — момент, когда флаг «Новинка» был выставлен последний раз
 * (это {@code updated_at} соответствующей записи в system_config). Поле только
 * для чтения: фронтенд использует его, чтобы понять, показывать ли пользователю
 * жёлтую точку в навигации заново, даже если раньше он уже «подтвердил» другую
 * фичу на этой же вкладке. При записи (PUT) значение поля игнорируется — оно
 * пересчитывается сервером из system_config.
 */
public record FeatureBadgeDto(
        boolean isNew,
        boolean isHot,
        LocalDateTime newSince
) {}
