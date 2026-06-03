package ru.sapa.gadalka_backend.referral;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.sapa.gadalka_backend.bot.GadalkaTelegramBot;
import ru.sapa.gadalka_backend.domain.ReferralEvent;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.CreditTransactionReason;
import ru.sapa.gadalka_backend.domain.type.ReferralEventType;
import ru.sapa.gadalka_backend.repository.ReferralEventRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;
import ru.sapa.gadalka_backend.service.FortuneCreditService;
import ru.sapa.gadalka_backend.service.ReferralService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты ReferralService.
 * Проверяют бизнес-логику записи реферальных событий без реальной БД.
 */
@ExtendWith(MockitoExtension.class)
class ReferralServiceTest {

    @Mock private ReferralEventRepository referralEventRepository;
    @Mock private UserRepository userRepository;
    @Mock private FortuneCreditService fortuneCreditService;
    @Mock private GadalkaTelegramBot telegramBot;

    private ReferralService service;

    @BeforeEach
    void setUp() {
        service = new ReferralService(
                referralEventRepository, userRepository, fortuneCreditService, telegramBot);
    }

    // ── recordBotEntry ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordBotEntry")
    class RecordBotEntry {

        @Test
        @DisplayName("Сохраняет событие BOT_ENTRY с верным кодом и telegramId")
        void savesEventWithCorrectFields() {
            service.recordBotEntry(123456L, "telegram_channel1");

            ArgumentCaptor<ReferralEvent> captor = ArgumentCaptor.forClass(ReferralEvent.class);
            verify(referralEventRepository).save(captor.capture());

            ReferralEvent saved = captor.getValue();
            assertThat(saved.getReferralCode()).isEqualTo("telegram_channel1");
            assertThat(saved.getTelegramId()).isEqualTo(123456L);
            assertThat(saved.getEventType()).isEqualTo(ReferralEventType.BOT_ENTRY);
            assertThat(saved.getUserId()).isNull();
            assertThat(saved.getIsNewUser()).isNull();
        }

        @Test
        @DisplayName("Пустой код — событие не сохраняется")
        void emptyCode_noSave() {
            service.recordBotEntry(123456L, "");
            verifyNoInteractions(referralEventRepository);
        }

        @Test
        @DisplayName("Null код — событие не сохраняется")
        void nullCode_noSave() {
            service.recordBotEntry(123456L, null);
            verifyNoInteractions(referralEventRepository);
        }

        @Test
        @DisplayName("Пробельный код — событие не сохраняется")
        void blankCode_noSave() {
            service.recordBotEntry(123456L, "   ");
            verifyNoInteractions(referralEventRepository);
        }
    }

    // ── recordAppOpen — маркетинговые коды ───────────────────────────────────

    @Nested
    @DisplayName("recordAppOpen — маркетинговые коды")
    class RecordAppOpen {

        @Test
        @DisplayName("Новый пользователь: событие APP_OPEN сохраняется, referralSource проставляется")
        void newUser_savesEventAndSetsReferralSource() {
            User user = User.builder().id(1L).telegramId(999L).referralSource(null).build();

            service.recordAppOpen(999L, user, true, "tiktok_video1");

            ArgumentCaptor<ReferralEvent> eventCaptor = ArgumentCaptor.forClass(ReferralEvent.class);
            verify(referralEventRepository).save(eventCaptor.capture());
            ReferralEvent event = eventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo(ReferralEventType.APP_OPEN);
            assertThat(event.getReferralCode()).isEqualTo("tiktok_video1");
            assertThat(event.getUserId()).isEqualTo(1L);
            assertThat(event.getIsNewUser()).isTrue();

            assertThat(user.getReferralSource()).isEqualTo("tiktok_video1");
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("Повторный пользователь: событие APP_OPEN сохраняется, referralSource НЕ меняется")
        void returningUser_savesEventButDoesNotOverwriteSource() {
            User user = User.builder().id(2L).telegramId(888L).referralSource(null).build();

            service.recordAppOpen(888L, user, false, "telegram_channel1");

            verify(referralEventRepository).save(any(ReferralEvent.class));
            assertThat(user.getReferralSource()).isNull();
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Новый пользователь с уже существующим referralSource: повторная запись не происходит")
        void newUserAlreadyHasSource_doesNotOverwrite() {
            User user = User.builder().id(3L).telegramId(777L).referralSource("old_source").build();

            service.recordAppOpen(777L, user, true, "new_source");

            verify(referralEventRepository).save(any(ReferralEvent.class));
            assertThat(user.getReferralSource()).isEqualTo("old_source");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Пустой start_param — ничего не сохраняется")
        void emptyCode_noSave() {
            User user = User.builder().id(4L).telegramId(666L).build();
            service.recordAppOpen(666L, user, true, "");
            verifyNoInteractions(referralEventRepository);
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("Null start_param — ничего не сохраняется")
        void nullCode_noSave() {
            User user = User.builder().id(5L).telegramId(555L).build();
            service.recordAppOpen(555L, user, true, null);
            verifyNoInteractions(referralEventRepository);
            verifyNoInteractions(userRepository);
        }
    }

    // ── recordAppOpen — user-to-user рефералы ────────────────────────────────

    @Nested
    @DisplayName("recordAppOpen — user-to-user рефералы (ref_<telegramId>)")
    class UserReferral {

        @Test
        @DisplayName("Новый пользователь по реф-ссылке: рефереру начисляются знаки и приходит уведомление")
        void newUser_referrerGetsReward() {
            User newUser   = User.builder().id(10L).telegramId(1001L).firstName("Иван").referralSource(null).build();
            User referrer  = User.builder().id(20L).telegramId(2002L).firstName("Анна").build();

            when(userRepository.findByTelegramId(2002L)).thenReturn(Optional.of(referrer));

            service.recordAppOpen(1001L, newUser, true, "ref_2002");

            // Знаки начислены рефереру
            verify(fortuneCreditService).grantCredits(
                    20L, ReferralService.REFERRAL_REWARD_CREDITS, CreditTransactionReason.REFERRAL_REWARD, null);

            // Уведомление в бот отправлено
            verify(telegramBot).sendReferralRewardNotification(
                    2002L, "Иван", ReferralService.REFERRAL_REWARD_CREDITS);

            // Сохранено событие USER_REFERRAL
            ArgumentCaptor<ReferralEvent> captor = ArgumentCaptor.forClass(ReferralEvent.class);
            verify(referralEventRepository, atLeast(2)).save(captor.capture());
            List<ReferralEvent> saved = captor.getAllValues();
            boolean hasUserReferral = saved.stream()
                    .anyMatch(e -> e.getEventType() == ReferralEventType.USER_REFERRAL
                            && e.getReferrerUserId().equals(20L));
            assertThat(hasUserReferral).isTrue();
        }

        @Test
        @DisplayName("Само-реферал: знаки НЕ начисляются")
        void selfReferral_noReward() {
            User user = User.builder().id(10L).telegramId(1001L).referralSource(null).build();

            service.recordAppOpen(1001L, user, true, "ref_1001");

            verifyNoInteractions(fortuneCreditService);
            verifyNoInteractions(telegramBot);
        }

        @Test
        @DisplayName("Реферер не найден в БД: знаки НЕ начисляются")
        void referrerNotFound_noReward() {
            User newUser = User.builder().id(10L).telegramId(1001L).referralSource(null).build();
            when(userRepository.findByTelegramId(9999L)).thenReturn(Optional.empty());

            service.recordAppOpen(1001L, newUser, true, "ref_9999");

            verifyNoInteractions(fortuneCreditService);
            verifyNoInteractions(telegramBot);
        }

        @Test
        @DisplayName("Повторный пользователь по реф-ссылке: знаки НЕ начисляются")
        void returningUser_noReward() {
            User user = User.builder().id(10L).telegramId(1001L).referralSource("ref_2002").build();

            service.recordAppOpen(1001L, user, false, "ref_2002");

            verifyNoInteractions(fortuneCreditService);
            verifyNoInteractions(telegramBot);
        }

        @Test
        @DisplayName("Некорректный суффикс кода: знаки НЕ начисляются")
        void malformedCode_noReward() {
            User newUser = User.builder().id(10L).telegramId(1001L).referralSource(null).build();

            service.recordAppOpen(1001L, newUser, true, "ref_not_a_number");

            verifyNoInteractions(fortuneCreditService);
            verifyNoInteractions(telegramBot);
        }
    }

    // ── buildReferralCode ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("buildReferralCode")
    class BuildReferralCode {

        @Test
        @DisplayName("Возвращает код в формате ref_<telegramId>")
        void returnsCorrectFormat() {
            assertThat(service.buildReferralCode(123456789L)).isEqualTo("ref_123456789");
        }
    }
}
