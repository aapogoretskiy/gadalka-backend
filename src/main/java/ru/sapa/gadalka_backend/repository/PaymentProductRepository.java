package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.PaymentProduct;

import java.util.List;
import java.util.Optional;

public interface PaymentProductRepository extends JpaRepository<PaymentProduct, Long> {

    Optional<PaymentProduct> findByCode(String code);

    List<PaymentProduct> findAllByIsActiveTrueOrderBySortOrderAsc();
}
