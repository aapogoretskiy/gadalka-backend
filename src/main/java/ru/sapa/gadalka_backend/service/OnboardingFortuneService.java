package ru.sapa.gadalka_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.sapa.gadalka_backend.api.dto.card.CardDto;
import ru.sapa.gadalka_backend.api.dto.fortune.FortuneResponse;
import ru.sapa.gadalka_backend.domain.Card;
import ru.sapa.gadalka_backend.domain.CardDeckTheme;
import ru.sapa.gadalka_backend.domain.Fortune;
import ru.sapa.gadalka_backend.domain.OnboardingSpread;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.domain.type.SpreadType;
import ru.sapa.gadalka_backend.repository.CardRepository;
import ru.sapa.gadalka_backend.repository.FortuneRepository;
import ru.sapa.gadalka_backend.repository.OnboardingSpreadRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Первый расклад «в подарок» в онбординге.
 *
 * <p>Отличия от обычного гадания ({@link FortuneService}):
 * <ul>
 *   <li>знаки НЕ списываются — это подарок за знакомство с приложением;</li>
 *   <li>AI НЕ вызывается — интерпретация берётся из пула предгенерированных
 *       вариантов ({@code onboarding_spreads}), не тратим токены на аудиторию,
 *       которая ещё не совершила ни одного действия;</li>
 *   <li>вопрос — только из фиксированного списка (кнопки в онбординге),
 *       свободный текст недоступен.</li>
 * </ul>
 *
 * <p>При этом результат для пользователя неотличим от обычного гадания:
 * настоящие карты с позициями и картинками активной темы, расклад сохраняется
 * в историю и дневник, счётчик действий растёт.
 *
 * <p>Доступен только пользователям без единого расклада — иначе 409.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingFortuneService {

    private final OnboardingSpreadRepository onboardingSpreadRepository;
    private final FortuneRepository fortuneRepository;
    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final SpreadService spreadService;
    private final ThemeService themeService;
    private final DiaryService diaryService;
    private final ObjectMapper objectMapper;

    /** Вопросы для кнопок онбординга */
    @Transactional(readOnly = true)
    public List<String> getQuestions() {
        return onboardingSpreadRepository.findActiveQuestions();
    }

    @Transactional
    public FortuneResponse createOnboardingFortune(User user, String question) {
        // Подарочный расклад — только первый. Если расклады уже были, онбординг
        // пройден (или пользователь пытается вызвать endpoint повторно) — 409.
        if (fortuneRepository.existsByUserId(user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Подарочный расклад уже использован");
        }

        OnboardingSpread spread = onboardingSpreadRepository.findRandomByQuestion(question)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестный вопрос онбординга"));

        // Повторный тап по той же кнопке вернёт кэшированный расклад (та же механика,
        // что и в FortuneService) — уникальный индекс (user_id, question_hash) не даст дубля
        String questionHash = hashQuestion(user.getId(), question);
        CardDeckTheme activeTheme = themeService.resolveActiveTheme(user.getId());

        // Собираем карты по слагам, сохраняя порядок из пула (порядок = позиции расклада)
        List<String> slugs = Arrays.asList(spread.getCardSlugs().split(","));
        Map<String, Card> bySlug = cardRepository.findBySlugIn(slugs).stream()
                .collect(Collectors.toMap(Card::getSlug, Function.identity()));
        List<Card> cards = slugs.stream().map(bySlug::get).toList();
        if (cards.contains(null)) {
            log.error("Онбординг-расклад id={}: не найдены карты по слагам {}", spread.getId(), spread.getCardSlugs());
            throw new IllegalStateException("Ошибка конфигурации онбординг-расклада");
        }

        List<CardDto> cardDtoList = spreadService.assignCardPosition(cards, SpreadType.THREE_CARD, activeTheme);

        // Накладываем предгенерированные интерпретации по картам
        List<String> perCard = parsePerCardInterpretations(spread);
        for (int i = 0; i < cardDtoList.size() && i < perCard.size(); i++) {
            cardDtoList.get(i).setInterpretation(perCard.get(i));
        }

        Fortune saved = saveFortune(user.getId(), questionHash, question, cardDtoList, spread.getGeneralInterpretation());
        log.info("Онбординг-расклад выдан: userId={}, variantId={}, fortuneId={}", user.getId(), spread.getId(), saved.getId());

        FortuneResponse response = new FortuneResponse(saved.getId(), user.getUsername(), question,
                cardDtoList, spread.getGeneralInterpretation(), SpreadType.THREE_CARD);
        diaryService.save(user.getId(), DiaryFeatureType.THREE_CARD, saved.getId(), response);
        return response;
    }

    private Fortune saveFortune(Long userId, String questionHash, String question,
                                List<CardDto> cardDtoList, String interpretation) {
        try {
            Fortune fortune = Fortune.builder()
                    .userId(userId)
                    .questionHash(questionHash)
                    .question(question)
                    .spreadType(SpreadType.THREE_CARD)
                    .cards(objectMapper.writeValueAsString(cardDtoList))
                    .interpretation(interpretation)
                    .build();
            Fortune saved = fortuneRepository.save(fortune);
            userRepository.incrementActionsCount(userId);
            return saved;
        } catch (Exception e) {
            log.error("Ошибка сохранения онбординг-расклада, userId={}: {}", userId, e.getMessage(), e);
            throw new IllegalStateException("Ошибка сохранения гадания", e);
        }
    }

    private List<String> parsePerCardInterpretations(OnboardingSpread spread) {
        try {
            return objectMapper.readValue(spread.getPerCardInterpretations(), new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Битый JSON per_card_interpretations в onboarding_spreads id={}", spread.getId(), e);
            return List.of();
        }
    }

    /** Тот же стиль хэширования, что в FortuneService — для совместимости с уникальным индексом */
    private String hashQuestion(Long userId, String question) {
        try {
            String input = userId + ":" + question.trim().toLowerCase() + "::" + SpreadType.THREE_CARD.name().toLowerCase();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
