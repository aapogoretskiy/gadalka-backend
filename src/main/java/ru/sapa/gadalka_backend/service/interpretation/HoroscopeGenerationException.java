package ru.sapa.gadalka_backend.service.interpretation;

/**
 * Бросается {@link AiInterpretationService#interpretDailyHoroscope}, когда не удалось получить
 * от AI валидный контент гороскопа после всех повторных попыток (см. реализацию в
 * {@code OpenAiCompatibleInterpretationService} — там же настроено количество попыток).
 *
 * <p>Это сигнал вызывающему коду (см. {@code HoroscopeGenerationService}), что свежего контента
 * на сегодня нет и нужно решать, чем заменить отсутствующую генерацию — например, оставить
 * вчерашний кэш вместо того, чтобы сохранять в БД мусор или сырой ответ модели.
 */
public class HoroscopeGenerationException extends RuntimeException {
    public HoroscopeGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
