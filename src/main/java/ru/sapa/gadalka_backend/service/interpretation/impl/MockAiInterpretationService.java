package ru.sapa.gadalka_backend.service.interpretation.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.card.CardDto;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityCategoryScore;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityRequest;
import ru.sapa.gadalka_backend.domain.type.ZodiacSign;
import ru.sapa.gadalka_backend.service.interpretation.AiInterpretationService;
import ru.sapa.gadalka_backend.service.interpretation.DreamContent;
import ru.sapa.gadalka_backend.service.interpretation.HoroscopeContent;
import ru.sapa.gadalka_backend.service.interpretation.InterpretationResult;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service("mock")
@RequiredArgsConstructor
public class MockAiInterpretationService implements AiInterpretationService {

    @Override
    public InterpretationResult interpret(List<CardDto> cards, String question, String category) {
        String generalInterpretation = "Карты намекают на важные изменения в вашей жизни. Следуйте интуиции.";

        List<CardDto> cardsWithInterpretation = cards.stream()
                .map(card -> CardDto.builder()
                        .id(card.getId())
                        .name(card.getName())
                        .meaning(card.getMeaning())
                        .cardPosition(card.getCardPosition())
                        .interpretation("Карта " + card.getName() + " в позиции «" + translatePosition(card.getCardPosition()) + "» указывает на перемены.")
                        .imageUrl(card.getImageUrl())
                        .build())
                .toList();

        return new InterpretationResult(generalInterpretation, cardsWithInterpretation);
    }

    @Override
    public String interpretCompatibility(List<CompatibilityRequest.PersonInput> persons,
                                         int overallScore,
                                         List<CompatibilityCategoryScore> categories) {
        String person1 = persons.get(0).getName();
        String person2 = persons.get(1).getName();
        return "Звёзды благосклонны к союзу " + person1 + " и " + person2 + ". " +
               "Числа судьбы говорят о глубокой внутренней связи. " +
               "Следуйте своей интуиции и доверяйте чувствам.";
    }

    @Override
    public HoroscopeContent interpretDailyHoroscope(ZodiacSign zodiacSign, LocalDate date) {
        String sign = zodiacSign.getDisplayName();
        return new HoroscopeContent(
                "Для знака " + sign + " день " + date + " пройдёт спокойно, с лёгким ощущением новых возможностей.",
                "Доверьтесь интуиции и не откладывайте важный разговор.",
                "В отношениях сегодня благоприятный период для откровенности.",
                "На работе стоит сосредоточиться на одной важной задаче, не разбрасываясь.",
                "Хороший день для обсуждения финансовых вопросов, но не для подписания документов.",
                4, 5, 3, 4
        );
    }

    @Override
    public DreamContent interpretDream(String dreamText,
                                       List<DreamContent.SymbolMeaning> selectedSymbols,
                                       ZodiacSign zodiacSign,
                                       int lifePathNumber) {
        // Заголовок собираем из выбранных чипов, чтобы мок вёл себя как настоящий разбор
        List<String> titleSymbols = selectedSymbols.isEmpty()
                ? List.of("Полёт", "Дом")
                : selectedSymbols.stream().map(DreamContent.SymbolMeaning::name).limit(3).toList();

        List<DreamContent.SymbolMeaning> symbols = selectedSymbols.isEmpty()
                ? List.of(
                        new DreamContent.SymbolMeaning("Полёт", "Желание вырваться за пределы привычного и обрести свободу."),
                        new DreamContent.SymbolMeaning("Дом", "Новый этап жизни, который ещё не изведан, но уже чувствуется."))
                : selectedSymbols.stream()
                        .map(s -> new DreamContent.SymbolMeaning(s.name(),
                                "Во сне символ «" + s.name() + "» может говорить о переменах, которые вы уже чувствуете."))
                        .toList();

        return new DreamContent(
                titleSymbols,
                "Этот сон говорит о стремлении к свободе и независимости. Подсознание подсказывает, " +
                        "что вы готовы к переменам, но ещё не решились сделать первый шаг.",
                "Для числа " + lifePathNumber + " сон особенно символичен — вы готовы к новому пути.",
                "Для знака " + zodiacSign.getDisplayName() + " сны сейчас часто связаны с балансом и отношениями.",
                symbols,
                "Сегодня обратите внимание на ситуации, где вы чувствуете ограничение — пришло время двигаться вперёд.",
                "Что мой сон хочет мне подсказать о переменах?"
        );
    }

    @Override
    public String classifySensitiveContent(String question) {
        return "LLM_REFUSED";
    }

    @Override
    public String classifyQuestionSensitivity(String question) {
        return "NOT_SENSITIVE";
    }

    @Override
    public String explainSensitiveClassification(String question, String category) {
        return "Мок-объяснение для категории " + category + ".";
    }

    @Override
    public String getProvider() {
        return "mock";
    }

    private String translatePosition(ru.sapa.gadalka_backend.api.dto.card.CardPosition position) {
        if (position == null) return "";
        return switch (position) {
            // Три карты
            case PAST    -> "Прошлое";
            case PRESENT -> "Настоящее";
            case FUTURE  -> "Будущее";
            // Подкова
            case HORSESHOE_PAST      -> "Прошлое";
            case HORSESHOE_PRESENT   -> "Настоящее";
            case HORSESHOE_HIDDEN    -> "Скрытые влияния";
            case HORSESHOE_OBSTACLES -> "Препятствия";
            case HORSESHOE_EXTERNAL  -> "Внешние влияния";
            case HORSESHOE_ADVICE    -> "Совет";
            case HORSESHOE_OUTCOME   -> "Итог";
            // Кельтский крест
            case CELTIC_HEART           -> "Суть вопроса";
            case CELTIC_CROSS           -> "Что мешает";
            case CELTIC_FOUNDATION      -> "Основа";
            case CELTIC_PAST            -> "Прошлое";
            case CELTIC_POSSIBLE_FUTURE -> "Возможное будущее";
            case CELTIC_NEAR_FUTURE     -> "Ближайшее будущее";
            case CELTIC_SELF            -> "Ваша позиция";
            case CELTIC_EXTERNAL        -> "Внешние влияния";
            case CELTIC_HOPES_FEARS     -> "Надежды и страхи";
            case CELTIC_OUTCOME         -> "Итог";
        };
    }
}
