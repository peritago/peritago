package com.skala.domainbridge.translate.entity;

/**
 * 답변 근거의 출처. Glossary → 위키 벡터 검색 → 일반 지식 순 폴백 결과를 기록한다.
 */
public enum SourceType {

    /** 은어 사전(Glossary) Exact Match */
    GLOSSARY,

    /** 사내 위키 벡터 검색(RAG) 결과 */
    WIKI,

    /** 사내 근거 없이 LLM 일반 지식으로 생성 */
    GENERAL;

    /** GENERAL이면 화면에 "사내 기준 아님"을 표시해야 한다. */
    public boolean isOutsideCompanyStandard() {
        return this == GENERAL;
    }
}
