package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.domain.DailyHoroscope;
import ru.sapa.gadalka_backend.domain.type.ZodiacSign;

import java.time.LocalDate;

/**
 * Предгенерирует гороскоп на новый день для всех 12 знаков сразу после полуночи по МСК — чтобы
 * к моменту, когда реальные пользователи начнут открывать раздел "Гороскоп на день", контент
 * уже лежал готовым в БД, а не генерировался на лету при первом запросе с задержкой похода к AI.
 *
 * <p>Ленивая генерация в {@link HoroscopeService#getDailyHoroscope} остаётся как страховка:
 * если эта задача не сработала для какого-то знака (например, AI был недоступен и в 00:05,
 * и при retry внутри неё), первый же пользовательский запрос этого знака за день попробует
 * сгенерировать заново — с тем же retry-механизмом.
 *
 * <p>Каждый знак обрабатывается через {@link HoroscopeGenerationService#resolveHoroscope} —
 * отдельный бин, чей метод помечен {@code @Transactional}. Вызов идёт межбиново (из этого
 * класса, который сам не транзакционный), поэтому каждый знак получает свою собственную
 * транзакцию: ошибка на одном знаке не откатывает уже сохранённые результаты по другим,
 * и pessimistic-лок на строку держится только на время обработки этого одного знака,
 * а не всех 12 сразу.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoroscopeScheduler {

    private final HoroscopeGenerationService horoscopeGenerationService;

    /**
     * Запуск в 00:05 по Москве — с запасом в 5 минут от ровно полуночи, чтобы не зависеть
     * от мелкого рассинхрона часов между сервером и тем, что считается "сегодня".
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "Europe/Moscow")
    public void pregenerateDailyHoroscopes() {
        LocalDate today = LocalDate.now(HoroscopeService.MOSCOW_ZONE);
        log.info("Запуск предгенерации гороскопов на день: дата={}", today);

        int updated = 0;
        int failed = 0;

        for (ZodiacSign sign : ZodiacSign.values()) {
            try {
                DailyHoroscope before = horoscopeGenerationService.resolveHoroscope(sign, today);
                if (today.equals(before.getDate())) {
                    // Не различаем здесь "обновили только что" и "уже было свежим до вызова" —
                    // не критично для лога, главное что для знака есть валидный контент на сегодня.
                    updated++;
                } else {
                    // resolveHoroscope не бросает исключений сам — если дата не сегодняшняя,
                    // значит генерация не удалась даже после retry, и метод вернул вчерашнюю запись как есть.
                    failed++;
                    log.warn("Предгенерация для знака {} не дала свежего контента — осталась запись от {}",
                            sign, before.getDate());
                }
            } catch (Exception ex) {
                failed++;
                log.error("Непредвиденная ошибка предгенерации гороскопа для знака {}: {}", sign, ex.getMessage(), ex);
            }
        }

        log.info("Предгенерация гороскопов завершена: знаков со свежим контентом={}, без свежего контента={}",
                updated, failed);
    }
}
