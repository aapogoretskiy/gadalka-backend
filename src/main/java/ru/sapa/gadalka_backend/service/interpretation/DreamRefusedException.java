package ru.sapa.gadalka_backend.service.interpretation;

/**
 * AI отказался разбирать сон по правилам чувствительности (политика, СВО и т.п.).
 *
 * <p>Отдельное исключение (а не текстовый паттерн отказа, как в раскладах Таро) — потому что
 * ответ Сонника строго JSON: модель инструктирована при отказе вернуть {@code {"refused": true, ...}},
 * и парсер превращает это в исключение. Это надёжнее regex-детекции по фразам.
 * Знаки при этом НЕ списываются (списание в {@code DreamService} идёт после успешной генерации).
 */
public class DreamRefusedException extends RuntimeException {
    public DreamRefusedException(String message) {
        super(message);
    }
}
