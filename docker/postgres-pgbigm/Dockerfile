FROM pgvector/pgvector:pg16

# pg_bigm: 문자 n-gram 기반 전문 검색 확장. 띄어쓰기/형태소 경계에 의존하지 않아
# 한국어(조사가 단어에 바로 붙는 언어)에 to_tsvector('simple', ...)보다 훨씬 잘 맞는다.
# PGDG 저장소엔 패키지가 없어서 소스에서 직접 빌드한다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential \
        postgresql-server-dev-16 \
        git \
        ca-certificates \
    && git clone --depth 1 https://github.com/pgbigm/pg_bigm.git /tmp/pg_bigm \
    && cd /tmp/pg_bigm \
    && make USE_PGXS=1 \
    && make USE_PGXS=1 install \
    && cd / \
    && rm -rf /tmp/pg_bigm \
    && apt-get purge -y --auto-remove build-essential postgresql-server-dev-16 git \
    && rm -rf /var/lib/apt/lists/*
