package ru.sapa.gadalka_backend.domain.type;

import java.time.LocalDate;
import java.util.List;

/**
 * Знак зодиака по дате рождения (тропический зодиак, привязка к солнцу).
 *
 * <p>Используется для фичи «Гороскоп на день»: контент генерируется и кэшируется
 * один раз в сутки на каждый знак (см. {@link ru.sapa.gadalka_backend.domain.DailyHoroscope}),
 * а не на каждого пользователя — это и держит количество обращений к AI в пределах 12 в день.
 *
 * <p>Поля {@code periodLabel}/{@code luckyNumbers}/{@code luckyColors}/{@code stone} —
 * справочные данные знака, не зависят от даты и не генерируются ИИ: захардкожены здесь,
 * чтобы фронту не нужно было вести собственную копию таблицы знаков.
 */
public enum ZodiacSign {
    ARIES("Овен", "21 марта — 19 апреля", List.of(1, 9, 18), List.of("Красный", "Алый"), "Гранат"),
    TAURUS("Телец", "20 апреля — 20 мая", List.of(2, 7, 14), List.of("Зелёный", "Синий"), "Малахит"),
    GEMINI("Близнецы", "21 мая — 20 июня", List.of(5, 7, 12), List.of("Жёлтый", "Серебристый"), "Агат"),
    CANCER("Рак", "21 июня — 22 июля", List.of(2, 4, 11), List.of("Белый", "Серебристый"), "Лунный камень"),
    LEO("Лев", "23 июля — 22 августа", List.of(1, 5, 19), List.of("Золотой", "Оранжевый"), "Янтарь"),
    VIRGO("Дева", "23 августа — 22 сентября", List.of(6, 14, 23), List.of("Тёмно-зелёный", "Коричневый"), "Сердолик"),
    LIBRA("Весы", "23 сентября — 22 октября", List.of(6, 9, 15), List.of("Розовый", "Голубой"), "Опал"),
    SCORPIO("Скорпион", "23 октября — 21 ноября", List.of(4, 8, 13), List.of("Тёмно-красный", "Бордовый"), "Аметист"),
    SAGITTARIUS("Стрелец", "22 ноября — 21 декабря", List.of(3, 9, 21), List.of("Фиолетовый", "Синий"), "Топаз"),
    CAPRICORN("Козерог", "22 декабря — 19 января", List.of(4, 8, 17), List.of("Чёрный", "Тёмно-серый"), "Оникс"),
    AQUARIUS("Водолей", "20 января — 18 февраля", List.of(4, 11, 22), List.of("Голубой", "Серебристый"), "Аквамарин"),
    PISCES("Рыбы", "19 февраля — 20 марта", List.of(3, 7, 12), List.of("Морская волна", "Лавандовый"), "Жемчуг");

    private final String displayName;
    private final String periodLabel;
    private final List<Integer> luckyNumbers;
    private final List<String> luckyColors;
    private final String stone;

    ZodiacSign(String displayName, String periodLabel, List<Integer> luckyNumbers, List<String> luckyColors, String stone) {
        this.displayName = displayName;
        this.periodLabel = periodLabel;
        this.luckyNumbers = luckyNumbers;
        this.luckyColors = luckyColors;
        this.stone = stone;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Диапазон дат знака для отображения, например "20 апреля — 20 мая". */
    public String getPeriodLabel() {
        return periodLabel;
    }

    /** Счастливые числа знака — справочные, статичные, не зависят от дня. */
    public List<Integer> getLuckyNumbers() {
        return luckyNumbers;
    }

    /** Цвета знака — справочные, статичные, не зависят от дня. */
    public List<String> getLuckyColors() {
        return luckyColors;
    }

    /** Камень-талисман знака. */
    public String getStone() {
        return stone;
    }

    /**
     * Определяет знак зодиака по дате (обычно — по дате рождения пользователя).
     * Диапазоны совпадают с {@link ru.sapa.gadalka_backend.service.NumerologyService#zodiacSign(LocalDate)},
     * который возвращает то же самое в виде строки для другой задачи (знак "дня" в нумерологии).
     */
    public static ZodiacSign fromDate(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        if ((month == 3 && day >= 21) || (month == 4 && day <= 19))   return ARIES;
        if ((month == 4 && day >= 20) || (month == 5 && day <= 20))   return TAURUS;
        if ((month == 5 && day >= 21) || (month == 6 && day <= 20))   return GEMINI;
        if ((month == 6 && day >= 21) || (month == 7 && day <= 22))   return CANCER;
        if ((month == 7 && day >= 23) || (month == 8 && day <= 22))   return LEO;
        if ((month == 8 && day >= 23) || (month == 9 && day <= 22))   return VIRGO;
        if ((month == 9 && day >= 23) || (month == 10 && day <= 22))  return LIBRA;
        if ((month == 10 && day >= 23) || (month == 11 && day <= 21)) return SCORPIO;
        if ((month == 11 && day >= 22) || (month == 12 && day <= 21)) return SAGITTARIUS;
        if ((month == 12 && day >= 22) || (month == 1 && day <= 19))  return CAPRICORN;
        if ((month == 1 && day >= 20) || (month == 2 && day <= 18))   return AQUARIUS;
        return PISCES;
    }
}
