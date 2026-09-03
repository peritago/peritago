package com.skala.domainbridge.wiki.repository;

import com.skala.domainbridge.wiki.entity.WikiDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WikiDocumentRepository extends JpaRepository<WikiDocument, Long> {
}
