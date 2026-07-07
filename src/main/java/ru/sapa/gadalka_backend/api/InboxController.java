package ru.sapa.gadalka_backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.sapa.gadalka_backend.api.dto.inbox.InboxMessageDto;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.service.InboxService;

import java.util.Map;

/**
 * "Входящие" — сообщения от администратора внутри приложения (вкладка Профиль).
 * Второй, гарантированный канал доставки — не зависит от прав бота слать в Telegram
 * (см. миграцию V60 и комментарий в {@code User.notificationsAllowed}).
 */
@RestController
@RequestMapping("/api/inbox")
@RequiredArgsConstructor
@Tag(name = "Inbox", description = "Входящие сообщения от администратора внутри приложения")
public class InboxController extends BaseController {

    private final InboxService inboxService;

    @GetMapping
    @Operation(summary = "Список сообщений во Входящих текущего пользователя")
    public ResponseEntity<Page<InboxMessageDto>> getInbox(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        User user = resolveUser(request);
        return ResponseEntity.ok(inboxService.listForUser(user.getId(), PageRequest.of(page, size)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Количество непрочитанных сообщений — для шилдика на кнопке в Profile")
    public ResponseEntity<Map<String, Long>> getUnreadCount(HttpServletRequest request) {
        User user = resolveUser(request);
        return ResponseEntity.ok(Map.of("unreadCount", inboxService.countUnread(user.getId())));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Отметить все сообщения прочитанными (вызывается при заходе на вкладку)")
    public ResponseEntity<Void> markAllRead(HttpServletRequest request) {
        User user = resolveUser(request);
        inboxService.markAllRead(user.getId());
        return ResponseEntity.ok().build();
    }
}
