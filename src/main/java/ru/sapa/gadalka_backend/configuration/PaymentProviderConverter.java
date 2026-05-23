package ru.sapa.gadalka_backend.configuration;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;

/**
 * Конвертер path variable → PaymentProvider для Spring MVC.
 * <p>
 * Позволяет использовать читаемые имена в URL:
 * <ul>
 *   <li>{@code "yookassa"} → {@link PaymentProvider#YOOKASSA}</li>
 *   <li>{@code "stars"} → {@link PaymentProvider#TELEGRAM_STARS}</li>
 * </ul>
 * <p>
 * Благодаря этому фронт вызывает {@code POST /payments/yookassa/create}
 * и {@code POST /payments/stars/create} — короткие, понятные пути.
 * При добавлении нового провайдера достаточно добавить один {@code case} в switch.
 */
@Component
public class PaymentProviderConverter implements Converter<String, PaymentProvider> {

    @Override
    public PaymentProvider convert(String source) {
        return switch (source.toLowerCase()) {
            case "yookassa"                -> PaymentProvider.YOOKASSA;
            case "stars", "telegram_stars" -> PaymentProvider.TELEGRAM_STARS;
            default -> throw new IllegalArgumentException(
                    "Неизвестный платёжный провайдер: '" + source + "'. " +
                    "Доступные: yookassa, stars");
        };
    }
}
