package ru.sapa.gadalka_backend.api.dto.card;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class CardDto {
    private Long id;
    private String name;
    private String meaning;
    private CardPosition cardPosition;
    private String interpretation;
}
