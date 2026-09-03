<script setup>
import { nextTick, ref } from 'vue'

defineProps({
  chats: { type: Array, default: () => [] },
  currentId: { type: [Number, String], default: null },
  recordingChatId: { type: [Number, String], default: null },
})

const emit = defineEmits(['create', 'select', 'rename'])

/** 인라인 제목 편집 — 한 번에 하나만 편집 가능하므로 단일 상태로 충분합니다. */
const editingId = ref(null)
const draftTitle = ref('')
const renameInput = ref(null)

function startEdit(chat) {
  editingId.value = chat.id
  draftTitle.value = chat.title
  nextTick(() => renameInput.value?.focus())
}

function commitEdit(chat) {
  if (editingId.value !== chat.id) return
  editingId.value = null
  const next = draftTitle.value.trim()
  if (next && next !== chat.title) emit('rename', chat.id, next)
}

function cancelEdit() {
  editingId.value = null
}
</script>

<template>
  <nav class="side" aria-label="채팅 목록">
    <button type="button" class="btn btn--primary btn--block side__new" @click="$emit('create')">
      + 새 채팅
    </button>

    <div class="side__group">
      <span class="u-label">채팅 목록</span>

      <ul class="side__list">
        <li v-for="chat in chats" :key="chat.id">
          <div
            class="item"
            :class="{ 'item--active': chat.id === currentId }"
            :aria-current="chat.id === currentId ? 'true' : undefined"
            role="button"
            tabindex="0"
            @click="editingId !== chat.id && $emit('select', chat.id)"
            @keydown.enter="editingId !== chat.id && $emit('select', chat.id)"
          >
            <span v-if="chat.id === currentId" class="item__rule" aria-hidden="true"></span>
            <span class="item__body">
              <span class="item__when">{{ chat.no }} · {{ chat.when }}</span>

              <span class="item__title-row">
                <input
                  v-if="editingId === chat.id"
                  ref="renameInput"
                  v-model="draftTitle"
                  type="text"
                  class="item__title-input"
                  maxlength="100"
                  aria-label="채팅 제목"
                  @click.stop
                  @keydown.enter.stop="commitEdit(chat)"
                  @keydown.esc.stop="cancelEdit"
                  @blur="commitEdit(chat)"
                />
                <template v-else>
                  <span class="item__title">{{ chat.title }}</span>
                  <button
                    type="button"
                    class="item__rename"
                    aria-label="채팅 제목 변경"
                    @click.stop="startEdit(chat)"
                  >
                    <i class="ti ti-pencil" aria-hidden="true"></i>
                  </button>
                </template>
              </span>

              <span class="item__meta">
                질의 {{ chat.count }}건<template v-if="chat.id === recordingChatId">
                  · 녹음 중</template
                >
              </span>
            </span>
          </div>
        </li>
      </ul>
    </div>

    <p class="side__note u-note">
      로그인하면 새 채팅이 자동 생성됩니다. 이 채팅의 질의만 여기에 기록됩니다.
    </p>
  </nav>
</template>

<style scoped>
.side {
  width: var(--w-sidebar);
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: var(--s-5) 18px;
  border-right: 1px solid var(--c-border);
  overflow-y: auto;
}

.side__new {
  height: 44px;
}

.side__group {
  display: flex;
  flex-direction: column;
  gap: 9px;
  min-height: 0;
}

.side__list {
  display: flex;
  flex-direction: column;
  gap: 9px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.item {
  display: flex;
  width: 100%;
  text-align: left;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-control);
  color: var(--c-text-muted);
  overflow: hidden;
  cursor: pointer;
  transition:
    background-color var(--t-fast),
    border-color var(--t-fast),
    color var(--t-fast);
}

.item:hover {
  background: var(--c-surface-raised);
  border-color: var(--c-border-hover);
  color: var(--c-text);
}

/* 선택된 채팅은 왼쪽 4px 바로 표시합니다 — 색 하나에 기대지 않습니다. */
.item--active {
  background: var(--c-surface-raised);
  color: var(--c-text);
}

.item__rule {
  width: 4px;
  flex: none;
  background: var(--c-accent);
}

.item__body {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 11px 12px;
  min-width: 0;
}

.item__when {
  font-weight: 500;
  font-size: 14px;
}

.item__title-row {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.item__title {
  flex: 1;
  font-weight: 600;
  font-size: 14px;
  line-height: 1.4;
  color: var(--c-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item__rename {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 4px;
  color: var(--c-text-muted);
  opacity: 0;
  transition:
    opacity var(--t-fast),
    color var(--t-fast);
}

.item:hover .item__rename,
.item__rename:focus-visible {
  opacity: 1;
}

.item__rename:hover {
  color: var(--c-accent);
}

.item__title-input {
  flex: 1;
  min-width: 0;
  padding: 1px 4px;
  font-weight: 600;
  font-size: 14px;
  line-height: 1.4;
  color: var(--c-text);
  background: var(--c-bg);
  border: 1px solid var(--c-accent);
  border-radius: 4px;
}

.item__meta {
  font-size: 14px;
  color: var(--c-text-muted);
}

.side__note {
  margin: auto 0 0;
  padding: 11px 12px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-control);
}

@media (max-width: 1100px) {
  .side {
    display: none;
  }
}
</style>
