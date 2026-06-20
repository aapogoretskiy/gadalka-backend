package ru.sapa.gadalka_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.api.dto.card.CardDto;
import ru.sapa.gadalka_backend.api.dto.fortune.FortuneResponse;
import ru.sapa.gadalka_backend.domain.Card;
import ru.sapa.gadalka_backend.domain.CardDeckTheme;
import ru.sapa.gadalka_backend.domain.Fortune;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.domain.type.SpreadType;
import ru.sapa.gadalka_backend.mapper.CardMapper;
import ru.sapa.gadalka_backend.repository.CardRepository;
import ru.sapa.gadalka_backend.repository.FortuneRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;
import ru.sapa.gadalka_backend.service.interpretation.AiInterpretationManager;
import ru.sapa.gadalka_backend.service.interpretation.InterpretationResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.AI_PROVIDER;

@Slf4j
@Service
@RequiredArgsConstructor
public class FortuneService {

    private final SpreadService spreadService;
    private final CardRepository cardRepository;
    private final FortuneRepository fortuneRepository;
    private final UserRepository userRepository;
    private final SystemConfigService systemConfigService;
    private final AiInterpretationManager interpretationManager;
    private final DiaryService diaryService;
    private final FortuneCreditService fortuneCreditService;
    private final FeatureCostService featureCostService;
    private final ObjectMapper objectMapper;
    private final ThemeService themeService;
    private final CardMapper cardMapper;

    @Transactional
    public FortuneResponse getFortune(User user, String question, String category, SpreadType spreadType) {
        String questionHash = hashQuestion(user.getId(), question, category, spreadType);

        // Резолвим активную тему один раз — используется и для нового гадания, и для кэша
        CardDeckTheme activeTheme = themeService.resolveActiveTheme(user.getId());

        // Сначала проверяем кэш — повторный запрос того же гадания бесплатен,
        // пользователь уже заплатил за него ранее.
        Optional<Fortune> cached = fortuneRepository.findByUserIdAndQuestionHash(user.getId(), questionHash);
        if (cached.isPresent()) {
            log.info("Возвращаем кэшированное гадание: userId={}, spreadType={}, questionHash={}",
                    user.getId(), spreadType, questionHash);
            return buildResponseFromCached(user.getUsername(), cached.get(), activeTheme);
        }

        // Новое гадание — списываем кредиты ДО вызова AI.
        // Если кредитов нет → InsufficientCreditsException → AI не вызывается.
        DiaryFeatureType featureType = toFeatureType(spreadType);
        fortuneCreditService.spendCredits(user.getId(), featureType, featureCostService.getCost(spreadType));

        int cardCount = spreadService.getCardCount(spreadType);
        log.info("Новое гадание: userId={}, spreadType={}, категория='{}', выбираем {} карт", user.getId(), spreadType, category, cardCount);

        List<Card> cards = cardRepository.findRandomCards(cardCount);
        List<CardDto> cardDtoList = spreadService.assignCardPosition(cards, spreadType, activeTheme);

        String currentAiProvider = systemConfigService.getValue(AI_PROVIDER);
        log.debug("Запрашиваем интерпретацию у AI-провайдера '{}' для userId={}", currentAiProvider, user.getId());
        InterpretationResult result = interpretationManager.interpret(currentAiProvider, cardDtoList, question, category);

        Fortune saved = saveFortune(user.getId(), questionHash, question, spreadType, result.getCards(), result.getGeneralInterpretation());
        log.info("Гадание сохранено: fortuneId={}, userId={}, spreadType={}", saved.getId(), user.getId(), spreadType);

        FortuneResponse response = new FortuneResponse(saved.getId(), user.getUsername(), question, result.getCards(), result.getGeneralInterpretation(), spreadType);
        diaryService.save(user.getId(), featureType, saved.getId(), response);
        return response;
    }

    private Fortune saveFortune(Long userId, String questionHash, String question, SpreadType spreadType,
                                List<CardDto> cardDtoList, String interpretation) {
        try {
            String cardsJson = objectMapper.writeValueAsString(cardDtoList);
            Fortune fortune = Fortune.builder()
                    .userId(userId)
                    .questionHash(questionHash)
                    .question(question)
                    .spreadType(spreadType)
                    .cards(cardsJson)
                    .interpretation(interpretation)
                    .build();
            Fortune saved = fortuneRepository.save(fortune);
            userRepository.incrementActionsCount(userId);
            return saved;
        } catch (JsonProcessingException e) {
            log.error("Ошибка сериализации карт при сохранении гадания, userId={}: {}", userId, e.getMessage(), e);
            throw new IllegalStateException("Ошибка сохранения гадания", e);
        }
    }

    /**
     * Строит ответ из кэшированного гадания.
     * imageUrl не хранится в JSON — пересчитываем по текущей активной теме пользователя.
     * Это значит что при смене темы старые гадания покажут картинки из новой темы — ожидаемое поведение.
     */
    private FortuneResponse buildResponseFromCached(String username, Fortune fortune, CardDeckTheme activeTheme) {
        try {
            List<CardDto> cards = objectMapper.readValue(fortune.getCards(), new TypeReference<>() {});
            SpreadType spreadType = fortune.getSpreadType() != null ? fortune.getSpreadType() : SpreadType.THREE_CARD;

            // Пересчитываем imageUrl: загружаем Card-сущности по id и резолвим через CardMapper
            List<Long> cardIds = cards.stream().map(CardDto::getId).toList();
            java.util.Map<Long, Card> cardById = cardRepository.findAllById(cardIds)
                    .stream()
                    .collect(java.util.stream.Collectors.toMap(Card::getId, c -> c));

            cards.forEach(dto -> {
                Card card = cardById.get(dto.getId());
                if (card != null) {
                    dto.setImageUrl(cardMapper.resolveImageUrl(card, activeTheme));
                }
            });

            return new FortuneResponse(fortune.getId(), username, fortune.getQuestion(), cards,
                    fortune.getInterpretation(), spreadType);
        } catch (JsonProcessingException e) {
            log.error("Ошибка десериализации карт из кэша гадания, fortuneId={}: {}", fortune.getId(), e.getMessage(), e);
            throw new IllegalStateException("Ошибка чтения сохранённого гадания", e);
        }
    }

    /**
     * Включает тип расклада в хэш — чтобы один и тот же вопрос с разными раскладами
     * давал независимые гадания (разные карты и интерпретации).
     */
    private String hashQuestion(Long userId, String question, String category, SpreadType spreadType) {
        try {
            String normalizedQuestion = question.trim().toLowerCase();
            String normalizedCategory = category != null ? category.trim().toLowerCase() : "";
            String normalizedSpread = spreadType.name().toLowerCase();
            String input = userId + ":" + normalizedQuestion + ":" + normalizedCategory + ":" + normalizedSpread;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }

    private DiaryFeatureType toFeatureType(SpreadType spreadType) {
        return switch (spreadType) {
            case THREE_CARD   -> DiaryFeatureType.THREE_CARD;
            case HORSESHOE    -> DiaryFeatureType.HORSESHOE;
            case CELTIC_CROSS -> DiaryFeatureType.CELTIC_CROSS;
        };
    }
}
