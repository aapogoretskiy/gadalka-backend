package ru.sapa.gadalka_backend.domain.type;

/**
 * Цель кнопки под сообщением регулярной рассылки — куда именно внутри Mini App
 * ведёт нажатие.
 *
 * <p>Кнопка открывает нужный экран сразу.
 *
 * <p>Как это работает. Значение {@code query} дописывается к {@code appUrl} —
 * получается ссылка вида {@code https://app.example/?screen=pay&tab=subscriptions}.
 * Фронтенд при инициализации разбирает эти query-параметры и роутит пользователя
 * (см. {@code App.vue}, блок обработки deep-link). Механизм уже использовался
 * для {@code screen=pay} в напоминаниях о брошенной оплате — здесь он просто расширен
 * до полного списка экранов.
 *
 * <p>Про подписки. У {@code subscription_plans} нет поля {@code code} — только
 * {@code id} (нестабилен между окружениями) и {@code name}. Поэтому план передаётся
 * по имени, а фронтенд ищет его без учёта регистра; если план переименовали или
 * отключили — просто откроется вкладка «Подписки» без предвыбора, без ошибки.
 */
public enum NotificationTarget {

    /** Главный экран — для сообщений без конкретного call-to-action. */
    HOME(null),

    // ── Оплата: подписки ──────────────────────────────────────────────────────

    /** Вкладка «Подписки» без предвыбора конкретного плана. */
    PAY_SUBSCRIPTIONS("screen=pay&tab=subscriptions"),
    /** Вкладка «Подписки», предвыбран план Lite. */
    PAY_PLAN_LITE("screen=pay&tab=subscriptions&plan=Lite"),
    /** Вкладка «Подписки», предвыбран план Premium. */
    PAY_PLAN_PREMIUM("screen=pay&tab=subscriptions&plan=Premium"),
    /** Вкладка «Подписки», предвыбран план Superb. */
    PAY_PLAN_SUPERB("screen=pay&tab=subscriptions&plan=Superb"),

    // ── Оплата: пакеты знаков ─────────────────────────────────────────────────

    /** Вкладка «Знаки» без предвыбора пакета. */
    PAY_CREDITS("screen=pay&tab=credits"),
    /** Вкладка «Знаки», предвыбран пакет 3 знака. */
    PAY_PACK_3("screen=pay&tab=credits&pack=PACK_3"),
    /** Вкладка «Знаки», предвыбран пакет 7 знаков. */
    PAY_PACK_7("screen=pay&tab=credits&pack=PACK_7"),
    /** Вкладка «Знаки», предвыбран пакет 15 знаков (+3 в подарок). */
    PAY_PACK_15("screen=pay&tab=credits&pack=PACK_15"),

    // ── Расклады Таро ─────────────────────────────────────────────────────────

    /** Экран расклада, предвыбран «Три карты». */
    FORTUNE_THREE_CARD("screen=fortune&spread=THREE_CARD"),
    /** Экран расклада, предвыбрана «Подкова». */
    FORTUNE_HORSESHOE("screen=fortune&spread=HORSESHOE"),
    /** Экран расклада, предвыбран «Кельтский крест». */
    FORTUNE_CELTIC_CROSS("screen=fortune&spread=CELTIC_CROSS"),
    /** Экран расклада без предвыбора типа. */
    FORTUNE("screen=fortune"),

    // ── Остальные разделы ─────────────────────────────────────────────────────

    /** Разбор сна. */
    DREAM("screen=dream"),
    /** Совместимость по нумерологии. */
    COMPATIBILITY("screen=compatibility"),
    /** Число дня. */
    NUMEROLOGY_DAY("screen=numerology-day"),
    /** Прогноз недели. */
    NUMEROLOGY_WEEK("screen=numerology-week"),
    /** Прогноз месяца. */
    NUMEROLOGY_MONTH("screen=numerology-month"),
    /** Разбор года. */
    NUMEROLOGY_YEAR("screen=numerology-year"),
    /** Нумерологический портрет. */
    NUMEROLOGY_PORTRAIT("screen=numerology"),
    /** Карта дня Таро. */
    TAROT_DAY("screen=tarot-day");

    /** Query-строка без ведущего «?» / «&»; null — открывать приложение как есть. */
    private final String query;

    NotificationTarget(String query) {
        this.query = query;
    }

    /**
     * Собирает итоговую ссылку для кнопки.
     *
     * @param appUrl базовый адрес Mini App из {@code telegram.bot.app-url}
     * @return appUrl с дописанными параметрами цели (или сам appUrl для {@link #HOME})
     */
    public String buildUrl(String appUrl) {
        if (query == null || query.isBlank()) {
            return appUrl;
        }
        String separator = appUrl.contains("?") ? "&" : "?";
        return appUrl + separator + query;
    }
}
