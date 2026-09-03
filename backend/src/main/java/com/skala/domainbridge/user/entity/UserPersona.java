package com.skala.domainbridge.user.entity;

import com.skala.domainbridge.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "user_persona")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPersona extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(columnDefinition = "TEXT")
    private String personaDescription;

    @Enumerated(EnumType.STRING)
    private ExplanationLength officialDefLength;

    @Enumerated(EnumType.STRING)
    private ExplanationLength personalizedExpLength;

    @Builder
    public UserPersona(User user, String personaDescription,
                       ExplanationLength officialDefLength, ExplanationLength personalizedExpLength) {
        this.user = user;
        this.personaDescription = personaDescription;
        this.officialDefLength = officialDefLength;
        this.personalizedExpLength = personalizedExpLength;
    }

    public void update(String personaDescription, ExplanationLength officialDefLength,
                       ExplanationLength personalizedExpLength) {
        this.personaDescription = personaDescription;
        this.officialDefLength = officialDefLength;
        this.personalizedExpLength = personalizedExpLength;
    }
}