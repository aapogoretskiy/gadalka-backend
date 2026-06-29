package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.sapa.gadalka_backend.api.dto.horoscope.DailyHoroscopeResponse;
import ru.sapa.gadalka_backend.domain.DailyHoroscope;
import ru.sapa.gadalka_backend.domain.UserProfile;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.domain.type.ZodiacSign;
import ru.sapa.gadalka_backend.repository.DiaryRepository;
import ru.sapa.gadalka_backend.repository.UserProfileRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Гороскоп на день.
 *
 * <p>Контент общий на знак зодиака (12 знаков = 12 строк в {@code daily_horoscopes}),
 * а не на пользователя — это и держит количество вызовов AI в пределах 12 в сутки.
 * Сутки считаются по московскому времени (см. {@link #MOSCOW_ZONE}), независимо
 * от часового пояса пользователя — договорённость зафиксирована при проектировании фичи.
 *
 * <p>Бесплатная фича — кредиты не списываются (как и "Карта дня").
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoroscopeService {

    static final ZoneId MOSCOW_ZONE = ZoneId.of("Europe/Moscow");

    private final UserProfileRepository userProfileRepository;
    private final DiaryRepository diaryRepository;
    private final DiaryService diaryService;
    private final UserRepository userRepository;
    private final HoroscopeGenerationService horoscopeGenerationService;

    @Transactional
    public DailyHoroscopeResponse getDailyHoroscope(Long userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Не найден профиль пользователя при попытке получения/создания гороскопа"));

        if (profile.getBirthDate() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Для гороскопа на день необходимо указать дату рождения в профиле");
        }

        ZodiacSign zodiacSign = ZodiacSign.fromDate(profile.getBirthDate());
        LocalDate today = LocalDate.now(MOSCOW_ZONE);

        DailyHoroscope horoscope = horoscopeGenerationService.resolveHoroscope(zodiacSign, today);
        DailyHoroscopeResponse response = toResponse(horoscope);

        recordDiaryEntryOnce(userId, horoscope, today);

        return response;
    }

    /**
     * Пишет запись в дневник пользователя не чаще одного раза за сутки (по МСК).
     * Нужна отдельная проверка, потому что сам гороскоп кэшируется на знак, а не на пользователя,
     * и не подскажет сам, видел ли этот конкретный пользователь его сегодня.
     */
    private void recordDiaryEntryOnce(Long userId, DailyHoroscope horoscope, LocalDate today) {
        OffsetDateTime from = today.atStartOfDay(MOSCOW_ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime to = today.plusDays(1).atStartOfDay(MOSCOW_ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);

        boolean alreadyRecorded = diaryRepository.existsByUserIdAndFeatureTypeAndCreatedAtBetween(
                userId, DiaryFeatureType.DAILY_HOROSCOPE, from, to);
        if (alreadyRecorded) {
            return;
        }

        DailyHoroscopeResponse snapshot = toResponse(horoscope);
        diaryService.save(userId, DiaryFeatureType.DAILY_HOROSCOPE, horoscope.getId(), snapshot);
        userRepository.incrementActionsCount(userId);
    }

    private DailyHoroscopeResponse toResponse(DailyHoroscope horoscope) {
        ZodiacSign zodiacSign = horoscope.getZodiacSign();
        boolean stale = !LocalDate.now(MOSCOW_ZONE).equals(horoscope.getDate());
        return new DailyHoroscopeResponse(
                horoscope.getDate(),
                zodiacSign.getDisplayName(),
                zodiacSign.getPeriodLabel(),
                horoscope.getGeneralScore(),
                horoscope.getLoveScore(),
                horoscope.getCareerScore(),
                horoscope.getMoneyScore(),
                horoscope.getGeneralText(),
                horoscope.getAdviceText(),
                horoscope.getLoveText(),
                horoscope.getCareerText(),
                horoscope.getMoneyText(),
                zodiacSign.getLuckyNumbers(),
                zodiacSign.getLuckyColors(),
                zodiacSign.getStone(),
                stale
        );
    }
}
