INSERT INTO domain_tags (name) VALUES ('개발') ON CONFLICT (name) DO NOTHING;
INSERT INTO domain_tags (name) VALUES ('기획') ON CONFLICT (name) DO NOTHING;
INSERT INTO domain_tags (name) VALUES ('디자인') ON CONFLICT (name) DO NOTHING;
INSERT INTO domain_tags (name) VALUES ('영업') ON CONFLICT (name) DO NOTHING;
INSERT INTO domain_tags (name) VALUES ('경영') ON CONFLICT (name) DO NOTHING;
INSERT INTO domain_tags (name) VALUES ('기타') ON CONFLICT (name) DO NOTHING;
-- 데모/테스트용: 타 도메인 비유 설명(F-06) 테스트에 쓰는 산업 태그.
INSERT INTO domain_tags (name) VALUES ('반도체') ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- 데모 계정 (로그인 테스트용, 매 기동 시 없으면만 생성 - 멱등)
--   admin@domainbridge.dev / Admin1234!  (ADMIN)
--   demo@domainbridge.dev  / Demo1234!   (USER, 반도체 페르소나 사전 설정)
-- 비밀번호는 BCrypt로 미리 해시해 넣었다 (평문은 이 주석에만 존재).
-- ============================================================
INSERT INTO users (email, password, name, role, created_at, updated_at)
SELECT 'admin@domainbridge.dev',
       '$2b$10$sbWCPc8dMNQJmFUSP3aKe.f0NoHnUrdsJcRWqfW828l64n8dgQz9a',
       '관리자', 'ADMIN', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@domainbridge.dev');

INSERT INTO users (email, password, name, role, created_at, updated_at)
SELECT 'demo@domainbridge.dev',
       '$2b$10$6SWm3n0Vnw6DbtrE3FsfU.CTYV.MRvrT/QxTAbZj8BO2JSuxfPJ9q',
       '데모 사용자', 'USER', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'demo@domainbridge.dev');

-- 데모 사용자 페르소나: 반도체 공정 엔지니어로 설정 (MSA 같은 개발 용어를 반도체에 빗대는 테스트용)
INSERT INTO user_persona (user_id, persona_description, official_def_length, personalized_exp_length, created_at, updated_at)
SELECT u.id,
       '반도체 공정 엔지니어. 개발/IT 용어를 반도체 공정에 빗대어 설명받고 싶어함.',
       'MEDIUM', 'MEDIUM', now(), now()
FROM users u
WHERE u.email = 'demo@domainbridge.dev'
  AND NOT EXISTS (SELECT 1 FROM user_persona p WHERE p.user_id = u.id);

-- 데모 사용자 관심 도메인 태그: 반도체
INSERT INTO user_domain_tags (user_id, domain_tag_id)
SELECT u.id, t.id
FROM users u, domain_tags t
WHERE u.email = 'demo@domainbridge.dev' AND t.name = '반도체'
  AND NOT EXISTS (
      SELECT 1 FROM user_domain_tags udt WHERE udt.user_id = u.id AND udt.domain_tag_id = t.id
  );