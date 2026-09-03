-- WIKI_SPEC_1.md 3장 "DB 준비" 참고. Spring Boot가 시작하며 자동 실행하는 파일이 아니라
-- 로컬/운영 DB에 최초 1회 직접 실행하는 참고용 스크립트다.
--
-- pg_bigm 확장이 필요하므로 반드시 docker/postgres-pgbigm 이미지(또는 pg_bigm이 설치된 Postgres)를
-- 써야 한다 — 기본 pgvector/pgvector 이미지에는 없다.

-- pgvector 확장 활성화 (vector_store 테이블이 VECTOR 타입 컬럼을 쓰기 위해 필수)
CREATE EXTENSION IF NOT EXISTS vector;

-- pg_bigm 활성화 — 한국어 키워드 검색용(문자 2-gram 유사도). "왜 mecab-ko 대신 pg_bigm인가" 참고.
CREATE EXTENSION IF NOT EXISTS pg_bigm;

-- wiki_documents 테이블은 spring.jpa.hibernate.ddl-auto=update 설정으로
-- WikiDocument 엔티티 기준으로 애플리케이션 기동 시 자동 생성된다 (개발 환경 한정).
-- vector_store 테이블은 spring.ai.vectorstore.pgvector.initialize-schema=true 설정으로
-- PgVectorStore가 기동 시 자동 생성한다. 둘 다 직접 DDL을 작성할 필요 없음.

-- ↓ 아래는 애플리케이션을 최초 1회 기동해서 vector_store 테이블이 생긴 "이후"에 실행할 것.
-- Hybrid 검색의 키워드(pg_bigm) 절반이 쓰는 GIN 인덱스. vector_store는 PgVectorStore가 관리하는
-- 스키마라 이 인덱스는 자동으로 안 생긴다 (VectorStoreKeywordSearchRepository 참고).
CREATE INDEX IF NOT EXISTS vector_store_content_bigm_idx
  ON vector_store USING GIN (content gin_bigm_ops);
