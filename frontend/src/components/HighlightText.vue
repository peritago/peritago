<script setup>
import { computed } from 'vue'

/**
 * 형광펜 처리. 두 가지 입력을 받습니다.
 *  1) 본문에 §…§로 감싼 구간  — LLM 응답에서 강조 구간을 표시할 때
 *  2) terms 배열              — STT 문장에서 감지된 용어를 칠할 때
 *
 * 가이드 3장대로 문장 전체가 아니라 용어·짧은 구절에만 칠합니다.
 */
const props = defineProps({
  text: { type: String, default: '' },
  terms: { type: Array, default: () => [] },
  strong: { type: Boolean, default: false },
})

const parts = computed(() => {
  const source = props.text ?? ''
  if (!source) return []

  const marks = []
  const explicit = /§([^§]+)§/g
  let match
  while ((match = explicit.exec(source))) {
    marks.push({ start: match.index, end: match.index + match[0].length, text: match[1] })
  }

  if (!marks.length && props.terms.length) {
    for (const term of props.terms) {
      if (!term) continue
      let from = 0
      let at
      while ((at = source.indexOf(term, from)) !== -1) {
        marks.push({ start: at, end: at + term.length, text: term })
        from = at + term.length
      }
    }
  }

  marks.sort((a, b) => a.start - b.start)

  const out = []
  let cursor = 0
  for (const mark of marks) {
    if (mark.start < cursor) continue
    if (mark.start > cursor) out.push({ mark: false, text: source.slice(cursor, mark.start) })
    out.push({ mark: true, text: mark.text })
    cursor = mark.end
  }
  if (cursor < source.length) out.push({ mark: false, text: source.slice(cursor) })
  return out
})
</script>

<template>
  <span>
    <template v-for="(part, i) in parts" :key="i">
      <mark v-if="part.mark" class="u-highlight" :class="{ 'u-highlight--lg': strong }">{{
        part.text
      }}</mark>
      <template v-else>{{ part.text }}</template>
    </template>
  </span>
</template>
