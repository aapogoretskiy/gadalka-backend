package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.card.CardDto;
import ru.sapa.gadalka_backend.api.dto.fortune.FortuneResponse;
import ru.sapa.gadalka_backend.domain.Card;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.repository.CardRepository;
import ru.sapa.gadalka_backend.service.interpretation.AiInterpretationManager;

import java.util.List;

import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.AI_PROVIDER;

@Slf4j
@Service
@RequiredArgsConstructor
public class FortuneService {

    private static final Integer STD_FORTUNE_CARD_COUNT = 3;

    private final SpreadService spreadService;
    private final CardRepository cardRepository;
    private final SystemConfigService systemConfigService;
    private final AiInterpretationManager interpretationManager;

    public FortuneResponse getFortune(User user) {
        List<Card> cards = cardRepository.findRandomCards(STD_FORTUNE_CARD_COUNT);
        List<CardDto> cardDtoList = spreadService.assignCardPosition(cards);
        String currentAiProvider = systemConfigService.getValue(AI_PROVIDER);
        String interpretation = interpretationManager.interpret(currentAiProvider, cardDtoList);
        return new FortuneResponse(user.getUsername(), cardDtoList, interpretation);
    }
}
