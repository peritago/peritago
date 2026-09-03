package com.skala.domainbridge.context.dto.response;

/**
 * 대화에서 자동 감지된 사내 은어 (F-11).
 *
 * 공식 정의를 함께 내려주어 프론트가 추가 호출 없이 하이라이트/툴팁을 띄울 수 있게 한다.
 * 페르소나 눈높이 설명까지 필요하면 사용자가 그 용어로 POST /api/translate 를 호출하면 된다.
 */
public record DetectedTermDto(
        Long glossaryId,
        String term,
        String officialDefinition
) {}
