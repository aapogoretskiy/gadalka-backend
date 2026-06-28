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
import ru.sapa.gadalka_backend.repository.DailyHoroscopeRepository;
import ru.sapa.gadalka_backend.repository.DiaryRepository;
import ru.sapa.gadalka_backend.repository.UserProfileRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;
import ru.sapa.gadalka_backend.service.interpretation.AiInterpretationManager;
import ru.sapa.gadalka_backend.service.interpretation.HoroscopeContent;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static ru.sapa.gadalka_backend.constant.SystemConfigConstants.AI_PROVIDER;

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

    private static final ZoneId MOSCOW_ZONE = ZoneId.of("Europe/Moscow");

    private final UserProfileRepository userProfileRepository;
    private final DailyHoroscopeRepository horoscopeRepository;
    private final DiaryRepository diaryRepository;
    private final DiaryService diaryService;
    private final UserRepository userRepository;
    private final AiInterpretationManager interpretationManager;
    private final SystemConfigService systemConfigService;

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

        DailyHoroscope horoscope = resolveHoroscope(zodiacSign, today);
        DailyHoroscopeResponse response = toResponse(horoscope);

        recordDiaryEntryOnce(userId, horoscope, today);

        return response;
    }

    /**
     * Возвращает актуальный гороскоп для знака, генерируя его при необходимости.
     *
     * <p>Сначала читаем без блокировки — это покрывает почти все запросы в течение дня,
     * когда контент уже свежий. Если он устарел или отсутствует, переходим на
     * {@code PESSIMISTIC_WRITE}-блокировку строки знака и перепроверяем дату ещё раз —
     * это защищает от того, чтобы при одновременных запросах нескольких пользователей
     * с одним знаком AI был вызван больше одного раза в сутки.
     */
    private DailyHoroscope resolveHoroscope(ZodiacSign zodiacSign, LocalDate today) {
        Optional<DailyHoroscope> cached = horoscopeRepository.findByZodiacSign(zodiacSign);
        if (cached.isPresent() && today.equals(cached.get().getDate())) {
            return cached.get();
        }

        Optional<DailyHoroscope> locked = horoscopeRepository.findByZodiacSignForUpdate(zodiacSign);
        if (locked.isPresent() && today.equals(locked.get().getDate())) {
            // Другой запрос успел сгенерировать гороскоп, пока мы ждали блокировку — используем его.
            return locked.get();
        }

        log.info("Генерируем гороскоп на день: знак={}, дата={}", zodiacSign, today);
        String provider = systemConfigService.getValue(AI_PROVIDER);
        HoroscopeContent content = interpretationManager.interpretDailyHoroscope(provider, zodiacSign, today);

        DailyHoroscope horoscope = locked.orElseGet(() -> DailyHoroscope.builder().zodiacSign(zodiacSign).build());
        horoscope.setDate(today);
        horoscope.setGeneralText(content.general());
        horoscope.setAdviceText(content.advice());
        horoscope.setLoveText(content.love());
        horoscope.setCareerText(content.career());
        horoscope.setMoneyText(content.money());
        horoscope.setGeneralScore(content.generalScore());
        horoscope.setLoveScore(content.loveScore());
        horoscope.setCareerScore(content.careerScore());
        horoscope.setMoneyScore(content.moneyScore());

        return horoscopeRepository.save(horoscope);
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
                zodiacSign.getStone()
        );
    }
}
