package ru.sapa.gadalka_backend.service.notification;

import ru.sapa.gadalka_backend.domain.type.NotificationTarget;

/**
 * Одно сообщение регулярной рассылки: текст, подпись кнопки и то, куда эта кнопка ведёт.
 *
 * <p>Раньше пул рассылки был просто {@code List<String>}, а кнопка у всех сообщений
 * была одна и та же («Открыть Гадалку» на главный экран). Теперь текст и call-to-action
 * связаны: если в тексте зовём разобрать сон — кнопка открывает экран снов.
 *
 * @param text       текст сообщения в Telegram-разметке Markdown. Может содержать
 *                   плейсхолдеры: {@code {name}} — имя пользователя, а также цены и
 *                   стоимости из БД (см. {@code NotificationPlaceholderResolver}).
 *                   Если хотя бы один плейсхолдер не удалось раскрыть (например, план
 *                   подписки переименовали в админке) — сообщение не отправляется,
 *                   вместо него берётся следующее по циклу.
 * @param buttonText подпись кнопки под сообщением. Markdown в кнопках Telegram
 *                   не поддерживается, эмодзи — да.
 * @param target     экран Mini App, который откроется по нажатию
 * @param slot       в какую рассылку сообщение годится (утро / вечер / любая)
 */
public record NotificationMessage(
        String text,
        String buttonText,
        NotificationTarget target,
        Slot slot
) {

    /** Время суток, для которого сообщение уместно. */
    public enum Slot {
        /** Только утренняя рассылка (9:00 МСК). */
        MORNING,
        /** Только вечерняя рассылка (20:00 МСК). */
        EVENING,
        /** Годится и утром, и вечером. */
        ANY;

        /** Подходит ли сообщение с этим слотом для рассылки слота {@code required}. */
        public boolean fits(Slot required) {
            return this == ANY || this == required;
        }
    }

    /** Короткий фабричный метод — чтобы каталог читался как таблица, а не как код. */
    public static NotificationMessage of(String text, String buttonText, NotificationTarget target, Slot slot) {
        return new NotificationMessage(text, buttonText, target, slot);
    }
}
