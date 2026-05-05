package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.Payment;
import ru.sapa.gadalka_backend.domain.type.PaymentStatus;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    List<Payment> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByProviderPaymentIdAndStatus(String providerPaymentId, PaymentStatus status);
}
