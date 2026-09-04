# DomainBridge Backend

사내 낯선 용어(줄임말/은어/타 도메인 전문용어)를 **공식 정의 + 개인화 설명(타 도메인 비유)** 2파트로
번역해주는 서비스의 백엔드. API 명세는 `/API.yml`(OpenAPI) 참고.

## 1. 사전 준비

- JDK 21
- Docker (Postgres/Redis 컨테이너용)
- (선택) OpenAI API Key — 없어도 Mock으로 전부 동작함

## 2. 빠른 시작 (Mock 모드 — 키 없이 5분 안에 실행)

```bash
# 1) 프로젝트 루트에서 인프라 기동 (Postgres + Redis)
docker compose up -d

# 2) 백엔드 기동
cd backend
./gradlew bootRun
```

`backend/.env`가 없거나 비어 있으면 자동으로 **전부 Mock 모드**로 뜬다 (OpenAI 호출 0건):

| 스위치                              | 기본값                 | 의미                                                                         |
| ----------------------------------- | ---------------------- | ---------------------------------------------------------------------------- |
| `peritago.translate.mock.wiki`      | `true`(미설정 시 기본) | 위키 근거 검색 결과를 `WikiEvidenceFinder`의 고정 데이터(`MOCK_DOCS`)로 반환 |
| `peritago.translate.mock.generator` | `true`(미설정 시 기본) | 공식 정의/개인화 설명을 규칙 기반으로 조합 (`MockTranslationGenerator`)      |

기동 시 `data.sql`이 아래 데모 계정을 자동 생성한다 (이미 있으면 스킵, 멱등):

| 계정      | 이메일                   | 비밀번호     | 비고                           |
| --------- | ------------------------ | ------------ | ------------------------------ |
| 관리자    | `admin@domainbridge.dev` | `Admin1234!` | role=ADMIN                     |
| 데모 유저 | `demo@domainbridge.dev`  | `Demo1234!`  | 페르소나=반도체 사전 설정 완료 |

이 상태로 `demo` 계정 로그인 → "MSA" 질의하면 `sourceType: WIKI` + 반도체 비유 응답이 바로 나온다
(코드에 내장된 MSA 샘플 데이터 사용, DB 등록 불필요).

## 3. 실제 OpenAI 연동으로 확인하기

`backend/.env`에 아래 3줄 추가 (`.env`는 `.gitignore`에 있어 커밋되지 않음):

```
OPENAI_API_KEY=
peritago.translate.mock.wiki=false
peritago.translate.mock.generator=false
```

서버 재기동 후, **실제 위키 문서를 등록**해야 한다 (Mock 데이터는 실모드에선 안 쓰이므로, 등록 안 하면
근거가 하나도 없어 전부 `sourceType: GENERAL`로 빠진다). admin 계정으로 로그인해서 등록:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@domainbridge.dev","password":"Admin1234!"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")

curl -X POST http://localhost:8080/api/wiki/admin \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "title": "아키텍처 가이드 - MSA",
    "content": "MSA(Microservice Architecture)는 ...",
    "industry": "개발",
    "sourceUrl": "wiki://architecture-guide/msa"
  }'
```

문서 1건 등록 = OpenAI 임베딩(`text-embedding-3-small`) 1회 호출 (비용 미미).
프론트에는 아직 이 등록 화면이 없어서(관리자 로그인해도 UI 없음), 지금은 API로 직접 등록해야 한다.

### 위키 문서 등록(`POST /api/wiki/admin`)이 403

ADMIN 역할만 가능 (`SecurityConfig`의 `/api/wiki/admin/**` → `hasRole("ADMIN")`). `admin@domainbridge.dev`로 로그인했는지 확인.

## 4. 계정 요약

| 계정      | 이메일                   | 비밀번호     | 용도                                                                                     |
| --------- | ------------------------ | ------------ | ---------------------------------------------------------------------------------------- |
| 관리자    | `admin@domainbridge.dev` | `Admin1234!` | 위키/은어 사전 등록 (`/api/wiki/admin`, `/api/glossary/admin`) — 프론트 UI 없음, API로만 |
| 일반 유저 | `demo@domainbridge.dev`  | `Demo1234!`  | 실제 서비스 시나리오 테스트 (프론트에서 바로 로그인해서 쓰면 됨)                         |
