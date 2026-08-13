package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.SubscriptionAutorenewConsentLog;
import ru.sapa.gadalka_backend.domain.type.ConsentAction;

import java.util.List;
import java.util.Optional;

public interface SubscriptionAutorenewConsentLogRepository extends JpaRepository<SubscriptionAutorenewConsentLog, Long> {

    /** История согласий конкретного пользователя — для админки и разбора спорных списаний */
    List<SubscriptionAutorenewConsentLog> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Согласие, данное в рамках оформления конкретного платежа — нужно
     * SubscriptionActivationService, чтобы дозаполнить subscription_id после того,
     * как подписка наконец создана. Берём последнее по id: повторов быть не должно,
     * но при ретрае webhook'а лучше однозначная выборка, чем NonUniqueResultException.
     */
    Optional<SubscriptionAutorenewConsentLog> findFirstByPaymentIdAndActionOrderByIdDesc(Long paymentId,
                                                                                         ConsentAction action);
}
