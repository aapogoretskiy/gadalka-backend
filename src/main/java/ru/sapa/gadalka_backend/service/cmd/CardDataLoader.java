package ru.sapa.gadalka_backend.service.cmd;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import ru.sapa.gadalka_backend.domain.Card;
import ru.sapa.gadalka_backend.repository.CardRepository;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardDataLoader implements CommandLineRunner {

    private static final String CARDS_PATH = "/data/cards.json";

    private final CardRepository cardRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) {
        if (cardRepository.count() > 0) {
            log.warn("Карты уже загружены, пропускаем");
            return;
        }
        log.info("Загружаем карты в БД...");
        try (InputStream cardInputStream = getClass().getResourceAsStream(CARDS_PATH)) {
            List<Card> cards = objectMapper.readValue(cardInputStream, new TypeReference<>() {
            });
            if (CollectionUtils.isEmpty(cards)) {
                log.warn("Невозможно найти данные по пути: [{}]", CARDS_PATH);
                return;
            }
            cardRepository.saveAll(cards);
            log.info("Загружено карт: {}", cards.size());
        } catch (Exception e) {
            log.error("Ошибка при чтении json с картами", e);
        }
    }
}
