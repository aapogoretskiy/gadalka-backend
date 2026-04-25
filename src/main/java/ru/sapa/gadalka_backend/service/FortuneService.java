package ru.sapa.gadalka_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.api.dto.card.CardDto;
import ru.sapa.gadalka_backend.api.dto.fortune.FortuneResponse;
import ru.sapa.gadalka_backend.domain.Card;
import ru.sapa.gadalka_backend.domain.Fortune;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.exception.FreeFortuneAlreadyUsedException;
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

    private static final int STD_FORTUNE_CARD_COUNT = 3;

    @Value("${telegram.auth.enable}")
    private boolean authEnabled;

    private final SpreadService spreadService;
    private final CardRepository cardRepository;
    private final FortuneRepository fortuneRepository;
    private final UserRepository userRepository;
    private final SystemConfigService systemConfigService;
    private final AiInterpretationManager interpretationManager;
    private final DiaryService diaryService;
    private final ObjectMapper objectMapper;

    @Transactional
    public FortuneResponse getFortune(User user, String question, String category) {
        if (authEnabled && user.isFortuneUsed()) {
            log.warn("Попытка повторного бесплатного гадания: userId={}", user.getId());
            throw new FreeFortuneAlreadyUsedException();
        }

        String questionHash = hashQuestion(user.getId(), question, category);

        Optional<Fortune> cached = fortuneRepository.findByUserIdAndQuestionHash(user.getId(), questionHash);
        if (cached.isPresent()) {
            log.info("Возвращаем кэшированное гадание: userId={}, questionHash={}", user.getId(), questionHash);
            return buildResponseFromCached(user.getUsername(), cached.get());
        }

        log.info("Новое гадание для userId={}, категория='{}', выбираем {} карт", user.getId(), category, STD_FORTUNE_CARD_COUNT);
        List<Card> cards = cardRepository.findRandomCards(STD_FORTUNE_CARD_COUNT);
        List<CardDto> cardDtoList = spreadService.assignCardPosition(cards);
        String currentAiProvider = systemConfigService.getValue(AI_PROVIDER);
        log.debug("Запрашиваем интерпретацию у AI-провайдера '{}' для userId={}", currentAiProvider, user.getId());
        InterpretationResult result = interpretationManager.interpret(currentAiProvider, cardDtoList, question, category);

        Fortune saved = saveFortune(user.getId(), questionHash, question, result.getCards(), result.getGeneralInterpretation());
        log.info("Гадание сохранено: fortuneId={}, userId={}", saved.getId(), user.getId());

        user.setFortuneUsed(true);
        userRepository.save(user);
        log.info("Бесплатное гадание использовано: userId={}", user.getId());

        FortuneResponse response = new FortuneResponse(user.getUsername(), result.getCards(), result.getGeneralInterpretation());
        diaryService.save(user.getId(), DiaryFeatureType.THREE_CARD, saved.getId(), response);
        return response;
    }

    private Fortune saveFortune(Long userId, String questionHash, String question,
                                List<CardDto> cardDtoList, String interpretation) {
        try {
            String cardsJson = objectMapper.writeValueAsString(cardDtoList);
            Fortune fortune = Fortune.builder()
                    .userId(userId)
                    .questionHash(questionHash)
                    .question(question)
                    .cards(cardsJson)
                    .interpretation(interpretation)
                    .build();
            return fortuneRepository.save(fortune);
        } catch (JsonProcessingException e) {
            log.error("Ошибка сериализации карт при сохранении гадания, userId={}: {}", userId, e.getMessage(), e);
            throw new IllegalStateException("Ошибка сохранения гадания", e);
        }
    }

    private FortuneResponse buildResponseFromCached(String username, Fortune fortune) {
        try {
            List<CardDto> cards = objectMapper.readValue(fortune.getCards(), new TypeReference<>() {});
            return new FortuneResponse(username, cards, fortune.getInterpretation());
        } catch (JsonProcessingException e) {
            log.error("Ошибка десериализации карт из кэша гадания, fortuneId={}: {}", fortune.getId(), e.getMessage(), e);
            throw new IllegalStateException("Ошибка чтения сохранённого гадания", e);
        }
    }

    private String hashQuestion(Long userId, String question, String category) {
        try {
            String normalizedQuestion = question.trim().toLowerCase();
            String normalizedCategory = category != null ? category.trim().toLowerCase() : "";
            String input = userId + ":" + normalizedQuestion + ":" + normalizedCategory;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }
}
