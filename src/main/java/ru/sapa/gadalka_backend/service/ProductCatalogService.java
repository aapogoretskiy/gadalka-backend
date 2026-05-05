package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.sapa.gadalka_backend.domain.PaymentProduct;
import ru.sapa.gadalka_backend.exception.ProductNotFoundException;
import ru.sapa.gadalka_backend.repository.PaymentProductRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCatalogService {

    private final PaymentProductRepository productRepository;

    /**
     * Возвращает все активные продукты, отсортированные по sort_order.
     * Используется для отображения каталога на фронте.
     */
    public List<PaymentProduct> getActiveProducts() {
        return productRepository.findAllByIsActiveTrueOrderBySortOrderAsc();
    }

    /**
     * Находит продукт по коду. Кидает исключение если не найден или неактивен.
     */
    public PaymentProduct getActiveProduct(String code) {
        PaymentProduct product = productRepository.findByCode(code)
                .orElseThrow(() -> new ProductNotFoundException(code));

        if (!product.getIsActive()) {
            log.warn("Попытка купить неактивный продукт: code={}", code);
            throw new ProductNotFoundException(code);
        }

        return product;
    }
}
