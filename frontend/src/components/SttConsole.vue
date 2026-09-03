<script setup>
import { nextTick, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useSttStore } from '@/stores/stt'
import RecordingStatusPanel from './RecordingStatusPanel.vue'
import TranscriptLine from './TranscriptLine.vue'

defineProps({
  sessionLabel: { type: String, default: 'SESSION 04' },
})

const emit = defineEmits(['request-start', 'collapse'])

const stt = useSttStore()
const { sentences, interimText, timecode, isRecording, candidateTerms, windowSize } =
  storeToRefs(stt)

const scroller = ref(null)

/** 새 문장이 붙으면 아래로 따라갑니다. 사용자가 위로 올려둔 경우에는 두고 봅니다. */
const pinned = ref(true)

function onScroll() {
  const el = scroller.value
  if (!el) return
  pinned.value = el.scrollHeight - el.scrollTop - el.clientHeight < 48
}

watch([sentences, interimText], async () => {
  if (!pinned.value) return
  await nextTick()
  const el = scroller.value
  if (el) el.scrollTop = el.scrollHeight
})
</script>

<template>
  <aside class="console" aria-label="STT 콘솔">
    <header class="console__head">
      <span class="u-label">STT 콘솔</span>
      <div class="console__head-right">
        <span class="u-meta">{{ sessionLabel }}</span>
        <button
          type="button"
          class="console__collapse"
          aria-label="STT 콘솔 접기"
          @click="emit('collapse')"
        >
          <i class="ti ti-chevron-right" aria-hidden="true"></i>
        </button>
      </div>
    </header>

    <!-- 스크롤러는 이 하나뿐입니다. 상태 패널은 이 안에서 sticky로 고정됩니다. -->
    <div ref="scroller" class="console__scroll" @scroll="onScroll">
      <RecordingStatusPanel placement="console" @request-start="emit('request-start')" />

      <div class="console__section">
        <div class="console__section-head">
          <span class="u-label">최근 {{ windowSize }}문장 · 스트리밍</span>
          <span class="u-meta">{{ timecode }}</span>
        </div>

        <!--
          확정된 문장만 보조기술에 알립니다. 인식 중 문장은 초당 여러 번 바뀌므로
          aria-live에서 제외해 낭독이 끊기지 않게 합니다.
        -->
        <div class="console__lines" aria-live="polite" aria-relevant="additions">
          <TranscriptLine
            v-for="sentence in sentences"
            :key="sentence.id"
            :at="sentence.at"
            :text="sentence.text"
            :terms="candidateTerms"
          />
        </div>

        <TranscriptLine
          v-if="interimText"
          :at="timecode"
          :text="interimText"
          :terms="candidateTerms"
          interim
          aria-hidden="true"
        />

        <p v-if="!sentences.length && !interimText" class="console__empty">
          {{
            isRecording
              ? '듣고 있습니다. 발화가 인식되면 여기에 문장이 쌓입니다.'
              : 'STT를 시작하면 인식된 문장이 여기에 시간순으로 쌓입니다. 켜지 않아도 아래 입력창으로 직접 질의할 수 있습니다.'
          }}
        </p>

        <p class="u-note">
          윈도우를 벗어난 문장은 화면에서 지워지고, 이력에는 최소 정보만 저장됩니다.
        </p>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.console {
  width: var(--w-console);
  flex: none;
  display: flex;
  flex-direction: column;
  border-left: 1px solid var(--c-border);
  background: var(--c-bg);
}

.console__head {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: var(--s-5) 20px var(--s-3);
}

.console__head-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.console__collapse {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: var(--r-control);
  color: var(--c-text-muted);
  transition:
    background-color var(--t-fast),
    color var(--t-fast);
}

.console__collapse:hover {
  background: var(--c-surface-raised);
  color: var(--c-text);
}

.console__scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 20px var(--s-5);
}

.console__section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: var(--s-5);
}

.console__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.console__lines {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.console__lines:not(:empty) + * {
  margin-top: 0;
}

.console__empty {
  margin: 0;
  padding: 16px 14px;
  background: var(--c-surface);
  border: 1px dashed var(--c-border);
  border-radius: var(--r-control);
  font-size: 14px;
  line-height: 1.6;
  color: var(--c-text-muted);
}
</style>
