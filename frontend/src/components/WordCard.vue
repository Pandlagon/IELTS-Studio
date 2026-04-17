<template>
  <div class="word-card" :class="{ 'is-flipping': flipping }">
    <div class="phonetic">{{ word.phonetic }}</div>
    <div class="word-text">{{ word.word }}</div>
    <div v-if="!hasPosGroups && !meaningRevealed" class="pos-badge" :class="`pos-${word.posType}`">{{ word.pos }}</div>
    
    <!-- Meaning section - hidden initially -->
    <div v-if="meaningRevealed" class="meaning-block">
      <div v-if="!hasPosGroups" class="pos-badge" :class="`pos-${word.posType}`">{{ word.pos }}</div>
      <div v-for="(group, i) in meaningGroups" :key="i" class="pos-group">
        <span v-if="group.pos" class="pos-label" :class="`pos-label-${group.posType}`">{{ group.pos }}</span>
        <span class="senses">
          <span v-for="(sense, j) in group.senses" :key="j" class="sense">{{ sense }}<span v-if="j < group.senses.length - 1" class="sep">；</span></span>
        </span>
      </div>
      <div class="example">{{ word.example }}</div>
    </div>

    <!-- Placeholder when meaning is hidden -->
    <div v-else class="meaning-placeholder">
      <div class="placeholder-text">Do you know this word?</div>
    </div>

    <!-- Action buttons change based on state -->
    <div class="card-actions">
      <template v-if="!meaningRevealed">
        <button class="btn-unknown" @click="revealMeaning('unknown')">
          <span class="btn-icon">✗</span> 不认识
        </button>
        <button class="btn-know" @click="revealMeaning('know')">
          <span class="btn-icon">✓</span> 认识了
        </button>
      </template>
      <template v-else>
        <button class="btn-next" @click="confirmAndNext">
          <span class="btn-icon">→</span> 下一个
        </button>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  word: {
    type: Object,
    required: true,
  },
})

// Parse meaning into POS groups.
// Supports: "n. 种子；来源 · v. 播种" or plain "能力；才能" (no POS prefix)
const hasPosGroups = computed(() =>
  (props.word.meaning || '').split(/\s*·\s*/).some(g =>
    /^(n\.|v\.|adj\.|adv\.|prep\.|conj\.|pron\.|int\.|phrase)\s+/i.test(g.trim())
  )
)

const meaningGroups = computed(() => {
  const raw = (props.word.meaning || '').trim()
  if (!raw) return []
  // Split on · (POS group separator)
  const groups = raw.split(/\s*·\s*/)
  return groups.map(g => {
    g = g.trim()
    // Match leading POS label like "n. ", "v. ", "adj. ", "adv. ", "prep. ", "phrase "
    const posMatch = g.match(/^(n\.|v\.|adj\.|adv\.|prep\.|conj\.|pron\.|int\.|phrase)\s+/i)
    if (posMatch) {
      const posLabel = posMatch[1]
      const rest = g.slice(posMatch[0].length)
      const posType = posLabel.replace('.', '').toLowerCase()
      return { pos: posLabel, posType, senses: rest.split('；').map(s => s.trim()).filter(Boolean) }
    }
    return { pos: '', posType: '', senses: g.split('；').map(s => s.trim()).filter(Boolean) }
  }).filter(g => g.senses.length > 0)
})

const emit = defineEmits(['know', 'unknown'])
const flipping = ref(false)
const shownAt = ref(Date.now())
const meaningRevealed = ref(false)
const userChoice = ref(null)

watch(() => props.word?.id, () => {
  shownAt.value = Date.now()
  meaningRevealed.value = false
  userChoice.value = null
}, { immediate: true })

function animate(cb) {
  flipping.value = true
  setTimeout(() => {
    cb()
    flipping.value = false
  }, 220)
}

function revealMeaning(choice) {
  userChoice.value = choice
  meaningRevealed.value = true
}

function confirmAndNext() {
  const reactionMs = Date.now() - shownAt.value
  animate(() => {
    if (userChoice.value === 'know') {
      emit('know', { id: props.word.id, reactionMs })
    } else {
      emit('unknown', { id: props.word.id, reactionMs })
    }
  })
}

function handleKeydown(e) {
  if (e.ctrlKey || e.metaKey || e.altKey) return
  const target = e.target
  const tag = target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target?.isContentEditable) return
  if (!meaningRevealed.value) {
    // First stage: reveal meaning
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      revealMeaning('know')
    } else if (e.key === 'ArrowDown') {
      e.preventDefault()
      revealMeaning('unknown')
    }
  } else {
    // Second stage: confirm and next
    if (e.key === 'ArrowUp' || e.key === 'ArrowDown' || e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      confirmAndNext()
    }
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.word-card {
  background: var(--bg-white);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-xl);
  padding: 40px 36px 32px;
  width: 380px;
  min-width: 360px;
  height: 420px;
  border: 1px solid var(--border-light);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  position: relative;
  display: flex;
  flex-direction: column;
}

.word-card:hover {
  box-shadow: 0 20px 56px rgba(0,0,0,0.13);
  transform: translateY(-2px);
}

.word-card.is-flipping {
  animation: cardFlipAnim 0.22s ease;
}

@keyframes cardFlipAnim {
  0% { transform: scale(1) rotateY(0deg); opacity: 1; }
  50% { transform: scale(0.95) rotateY(8deg); opacity: 0.7; }
  100% { transform: scale(1) rotateY(0deg); opacity: 1; }
}

.phonetic {
  font-size: 12px;
  color: var(--text-muted);
  font-style: italic;
  margin-bottom: 8px;
  letter-spacing: 0.5px;
}

.word-text {
  font-size: 48px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.1;
  margin-bottom: 12px;
  font-family: 'Lora', var(--font-sans);
  letter-spacing: -1px;
}

.pos-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: var(--radius-full);
  font-size: 11px;
  font-weight: 600;
  margin-bottom: 14px;
  letter-spacing: 0.3px;
}

.pos-adj { background: #FFF3CD; color: #8B6A00; }
.pos-n   { background: #DBEAFE; color: #1D4ED8; }
.pos-v   { background: #D1FAE5; color: #065F46; }
.pos-adv { background: #EDE9FE; color: #5B21B6; }
.pos-prep{ background: #FEE2E2; color: #991B1B; }

.meaning-block {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-bottom: 10px;
  flex: 1;
  min-height: 0;
}

.pos-group {
  display: flex;
  align-items: baseline;
  gap: 6px;
  flex-wrap: wrap;
}

.pos-label {
  font-size: 11px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 4px;
  flex-shrink: 0;
  letter-spacing: 0.2px;
}
.pos-label-n    { background: #DBEAFE; color: #1D4ED8; }
.pos-label-v    { background: #D1FAE5; color: #065F46; }
.pos-label-adj  { background: #FFF3CD; color: #8B6A00; }
.pos-label-adv  { background: #EDE9FE; color: #5B21B6; }
.pos-label-prep { background: #FEE2E2; color: #991B1B; }
.pos-label-phrase { background: #F3F4F6; color: #4B5563; }

.senses {
  font-size: 19px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.4;
}

.sense { display: inline; }

.sep {
  color: var(--text-muted);
  font-weight: 400;
  font-size: 14px;
  margin: 0 1px;
}

.example {
  font-size: 14px;
  color: var(--text-muted);
  font-style: italic;
  line-height: 1.5;
  margin-bottom: 28px;
  padding: 12px 16px;
  background: var(--bg-primary);
  border-radius: var(--radius-md);
  border-left: 3px solid var(--color-accent-light);
}

.card-actions {
  display: flex;
  gap: 10px;
  margin-top: auto;
}

.meaning-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 28px;
}

.placeholder-text {
  font-size: 18px;
  color: var(--text-muted);
  font-style: italic;
  text-align: center;
}

.btn-unknown,
.btn-know,
.btn-next {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 600;
  border: none;
  cursor: pointer;
  transition: all 0.15s ease;
  font-family: var(--font-sans);
}

.btn-unknown {
  background: #FFEBEE;
  color: #C62828;
}

.btn-unknown:hover {
  background: #FFCDD2;
  transform: translateY(-1px);
}

.btn-know {
  background: #E8F5E9;
  color: #2E7D32;
}

.btn-know:hover {
  background: #C8E6C9;
  transform: translateY(-1px);
}

.btn-next {
  background: #E3F2FD;
  color: #1565C0;
}

.btn-next:hover {
  background: #BBDEFB;
  transform: translateY(-1px);
}

.btn-icon {
  font-size: 14px;
}
</style>
