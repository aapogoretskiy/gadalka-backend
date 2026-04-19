package ru.sapa.gadalka_backend.service;

import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityCategoryScore;
import ru.sapa.gadalka_backend.api.dto.compatibility.CompatibilityRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Нумерологический расчёт совместимости двух людей.
 *
 * <p>Используется пифагорейская система нумерологии:
 * <ul>
 *   <li><b>Число жизненного пути</b> — сумма цифр даты рождения, редуцированная до 1–9.</li>
 *   <li><b>Число судьбы</b> — сумма числовых значений всех букв имени, редуцированная до 1–9.</li>
 *   <li><b>Число души</b> — сумма значений гласных букв имени, редуцированная до 1–9.</li>
 *   <li><b>Число личности</b> — сумма значений согласных букв имени, редуцированная до 1–9.</li>
 * </ul>
 *
 * <p>Совместимость по категориям:
 * <ul>
 *   <li>Эмоции — по числам души.</li>
 *   <li>Интеллект — по числам судьбы.</li>
 *   <li>Ценности — по числам жизненного пути.</li>
 *   <li>Страсть — по числам личности и числам дня рождения.</li>
 * </ul>
 */
@Service
public class NumerologyService {

    /**
     * Матрица базовой совместимости чисел 1–9.
     * compatibilityMatrix[i][j] — совместимость числа (i+1) с числом (j+1), в процентах.
     */
    private static final int[][] COMPATIBILITY_MATRIX = {
        //  1    2    3    4    5    6    7    8    9
        {  75,  55,  80,  50,  85,  70,  65,  90,  70 }, // 1
        {  55,  80,  65,  85,  60,  95,  70,  55,  80 }, // 2
        {  80,  65,  75,  50,  85,  75,  60,  60,  90 }, // 3
        {  50,  85,  50,  75,  55,  85,  80,  90,  50 }, // 4
        {  85,  60,  85,  55,  65,  65,  85,  60,  80 }, // 5
        {  70,  95,  75,  85,  65,  85,  60,  70,  80 }, // 6
        {  65,  70,  60,  80,  85,  60,  85,  60,  75 }, // 7
        {  90,  55,  60,  90,  60,  70,  60,  80,  65 }, // 8
        {  70,  80,  90,  50,  80,  80,  75,  65,  85 }, // 9
    };

    /** Пифагорейские значения русских букв */
    private static final Map<Character, Integer> RUSSIAN_VALUES = Map.ofEntries(
        Map.entry('а', 1), Map.entry('б', 2), Map.entry('в', 3), Map.entry('г', 4),
        Map.entry('д', 5), Map.entry('е', 6), Map.entry('ж', 7), Map.entry('з', 8),
        Map.entry('и', 9), Map.entry('й', 1), Map.entry('к', 2), Map.entry('л', 3),
        Map.entry('м', 4), Map.entry('н', 5), Map.entry('о', 6), Map.entry('п', 7),
        Map.entry('р', 8), Map.entry('с', 9), Map.entry('т', 1), Map.entry('у', 2),
        Map.entry('ф', 3), Map.entry('х', 4), Map.entry('ц', 5), Map.entry('ч', 6),
        Map.entry('ш', 7), Map.entry('щ', 8), Map.entry('ъ', 9), Map.entry('ы', 1),
        Map.entry('ь', 2), Map.entry('э', 3), Map.entry('ю', 4), Map.entry('я', 5),
        Map.entry('ё', 6)
    );

    /** Пифагорейские значения латинских букв */
    private static final Map<Character, Integer> LATIN_VALUES = Map.ofEntries(
        Map.entry('a', 1), Map.entry('b', 2), Map.entry('c', 3), Map.entry('d', 4),
        Map.entry('e', 5), Map.entry('f', 6), Map.entry('g', 7), Map.entry('h', 8),
        Map.entry('i', 9), Map.entry('j', 1), Map.entry('k', 2), Map.entry('l', 3),
        Map.entry('m', 4), Map.entry('n', 5), Map.entry('o', 6), Map.entry('p', 7),
        Map.entry('q', 8), Map.entry('r', 9), Map.entry('s', 1), Map.entry('t', 2),
        Map.entry('u', 3), Map.entry('v', 4), Map.entry('w', 5), Map.entry('x', 6),
        Map.entry('y', 7), Map.entry('z', 8)
    );

    private static final String RUSSIAN_VOWELS = "аеёиоуыэюя";
    private static final String LATIN_VOWELS   = "aeiouy";

    // -------------------------------------------------------------------------

    public NumerologyCompatibilityResult calculate(
            CompatibilityRequest.PersonInput p1,
            CompatibilityRequest.PersonInput p2) {

        int lifePath1 = lifePathNumber(p1.getBirthDate());
        int lifePath2 = lifePathNumber(p2.getBirthDate());
        int destiny1  = destinyNumber(p1.getName());
        int destiny2  = destinyNumber(p2.getName());
        int soul1     = soulNumber(p1.getName());
        int soul2     = soulNumber(p2.getName());
        int person1   = personalityNumber(p1.getName());
        int person2   = personalityNumber(p2.getName());
        int birthDay1 = reduce(p1.getBirthDate().getDayOfMonth());
        int birthDay2 = reduce(p2.getBirthDate().getDayOfMonth());

        int emotions  = clamp(compat(soul1,     soul2)    + deterministic(birthDay1, birthDay2, 8));
        int intellect = clamp(compat(destiny1,  destiny2) + deterministic(lifePath1, lifePath2, 5));
        int values    = clamp(compat(lifePath1, lifePath2));
        int passion   = clamp(compat(person1,   person2)  + deterministic(birthDay1, birthDay2, 10));

        int overall   = (emotions + intellect + values + passion) / 4;

        List<CompatibilityCategoryScore> categories = List.of(
            new CompatibilityCategoryScore("Эмоции",    emotions),
            new CompatibilityCategoryScore("Интеллект", intellect),
            new CompatibilityCategoryScore("Ценности",  values),
            new CompatibilityCategoryScore("Страсть",   passion)
        );

        return new NumerologyCompatibilityResult(overall, categories);
    }

    // -------------------------------------------------------------------------

    /** Число жизненного пути: сумма всех цифр даты рождения, редуцированная до 1–9 */
    int lifePathNumber(LocalDate date) {
        int sum = sumDigits(date.getDayOfMonth())
                + sumDigits(date.getMonthValue())
                + sumDigits(date.getYear());
        return reduce(sum);
    }

    /** Число судьбы: сумма значений всех букв имени, редуцированная до 1–9 */
    int destinyNumber(String name) {
        return reduce(letterSum(name, false));
    }

    /** Число души: сумма значений гласных букв имени, редуцированная до 1–9 */
    int soulNumber(String name) {
        return reduce(letterSum(name, true));
    }

    /** Число личности: сумма значений согласных букв имени, редуцированная до 1–9 */
    int personalityNumber(String name) {
        int all     = letterSum(name, false);
        int vowels  = letterSum(name, true);
        return reduce(Math.max(1, all - vowels));
    }

    // -------------------------------------------------------------------------

    /** Берёт значение из матрицы совместимости двух чисел 1–9 */
    private int compat(int a, int b) {
        int i = Math.max(0, Math.min(8, a - 1));
        int j = Math.max(0, Math.min(8, b - 1));
        return COMPATIBILITY_MATRIX[i][j];
    }

    /**
     * Детерминированная малая поправка (±maxDelta) на основе двух чисел.
     * Позволяет добавить вариативность без случайности.
     */
    private int deterministic(int n1, int n2, int maxDelta) {
        int combined = (n1 * 7 + n2 * 13) % (maxDelta * 2 + 1);
        return combined - maxDelta;
    }

    /** Сумма цифр числа */
    private int sumDigits(int n) {
        int sum = 0;
        while (n > 0) {
            sum += n % 10;
            n /= 10;
        }
        return sum;
    }

    /** Редукция числа до 1–9 (мастер-числа 11, 22, 33 также допустимы, но для простоты сводим к 1–9) */
    int reduce(int n) {
        while (n > 9) {
            n = sumDigits(n);
        }
        return Math.max(1, n);
    }

    /** Сумма числовых значений букв имени. Если {@code vowelsOnly}, считаем только гласные. */
    private int letterSum(String name, boolean vowelsOnly) {
        return name.toLowerCase().chars()
            .mapToObj(c -> (char) c)
            .filter(c -> !vowelsOnly || isVowel(c))
            .mapToInt(this::letterValue)
            .filter(v -> v > 0)
            .sum();
    }

    private int letterValue(char c) {
        if (RUSSIAN_VALUES.containsKey(c)) return RUSSIAN_VALUES.get(c);
        if (LATIN_VALUES.containsKey(c))   return LATIN_VALUES.get(c);
        return 0;
    }

    private boolean isVowel(char c) {
        return RUSSIAN_VOWELS.indexOf(c) >= 0 || LATIN_VOWELS.indexOf(c) >= 0;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
