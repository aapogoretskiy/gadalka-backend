package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Метод получения пользователя по telegramId
     * @param telegramId идентификатор пользователя в телеграмме
     * @return модель пользователя {@link User}
     */
    public User getUserByTelegramId(Long telegramId) {
        log.info("Try to receive user by telegram id: [{}]", telegramId);

        var user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new IllegalArgumentException(String.format("User not found by id: %d", telegramId)));
        log.info("Successfully found user: [{} {}. {}] by telegram id: [{}] with id: [{}]",
                user.getLastName(),
                user.getFirstName(),
                user.getUsername(),
                user.getTelegramId(),
                user.getId());

        return user;
    }

}
