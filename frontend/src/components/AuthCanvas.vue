<script setup>
defineProps({
  eyebrow: { type: String, default: '' },
})
</script>

<template>
  <div class="canvas">
    <!-- 좌: 브랜드 헤드라인. 가이드 6장 "검정 캔버스 + 큰 타이포" 자리입니다. -->
    <section class="canvas__brand">
      <div class="canvas__logo">
        <img src="/assets/peritago-parrot.png" alt="" width="44" height="44" />
        <span>PERITAGO</span>
      </div>

      <h1 class="canvas__headline">낯선 말을<br />알아듣는<br />가장 빠른 길</h1>

      <p class="canvas__lead">
        회의 중 들리는 도메인 용어와 은어를 내 업무 도메인의 언어로 통역합니다. 공식 정의는 그대로,
        설명만 내 렌즈로.
      </p>

      <ul class="canvas__points">
        <li>
          <strong>실시간 발화 인식</strong>
          <span>말하는 동안 문장이 흘러 들어옵니다</span>
        </li>
        <li>
          <strong>사내 공식 정의 우선</strong>
          <span>Glossary 원문을 그대로 씁니다</span>
        </li>
        <li>
          <strong>내 도메인 비유</strong>
          <span>설명만 내 업무 언어로 바꿉니다</span>
        </li>
      </ul>
    </section>

    <section class="canvas__panel">
      <div class="canvas__panel-inner">
        <span v-if="eyebrow" class="u-label">{{ eyebrow }}</span>
        <slot />
      </div>
    </section>
  </div>
</template>

<style scoped>
.canvas {
  display: flex;
  min-height: 100%;
}

.canvas__brand {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--s-6);
  padding: 64px 56px;
  background: var(--c-surface);
  border-right: 1px solid var(--c-border);
}

.canvas__logo {
  display: flex;
  align-items: center;
  gap: 12px;
  font-weight: 800;
  font-size: 17px;
}

.canvas__logo img {
  object-fit: contain;
}

.canvas__headline {
  margin: 0;
  font-weight: 800;
  font-size: clamp(38px, 4.4vw, 60px);
  line-height: 1.04;
  letter-spacing: -0.04em;
}

.canvas__lead {
  margin: 0;
  max-width: 46ch;
  font-size: 16px;
  line-height: 1.7;
  color: var(--c-text-muted);
  text-wrap: pretty;
}

.canvas__points {
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin: auto 0 0;
  padding: 0;
  list-style: none;
  background: var(--c-border);
  border: 1px solid var(--c-border);
  border-radius: var(--r-card);
  overflow: hidden;
}

.canvas__points li {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 16px 18px;
  background: var(--c-bg);
}

.canvas__points strong {
  font-weight: 700;
  font-size: 14px;
}

.canvas__points span {
  font-size: 14px;
  color: var(--c-text-muted);
}

.canvas__panel {
  width: 520px;
  flex: none;
  display: flex;
  align-items: center;
  padding: 56px 48px;
}

.canvas__panel-inner {
  display: flex;
  flex-direction: column;
  gap: var(--s-5);
  width: 100%;
}

@media (max-width: 1000px) {
  .canvas {
    flex-direction: column;
  }
  .canvas__brand {
    border-right: none;
    border-bottom: 1px solid var(--c-border);
    padding: 40px 28px;
  }
  .canvas__points {
    margin-top: var(--s-4);
  }
  .canvas__panel {
    width: 100%;
    padding: 32px 28px 56px;
  }
}
</style>
