/**
 * 개발용 Glossary 시드 + 매칭/감지 알고리즘.
 *
 * 도메인 값은 백엔드 data.sql의 domain_tags(개발·기획·디자인·영업·경영·기타)를 씁니다.
 * 서버 Glossary에 도메인 태그가 붙으면 이 시드는 지워도 됩니다.
 */
export const GLOSSARY = [
  {
    term: '컨테이너',
    aliases: ['container', '도커 컨테이너'],
    domains: ['개발'],
    subdomain: 'DEVOPS',
    definition:
      '애플리케이션과 그 실행에 필요한 라이브러리·설정을 하나로 묶어, 호스트 OS로부터 격리된 상태로 실행하는 가상화 단위. 이미지로 배포되며 동일한 실행 결과를 보장한다.',
    source: '사내 Glossary · DevOps 표준용어집 v3',
    evidenceType: 'glossary',
  },
  {
    term: '오케스트레이션',
    aliases: ['orchestration'],
    domains: ['개발'],
    subdomain: 'DEVOPS',
    definition: '여러 컨테이너의 배치·확장·복구·네트워크 연결을 자동으로 조정하는 관리 계층.',
    source: '사내 Glossary · DevOps 표준용어집 v3',
    evidenceType: 'glossary',
  },
  {
    term: '롤아웃',
    aliases: ['rollout', '롤 아웃'],
    domains: ['개발'],
    subdomain: 'DEVOPS',
    definition: '새 버전을 운영 환경의 인스턴스에 점진적으로 배포하는 절차.',
    source: '사내 Glossary · 배포 운영 가이드',
    evidenceType: 'glossary',
  },
  {
    term: '스테이징',
    aliases: ['staging'],
    domains: ['개발'],
    subdomain: 'DEVOPS',
    definition: '운영과 동일한 구성으로 배포 전 검증을 수행하는 사전 환경.',
    source: '사내 Glossary · 배포 운영 가이드',
    evidenceType: 'glossary',
  },
  {
    term: '레거시',
    aliases: ['legacy', '레거시 시스템'],
    domains: ['개발'],
    subdomain: 'IT',
    definition: '오래돼 유지보수만 이어가는 기존 시스템.',
    source: '사내 위키 · 인프라 현대화 로드맵',
    evidenceType: 'wiki',
  },
  {
    term: '프록시',
    aliases: ['proxy'],
    domains: ['개발'],
    subdomain: 'IT',
    definition: '클라이언트와 서버 사이에서 요청을 중계하는 서버.',
    source: '사내 위키 · 네트워크 구성도',
    evidenceType: 'wiki',
  },
  {
    term: '스프린트',
    aliases: ['sprint'],
    domains: ['개발', '기획'],
    subdomain: 'AGILE',
    definition: '2주 내외의 고정 기간 단위로 개발 범위를 끊어 진행하는 반복 주기.',
    source: '사내 Glossary · 애자일 운영 표준',
    evidenceType: 'glossary',
  },
  {
    term: '벨로시티',
    aliases: ['velocity'],
    domains: ['기획'],
    subdomain: 'AGILE',
    definition: '한 스프린트에서 실제로 완료한 작업량을 나타내는 지표.',
    source: null,
    evidenceType: 'general_knowledge',
  },
  {
    term: '와이어프레임',
    aliases: ['wireframe'],
    domains: ['디자인'],
    subdomain: 'UX',
    definition: '화면의 구조와 정보 배치를 선과 상자로만 표현한 저해상도 설계도.',
    source: '사내 Glossary · 디자인 시스템 문서',
    evidenceType: 'glossary',
  },
  {
    term: '리드',
    aliases: ['lead'],
    domains: ['영업'],
    subdomain: 'SALES',
    definition: '제품에 관심을 보여 후속 영업 활동의 대상이 되는 잠재 고객.',
    source: '사내 Glossary · 영업 프로세스 안내',
    evidenceType: 'glossary',
  },
  {
    term: '런웨이',
    aliases: ['runway'],
    domains: ['경영'],
    subdomain: 'FINANCE',
    definition: '현재 현금과 소진 속도를 기준으로 회사가 버틸 수 있는 남은 기간.',
    source: '사내 Glossary · 재무 용어',
    evidenceType: 'glossary',
  },
]

/** [{needle, entry}]로 펼치고 긴 표현부터 매칭되도록 정렬합니다. */
export function buildIndex(entries = GLOSSARY) {
  return entries
    .flatMap((entry) => [entry.term, ...(entry.aliases ?? [])].map((needle) => ({ needle, entry })))
    .sort((a, b) => b.needle.length - a.needle.length)
}

const DEFAULT_INDEX = buildIndex()

/** UC-05: 완전 일치 → 별칭·정규화 표현 비교. */
export function matchIn(index, raw) {
  const term = normalize(raw)
  if (!term) return null
  return (
    index.find(({ needle }) => normalize(needle) === term)?.entry ??
    index.find(({ needle }) => term.includes(normalize(needle)))?.entry ??
    null
  )
}

/**
 * UC-13: 슬라이딩 윈도우 문장에서 "내 도메인 밖" 용어만 후보로 뽑습니다.
 * Glossary에 없는 용어는 후보로 만들지 않습니다(오탐 방지).
 */
export function detectIn(index, sentences, personaDomains = []) {
  const mine = new Set(personaDomains)
  const found = new Map()

  for (const sentence of sentences) {
    const text = sentence.text ?? ''
    for (const { needle, entry } of index) {
      if (found.has(entry.term)) continue
      if (!text.includes(needle)) continue
      if (!entry.domains?.length) continue // 도메인을 모르면 낯섦을 판정할 수 없습니다
      if (entry.domains.some((d) => mine.has(d))) continue
      found.set(entry.term, {
        term: entry.term,
        domain: entry.subdomain ?? entry.domains[0],
        sentenceId: sentence.id,
        at: sentence.at,
      })
    }
  }

  return [...found.values()]
}

/* 시드만으로 쓰는 편의 함수 (테스트·폴백용) */
export const matchGlossary = (raw) => matchIn(DEFAULT_INDEX, raw)
export const detectUnfamiliarTerms = (sentences, domains) =>
  detectIn(DEFAULT_INDEX, sentences, domains)

function normalize(value) {
  return String(value ?? '')
    .trim()
    .toLowerCase()
    .replace(/\s+/g, '')
}
