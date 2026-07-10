package ru.sapa.gadalka_backend.domain.type;

/**
 * Цветовая метка "рейтинга склонности к чувствительным вопросам" для админки.
 * RED выставляется либо по превышению порога процента, либо принудительно —
 * при наличии хотя бы одного вопроса категории {@link SensitiveContentCategory#SELF_HARM_SUICIDE}
 * (override, независимо от процента: тут важна не статистика, а внимание к конкретному человеку).
 */
public enum RiskLevel {
    GREEN,
    YELLOW,
    RED
}
