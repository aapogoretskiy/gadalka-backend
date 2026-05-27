package ru.sapa.gadalka_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Защита от prompt injection атак.
 * Проверяет пользовательский ввод перед отправкой в AI на наличие паттернов,
 * которые могут использоваться для манипуляции поведением языковой модели.
 *
 * <p>При обнаружении подозрительного ввода:
 * <ul>
 *   <li>Логирует попытку с userId и первыми символами вопроса (без полного текста в prod-логах)</li>
 *   <li>Бросает IllegalArgumentException с нейтральным сообщением для пользователя</li>
 * </ul>
 */
@Slf4j
@Service
public class PromptInjectionFilterService {

    private static final String REJECTION_MESSAGE = "Карты не могут ответить на этот вопрос. Попробуйте переформулировать";

    /**
     * Паттерны prompt injection на русском и английском языках.
     * Каждый паттерн сопровождается комментарием — что именно ловит.
     */
    private static final List<InjectionPattern> INJECTION_PATTERNS = List.of(

            new InjectionPattern(
                    Pattern.compile("(?i)(игнорируй|игнорируйте|ignore|забудь|забудьте|forget|disregard|не\\s+следуй|не\\s+следуйте).{0,40}(инструкци|правил|промпт|system|роль|задан|instruction|rule|prompt)"),
                    "игнорирование инструкций"
            ),

            new InjectionPattern(
                    Pattern.compile("(?i)(ты\\s+теперь|ты\\s+сейчас|притворись|притворитесь|веди\\s+себя|act\\s+as|pretend|you\\s+are\\s+now|roleplay|ролевая)"),
                    "смена роли AI"
            ),

            // "системный промпт / system prompt / jailbreak"
            new InjectionPattern(
                    Pattern.compile("(?i)(системный\\s+промпт|system\\s+prompt|jailbreak|джейлбрейк|DAN\\b|do\\s+anything\\s+now)"),
                    "упоминание системного промпта или jailbreak"
            ),

            // "обойди / override / bypass ... ограничение / фильтр / защит"
            new InjectionPattern(
                    Pattern.compile("(?i)(обойди|обойдите|override|bypass|circumvent|отключи|отключите|disable).{0,30}(ограничени|фильтр|защит|limit|filter|restrict|censor|safeguard)"),
                    "попытка обойти ограничения"
            ),

            // "предыдущие инструкции / previous instructions"
            new InjectionPattern(
                    Pattern.compile("(?i)(предыдущ.{0,10}инструкци|previous\\s+instruction|above\\s+instruction|earlier\\s+instruction)"),
                    "ссылка на предыдущие инструкции"
            )
    );

    /**
     * Проверяет текст на наличие признаков prompt injection.
     *
     * @param text    входящий текст вопроса пользователя
     * @param userId  идентификатор пользователя — используется только для логирования
     * @throws IllegalArgumentException если обнаружена попытка инъекции
     */
    public void validate(String text, Long userId) {
        if (text == null || text.isBlank()) return;

        for (InjectionPattern injectionPattern : INJECTION_PATTERNS) {
            if (injectionPattern.pattern().matcher(text).find()) {
                String preview = text.length() > 80 ? text.substring(0, 80) + "…" : text;
                log.warn("Обнаружена попытка prompt injection: userId={}, тип='{}', начало вопроса='{}'", userId, injectionPattern.description(), preview);
                throw new IllegalArgumentException(REJECTION_MESSAGE);
            }
        }
    }

    /**
     * Вспомогательный record для хранения паттерна вместе с описанием для логов.
     */
    private record InjectionPattern(Pattern pattern, String description) {}
}
