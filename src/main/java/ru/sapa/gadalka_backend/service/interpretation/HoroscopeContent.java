package ru.sapa.gadalka_backend.service.interpretation;

/**
 * Сгенерированный текст и рейтинги гороскопа на день для одного знака зодиака.
 * Раздел про здоровье намеренно не предусмотрен — решили не давать
 * прогнозы/советы по здоровью, чтобы не создавать юридических рисков (намёки на медицинские рекомендации/гарантии).
 *
 * <p>Рейтинги ({@code generalScore}/{@code loveScore}/{@code careerScore}/{@code moneyScore}) —
 * число от 1 до 5, генерируется ИИ вместе с текстом за тот же вызов.
 */
public record HoroscopeContent(
        String general,
        String advice,
        String love,
        String career,
        String money,
        int generalScore,
        int loveScore,
        int careerScore,
        int moneyScore
) {
}
