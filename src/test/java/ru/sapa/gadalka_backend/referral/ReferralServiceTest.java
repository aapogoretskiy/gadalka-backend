package ru.sapa.gadalka_backend.referral;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.sapa.gadalka_backend.domain.ReferralEvent;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.ReferralEventType;
import ru.sapa.gadalka_backend.repository.ReferralEventRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;
import ru.sapa.gadalka_backend.service.ReferralService;

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

    private ReferralService service;

    @BeforeEach
    void setUp() {
        service = new ReferralService(referralEventRepository, userRepository);
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
            assertThat(saved.getUserId()).isNull();       // userId ещё не известен
            assertThat(saved.getIsNewUser()).isNull();    // тоже неизвестно
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

    // ── recordAppOpen ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordAppOpen")
    class RecordAppOpen {

        @Test
        @DisplayName("Новый пользователь: событие APP_OPEN сохраняется, referralSource проставляется")
        void newUser_savesEventAndSetsReferralSource() {
            User user = User.builder().id(1L).telegramId(999L).referralSource(null).build();

            service.recordAppOpen(999L, user, true, "tiktok_video1");

            // Событие записано
            ArgumentCaptor<ReferralEvent> eventCaptor = ArgumentCaptor.forClass(ReferralEvent.class);
            verify(referralEventRepository).save(eventCaptor.capture());
            ReferralEvent event = eventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo(ReferralEventType.APP_OPEN);
            assertThat(event.getReferralCode()).isEqualTo("tiktok_video1");
            assertThat(event.getUserId()).isEqualTo(1L);
            assertThat(event.getIsNewUser()).isTrue();

            // referralSource проставлен на пользователе и пользователь сохранён
            assertThat(user.getReferralSource()).isEqualTo("tiktok_video1");
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("Повторный пользователь: событие APP_OPEN сохраняется, referralSource НЕ меняется")
        void returningUser_savesEventButDoesNotOverwriteSource() {
            User user = User.builder().id(2L).telegramId(888L).referralSource(null).build();

            service.recordAppOpen(888L, user, false, "telegram_channel1");

            // Событие записано
            verify(referralEventRepository).save(any(ReferralEvent.class));

            // referralSource не тронут, пользователь не сохранён
            assertThat(user.getReferralSource()).isNull();
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Новый пользователь с уже существующим referralSource: повторная запись не происходит")
        void newUserAlreadyHasSource_doesNotOverwrite() {
            User user = User.builder().id(3L).telegramId(777L).referralSource("old_source").build();

            service.recordAppOpen(777L, user, true, "new_source");

            // Событие записано
            verify(referralEventRepository).save(any(ReferralEvent.class));

            // referralSource НЕ перезаписан (был уже задан)
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
}
