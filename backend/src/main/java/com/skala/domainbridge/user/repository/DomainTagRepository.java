package com.skala.domainbridge.user.repository;

import com.skala.domainbridge.user.entity.DomainTag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainTagRepository extends JpaRepository<DomainTag, Long> {
}