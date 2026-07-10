package ru.sapa.gadalka_backend.domain.type;

public enum SensitiveContentCategory {
    /** СВО, война, военные конфликты */
    MILITARY_CONFLICT,
    /** Медицинские диагнозы, лечение, прогноз болезни */
    HEALTH_MEDICAL,
    /** Смерть, срок жизни, причины смерти конкретного человека */
    DEATH_MORTALITY,
    /** Суицид, причинение вреда себе или другим */
    SELF_HARM_SUICIDE,
    /** Преступления, мошенничество, насилие */
    CRIME_VIOLENCE,
    /** Юридические/финансовые советы, воспринимаемые как профессиональные */
    LEGAL_FINANCIAL_ADVICE,
    /** Азартные игры, гарантированные инвестиции */
    GAMBLING_INVESTMENT,
    /** Политические деятели/партии, религиозные утверждения */
    POLITICAL_RELIGIOUS,
    /** Поиск пропавших людей, определение виновности */
    MISSING_PERSONS_GUILT,
    /** LLM отказал, но ключевые слова не сработали — пограничный случай */
    LLM_REFUSED,
    /** Явный результат LLM-классификатора: вопрос признан безопасным */
    NOT_SENSITIVE,
    /** LLM так и не вернула ожидаемый формат ответа после всех ретраев — блокируем fail-closed */
    CLASSIFICATION_FAILED
}
