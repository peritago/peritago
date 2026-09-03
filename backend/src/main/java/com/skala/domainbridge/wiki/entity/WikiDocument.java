package com.skala.domainbridge.wiki.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 관리자 화면용 메타데이터만 담당 (제목/산업분류/출처/등록일).
 * 실제 청크·임베딩은 Spring AI VectorStore가 관리하는 vector_store 테이블에 저장된다.
 * WIKI_SPEC_1.md 3장 참고.
 *
 * industry(반도체/통신/자동차 등 산업 분류)는 타 도메인 비유 설명(F-06 개인화 설명) 시,
 * 사용자 자신의 산업에서 비교 가능한 개념을 wiki에서 별도로 검색해 grounding하기 위한 필드다.
 * "domain"이라는 이름을 쓰지 않은 이유: 이 프로젝트에서 domain은 이미 wiki/translate/glossary
 * 같은 서비스 모듈(bounded context)을 가리키는 말이라, 산업 분류에 같은 단어를 쓰면 혼동된다.
 */
@Entity
@Table(name = "wiki_documents")
public class WikiDocument {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, length = 50)
	private String industry;

	@Column(name = "source_url", columnDefinition = "TEXT")
	private String sourceUrl;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected WikiDocument() {
	}

	public WikiDocument(String title, String industry, String sourceUrl) {
		this.title = title;
		this.industry = industry;
		this.sourceUrl = sourceUrl;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getIndustry() {
		return industry;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
