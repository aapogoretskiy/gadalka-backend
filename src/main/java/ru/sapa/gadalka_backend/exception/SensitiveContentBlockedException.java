package ru.sapa.gadalka_backend.exception;

import ru.sapa.gadalka_backend.domain.type.SensitiveContentCategory;

/**
 * Выбрасывается, когда вопрос пользователя относится к чувствительной теме:
 * суицид, смерть, война, медицинские диагнозы и т.д.
 * <p>
 * Для категории {@link SensitiveContentCategory#SELF_HARM_SUICIDE} возвращается
 * расширенный ответ с телефоном доверия.
 */
public class SensitiveContentBlockedException extends RuntimeException {

    private static final String SELF_HARM_MESSAGE =
            "Этот вопрос выходит за пределы того, о чём могут говорить карты. " +
            "Если тебе сейчас тяжело — есть люди, которые готовы помочь: " +
            "телефон доверия 8-800-2000-122 (звонок бесплатный, круглосуточно).";

    private static final String DEFAULT_MESSAGE =
            "Этот вопрос за пределами того, что карты могут открыть. " +
            "Давай сосредоточимся на том, что действительно в твоих силах изменить.";

    public SensitiveContentBlockedException(SensitiveContentCategory category) {
        super(category == SensitiveContentCategory.SELF_HARM_SUICIDE
                ? SELF_HARM_MESSAGE
                : DEFAULT_MESSAGE);
    }

    public SensitiveContentBlockedException() {
        super(DEFAULT_MESSAGE);
    }
}
