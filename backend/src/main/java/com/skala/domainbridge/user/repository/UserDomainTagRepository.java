package com.skala.domainbridge.user.repository;

import com.skala.domainbridge.user.entity.UserDomainTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserDomainTagRepository extends JpaRepository<UserDomainTag, Long> {
    List<UserDomainTag> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}