package ru.sapa.gadalka_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.card.CardDto;
import ru.sapa.gadalka_backend.api.dto.fortune.FortuneResponse;
import ru.sapa.gadalka_backend.domain.Card;
import ru.sapa.gadalka_backend.domain.Fortune;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.repository.CardRepository;
import ru.sapa.gadalka_backend.repository.FortuneRepository;
import ru.sapa.gadalka_backend.service.interpretation.AiInterpretationManager;

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

    private final SpreadService spreadService;
    private final CardRepository cardRepository;
    private final FortuneRepository fortuneRepository;
    private final SystemConfigService systemConfigService;
    private final AiInterpretationManager interpretationManager;
    private final ObjectMapper objectMapper;

    public FortuneResponse getFortune(User user, String question) {
        String questionHash = hashQuestion(user.getId(), question);

        Optional<Fortune> cached = fortuneRepository.findByUserIdAndQuestionHash(user.getId(), questionHash);
        if (cached.isPresent()) {
            log.info("Returning cached fortune for userId={} questionHash={}", user.getId(), questionHash);
            return buildResponseFromCached(user.getUsername(), cached.get());
        }

        List<Card> cards = cardRepository.findRandomCards(STD_FORTUNE_CARD_COUNT);
        List<CardDto> cardDtoList = spreadService.assignCardPosition(cards);
        String currentAiProvider = systemConfigService.getValue(AI_PROVIDER);
        String interpretation = interpretationManager.interpret(currentAiProvider, cardDtoList, question);

        saveFortune(user.getId(), questionHash, question, cardDtoList, interpretation);

        return new FortuneResponse(user.getUsername(), cardDtoList, interpretation);
    }

    private void saveFortune(Long userId, String questionHash, String question,
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
            fortuneRepository.save(fortune);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize cards for fortune persistence, userId={}", userId, e);
        }
    }

    private FortuneResponse buildResponseFromCached(String username, Fortune fortune) {
        try {
            List<CardDto> cards = objectMapper.readValue(fortune.getCards(), new TypeReference<>() {});
            return new FortuneResponse(username, cards, fortune.getInterpretation());
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cached fortune cards, fortuneId={}", fortune.getId(), e);
            throw new IllegalStateException("Ошибка чтения сохранённого гадания", e);
        }
    }

    private String hashQuestion(Long userId, String question) {
        try {
            String normalized = question.trim().toLowerCase();
            String input = userId + ":" + normalized;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }
}
