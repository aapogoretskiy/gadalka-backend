package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.ActionFeedback;
import ru.sapa.gadalka_backend.domain.type.FeedbackTargetType;

import java.util.List;
import java.util.Optional;

public interface ActionFeedbackRepository extends JpaRepository<ActionFeedback, Long> {

    /** Фидбэк конкретного пользователя на конкретное действие */
    Optional<ActionFeedback> findByUserIdAndActionTypeAndActionId(Long userId, FeedbackTargetType actionType, Long actionId);

    /** Все фидбэки по набору action_id одного типа — для батчевой загрузки в истории */
    List<ActionFeedback> findByActionTypeAndActionIdIn(FeedbackTargetType actionType, List<Long> actionIds);
}
