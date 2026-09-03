package com.skala.domainbridge.glossary.repository;

import com.skala.domainbridge.glossary.entity.Glossary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GlossaryRepository extends JpaRepository<Glossary, Long> {
    Optional<Glossary> findByTerm(String term);
    boolean existsByTerm(String term);
}
