package ru.sapa.gadalka_backend.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.sapa.gadalka_backend.domain.PaymentProduct;
import ru.sapa.gadalka_backend.domain.SubscriptionPlan;
import ru.sapa.gadalka_backend.domain.type.NotificationTarget;
import ru.sapa.gadalka_backend.domain.type.SpreadType;
import ru.sapa.gadalka_backend.repository.PaymentProductRepository;
import ru.sapa.gadalka_backend.repository.SubscriptionPlanRepository;
import ru.sapa.gadalka_backend.service.FeatureCostService;
import ru.sapa.gadalka_backend.service.SystemConfigService;
import ru.sapa.gadalka_backend.service.notification.NotificationMessage;
import ru.sapa.gadalka_backend.service.notification.NotificationMessageCatalog;
import ru.sapa.gadalka_backend.service.notification.NotificationPlaceholderResolver;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Тексты рассылки нельзя проверить «на глаз»: их несколько десятков, и любая опечатка
 * доезжает до пользователя только через сутки после деплоя. Этот тест ловит три вещи,
 * которые ломаются чаще всего:
 * <ul>
 *   <li>непарная звёздочка в Markdown — Telegram отклонит сообщение целиком;</li>
 *   <li>плейсхолдер, которого нет в {@link NotificationPlaceholderResolver} (опечатка
 *       в имени) — сообщение будет молча пропускаться при каждой рассылке;</li>
 *   <li>пустой пул для утра или вечера — рассылка уйдёт «в никуда».</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Каталог сообщений регулярной рассылки")
class NotificationMessageCatalogTest {

    @Mock
    private PaymentProductRepository productRepository;
    @Mock
    private SubscriptionPlanRepository planRepository;
    @Mock
    private FeatureCostService featureCostService;
    @Mock
    private SystemConfigService systemConfigService;

    @InjectMocks
    private NotificationPlaceholderResolver resolver;

    /** Снимок, в котором доступно всё: и пакеты, и планы, и стоимости фич. */
    private Map<String, String> fullSnapshot() {
        when(productRepository.findByCode("PACK_3")).thenReturn(Optional.of(product("PACK_3", 3, 0, 9900)));
        when(productRepository.findByCode("PACK_7")).thenReturn(Optional.of(product("PACK_7", 7, 0, 19900)));
        when(productRepository.findByCode("PACK_15")).thenReturn(Optional.of(product("PACK_15", 15, 3, 35900)));
        when(systemConfigService.getBooleanValue(anyString(), anyBoolean()))
                .thenReturn(true);
        when(planRepository.findAllByIsActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(
                plan("Lite", 59900, 30),
                plan("Premium", 129900, 30),
                plan("Superb", 189000, 30)));
        when(featureCostService.getCost(any(SpreadType.class))).thenReturn(3);
        when(featureCostService.getDreamCost()).thenReturn(3);
        when(featureCostService.getNumerologyWeekCost()).thenReturn(3);
        when(featureCostService.getNumerologyMonthCost()).thenReturn(10);
        when(featureCostService.getNumerologyYearCost()).thenReturn(18);
        when(featureCostService.getCompatibilityUnlockCost()).thenReturn(3);
        return resolver.snapshot();
    }

    private PaymentProduct product(String code, int readings, int bonus, int priceRub) {
        return PaymentProduct.builder()
                .code(code)
                .name(code)
                .readingsCount(readings)
                .bonusCredits(bonus)
                .priceRub(priceRub)
                .priceStars(1)
                .isActive(true)
                .sortOrder(1)
                .build();
    }

    private SubscriptionPlan plan(String name, int priceRub, int days) {
        return SubscriptionPlan.builder()
                .id(1L)
                .name(name)
                .priceRub(priceRub)
                .priceStars(1)
                .durationDays(days)
                .isActive(true)
                .sortOrder(1)
                .build();
    }

    private List<NotificationMessage> allMessages() {
        return java.util.stream.Stream
                .concat(NotificationMessageCatalog.PROMO.stream(), NotificationMessageCatalog.AMBIENT.stream())
                .toList();
    }

    @Nested
    @DisplayName("Структура каталога")
    class Structure {

        @Test
        @DisplayName("в каждом слоте есть и продающие, и атмосферные сообщения")
        void poolsAreNotEmpty() {
            for (NotificationMessage.Slot slot : List.of(NotificationMessage.Slot.MORNING, NotificationMessage.Slot.EVENING)) {
                assertThat(NotificationMessageCatalog.forSlot(NotificationMessageCatalog.PROMO, slot))
                        .as("продающие сообщения для слота %s", slot)
                        .isNotEmpty();
                assertThat(NotificationMessageCatalog.forSlot(NotificationMessageCatalog.AMBIENT, slot))
                        .as("атмосферные сообщения для слота %s", slot)
                        .isNotEmpty();
            }
        }

        @Test
        @DisplayName("у каждого сообщения есть текст, подпись кнопки и цель")
        void everyMessageIsComplete() {
            for (NotificationMessage message : allMessages()) {
                assertThat(message.text()).isNotBlank();
                assertThat(message.buttonText()).isNotBlank();
                assertThat(message.target()).isNotNull();
                assertThat(message.slot()).isNotNull();
            }
        }

        @Test
        @DisplayName("подпись кнопки укладывается в лимит Telegram")
        void buttonLabelsAreShortEnough() {
            Map<String, String> values = fullSnapshot();
            for (NotificationMessage message : allMessages()) {
                String label = NotificationPlaceholderResolver.apply(message.buttonText(), values).orElseThrow();
                assertThat(label.length())
                        .as("подпись кнопки «%s»", label)
                        .isLessThanOrEqualTo(64);
            }
        }
    }

    @Nested
    @DisplayName("Разметка и плейсхолдеры")
    class Rendering {

        @Test
        @DisplayName("все плейсхолдеры раскрываются полным снимком цен")
        void everyPlaceholderIsResolvable() {
            Map<String, String> values = fullSnapshot();
            for (NotificationMessage message : allMessages()) {
                assertThat(NotificationPlaceholderResolver.apply(message.text(), values))
                        .as("текст сообщения с целью %s", message.target())
                        .isPresent();
                assertThat(NotificationPlaceholderResolver.apply(message.buttonText(), values))
                        .as("подпись кнопки сообщения с целью %s", message.target())
                        .isPresent();
            }
        }

        @Test
        @DisplayName("звёздочки Markdown парные — иначе Telegram отклонит сообщение")
        void markdownBoldIsBalanced() {
            Map<String, String> values = fullSnapshot();
            for (NotificationMessage message : allMessages()) {
                String text = NotificationPlaceholderResolver.apply(message.text(), values).orElseThrow();
                long stars = text.chars().filter(c -> c == '*').count();
                assertThat(stars % 2)
                        .as("непарная * в сообщении: %s", text)
                        .isZero();
            }
        }

        @Test
        @DisplayName("сообщение с недоступной ценой не отправляется")
        void messageWithMissingPriceIsSkipped() {
            Optional<String> result = NotificationPlaceholderResolver
                    .apply("Подписка за {price_lite} ₽", Map.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("{name} не считается нераскрытым плейсхолдером")
        void namePlaceholderSurvives() {
            Optional<String> result = NotificationPlaceholderResolver
                    .apply("Доброе утро, {name}!", Map.of());
            assertThat(result).contains("Доброе утро, {name}!");
        }
    }

    @Nested
    @DisplayName("Ссылки кнопок")
    class Links {

        @Test
        @DisplayName("параметры цели дописываются к appUrl через ? или &")
        void buildsCorrectSeparator() {
            assertThat(NotificationTarget.PAY_CREDITS.buildUrl("https://app.example"))
                    .isEqualTo("https://app.example?screen=pay&tab=credits");
            assertThat(NotificationTarget.PAY_CREDITS.buildUrl("https://app.example?v=2"))
                    .isEqualTo("https://app.example?v=2&screen=pay&tab=credits");
        }

        @Test
        @DisplayName("HOME открывает приложение как есть")
        void homeKeepsUrlIntact() {
            assertThat(NotificationTarget.HOME.buildUrl("https://app.example"))
                    .isEqualTo("https://app.example");
        }
    }

    @Nested
    @DisplayName("Форматирование цен")
    class Formatting {

        @Test
        @DisplayName("копейки превращаются в рубли без лишних нулей")
        void formatsRubles() {
            Map<String, String> values = fullSnapshot();
            assertThat(values.get("{price_pack3}")).isEqualTo("99");
            assertThat(values.get("{price_lite}")).isEqualTo("599");
            assertThat(values.get("{price_lite_day}")).isEqualTo("20");
            assertThat(values.get("{credits_pack15_total}")).isEqualTo("18");
            assertThat(values.get("{bonus_pack15}")).isEqualTo("3");
        }

        @Test
        @DisplayName("знаки склоняются по числу")
        void pluralizesCredits() {
            Map<String, String> values = fullSnapshot();
            assertThat(values.get("{cost_dream_full}")).isEqualTo("3 знака");
            assertThat(values.get("{cost_year_full}")).isEqualTo("18 знаков");
            assertThat(values.get("{cost_month_full}")).isEqualTo("10 знаков");
        }
    }

    @Test
    @DisplayName("без активных планов продающие сообщения о подписке не отправляются")
    void skipsSubscriptionPromoWhenPlansUnavailable() {
        when(productRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(systemConfigService.getBooleanValue(anyString(), anyBoolean()))
                .thenReturn(false);
        when(featureCostService.getCost(any(SpreadType.class))).thenReturn(3);
        when(featureCostService.getDreamCost()).thenReturn(3);
        when(featureCostService.getNumerologyWeekCost()).thenReturn(3);
        when(featureCostService.getNumerologyMonthCost()).thenReturn(10);
        when(featureCostService.getNumerologyYearCost()).thenReturn(18);
        when(featureCostService.getCompatibilityUnlockCost()).thenReturn(3);

        Map<String, String> values = resolver.snapshot();

        assertThat(values).doesNotContainKey("{price_lite}");
        assertThat(NotificationPlaceholderResolver.apply("Lite за {price_lite} ₽", values)).isEmpty();
        // при этом сообщения без цен остаются отправляемыми
        assertThat(NotificationPlaceholderResolver.apply("Разбор сна за {cost_dream_full}", values))
                .contains("Разбор сна за 3 знака");
    }
}
