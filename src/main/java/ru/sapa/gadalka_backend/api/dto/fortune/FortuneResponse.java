package ru.sapa.gadalka_backend.api.dto.fortune;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.sapa.gadalka_backend.api.dto.card.CardDto;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FortuneResponse {
    private String username;
    private List<CardDto> cards = new ArrayList<>();
    private String interpretation;
}
