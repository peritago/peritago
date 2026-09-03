package com.skala.domainbridge.glossary.entity;

import com.skala.domainbridge.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "glossaries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Glossary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String term;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String officialDefinition;

    @Column(nullable = false)
    private Long createdBy;

    @Builder
    public Glossary(String term, String officialDefinition, Long createdBy) {
        this.term = term;
        this.officialDefinition = officialDefinition;
        this.createdBy = createdBy;
    }
}
