package com.skala.domainbridge.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "user_domain_tags")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDomainTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_tag_id", nullable = false)
    private DomainTag domainTag;

    @Builder
    public UserDomainTag(User user, DomainTag domainTag) {
        this.user = user;
        this.domainTag = domainTag;
    }
}