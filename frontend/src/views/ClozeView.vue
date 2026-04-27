<template>
  <div class="page-wrapper">
    <NavBar />
    <div class="cloze-page">
      <div class="container">

        <!-- Step 1: Select words -->
        <template v-if="step === 'select'">
          <div class="cloze-header">
            <h1 class="page-title">完形填空练习</h1>
            <p class="page-sub">从词书中选择 1-10 个单词，AI 生成完形填空</p>
          </div>

          <!-- Difficulty selector -->
          <div class="setting-card card">
            <div class="setting-row">
              <span class="setting-label">难度</span>
              <div class="diff-opts">
                <button v-for="d in difficulties" :key="d.value"
                  :class="['diff-btn', { active: difficulty === d.value }]"
                  @click="difficulty = d.value">
                  {{ d.label }}
                </button>
              </div>
            </div>
          </div>

          <!-- Book selector -->
          <div class="book-selector">
            <div class="book-tabs">
              <div v-for="book in wordStore.books" :key="book.id"
                class="book-tab" :class="{ active: selectedBookId === book.id }"
                @click="switchBook(book.id)">
                <span class="book-tab-name">{{ book.name }}</span>
                <span class="book-tab-count">{{ book.wordCount || (book.isBuiltin ? 20 : 0) }}</span>
              </div>
            </div>
          </div>

          <!-- Selected words chips -->
          <div v-if="selectedWords.length > 0" class="selected-bar card">
            <span class="selected-label">已选 {{ selectedWords.length }}/10：</span>
            <div class="selected-chips">
              <span v-for="w in selectedWords" :key="w.id" class="word-chip">
                {{ w.word }}
                <button class="chip-remove" @click="toggleWord(w)">&times;</button>
              </span>
            </div>
            <button class="btn-primary btn-sm" :disabled="selectedWords.length === 0 || generating"
              :loading="generating" @click="doGenerate">
              {{ generating ? 'AI 生成中...' : '开始生成' }}
            </button>
          </div>

          <!-- Word list for selection -->
          <div class="word-select-list">
            <div v-for="word in availableWords" :key="word.id"
              class="word-select-row" :class="{ selected: isSelected(word) }"
              @click="toggleWord(word)">
              <div class="ws-check">
                <div class="checkbox" :class="{ checked: isSelected(word) }">
                  <svg v-if="isSelected(word)" width="12" height="12" viewBox="0 0 24 24" fill="none"
                    stroke="white" stroke-width="3"><polyline points="20 6 9 17 4 12"/></svg>
                </div>
              </div>
              <div class="ws-word">{{ word.word }}</div>
              <div class="ws-phonetic">{{ word.phonetic }}</div>
              <div class="ws-meaning">{{ word.meaning }}</div>
            </div>
          </div>

          <!-- Empty state -->
          <div v-if="availableWords.length === 0 && !wordStore.loadingEntries" class="empty-state">
            <p>当前词书没有单词，请切换词书</p>
          </div>

          <!-- Floating generate button for mobile -->
          <div v-if="selectedWords.length > 0" class="floating-generate">
            <button class="btn-primary" :disabled="generating" @click="doGenerate">
              {{ generating ? 'AI 生成中...' : `生成完形填空 (${selectedWords.length} 词)` }}
            </button>
          </div>
        </template>

        <!-- Step 2: Answer -->
        <template v-if="step === 'answer'">
          <div class="cloze-header">
            <h1 class="page-title">{{ clozeData.title || '完形填空' }}</h1>
            <p class="page-sub">阅读短文，选择正确答案填入空格</p>
          </div>

          <!-- Passage -->
          <div class="passage-card card">
            <div class="passage-text" v-html="renderedPassage"></div>
          </div>

          <!-- Blanks / Questions -->
          <div class="blanks-section">
            <div v-for="blank in clozeData.blanks" :key="blank.number" class="blank-card card">
              <div class="blank-num">{{ blank.number }}</div>
              <div class="blank-options">
                <button v-for="(text, label) in blank.options" :key="label"
                  class="opt-btn"
                  :class="{ selected: userAnswers[blank.number] === label }"
                  @click="userAnswers[blank.number] = label">
                  <span class="opt-label">{{ label }}</span>
                  <span class="opt-text">{{ text }}</span>
                </button>
              </div>
            </div>
          </div>

          <!-- Submit -->
          <div class="submit-bar">
            <div class="answer-progress">
              已答 {{ answeredCount }} / {{ clozeData.blanks.length }}
            </div>
            <button class="btn-ghost" @click="step = 'select'; clozeData = null">放弃</button>
            <button class="btn-primary" :disabled="checking || answeredCount === 0" @click="doCheck">
              {{ checking ? 'AI 批改中...' : '提交批改' }}
            </button>
          </div>
        </template>

        <!-- Step 3: Result -->
        <template v-if="step === 'result'">
          <div class="cloze-header">
            <h1 class="page-title">批改结果</h1>
            <p class="page-sub">{{ checkResult.summary }}</p>
          </div>

          <!-- Score -->
          <div class="score-card card">
            <div class="score-ring" :class="scoreClass">
              <span class="score-num">{{ checkResult.score }}</span>
              <span class="score-sep">/</span>
              <span class="score-total">{{ checkResult.total }}</span>
            </div>
            <div class="score-label">得分</div>
          </div>

          <!-- Passage with highlights -->
          <div class="passage-card card">
            <div class="passage-text" v-html="resultPassage"></div>
          </div>

          <!-- Detailed results -->
          <div class="results-section">
            <div v-for="r in checkResult.results" :key="r.number"
              class="result-card card" :class="{ correct: r.correct, wrong: !r.correct }">
              <div class="result-header">
                <span class="result-num">{{ r.number }}</span>
                <span v-if="r.correct" class="result-badge correct">正确</span>
                <span v-else class="result-badge wrong">错误</span>
              </div>
              <div class="result-body">
                <div class="result-answers">
                  <span class="ra-item">
                    <span class="ra-label">你的答案</span>
                    <span :class="r.correct ? 'ra-val correct' : 'ra-val wrong'">{{ r.userAnswer }}</span>
                  </span>
                  <span v-if="!r.correct" class="ra-item">
                    <span class="ra-label">正确答案</span>
                    <span class="ra-val correct">{{ r.correctAnswer }}</span>
                  </span>
                </div>
                <p class="result-explanation">{{ r.explanation }}</p>
              </div>
            </div>
          </div>

          <!-- Actions -->
          <div class="result-actions">
            <button class="btn-ghost" @click="restart">再来一次</button>
            <button class="btn-primary" @click="$router.push('/words')">返回词书</button>
          </div>
        </template>

      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import NavBar from '@/components/NavBar.vue'
import { useWordStore } from '@/stores/word'
import request from '@/api'

const router = useRouter()
const wordStore = useWordStore()

const step = ref('select') // 'select' | 'answer' | 'result'
const difficulty = ref('medium')
const difficulties = [
  { value: 'easy', label: '简单' },
  { value: 'medium', label: '中等' },
  { value: 'hard', label: '困难' },
]

// ── Word selection ───────────────────────────────────────
const selectedBookId = ref(wordStore.currentBookId || 'builtin')
const selectedWords = ref([])
const bookWords = ref([])
const generating = ref(false)
const checking = ref(false)

const availableWords = computed(() => {
  if (selectedBookId.value === 'builtin') {
    return wordStore.words
  }
  return bookWords.value.map(e => ({
    id: e.id,
    word: e.word,
    phonetic: e.phonetic || '',
    meaning: e.meaning || '',
  }))
})

function isSelected(word) {
  return selectedWords.value.some(w => w.word === word.word)
}

function toggleWord(word) {
  const idx = selectedWords.value.findIndex(w => w.word === word.word)
  if (idx >= 0) {
    selectedWords.value.splice(idx, 1)
  } else if (selectedWords.value.length < 10) {
    selectedWords.value.push(word)
  } else {
    ElMessage.warning('最多选择 10 个单词')
  }
}

async function switchBook(bookId) {
  selectedBookId.value = bookId
  if (bookId === 'builtin') {
    bookWords.value = []
  } else {
    try {
      const res = await request.get(`/words/books/${bookId}/entries`)
      bookWords.value = res.data || []
    } catch {
      bookWords.value = []
    }
  }
}

// ── Generate ─────────────────────────────────────────────
const clozeData = ref(null)

async function doGenerate() {
  if (selectedWords.value.length === 0) {
    ElMessage.warning('请至少选择 1 个单词')
    return
  }
  generating.value = true
  try {
    const res = await request.post('/words/cloze/generate', {
      words: selectedWords.value.map(w => w.word),
      meanings: selectedWords.value.map(w => w.meaning),
      difficulty: difficulty.value,
    }, { timeout: 90000 })
    clozeData.value = res.data
    userAnswers.value = {}
    step.value = 'answer'
  } catch (e) {
    ElMessage.error(e?.message || '生成失败，请重试')
  }
  generating.value = false
}

// ── Answer ───────────────────────────────────────────────
const userAnswers = ref({}) // { 1: 'A', 2: 'C' }

const answeredCount = computed(() => {
  if (!clozeData.value) return 0
  return clozeData.value.blanks.filter(b => userAnswers.value[b.number]).length
})

const renderedPassage = computed(() => {
  if (!clozeData.value) return ''
  let text = clozeData.value.passage
  // Replace __(N)__ with styled blanks
  text = text.replace(/__\((\d+)\)__/g, (match, num) => {
    const n = parseInt(num)
    const answer = userAnswers.value[n]
    const blank = clozeData.value.blanks.find(b => b.number === n)
    const displayText = answer && blank ? blank.options[answer] : '______'
    const cls = answer ? 'blank-filled' : 'blank-empty'
    return `<span class="${cls}" data-num="${n}"><sup>${n}</sup>${displayText}</span>`
  })
  return text
})

// ── Check ────────────────────────────────────────────────
const checkResult = ref(null)

async function doCheck() {
  if (answeredCount.value === 0) {
    ElMessage.warning('请至少回答一道题')
    return
  }
  checking.value = true
  try {
    const res = await request.post('/words/cloze/check', {
      passage: clozeData.value.passage,
      blanks: clozeData.value.blanks,
      userAnswers: Object.fromEntries(
        Object.entries(userAnswers.value).map(([k, v]) => [String(k), String(v)])
      ),
    }, { timeout: 90000 })
    checkResult.value = res.data
    step.value = 'result'
  } catch (e) {
    ElMessage.error(e?.message || '批改失败，请重试')
  }
  checking.value = false
}

const scoreClass = computed(() => {
  if (!checkResult.value) return ''
  const pct = checkResult.value.score / checkResult.value.total
  if (pct >= 0.8) return 'excellent'
  if (pct >= 0.5) return 'good'
  return 'poor'
})

const resultPassage = computed(() => {
  if (!clozeData.value || !checkResult.value) return ''
  let text = clozeData.value.passage
  text = text.replace(/__\((\d+)\)__/g, (match, num) => {
    const n = parseInt(num)
    const r = checkResult.value.results.find(x => x.number === n)
    const blank = clozeData.value.blanks.find(b => b.number === n)
    const correctWord = blank ? blank.answer : '?'
    const cls = r?.correct ? 'blank-correct' : 'blank-wrong'
    return `<span class="${cls}" data-num="${n}"><sup>${n}</sup>${correctWord}</span>`
  })
  return text
})

function restart() {
  step.value = 'select'
  clozeData.value = null
  checkResult.value = null
  userAnswers.value = {}
}

onMounted(async () => {
  await wordStore.loadBooks()
  // Load words from route query if provided
  const q = router.currentRoute.value.query
  if (q.words) {
    try {
      const parsed = JSON.parse(q.words)
      if (Array.isArray(parsed)) selectedWords.value = parsed
    } catch {}
  }
  if (q.bookId) {
    await switchBook(q.bookId === 'builtin' ? 'builtin' : Number(q.bookId))
  }
})
</script>

<style scoped>
.cloze-page { padding: 32px 0 100px; }

.cloze-header {
  margin-bottom: 24px;
}
.page-title { font-size: 28px; font-weight: 800; color: var(--text-primary); margin-bottom: 4px; }
.page-sub { font-size: 14px; color: var(--text-muted); }

/* ── Setting card ── */
.setting-card {
  padding: 16px 20px;
  margin-bottom: 16px;
}
.setting-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.setting-label { font-size: 13px; font-weight: 600; color: var(--text-secondary); }
.diff-opts { display: flex; gap: 6px; }
.diff-btn {
  padding: 6px 18px;
  border-radius: var(--radius-full);
  border: 1px solid var(--border-color);
  background: var(--bg-white);
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s;
}
.diff-btn:hover { border-color: var(--color-primary); color: var(--color-primary); }
.diff-btn.active { background: var(--color-primary); color: white; border-color: var(--color-primary); }

/* ── Book selector ── */
.book-selector {
  margin-bottom: 16px;
}
.book-tabs {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding-bottom: 4px;
}
.book-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: var(--radius-full);
  border: 1px solid var(--border-color);
  background: var(--bg-white);
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
}
.book-tab:hover { border-color: var(--color-primary); }
.book-tab.active { background: var(--color-primary); color: white; border-color: var(--color-primary); }
.book-tab-name { font-weight: 500; }
.book-tab-count {
  font-size: 11px;
  opacity: 0.7;
  background: rgba(0,0,0,0.08);
  padding: 1px 6px;
  border-radius: 10px;
}
.book-tab.active .book-tab-count { background: rgba(255,255,255,0.25); }

/* ── Selected bar ── */
.selected-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.selected-label { font-size: 13px; font-weight: 600; color: var(--text-secondary); white-space: nowrap; }
.selected-chips { display: flex; flex-wrap: wrap; gap: 6px; flex: 1; }
.word-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: var(--radius-full);
  background: #E8F5E9;
  color: #1B5E20;
  font-size: 13px;
  font-weight: 500;
}
.chip-remove {
  background: none;
  border: none;
  color: #1B5E20;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  opacity: 0.6;
}
.chip-remove:hover { opacity: 1; }

/* ── Word select list ── */
.word-select-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.word-select-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--bg-white);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.12s;
  border: 1px solid transparent;
}
.word-select-row:hover { background: #F8FAF8; border-color: var(--border-color); }
.word-select-row.selected { background: #E8F5E9; border-color: #A5D6A7; }

.ws-check { flex-shrink: 0; }
.checkbox {
  width: 20px; height: 20px;
  border: 2px solid #D1D5DB;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}
.checkbox.checked { background: var(--color-primary); border-color: var(--color-primary); }

.ws-word { font-weight: 600; font-size: 15px; color: var(--text-primary); min-width: 120px; }
.ws-phonetic { font-size: 12px; color: var(--text-muted); min-width: 120px; }
.ws-meaning { font-size: 13px; color: var(--text-secondary); flex: 1; }

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: var(--text-muted);
}

/* ── Floating generate ── */
.floating-generate {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 100;
}
.floating-generate .btn-primary {
  padding: 12px 32px;
  font-size: 15px;
  border-radius: var(--radius-full);
  box-shadow: 0 4px 20px rgba(27, 67, 50, 0.25);
}

/* ── Passage card ── */
.passage-card {
  padding: 24px;
  margin-bottom: 24px;
  line-height: 1.9;
  font-size: 15px;
  color: var(--text-primary);
}
.passage-text :deep(.blank-empty) {
  display: inline;
  padding: 2px 8px;
  border-bottom: 2px dashed var(--color-primary);
  color: var(--color-primary);
  font-weight: 600;
  cursor: default;
}
.passage-text :deep(.blank-filled) {
  display: inline;
  padding: 2px 8px;
  background: #E8F5E9;
  border-radius: 4px;
  color: #1B5E20;
  font-weight: 600;
}
.passage-text :deep(.blank-correct) {
  display: inline;
  padding: 2px 8px;
  background: #E8F5E9;
  border-radius: 4px;
  color: #16A34A;
  font-weight: 600;
}
.passage-text :deep(.blank-wrong) {
  display: inline;
  padding: 2px 8px;
  background: #FEE2E2;
  border-radius: 4px;
  color: #DC2626;
  font-weight: 600;
  text-decoration: line-through;
}
.passage-text :deep(sup) {
  font-size: 10px;
  margin-right: 2px;
  opacity: 0.7;
}

/* ── Blanks section ── */
.blanks-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 80px;
}
.blank-card {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px;
}
.blank-num {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary);
  color: white;
  border-radius: 50%;
  font-weight: 700;
  font-size: 14px;
}
.blank-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex: 1;
}
.opt-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-white);
  cursor: pointer;
  transition: all 0.15s;
  font-size: 14px;
}
.opt-btn:hover { border-color: var(--color-primary); background: #F8FAF8; }
.opt-btn.selected { border-color: var(--color-primary); background: #E8F5E9; color: #1B5E20; }
.opt-label {
  font-weight: 700;
  width: 20px;
  text-align: center;
  color: var(--color-primary);
}
.opt-text { color: var(--text-primary); }

/* ── Submit bar ── */
.submit-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 24px;
  background: var(--bg-white);
  border-top: 1px solid var(--border-color);
  z-index: 100;
  justify-content: flex-end;
}
.answer-progress {
  font-size: 13px;
  color: var(--text-muted);
  margin-right: auto;
}

/* ── Score card ── */
.score-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px;
  margin-bottom: 24px;
}
.score-ring {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 8px;
}
.score-num { font-size: 48px; font-weight: 800; }
.score-sep { font-size: 28px; color: var(--text-muted); }
.score-total { font-size: 28px; color: var(--text-muted); }
.score-ring.excellent .score-num { color: #16A34A; }
.score-ring.good .score-num { color: #F59E0B; }
.score-ring.poor .score-num { color: #DC2626; }
.score-label { font-size: 14px; color: var(--text-muted); }

/* ── Results section ── */
.results-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 32px;
}
.result-card {
  padding: 16px;
}
.result-card.correct { border-left: 3px solid #16A34A; }
.result-card.wrong { border-left: 3px solid #DC2626; }
.result-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.result-num {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 700;
  background: #F3F4F6;
  color: var(--text-secondary);
}
.result-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: var(--radius-full);
}
.result-badge.correct { background: #DCFCE7; color: #16A34A; }
.result-badge.wrong { background: #FEE2E2; color: #DC2626; }
.result-answers {
  display: flex;
  gap: 20px;
  margin-bottom: 8px;
}
.ra-item { display: flex; align-items: center; gap: 6px; }
.ra-label { font-size: 12px; color: var(--text-muted); }
.ra-val { font-size: 14px; font-weight: 600; }
.ra-val.correct { color: #16A34A; }
.ra-val.wrong { color: #DC2626; }
.result-explanation { font-size: 13px; color: var(--text-secondary); line-height: 1.6; }

/* ── Result actions ── */
.result-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}

/* ── Shared btn styles (reuse from global) ── */
.btn-primary {
  padding: 8px 20px;
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: var(--radius-md);
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.15s;
}
.btn-primary:hover { opacity: 0.9; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-ghost {
  padding: 8px 20px;
  background: transparent;
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-ghost:hover { border-color: var(--color-primary); color: var(--color-primary); }
.btn-sm { padding: 6px 14px; font-size: 13px; }
</style>
