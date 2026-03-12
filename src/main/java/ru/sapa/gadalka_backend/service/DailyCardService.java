package ru.sapa.gadalka_backend.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.card.DailyCardResponse;
import ru.sapa.gadalka_backend.domain.Card;
import ru.sapa.gadalka_backend.domain.DailyCard;
import ru.sapa.gadalka_backend.mapper.CardMapper;
import ru.sapa.gadalka_backend.repository.CardRepository;
import ru.sapa.gadalka_backend.repository.DailyCardRepository;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DailyCardService {

    private final CardMapper cardMapper;
    private final DailyCardRepository dailyCardRepository;
    private final CardRepository cardRepository;

    @Transactional
    public DailyCardResponse getDailyCard(Long userId) {
        LocalDate today = LocalDate.now();

        DailyCard dailyCard = dailyCardRepository.findByUserIdAndDate(userId, today)
                .orElseGet(() -> createDailyCard(userId, today));

        return cardMapper.toDailyCardDto(dailyCard);
    }

    private DailyCard createDailyCard(Long userId, LocalDate today) {
        Card randomCard = cardRepository.findRandomCard();

        DailyCard dailyCard = DailyCard.builder()
                .userId(userId)
                .card(randomCard)
                .date(today)
                .build();

        dailyCardRepository.save(dailyCard);

        return dailyCard;
    }
}
