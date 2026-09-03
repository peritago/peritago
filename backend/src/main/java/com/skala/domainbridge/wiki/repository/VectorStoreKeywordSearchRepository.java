package com.skala.domainbridge.wiki.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Hybrid 검색의 키워드 절반 — pg_bigm(문자 2-gram 유사도) 기반.
 *
 * Spring AI의 PgVectorStore는 벡터 유사도 검색만 제공하고 키워드 검색은 없어서, 같은 vector_store
 * 테이블(Spring AI가 스키마를 관리하는 그 테이블)에 직접 SQL로 접근해 보완한다. 정확한 문자열
 * (부품 코드명, 스펙 번호 등)은 임베딩이 의미로 잘 못 잡아내는 대표 케이스라, 벡터 검색만으로는
 * 놓치기 쉽다 — WikiService가 이 결과를 벡터 검색 결과와 RRF로 합친다.
 *
 * Postgres 기본 전문 검색(to_tsvector('simple', ...))은 띄어쓰기로만 토큰을 나누는데, 한국어는
 * 조사가 단어에 바로 붙어서("포토공정은") 검색어("포토공정")와 토큰 자체가 달라져 매치가 실패한다
 * (실측 확인됨). pg_bigm은 띄어쓰기/형태소 경계와 무관하게 2글자 단위로 겹치는 정도를 재기 때문에
 * 이 문제가 없다 — "왜 mecab-ko 대신 pg_bigm인가" 문서 참고.
 *
 * gin_bigm_ops GIN 인덱스가 있어야 성능이 나온다 (db-setup.sql 참고, 최초 1회 수동 실행 필요 —
 * PgVectorStore가 관리하는 스키마 밖의 추가 인덱스라 자동 생성되지 않는다). pg_bigm 확장 자체도
 * CREATE EXTENSION pg_bigm이 먼저 되어 있어야 한다(docker/postgres-pgbigm 이미지 사용 전제).
 */
@Repository
public class VectorStoreKeywordSearchRepository {

	private final JdbcTemplate jdbcTemplate;

	public VectorStoreKeywordSearchRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * @param minSimilarity `sim > 0`(조금이라도 겹치면 통과)로는 부족하다 — 실측 확인: "포토공정"
	 *                      질의어가 정답 문서와는 0.051, "차체 도장 공정"처럼 "공정"이라는 흔한
	 *                      글자만 겹치는 무관한 문서와도 0.011이 나와서 그냥 통과해버렸다. 호출부가
	 *                      실제 임계값을 넘겨야 한다(WikiService 참고).
	 */
	public List<KeywordMatch> search(String query, String industry, int limit, double minSimilarity) {
		boolean filterByIndustry = StringUtils.hasText(industry);

		String sql = """
				SELECT id, content, source_url
				FROM (
					SELECT id, content, metadata->>'sourceUrl' AS source_url,
					       bigm_similarity(content, ?) AS sim
					FROM vector_store
					%s
				) matches
				WHERE sim > ?
				ORDER BY sim DESC
				LIMIT ?
				"""
				.formatted(filterByIndustry ? "WHERE metadata->>'industry' = ?" : "");

		Object[] params = filterByIndustry
				? new Object[]{query, industry, minSimilarity, limit}
				: new Object[]{query, minSimilarity, limit};

		return jdbcTemplate.query(sql, (resultSet, rowNum) -> new KeywordMatch(
				resultSet.getString("id"),
				resultSet.getString("content"),
				resultSet.getString("source_url")
		), params);
	}

	public record KeywordMatch(String id, String content, String sourceUrl) {
	}
}
