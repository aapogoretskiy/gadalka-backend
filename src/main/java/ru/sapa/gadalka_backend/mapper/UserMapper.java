package ru.sapa.gadalka_backend.mapper;

import org.springframework.stereotype.Component;
import ru.sapa.gadalka_backend.api.dto.telegram.TelegramUserDto;
import ru.sapa.gadalka_backend.domain.User;

@Component
public class UserMapper {

    public TelegramUserDto toDto(User user) {
        if (user == null) {
            return null;
        }
        return TelegramUserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }
}
