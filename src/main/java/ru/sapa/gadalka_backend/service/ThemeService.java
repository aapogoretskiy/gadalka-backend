package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.api.dto.theme.ThemeDto;
import ru.sapa.gadalka_backend.domain.CardDeckTheme;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.UserTheme;
import ru.sapa.gadalka_backend.domain.UserThemeId;
import ru.sapa.gadalka_backend.exception.ThemeAlreadyOwnedException;
import ru.sapa.gadalka_backend.exception.ThemeNotFoundException;
import ru.sapa.gadalka_backend.repository.CardDeckThemeRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;
import ru.sapa.gadalka_backend.repository.UserThemeRepository;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThemeService {

    private final CardDeckThemeRepository themeRepository;
    private final UserThemeRepository userThemeRepository;
    private final UserRepository userRepository;
    private final FortuneCreditService fortuneCreditService;

    /**
     * Возвращает список всех тем для отображения в магазине.
     * Для каждой темы проставляем флаги owned и active конкретного пользователя.
     * owned = true если:
     *   - тема is_free (классика) — она принадлежит всем
     *   - ИЛИ у пользователя есть запись в user_themes
     * active = true если:
     *   - user.activeThemeId совпадает с id темы
     *   - ИЛИ user.activeThemeId == null И тема is_free (классика по умолчанию)
     */
    @Transactional(readOnly = true)
    public List<ThemeDto> getThemes(Long userId) {
        List<CardDeckTheme> allThemes = themeRepository.findAllByOrderBySortOrderAsc();
        Set<Long> ownedThemeIds = userThemeRepository.findThemeIdsByUserId(userId);

        Long activeThemeId = userRepository.findById(userId)
                .map(User::getActiveThemeId)
                .orElse(null);

        return allThemes.stream()
                .map(theme -> {
                    boolean owned = theme.getIsFree() || ownedThemeIds.contains(theme.getId());
                    boolean active = isActiveTheme(theme, activeThemeId);
                    return ThemeDto.from(theme, owned, active);
                })
                .toList();
    }

    /**
     * Активирует тему для пользователя — сохраняет active_theme_id на пользователе.
     * Нельзя активировать тему, которой пользователь не владеет.
     */
    @Transactional
    public void activateTheme(Long userId, Long themeId) {
        CardDeckTheme theme = themeRepository.findById(themeId)
                .orElseThrow(() -> new ThemeNotFoundException(themeId));
        if (!isOwned(userId, theme)) {
            throw new ThemeNotFoundException(themeId);
        }
        userRepository.findById(userId).ifPresent(user -> {
            user.setActiveThemeId(themeId);
            userRepository.save(user);
        });
        log.info("Пользователь {} активировал тему: themeId={}, slug={}", userId, themeId, theme.getSlug());
    }

    /**
     * Покупает тему за кредиты.
     * Порядок операций:
     * 1. Проверяем что тема существует и не является бесплатной
     * 2. Проверяем что пользователь ещё не владеет темой
     * 3. Списываем кредиты (если кредитов не хватает — InsufficientCreditsException, до записи в user_themes)
     * 4. Сохраняем запись о покупке в user_themes
     */
    @Transactional
    public void purchaseTheme(Long userId, Long themeId) {
        CardDeckTheme theme = themeRepository.findById(themeId)
                .orElseThrow(() -> new ThemeNotFoundException(themeId));

        if (theme.getIsFree()) {
            throw new ThemeAlreadyOwnedException();
        }

        if (userThemeRepository.existsById(new UserThemeId(userId, themeId))) {
            throw new ThemeAlreadyOwnedException();
        }

        // Списываем кредиты ПЕРЕД сохранением покупки.
        fortuneCreditService.spendCreditsForTheme(userId, theme.getPrice());

        userThemeRepository.save(UserTheme.builder()
                .id(new UserThemeId(userId, themeId))
                .build());

        log.info("Пользователь {} купил тему: themeId={}, slug={}, цена={} кредитов", userId, themeId, theme.getSlug(), theme.getPrice());
    }

    /**
     * Загружает активную тему пользователя для подстановки в карты.
     * Используется в DailyCardService и FortuneService.
     * Возвращает классическую тему если active_theme_id не задан.
     */
    @Transactional(readOnly = true)
    public CardDeckTheme resolveActiveTheme(Long userId) {
        Long activeThemeId = userRepository.findById(userId)
                .map(User::getActiveThemeId)
                .orElse(null);

        if (activeThemeId == null) {
            return themeRepository.findBySlug("classic").orElse(null);
        }
        return themeRepository.findById(activeThemeId).orElse(null);
    }

    private boolean isOwned(Long userId, CardDeckTheme theme) {
        if (theme.getIsFree()) return true;
        return userThemeRepository.existsById(new UserThemeId(userId, theme.getId()));
    }

    private boolean isActiveTheme(CardDeckTheme theme, Long activeThemeId) {
        if (activeThemeId == null) {
            return theme.getIsFree();
        }
        return theme.getId().equals(activeThemeId);
    }
}
