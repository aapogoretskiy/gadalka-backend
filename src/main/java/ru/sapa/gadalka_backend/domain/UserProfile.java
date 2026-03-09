package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.sapa.gadalka_backend.api.dto.profile.Goal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "user_profile")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    private LocalDate birthDate;

    private LocalTime birthTime;

    private String birthCity;

    @ElementCollection(targetClass = Goal.class)
    @CollectionTable(
            name = "user_profile_goals",
            joinColumns = @JoinColumn(name = "user_profile_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "goal")
    private Set<Goal> goals = new HashSet<>();
}
