package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.UserSensitivityProfile;
import ru.sapa.gadalka_backend.domain.type.RiskLevel;

public interface UserSensitivityProfileRepository extends JpaRepository<UserSensitivityProfile, Long> {

    /** Для админки: сначала самые "красные", внутри уровня — по проценту */
    Page<UserSensitivityProfile> findByRiskLevelOrderBySensitivePercentageDesc(RiskLevel riskLevel, Pageable pageable);

    Page<UserSensitivityProfile> findAllByOrderBySensitivePercentageDesc(Pageable pageable);
}
