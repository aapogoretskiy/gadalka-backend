package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.domain.DailyHoroscope;
import ru.sapa.gadalka_backend.domain.type.ZodiacSign;
import ru.sapa.gadalka_backend.repository.DailyHoroscopeRepository;
import ru.sapa.gadalka_backend.service.interpretation.AiInterpretationManager;
import ru.sapa.gadalka_backend.service.interpretation.HoroscopeContent;
import ru.sapa.gadalka_backend.service.interpretation.HoroscopeGenerationException;

import java.time.LocalDate;
import java.util.Optional;

import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.AI_PROVIDER;

/**
 * Отвечает за получение/обновление гороскопа на день для одного знака зодиака — включая поход к AI
 * и сохранение результата в {@code daily_horoscopes}.
 *
 * <p>Выделено из {@link HoroscopeService} в отдельный бин намеренно: метод {@link #resolveHoroscope}
 * вызывается как из обычного пользовательского запроса (через {@code HoroscopeService}, внутри уже
 * идущей транзакции), так и из ночного cron-задания {@code HoroscopeScheduler}, которое обходит все
 * 12 знаков подряд. Для cron-сценария критично, чтобы каждый знак обрабатывался в своей отдельной
 * транзакции — иначе ошибка на одном знаке (например, исключение при сохранении) откатила бы уже
 * обработанные знаки той же пачки, а pessimistic-лок на все 12 строк держался бы до конца всего обхода.
 * Этого можно добиться только межбиновым вызовом {@code @Transactional}-метода (самовызов внутри одного
 * класса в Spring не проходит через прокси и аннотация будет проигнорирована) — поэтому это отдельный сервис.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoroscopeGenerationService {

    /**
     * Нейтральный нерискованный текст на случай, если для знака вообще ещё не было ни одной
     * успешной генерации (например, первый день работы фичи) и при этом AI не отвечает —
     * возвращать пользователю ошибку в этом случае хуже, чем показать обезличенный, но связный текст.
     * В отличие от обычного дневного контента это не помечается как "стейл" в ответе: альтернативы
     * (вчерашнего кэша) просто не существует.
     */
    private static final HoroscopeContent NO_HISTORY_FALLBACK = new HoroscopeContent(
            "Звёзды сегодня немногословны — мы уже готовим обновлённый прогноз, загляните чуть позже.",
            "Сделайте сегодня то, что давно откладывали — момент располагает.",
            "Будьте открыты к проявлениям внимания — они могут прийти неожиданно.",
            "Сфокусируйтесь на одной задаче — разбросанность сегодня не на пользу.",
            "Прежде чем тратить — посчитайте дважды.",
            3, 3, 3, 3
    );

    private final DailyHoroscopeRepository horoscopeRepository;
    private final AiInterpretationManager interpretationManager;
    private final SystemConfigService systemConfigService;

    /**
     * Возвращает актуальный гороскоп для знака, генерируя/обновляя его при необходимости.
     *
     * <p>Сначала читаем без блокировки — это покрывает почти все запросы в течение дня,
     * когда контент уже свежий. Если он устарел или отсутствует, переходим на
     * {@code PESSIMISTIC_WRITE}-блокировку строки знака и перепроверяем дату ещё раз —
     * это защищает от того, чтобы при одновременных запросах нескольких пользователей
     * с одним знаком AI был вызван больше одного раза в сутки.
     *
     * <p>Если AI так и не смог вернуть валидный контент после всех retry (см.
     * {@code OpenAiCompatibleInterpretationService}) — запись знака НЕ перезаписывается:
     * возвращается то, что было сохранено раньше (вчерашний гороскоп), чтобы вызывающий код
     * мог отдать пользователю хоть что-то осмысленное вместо ошибки. Понять, что вернулся
     * именно устаревший контент, можно сравнив {@link DailyHoroscope#getDate()} с сегодняшней датой —
     * этим пользуется {@code HoroscopeService} при формировании поля {@code stale} в ответе.
     */
    @Transactional
    public DailyHoroscope resolveHoroscope(ZodiacSign zodiacSign, LocalDate today) {
        Optional<DailyHoroscope> cached = horoscopeRepository.findByZodiacSign(zodiacSign);
        if (cached.isPresent() && today.equals(cached.get().getDate())) {
            return cached.get();
        }

        Optional<DailyHoroscope> locked = horoscopeRepository.findByZodiacSignForUpdate(zodiacSign);
        if (locked.isPresent() && today.equals(locked.get().getDate())) {
            // Другой запрос успел сгенерировать гороскоп, пока мы ждали блокировку — используем его.
            return locked.get();
        }

        return generateAndSave(zodiacSign, today, locked);
    }

    private DailyHoroscope generateAndSave(ZodiacSign zodiacSign, LocalDate today, Optional<DailyHoroscope> existing) {
        log.info("Генерируем гороскоп на день: знак={}, дата={}", zodiacSign, today);
        String provider = systemConfigService.getValue(AI_PROVIDER);

        HoroscopeContent content;
        try {
            content = interpretationManager.interpretDailyHoroscope(provider, zodiacSign, today);
        } catch (HoroscopeGenerationException ex) {
            if (existing.isPresent()) {
                log.error("Не удалось обновить гороскоп для знака {} на {} — оставляем версию от {}: {}",
                        zodiacSign, today, existing.get().getDate(), ex.getMessage());
                return existing.get();
            }
            log.error("Не удалось сгенерировать гороскоп для знака {} впервые (нет прошлой версии) — " +
                    "используем нейтральный fallback: {}", zodiacSign, ex.getMessage());
            content = NO_HISTORY_FALLBACK;
        }

        DailyHoroscope horoscope = existing.orElseGet(() -> DailyHoroscope.builder().zodiacSign(zodiacSign).build());
        horoscope.setDate(today);
        horoscope.setGeneralText(content.general());
        horoscope.setAdviceText(content.advice());
        horoscope.setLoveText(content.love());
        horoscope.setCareerText(content.career());
        horoscope.setMoneyText(content.money());
        horoscope.setGeneralScore(content.generalScore());
        horoscope.setLoveScore(content.loveScore());
        horoscope.setCareerScore(content.careerScore());
        horoscope.setMoneyScore(content.moneyScore());

        return horoscopeRepository.save(horoscope);
    }
}
