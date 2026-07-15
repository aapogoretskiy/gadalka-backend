package ru.sapa.gadalka_backend.api.dto.dream;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.sapa.gadalka_backend.domain.type.SpendMode;
import ru.sapa.gadalka_backend.domain.type.SpendMode;

import java.util.List;

/**
 * Запрос на разбор сна.
 * Валидны три комбинации: только текст, только символы, текст + символы.
 * Полностью пустой запрос отклоняется в {@code DreamService} (кнопка на фронте
 * в этом случае неактивна, но защита на бэке обязательна).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DreamRequest {

    /** Текст сна. Лимит совпадает с counter'ом 0/1000 на экране ввода. */
    @Size(max = 1000, message = "Описание сна не должно превышать 1000 символов")
    private String dreamText;

    /** ID выбранных символов-чипов из справочника dream_symbols. */
    private List<Long> symbolIds;

    /**
     * Чем оплатить разбор: CREDITS (знаки) или QUOTA (квота подписки).
     * По умолчанию CREDITS — обратная совместимость со старым фронтом.
     */
    @NotNull(message = "Способ оплаты не может быть null")
    private SpendMode spendMode = SpendMode.CREDITS;
}
