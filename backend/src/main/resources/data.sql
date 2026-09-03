INSERT INTO domain_tags (name) VALUES ('개발') ON CONFLICT (name) DO NOTHING;
INSERT INTO domain_tags (name) VALUES ('기획') ON CONFLICT (name) DO NOTHING;
INSERT INTO domain_tags (name) VALUES ('디자인') ON CONFLICT (name) DO NOTHING;
INSERT INTO domain_tags (name) VALUES ('영업') ON CONFLICT (name) DO NOTHING;
INSERT INTO domain_tags (name) VALUES ('경영') ON CONFLICT (name) DO NOTHING;
INSERT INTO domain_tags (name) VALUES ('기타') ON CONFLICT (name) DO NOTHING;