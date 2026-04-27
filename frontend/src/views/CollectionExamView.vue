<template>
  <div class="exam-layout" v-if="loaded">
    <!-- Top Bar -->
    <header class="exam-topbar">
      <div class="exam-topbar-left">
        <router-link to="/exams" class="back-btn">
          <el-icon><ArrowLeft /></el-icon>
        </router-link>
        <div class="exam-info">
          <span class="exam-name">{{ collectionTitle }}</span>
          <span class="exam-section">第 {{ currentIdx + 1 }} / {{ examItems.length }} 套 · {{ currentExamData?.title }}</span>
        </div>
      </div>
      <div class="exam-topbar-center">
        <div class="timer" :class="{ 'timer-warning': timeLeft < 300 }">
          <el-icon><Timer /></el-icon>
          {{ formatTime(timeLeft) }}
        </div>
      </div>
      <div class="exam-topbar-right">
        <button class="sheet-btn" @click="showSheet = !showSheet">
          <el-icon><Grid /></el-icon>
          答题卡 ({{ totalAnswered }}/{{ totalQuestions }})
        </button>
        <button class="submit-btn-top" @click="confirmSubmit">提交全部</button>
      </div>
    </header>

    <!-- Main Content -->
    <div class="exam-body" ref="examBodyRef" :class="{ 'collect-mode': collectMode, 'highlight-mode': highlightMode }" v-if="currentExamData">
      <!-- LEFT: Passage -->
      <div class="passage-col" ref="passageRef">
        <div class="passage-header">
          <h2 class="passage-title">{{ currentExamData.title }}</h2>
          <div class="passage-controls">
            <button class="ctrl-btn" @click="fontSize = Math.max(12, fontSize - 1)">A-</button>
            <button class="ctrl-btn" @click="fontSize = Math.min(20, fontSize + 1)">A+</button>
          </div>
        </div>

        <!-- Translate bubble -->
        <div
          v-if="translateBubble.show"
          class="translate-bubble"
          :style="{ left: translateBubble.x + 'px', top: translateBubble.y + 'px' }"
        >
          <div class="tb-actions">
            <button class="tb-btn" :disabled="translateBubble.loading" @click="doTranslate">
              {{ translateBubble.loading ? '翻译中...' : '翻译' }}
            </button>
            <button class="tb-close" @click="hideTranslateBubble">×</button>
          </div>
          <div v-if="translateBubble.translation" class="tb-result">
            <div class="tb-translation">{{ translateBubble.translation }}</div>
            <div v-if="translateBubble.notes" class="tb-notes">{{ translateBubble.notes }}</div>
          </div>
        </div>

        <!-- Write task: structured visual -->
        <div v-if="currentIsWrite" class="write-task-body" :style="{ fontSize: fontSize + 'px' }">
          <div v-if="currentVisual.hasVisual" class="write-visual-panel">
            <div class="wvp-header">
              <div class="wvp-title">📊 图表数据可视化</div>
              <div class="wvp-sub" v-if="currentVisual.chartType">{{ currentVisual.chartType }}</div>
            </div>
            <div v-if="currentVisual.summary.length" class="wvp-summary">
              <div class="wvp-summary-item" v-for="(s, i) in currentVisual.summary" :key="`sum-${i}`">{{ s }}</div>
            </div>
            <div v-if="currentVisual.chartData.length" class="wvp-charts">
              <div v-if="currentVisual.chartType && currentVisual.chartType.toLowerCase().includes('bar')" ref="writeBarChartRef" class="wvp-chart-canvas"></div>
              <div v-if="currentVisual.chartType && currentVisual.chartType.toLowerCase().includes('pie')" ref="writePieChartRef" class="wvp-chart-canvas"></div>
            </div>
            <div v-if="currentVisual.table.headers.length && currentVisual.table.rows.length" class="wvp-table-wrap">
              <div v-if="currentVisual.table.title" class="wvp-table-title">{{ currentVisual.table.title }}</div>
              <table class="wvp-table">
                <thead><tr><th v-for="(h, hi) in currentVisual.table.headers" :key="hi">{{ h }}</th></tr></thead>
                <tbody><tr v-for="(row, ri) in currentVisual.table.rows" :key="ri"><td v-for="(cell, ci) in row" :key="ci">{{ cell }}</td></tr></tbody>
              </table>
            </div>
          </div>
          <div v-html="currentPassageHtml"></div>
        </div>
        <!-- Reading passage: normal paragraphs -->
        <div v-else class="passage-body" :style="{ fontSize: fontSize + 'px' }">
          <template v-for="(para, idx) in passageParagraphs" :key="idx">
            <template v-if="para.isHeader">
              <hr v-if="idx > 0" class="passage-divider" />
              <div class="passage-section-header">
                <span class="passage-section-badge">{{ para.label }}</span>
              </div>
            </template>
            <p class="passage-para" :data-para-idx="idx" v-html="para.html"></p>
          </template>
        </div>
      </div>

      <!-- RIGHT: Questions -->
      <div class="questions-col">
        <div class="questions-inner" ref="questionsRef">
          <div
            v-for="question in currentQuestions"
            :key="question.globalKey"
            :id="`q-${question.globalKey}`"
            class="question-block"
            :class="{ answered: !!getAnswer(question.globalKey), active: currentQId === question.globalKey }"
            @click.capture="selectQuestion(question.globalKey)"
          >
            <div class="q-number-row">
              <div class="q-number">{{ question.questionNumber }}</div>
              <button
                class="hint-btn"
                :class="{ active: hintQId === question.globalKey }"
                @click.stop="toggleHint(question)"
                :title="question.type === 'write' ? '写作提示' : '查看原文定位'"
              >💡</button>
            </div>

            <!-- TFNG -->
            <template v-if="question.type === 'tfng'">
              <p class="q-text">{{ question.text }}</p>
              <div class="tfng-options">
                <button
                  v-for="opt in tfngOptions"
                  :key="opt.value"
                  class="tfng-btn"
                  :class="{ selected: getAnswer(question.globalKey) === opt.value }"
                  @click="setAnswer(question.globalKey, opt.value)"
                >{{ opt.label }}</button>
              </div>
            </template>

            <!-- MCQ -->
            <template v-else-if="question.type === 'mcq'">
              <p class="q-text">{{ question.text }}</p>
              <div class="mcq-collapse-toggle" @click="toggleMcqCollapse(question.globalKey)">
                <span>{{ collapsedMcq.has(question.globalKey) ? '▶ 展开选项' : '▼ 收起选项' }}</span>
              </div>
              <div class="mcq-options" v-show="!collapsedMcq.has(question.globalKey) || getAnswer(question.globalKey)">
                <button
                  v-for="opt in visibleOptions(question)"
                  :key="opt.label"
                  class="mcq-btn"
                  :class="{ selected: getAnswer(question.globalKey) === opt.label }"
                  @click="collapsedMcq.has(question.globalKey) ? toggleMcqCollapse(question.globalKey) : setAnswer(question.globalKey, opt.label)"
                >
                  <span class="opt-label">{{ opt.label }}</span>
                  <span class="opt-text">{{ opt.text }}</span>
                </button>
              </div>
            </template>

            <!-- Fill -->
            <template v-else-if="question.type === 'fill'">
              <p class="q-text fill-text" v-html="renderFill(escapeHtml(question.text), question.globalKey)"></p>
              <div class="fill-input-wrap">
                <input
                  class="fill-input"
                  :value="getAnswer(question.globalKey)"
                  @input="setAnswer(question.globalKey, $event.target.value)"
                  placeholder="输入答案..."
                  spellcheck="false"
                />
              </div>
            </template>

            <!-- Write -->
            <template v-else-if="question.type === 'write'">
              <div class="write-task-label">
                {{ question.taskType || 'Writing Task' }}
                <span class="word-limit">字数要求：{{ question.wordLimit || 250 }}词以上</span>
              </div>
              <div class="write-input-wrap">
                <textarea
                  class="write-textarea"
                  :value="getAnswer(question.globalKey)"
                  @input="setAnswer(question.globalKey, $event.target.value)"
                  :placeholder="`在此输入你的${question.taskType === 'Task1' ? '图表描述' : '作文'}（至少${question.wordLimit || 250}词）...`"
                  spellcheck="false"
                  rows="12"
                ></textarea>
                <div class="word-count">
                  已输入 {{ wordCount(getAnswer(question.globalKey)) }} 词
                  <span :class="wordCount(getAnswer(question.globalKey)) >= (question.wordLimit || 250) ? 'wc-ok' : 'wc-warn'">
                    / {{ question.wordLimit || 250 }} 词要求
                  </span>
                </div>
              </div>
              <!-- Write hint panel -->
              <div v-if="hintQId === question.globalKey" class="write-hint-panel">
                <div class="hint-panel-title">💡 写作思路提示</div>
                <div v-if="question.answer" class="hint-points">
                  <div class="hint-section-label">📝 写作思路与要点</div>
                  <p>{{ question.answer }}</p>
                </div>
                <div v-if="question.explanation" class="hint-criteria">
                  <div class="hint-section-label">✅ 评分维度提示</div>
                  <p>{{ question.explanation }}</p>
                </div>
              </div>
            </template>

            <!-- Fallback -->
            <template v-else>
              <p class="q-text fill-text">{{ question.text }}</p>
              <div class="fill-input-wrap">
                <input
                  class="fill-input"
                  :value="getAnswer(question.globalKey)"
                  @input="setAnswer(question.globalKey, $event.target.value)"
                  placeholder="输入答案..."
                  spellcheck="false"
                />
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- Loading/Error -->
    <div v-else class="loading-state">
      <div class="loading-spinner"></div>
      <p>正在加载试卷数据...</p>
    </div>

    <!-- Answer Sheet Sidebar -->
    <transition name="slide-right">
      <div v-if="showSheet" class="answer-sheet">
        <div class="sheet-header">
          <h3>答题卡</h3>
          <button class="sheet-close" @click="showSheet = false">
            <el-icon><Close /></el-icon>
          </button>
        </div>
        <div class="sheet-progress">
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: progressPct + '%' }"></div>
          </div>
          <span class="progress-text">{{ totalAnswered }} / {{ totalQuestions }} 已作答</span>
        </div>
        <div v-for="(item, eIdx) in examItems" :key="item.examId" class="sheet-exam-group">
          <div class="sheet-exam-label" :class="{ 'sheet-exam-active': currentIdx === eIdx }" @click="switchExam(eIdx)">
            {{ eIdx + 1 }}. {{ item.examTitle }}
          </div>
          <div class="sheet-grid">
            <button
              v-for="q in getExamQuestions(eIdx)"
              :key="q.globalKey"
              class="sheet-num"
              :class="{ answered: !!getAnswer(q.globalKey), current: currentIdx === eIdx }"
              @click="scrollToQuestion(q.globalKey, eIdx)"
            >{{ q.questionNumber }}</button>
          </div>
        </div>
        <div class="sheet-legend">
          <span class="legend-item"><span class="legend-dot answered"></span>已答</span>
          <span class="legend-item"><span class="legend-dot"></span>未答</span>
        </div>
        <button class="submit-btn-sheet" @click="confirmSubmit">提交全部答卷</button>
      </div>
    </transition>

    <!-- FAB Group -->
    <div class="exam-fabs">
      <!-- Highlight FAB -->
      <div class="highlight-fab" :class="{ active: highlightMode }">
        <transition name="collector-expand">
          <div v-if="highlightMode && showColorPicker" class="color-picker-panel">
            <span class="cp-label">高亮颜色</span>
            <div class="color-swatches">
              <button
                v-for="c in highlightColors" :key="c.value"
                class="color-swatch"
                :class="{ selected: highlightColor === c.value }"
                :style="{ background: c.value }"
                :title="c.name"
                @click.stop="highlightColor = c.value; showColorPicker = false"
              />
            </div>
          </div>
        </transition>
        <div class="fab-row">
          <button v-if="highlightMode" class="color-dot-btn" :style="{ background: highlightColor }" @click.stop="showColorPicker = !showColorPicker" title="选择颜色" />
          <button class="fab-toggle hl-toggle" @click.stop="toggleHighlight" :title="highlightMode ? '退出高亮模式' : '划重点'">
            <span class="fab-icon">📌</span>
            <span class="fab-label">{{ highlightMode ? '退出划线' : '划重点' }}</span>
          </button>
        </div>
      </div>
      <!-- Translate FAB -->
      <div class="translate-fab" :class="{ active: translateMode }">
        <div class="fab-row">
          <button class="fab-toggle tl-toggle" @click.stop="toggleTranslate" :title="translateMode ? '退出翻译模式' : '翻译'">
            <span class="fab-icon">🌐</span>
            <span class="fab-label">{{ translateMode ? '退出翻译' : '翻译' }}</span>
          </button>
        </div>
      </div>
      <!-- Word Collector FAB -->
      <div class="word-collector-fab" :class="{ active: collectMode }">
        <transition name="collector-expand">
          <div v-if="collectMode && collectedWords.size > 0" class="collected-panel">
            <div class="collected-header">
              <span>已选 {{ collectedWords.size }} 个单词</span>
              <button class="clear-btn" @click="clearCollected">清空</button>
            </div>
            <div class="collected-tags">
              <span v-for="w in collectedWords" :key="w" class="collected-tag">
                {{ w }}
                <button class="tag-del" @click.stop="removeCollected(w)">×</button>
              </span>
            </div>
          </div>
        </transition>
        <div class="fab-row">
          <button v-if="collectMode && collectedWords.size > 0" class="fab-send-btn" @click.stop="flushWords" :disabled="flushing">
            {{ flushing ? '发送中...' : `加入生词本 (${collectedWords.size})` }}
          </button>
          <button class="fab-toggle" @click.stop="toggleCollect" :title="collectMode ? '退出选词模式' : '点击选词加入生词本'">
            <span class="fab-icon">🔖</span>
            <span class="fab-label">{{ collectMode ? '退出选词' : '选词入册' }}</span>
            <span v-if="collectMode && collectedWords.size > 0" class="fab-badge">{{ collectedWords.size }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Navigation Buttons -->
    <div class="nav-buttons">
      <button class="btn-secondary nav-btn" :disabled="currentIdx === 0" @click="switchExam(currentIdx - 1)">
        <el-icon><ArrowLeft /></el-icon> 上一篇
      </button>
      <span class="nav-label">{{ currentIdx + 1 }} / {{ examItems.length }}</span>
      <button class="btn-primary nav-btn" v-if="currentIdx < examItems.length - 1" @click="switchExam(currentIdx + 1)">
        下一篇 <el-icon><ArrowRight /></el-icon>
      </button>
      <button class="btn-primary nav-btn submit-final" v-else @click="confirmSubmit">
        提交全部答案
      </button>
    </div>

    <!-- Confirm Submit Dialog -->
    <el-dialog v-model="showConfirm" width="400px" :close-on-click-modal="false" align-center>
      <template #header>
        <div class="dialog-header">
          <span class="dialog-header-icon">📝</span>
          <span>确认提交</span>
        </div>
      </template>
      <div class="confirm-content">
        <div class="confirm-progress-ring">
          <span class="confirm-answered">{{ totalAnswered }}</span>
          <span class="confirm-slash">/</span>
          <span class="confirm-total">{{ totalQuestions }}</span>
        </div>
        <p class="confirm-label">已作答题数</p>
        <div v-if="totalAnswered < totalQuestions" class="confirm-warning">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
          还有 {{ totalQuestions - totalAnswered }} 题未作答
        </div>
        <div v-else class="confirm-ready">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
          全部题目已完成，可以提交
        </div>
      </div>
      <template #footer>
        <el-button @click="showConfirm = false">继续作答</el-button>
        <el-button type="primary" @click="doSubmit" :loading="submitting">确认提交</el-button>
      </template>
    </el-dialog>
  </div>

  <!-- Loading -->
  <div v-else class="exam-loading">
    <el-icon class="loading-icon" size="32"><Loading /></el-icon>
    <p>正在加载试卷集...</p>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api'
import { useExamStore } from '@/stores/exam'
import { useWordStore } from '@/stores/word'
import * as echarts from 'echarts'
import { translateApi } from '@/api/translate'
import { ArrowLeft, ArrowRight, Timer, Grid, Close, Loading } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const examStore = useExamStore()
const wordStore = useWordStore()

const loaded = ref(false)
const collectionTitle = ref('')
const examItems = ref([])     // [{examId, examTitle, examType, questionCount, ...}]
const examDataMap = ref({})   // examId -> {title, passage, questions[]}
const answers = ref({})       // globalKey -> answer
const currentIdx = ref(0)
const fontSize = ref(15)
const showSheet = ref(false)
const showConfirm = ref(false)
const submitting = ref(false)
const timeLeft = ref(3600)
const currentQId = ref(null)
const hintQId = ref(null)
const passageRef = ref(null)
const questionsRef = ref(null)
const examBodyRef = ref(null)

let timer = null

// MCQ collapse state
const collapsedMcq = ref(new Set())

// writing charts
const writeBarChartRef = ref(null)
const writePieChartRef = ref(null)
let writeBarChart = null
let writePieChart = null

const tfngOptions = [
  { label: 'TRUE', value: 'TRUE' },
  { label: 'FALSE', value: 'FALSE' },
  { label: 'NOT GIVEN', value: 'NOT GIVEN' },
]

function parseOptionsString(str) {
  if (typeof str !== 'string') return null
  const lines = str.split('\n').filter(l => l.trim())
  const opts = []
  for (const line of lines) {
    const m = line.trim().match(/^([A-Z])[:：]\s*(.+)$/) || line.trim().match(/^([A-Z])\s{2,}(.+)$/)
    if (m) opts.push({ label: m[1], text: m[2].trim() })
  }
  return opts.length >= 2 ? opts : null
}

function typeLabel(type) {
  return { reading: '阅读', listening: '听力', writing: '写作' }[type] || type
}

function formatTime(sec) {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function wordCount(text) {
  if (!text) return 0
  return text.trim().split(/\s+/).filter(Boolean).length
}

function selectQuestion(qKey) {
  currentQId.value = qKey
}

// ── Data loading ──────────────────────────────────────────────────────

onMounted(async () => {
  const collectionId = route.params.id
  try {
    // Load collection detail
    const detailRes = await request.get(`/exam-collections/${collectionId}`)
    const detail = detailRes.data
    collectionTitle.value = detail.collection.title
    examItems.value = detail.items || []

    // Calculate total duration
    let totalDuration = 0

    // Load each exam's data in parallel
    await Promise.all(examItems.value.map(async (item) => {
      try {
        const [examRes, qRes] = await Promise.all([
          request.get(`/exams/${item.examId}`),
          request.get(`/exams/${item.examId}/questions`),
        ])
        const exam = examRes.data
        const questions = qRes.data || []
        totalDuration += (exam.duration || 60) * 60

        // Parse passage from parseResult
        let passage = ''
        try {
          const parsed = exam.parseResult ? JSON.parse(exam.parseResult) : {}
          if (Array.isArray(parsed.passages) && parsed.passages.length > 0) {
            passage = parsed.passages.join('\n\n')
          }
        } catch { /* ignore */ }

        // Parse options for each question
        const mappedQuestions = questions.map(q => {
          let options
          if (q.options) {
            try {
              const raw = typeof q.options === 'string' ? JSON.parse(q.options) : q.options
              if (typeof raw === 'string') {
                // AI returned options as a plain text string "A: text\nB: text\n..."
                options = parseOptionsString(raw)
              } else if (Array.isArray(raw)) {
                options = raw.map(item => {
                  if (typeof item === 'object' && item !== null && item.label) return item
                  if (typeof item === 'string') return { label: item, text: item }
                  return { label: String(item), text: String(item) }
                })
              } else if (raw && typeof raw === 'object') {
                // Check if it's write meta (taskType/wordLimit) or actual MCQ options
                if (raw.taskType || raw.wordLimit) {
                  // Write task metadata
                  return {
                    id: q.id,
                    globalKey: `${item.examId}_${q.id}`,
                    questionNumber: q.questionNumber,
                    type: q.type || 'fill',
                    text: q.questionText || q.text || '',
                    answer: q.answer || '',
                    explanation: q.explanation || '',
                    locatorText: q.locatorText || '',
                    taskType: raw.taskType,
                    wordLimit: raw.wordLimit,
                    options: undefined,
                  }
                }
                options = Object.entries(raw).map(([label, text]) => ({ label, text: String(text) }))
              }
            } catch {
              // Fallback: try parsing "A: text\nB: text" format
              options = parseOptionsString(q.options)
            }
          }
          return {
            id: q.id,
            globalKey: `${item.examId}_${q.id}`,
            questionNumber: q.questionNumber,
            type: q.type || 'fill',
            text: q.questionText || q.text || '',
            answer: q.answer || '',
            explanation: q.explanation || '',
            locatorText: q.locatorText || '',
            options,
          }
        })

        examDataMap.value[item.examId] = {
          title: exam.title,
          passage,
          questions: mappedQuestions,
        }
      } catch (e) {
        console.error(`Failed to load exam ${item.examId}:`, e)
        examDataMap.value[item.examId] = { title: item.examTitle || '加载失败', passage: '', questions: [] }
      }
    }))

    timeLeft.value = totalDuration || 3600
    loaded.value = true

    // Start timer
    timer = setInterval(() => {
      if (timeLeft.value > 0) timeLeft.value--
      else {
        clearInterval(timer)
        confirmSubmit()
      }
    }, 1000)

    // resize charts on window resize
    window.addEventListener('resize', resizeWriteCharts)

    // click outside to close translate bubble
    document.addEventListener('mousedown', onDocMouseDownForTranslate)
  } catch (e) {
    ElMessage.error('加载试卷集失败: ' + e.message)
    router.push('/exams')
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  examBodyRef.value?.removeEventListener('click', onCollectClick, true)
  examBodyRef.value?.removeEventListener('mouseup', onHighlightMouseUp)
  // Silently flush remaining collected words on page leave
  if (collectedWords.value.size > 0) {
    wordStore.quickAddWords([...collectedWords.value]).catch(() => {})
  }
  window.removeEventListener('resize', resizeWriteCharts)
  disposeWriteCharts()

  passageRef.value?.removeEventListener('mouseup', onPassageMouseUp)
  document.removeEventListener('mousedown', onDocMouseDownForTranslate)
  translateMode.value = false
})

// ── Computed ──────────────────────────────────────────────────────────

const currentExamData = computed(() => {
  if (!examItems.value[currentIdx.value]) return null
  return examDataMap.value[examItems.value[currentIdx.value].examId]
})

const currentQuestions = computed(() => currentExamData.value?.questions || [])

// Parse passage into paragraph objects (with marker headers + locator highlight)
const passageParagraphs = computed(() => {
  const text = currentExamData.value?.passage || ''
  const qs = currentQuestions.value || []
  const hintQ = qs.find(q => q.globalKey === hintQId.value)
  const locator = hintQ?.locatorText?.trim().replace(/\s+/g, ' ') || null
  const MARKER = /^(P\d+\b|【[^】]+】)/

  return text.split('\n\n').filter(Boolean).map(para => {
    const markerMatch = para.match(MARKER)
    const isHeader = !!markerMatch
    const label = markerMatch ? markerMatch[1] : null
    const bodyText = isHeader
      ? (para.includes('\n') ? para.replace(/^[^\n]*\n/, '').trim()
                            : para.replace(/^P\d+\s+\S*\s*/, '').trim())
      : para
    let html = escapeHtml(bodyText).replace(/\n/g, ' ')
    if (locator && locator.length > 3) {
      const escaped = locator.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
      html = html.replace(
        new RegExp(escaped, 'i'),
        m => `<mark class="passage-keyword">${m}</mark>`
      )
    }
    return { html, isHeader, label }
  })
})

const currentIsWrite = computed(() => {
  const qs = currentQuestions.value
  if (qs.some(q => q.type === 'write' || q.taskType)) return true
  const passage = (currentExamData.value?.passage || '').toLowerCase()
  return passage.includes('[visual data summary]') || passage.includes('[table data]') ||
         passage.includes('writing task') || qs.some(q => {
           const t = (q.text || '').toLowerCase()
           return t.includes('writing task') || t.includes('[visual data summary]') || t.includes('[table data]')
         })
})

const currentVisual = computed(() => {
  const raw = currentExamData.value?.passage || ''
  return buildWriteVisual(raw)
})

const currentPassageHtml = computed(() => {
  const raw = currentExamData.value?.passage || ''
  const cleaned = currentVisual.value?.hasVisual ? stripVisualBlocks(raw) : raw
  return renderWritePassage(cleaned)
})

// Answer sheet progress
const progressPct = computed(() => {
  if (!totalQuestions.value) return 0
  return Math.round((totalAnswered.value / totalQuestions.value) * 100)
})

// ── Visual parsing (from ExamView) ──────────────────────────────────
function extractTaggedBlock(text, tag) {
  const escaped = tag.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const re = new RegExp(`\\[${escaped}\\]([\\s\\S]*?)(?=\\n\\[[^\\]]+\\]|$)`, 'i')
  const m = text.match(re)
  return m ? m[1].trim() : ''
}

function buildWriteVisual(raw) {
  const text = (raw || '').replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  const visualBlock = extractTaggedBlock(text, 'Visual Data Summary')
  const tableBlock = extractTaggedBlock(text, 'Table Data')

  // Chart type
  let chartType = ''
  if (visualBlock) {
    const line = visualBlock.split('\n').map(l => l.trim()).find(l => /chartType\s*[:：]/i.test(l))
    if (line) chartType = line.replace(/.*chartType\s*[:：]\s*/i, '').trim()
  }

  // Summary items
  const summary = visualBlock
    ? visualBlock.split('\n').map(l => l.trim()).filter(Boolean).filter(l => !/^chartType\s*[:：]/i.test(l)).map(l => l.replace(/^[-*]\s*/, '')).slice(0, 5)
    : []

  // Table
  let tableTitle = ''
  if (tableBlock) {
    const titleLine = tableBlock.split('\n').map(l => l.trim()).find(l => /^tableTitle\s*[:：]/i.test(l))
    if (titleLine) tableTitle = titleLine.replace(/^tableTitle\s*[:：]\s*/i, '').trim()
  }
  const tableLines = findLongestTableBlock(tableBlock || text)
  const table = parseVisualTable(tableLines, tableTitle)

  // Chart data
  let chartData = extractChartData(visualBlock || text)
  if (!chartData.length && table.rows.length) {
    chartData = extractChartDataFromTable(table)
  }

  return { hasVisual: chartData.length > 0 || table.rows.length > 0, chartType, summary, chartData, table }
}

function stripVisualBlocks(text) {
  if (!text) return ''
  return text
    .replace(/\n?\[Visual Data Summary\][\s\S]*?(?=\n\[[^\]]+\]|$)/i, '\n')
    .replace(/\n?\[Table Data\][\s\S]*?(?=\n\[[^\]]+\]|$)/i, '\n')
    .replace(/\n{3,}/g, '\n\n').trim()
}

function isMarkdownTableLine(line) {
  if (!line || !line.includes('|')) return false
  const cells = parseMarkdownRow(line)
  return cells.length >= 2
}

function parseMarkdownRow(line) {
  const normalized = line.trim().replace(/^\|/, '').replace(/\|$/, '')
  return normalized.split('|').map(c => c.trim())
}

function isMarkdownSeparatorRow(cells) {
  if (!cells.length) return false
  return cells.every(c => /^:?-{3,}:?$/.test(c.replace(/\s+/g, '')))
}

function renderMarkdownTable(lines) {
  const rows = lines
    .map(parseMarkdownRow)
    .filter(r => r.length >= 2)

  if (!rows.length) return ''

  let header = null
  let bodyStart = 0

  if (rows.length >= 2 && isMarkdownSeparatorRow(rows[1])) {
    header = rows[0]
    bodyStart = 2
  }

  const body = rows.slice(bodyStart)
  const maxCols = Math.max(
    header ? header.length : 0,
    ...body.map(r => r.length)
  )
  if (maxCols < 2) return ''

  const fillCols = (row) => [...row, ...Array(Math.max(0, maxCols - row.length)).fill('')]

  let html = '<div class="wtp-table-wrap"><table class="wtp-table">'
  if (header) {
    html += '<thead><tr>'
    for (const cell of fillCols(header)) html += `<th>${escapeHtml(cell)}</th>`
    html += '</tr></thead>'
  }
  html += '<tbody>'
  for (const row of body) {
    html += '<tr>'
    for (const cell of fillCols(row)) html += `<td>${escapeHtml(cell)}</td>`
    html += '</tr>'
  }
  html += '</tbody></table></div>'
  return html
}

function findLongestTableBlock(text) {
  const lines = (text || '').split('\n')
  let best = [], cur = []
  for (const line of lines) {
    const t = line.trim()
    if (isMarkdownTableLine(t)) { cur.push(t); continue }
    if (cur.length > best.length) best = [...cur]
    cur = []
  }
  if (cur.length > best.length) best = cur
  return best.length >= 2 ? best : []
}

function parseVisualTable(tableLines, tableTitle = '') {
  if (!tableLines.length) return { headers: [], rows: [], title: tableTitle }
  const rows = tableLines.map(parseMarkdownRow).filter(r => r.length >= 2)
  if (!rows.length) return { headers: [], rows: [], title: tableTitle }
  const isSep = row => row.every(c => /^[-:]+$/.test(c))
  const headers = rows[0]
  const dataRows = rows.slice(1).filter(r => !isSep(r))
  return { headers, rows: dataRows, title: tableTitle }
}

function extractChartData(text) {
  if (!text) return []
  const items = []
  const lines = text.split('\n').map(l => l.trim()).filter(Boolean)
  for (const line of lines) {
    const m = line.match(/^(.+?)\s*[:：]\s*([\d.]+)\s*%?$/i)
    if (m && !/chartType|tableTitle/i.test(m[1])) {
      items.push({ label: m[1].trim(), value: parseFloat(m[2]) })
    }
  }
  return items
}

function extractChartDataFromTable(table) {
  if (!table.rows.length || table.headers.length < 2) return []
  return table.rows.map(row => {
    const label = row[0] || ''
    const val = parseFloat((row[row.length - 1] || '').replace('%', ''))
    return isNaN(val) ? null : { label, value: val }
  }).filter(Boolean)
}

function escapeHtml(str) {
  return (str || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function renderWritePassage(raw) {
  const normalized = (raw || '').replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  const lines = normalized.split('\n')
  if (!lines.some(l => l.trim())) return ''
  let html = ''
  let inList = false
  const closeList = () => { if (inList) { html += '</ul>'; inList = false } }

  for (let i = 0; i < lines.length; i++) {
    const line = (lines[i] || '').trim()
    if (!line) {
      closeList()
      html += '<div class="wtp-gap"></div>'
      continue
    }

    if (isMarkdownTableLine(line)) {
      closeList()
      const tableLines = [line]
      while (i + 1 < lines.length && isMarkdownTableLine((lines[i + 1] || '').trim())) {
        tableLines.push((lines[i + 1] || '').trim())
        i++
      }
      html += renderMarkdownTable(tableLines)
      continue
    }

    if (/^\[(Task Prompt|Visual Data Summary|Table Data)\]$/i.test(line)) {
      closeList()
      html += `<div class="wtp-block-tag">${escapeHtml(line)}</div>`
      continue
    }

    if (/^(Requirements?|Instructions?|Notes?|Marks?|Task\s*\d*):?$/i.test(line)) {
      closeList()
      html += `<div class="wtp-section">${escapeHtml(line)}</div>`
      inList = true
      html += '<ul class="wtp-list">'
      continue
    }

    if (/^Directions?:/i.test(line)) {
      closeList()
      const body = line.replace(/^Directions?:\s*/i, '')
      html += `<p class="wtp-directions"><span class="wtp-dir-label">Directions:</span> ${escapeHtml(body)}</p>`
      continue
    }

    if (/^(Topic|Task\s*\d+|Subject|Title):/i.test(line)) {
      closeList()
      const [label, ...rest] = line.split(':')
      html += `<p class="wtp-topic"><span class="wtp-topic-label">${escapeHtml(label)}:</span> ${escapeHtml(rest.join(':').trim())}</p>`
      continue
    }

    if (i === 0 || /^[A-Z][^.!?]{0,80}$/.test(line) && lines.length > 1 && i === 0) {
      closeList()
      html += `<h3 class="wtp-title">${escapeHtml(line)}</h3>`
      continue
    }

    const isBullet = (/^[-•]\s+/.test(line) || /^[A-Za-z一-龥]/.test(line)) && line.length < 140
      && (inList || /^(About|Write|Use|Include|Describe|Your|Who|What|Why|How|When|Where|A\s)/.test(line))
    if (isBullet) {
      if (!inList) {
        inList = true
        html += '<ul class="wtp-list">'
      }
      html += `<li>${escapeHtml(line.replace(/^[-•]\s+/, ''))}</li>`
      continue
    }

    closeList()
    html += `<p class="wtp-para">${escapeHtml(line)}</p>`
  }
  closeList()
  return html.replace(/(?:<div class="wtp-gap"><\/div>){2,}/g, '<div class="wtp-gap"></div>')
}

function disposeWriteCharts() {
  if (writeBarChart) { writeBarChart.dispose(); writeBarChart = null }
  if (writePieChart) { writePieChart.dispose(); writePieChart = null }
}

function renderWriteCharts() {
  const data = currentVisual.value?.chartData || []
  const chartType = (currentVisual.value?.chartType || '').toLowerCase()
  if (!currentIsWrite.value || !data.length) {
    disposeWriteCharts()
    return
  }

  const shouldRenderBar = chartType.includes('bar')
  const shouldRenderPie = chartType.includes('pie')
  if (!shouldRenderBar && !shouldRenderPie) return

  if (shouldRenderBar && writeBarChartRef.value) {
    if (!writeBarChart) writeBarChart = echarts.init(writeBarChartRef.value)
    const labels = data.map(d => d.label)
    const values = data.map(d => Number(d.value) || 0)
    writeBarChart.setOption({
      animationDuration: 450,
      color: ['#4AA36F'],
      grid: { left: 12, right: 12, top: 28, bottom: 36, containLabel: true },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params) => {
          const p = Array.isArray(params) ? params[0] : params
          const item = data[p?.dataIndex ?? 0]
          return `${item?.label || ''}<br/>${item?.displayValue || p?.value || ''}`
        },
      },
      xAxis: {
        type: 'category',
        data: labels,
        axisLabel: { color: '#475569', fontSize: 11, interval: 0, rotate: labels.length > 4 ? 25 : 0 },
        axisTick: { alignWithLabel: true },
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: '#64748B', fontSize: 11 },
        splitLine: { lineStyle: { color: '#E2E8F0' } },
      },
      series: [{ type: 'bar', data: values, barMaxWidth: 28, itemStyle: { borderRadius: [6, 6, 0, 0] } }],
    }, true)
  }

  if (shouldRenderPie && writePieChartRef.value) {
    if (!writePieChart) writePieChart = echarts.init(writePieChartRef.value)
    const colorPalette = ['#2E8B57', '#3CAEA3', '#F6C85F', '#F08A5D', '#6A89CC', '#B8DE6F', '#7DCEA0', '#5DADE2']
    writePieChart.setOption({
      animationDuration: 450,
      color: colorPalette,
      tooltip: {
        trigger: 'item',
        formatter: (p) => {
          const item = data[p?.dataIndex ?? 0]
          return `${item?.label || p?.name}<br/>${item?.displayValue || p?.value}`
        },
      },
      legend: { bottom: 0, left: 'center', textStyle: { color: '#475569', fontSize: 11 } },
      series: [{
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['50%', '42%'],
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 1 },
        label: {
          formatter: ({ name, percent }) => `${name}\n${Math.round(percent)}%`,
          color: '#334155',
          fontSize: 10,
        },
        data: data.map(d => ({ name: d.label, value: Math.max(Math.abs(Number(d.value) || 0), 0.0001) })),
      }],
    }, true)
  }
}

function resizeWriteCharts() {
  writeBarChart?.resize()
  writePieChart?.resize()
}

watch(
  () => [currentIdx.value, currentIsWrite.value, currentVisual.value?.chartData?.map(i => `${i.label}:${i.value}`).join('|')],
  async () => {
    await nextTick()
    requestAnimationFrame(() => renderWriteCharts())
  },
  { immediate: true }
)

const totalQuestions = computed(() => {
  return examItems.value.reduce((sum, item) => {
    const data = examDataMap.value[item.examId]
    return sum + (data?.questions?.length || 0)
  }, 0)
})

const totalAnswered = computed(() => {
  return Object.values(answers.value).filter(v => v && String(v).trim()).length
})

function getExamQuestions(idx) {
  const item = examItems.value[idx]
  if (!item) return []
  return examDataMap.value[item.examId]?.questions || []
}

function examQuestionCount(idx) {
  return getExamQuestions(idx).length
}

function examAnsweredCount(idx) {
  const qs = getExamQuestions(idx)
  return qs.filter(q => answers.value[q.globalKey] && String(answers.value[q.globalKey]).trim()).length
}

// ── Answer management ─────────────────────────────────────────────────

function getAnswer(key) { return answers.value[key] || '' }

function setAnswer(key, val) {
  answers.value[key] = val
  selectQuestion(key)
}

// ── Navigation ────────────────────────────────────────────────────────

function switchExam(idx) {
  if (idx >= 0 && idx < examItems.value.length) {
    currentIdx.value = idx
    currentQId.value = null
    hintQId.value = null
    showSheet.value = false
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

function scrollToQuestion(qKey, targetExamIdx) {
  if (typeof targetExamIdx === 'number' && targetExamIdx !== currentIdx.value) {
    switchExam(targetExamIdx)
  }
  nextTick(() => {
    const el = document.getElementById(`q-${qKey}`)
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    currentQId.value = qKey
    showSheet.value = false
  })
}

function toggleHint(question) {
  const qKey = question.globalKey
  if (hintQId.value === qKey) {
    hintQId.value = null
    return
  }
  hintQId.value = qKey
  if (question.type !== 'write') {
    nextTick(() => {
      const hl = passageRef.value?.querySelector('.passage-keyword')
      if (hl) hl.scrollIntoView({ behavior: 'smooth', block: 'center' })
    })
  }
}

function renderFill(text) {
  const BLANK = `<span class="fill-blank">[    ]</span>`
  const replaced = (text || '').replace(/_{3,}/g, BLANK)
  if (replaced === text) {
    return `${text} ${BLANK}`.replace(/\n/g, '<br/>')
  }
  return replaced.replace(/\n/g, '<br/>')
}

// MCQ helpers
function toggleMcqCollapse(qKey) {
  const s = new Set(collapsedMcq.value)
  if (s.has(qKey)) s.delete(qKey); else s.add(qKey)
  collapsedMcq.value = s
}

function visibleOptions(question) {
  const opts = question.options || []
  if (!collapsedMcq.value.has(question.globalKey)) return opts
  const ans = getAnswer(question.globalKey)
  if (!ans) return []
  return opts.filter(o => o.label === ans)
}

// ── word collector ─────────────────────────────────────
const collectMode = ref(false)
const collectedWords = ref(new Set())
const flushing = ref(false)

function captureWordAt(e) {
  const sel = window.getSelection()
  let word = sel?.toString().trim()
  if (!word) {
    let range = null
    if (document.caretRangeFromPoint) {
      range = document.caretRangeFromPoint(e.clientX, e.clientY)
    } else if (document.caretPositionFromPoint) {
      const pos = document.caretPositionFromPoint(e.clientX, e.clientY)
      if (pos) {
        range = document.createRange()
        range.setStart(pos.offsetNode, pos.offset)
        range.setEnd(pos.offsetNode, pos.offset)
      }
    }
    if (range) {
      try { range.expand('word') } catch {}
      word = range.toString().trim()
    }
  }
  if (!word) return null
  word = word.replace(/[^a-zA-Z'-]/g, '').trim().toLowerCase()
  return word.length >= 2 ? word : null
}

function onCollectClick(e) {
  const word = captureWordAt(e)
  if (!word) return
  e.preventDefault()
  e.stopPropagation()
  const prev = collectedWords.value.size
  const next = new Set(collectedWords.value)
  if (next.has(word)) {
    next.delete(word)
    collectedWords.value = next
  } else {
    next.add(word)
    collectedWords.value = next
    if (next.size > prev) ElMessage.success({ message: `已添加「${word}」`, duration: 1200 })
  }
}

function clearCollected() {
  collectedWords.value = new Set()
}

function removeCollected(word) {
  const next = new Set(collectedWords.value)
  next.delete(word)
  collectedWords.value = next
}

function toggleCollect() {
  collectMode.value = !collectMode.value
  if (collectMode.value) {
    if (highlightMode.value) {
      highlightMode.value = false
      showColorPicker.value = false
      examBodyRef.value?.removeEventListener('mouseup', onHighlightMouseUp)
    }
    if (translateMode.value) {
      translateMode.value = false
      passageRef.value?.removeEventListener('mouseup', onPassageMouseUp)
      hideTranslateBubble()
    }
    examBodyRef.value?.addEventListener('click', onCollectClick, true)
    ElMessage.info({ message: '选词模式已开启，点击文中任意单词加入生词本', duration: 2500 })
  } else {
    examBodyRef.value?.removeEventListener('click', onCollectClick, true)
  }
}

async function flushWords() {
  if (collectedWords.value.size === 0) return
  flushing.value = true
  try {
    const words = [...collectedWords.value]
    await wordStore.quickAddWords(words)
    ElMessage.success(`${words.length} 个单词已提交，AI 将自动添加到生词本`)
    collectedWords.value = new Set()
    collectMode.value = false
    examBodyRef.value?.removeEventListener('click', onCollectClick, true)
  } catch {
    ElMessage.error('添加失败，请稍后重试')
  }
  flushing.value = false
}

// ── translate overlay ───────────────────────────────────
const translateMode = ref(false)
const translateBubble = ref({
  show: false,
  x: 0,
  y: 0,
  selectedText: '',
  loading: false,
  translation: '',
  notes: '',
})

function hideTranslateBubble() {
  translateBubble.value = { ...translateBubble.value, show: false, loading: false }
}

function onDocMouseDownForTranslate(ev) {
  if (!translateBubble.value.show) return
  const el = ev.target
  if (el?.closest?.('.translate-bubble')) return
  if (!passageRef.value?.contains(el)) hideTranslateBubble()
}

function onPassageMouseUp(e) {
  if (!translateMode.value) return
  if (highlightMode.value) return
  if (collectMode.value) return
  const sel = window.getSelection()
  if (!sel || sel.isCollapsed || !sel.rangeCount) return
  const text = sel.toString().trim()
  if (!text) return
  if (!passageRef.value?.contains(sel.anchorNode)) return

  const range = sel.getRangeAt(0)
  const rect = range.getBoundingClientRect()
  const hostRect = passageRef.value.getBoundingClientRect()
  const scrollTop = passageRef.value?.scrollTop || 0

  const x = Math.min(Math.max(rect.left - hostRect.left, 8), hostRect.width - 120)
  const y = Math.max(rect.bottom - hostRect.top + scrollTop + 6, 8)

  translateBubble.value = {
    ...translateBubble.value,
    show: true,
    x,
    y,
    selectedText: text,
    loading: false,
    translation: '',
    notes: '',
  }
}

async function doTranslate() {
  const selText = translateBubble.value.selectedText
  if (!selText) return
  const passageText = currentExamData.value?.passage || ''
  translateBubble.value = { ...translateBubble.value, loading: true, translation: '', notes: '' }
  try {
    const res = await translateApi.translate(passageText, selText)
    translateBubble.value = {
      ...translateBubble.value,
      loading: false,
      translation: res?.translation || '',
      notes: res?.notes || '',
    }
  } catch (e) {
    console.error('[doTranslate] error:', e)
    translateBubble.value = { ...translateBubble.value, loading: false }
    ElMessage.error(e?.message || '翻译失败，请重试')
  }
}

function toggleTranslate() {
  translateMode.value = !translateMode.value
  if (translateMode.value) {
    if (highlightMode.value) {
      highlightMode.value = false
      showColorPicker.value = false
      examBodyRef.value?.removeEventListener('mouseup', onHighlightMouseUp)
    }
    if (collectMode.value) {
      collectMode.value = false
      examBodyRef.value?.removeEventListener('click', onCollectClick, true)
    }
    passageRef.value?.addEventListener('mouseup', onPassageMouseUp)
    ElMessage.info({ message: '翻译模式已开启：选中文章中的句子/短语后点击翻译', duration: 2500 })
  } else {
    passageRef.value?.removeEventListener('mouseup', onPassageMouseUp)
    hideTranslateBubble()
  }
}

// ── text highlighter ───────────────────────────────────
const highlightMode = ref(false)
const highlightColor = ref('rgba(166,110,60,0.32)')
const showColorPicker = ref(false)
const highlightColors = [
  { name: '棕色（默认）', value: 'rgba(166,110,60,0.32)' },
  { name: '黄色', value: 'rgba(250,204,21,0.45)' },
  { name: '绿色', value: 'rgba(74,222,128,0.40)' },
  { name: '蓝色', value: 'rgba(96,165,250,0.40)' },
  { name: '粉色', value: 'rgba(244,114,182,0.40)' },
  { name: '橙色', value: 'rgba(251,146,60,0.40)' },
]

function createHighlightSpan() {
  const span = document.createElement('span')
  span.className = 'text-highlight'
  span.style.backgroundColor = highlightColor.value
  span.title = '点击可移除高亮'
  span.addEventListener('click', (ev) => {
    ev.stopPropagation()
    const parent = span.parentNode
    if (!parent) return
    while (span.firstChild) parent.insertBefore(span.firstChild, span)
    parent.removeChild(span)
  }, { once: true })
  return span
}

function getTextNodesInRange(range) {
  const results = []
  const container = range.commonAncestorContainer
  if (container.nodeType === Node.TEXT_NODE) {
    results.push({ node: container, start: range.startOffset, end: range.endOffset })
    return results
  }
  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT)
  let node
  while ((node = walker.nextNode())) {
    if (!range.intersectsNode(node)) continue
    const start = node === range.startContainer ? range.startOffset : 0
    const end = node === range.endContainer ? range.endOffset : node.length
    if (start < end) results.push({ node, start, end })
  }
  return results
}

function onHighlightMouseUp(e) {
  const sel = window.getSelection()
  if (!sel || sel.isCollapsed || !sel.rangeCount) return
  const selectedText = sel.toString().trim()
  if (!selectedText) return
  if (e.target.closest?.('.exam-fabs')) return
  try {
    const range = sel.getRangeAt(0)
    try {
      const span = createHighlightSpan()
      range.surroundContents(span)
      sel.removeAllRanges()
      return
    } catch { /* crosses element boundary, fall through */ }
    const textNodes = getTextNodesInRange(range)
    for (const info of textNodes) {
      const subRange = document.createRange()
      subRange.setStart(info.node, info.start)
      subRange.setEnd(info.node, info.end)
      const span = createHighlightSpan()
      subRange.surroundContents(span)
    }
    sel.removeAllRanges()
  } catch {
    sel.removeAllRanges()
  }
}

function toggleHighlight() {
  highlightMode.value = !highlightMode.value
  showColorPicker.value = false
  if (highlightMode.value) {
    if (translateMode.value) {
      translateMode.value = false
      passageRef.value?.removeEventListener('mouseup', onPassageMouseUp)
      hideTranslateBubble()
    }
    if (collectMode.value) {
      collectMode.value = false
      examBodyRef.value?.removeEventListener('click', onCollectClick, true)
    }
    examBodyRef.value?.addEventListener('mouseup', onHighlightMouseUp)
    ElMessage.info({ message: '划线模式已开启，选中文字即可高亮；点击高亮可移除', duration: 2500 })
  } else {
    examBodyRef.value?.removeEventListener('mouseup', onHighlightMouseUp)
  }
}

// ── Band score calculation ─────────────────────────────────────────────
function getBandScore(correct, total) {
  const ratio = correct / total
  if (ratio >= 0.925) return 9.0
  if (ratio >= 0.875) return 8.5
  if (ratio >= 0.825) return 8.0
  if (ratio >= 0.775) return 7.5
  if (ratio >= 0.700) return 7.0
  if (ratio >= 0.625) return 6.5
  if (ratio >= 0.550) return 6.0
  if (ratio >= 0.475) return 5.5
  if (ratio >= 0.400) return 5.0
  if (ratio >= 0.325) return 4.5
  return 4.0
}

// ── Submit ────────────────────────────────────────────────────────────

function confirmSubmit() { showConfirm.value = true }

async function doSubmit() {
  submitting.value = true
  const startTime = Date.now()
  const timeUsed = timeLeft.value > 0
    ? Math.max(1, Math.round((examItems.value.reduce((s, i) => s + ((examDataMap.value[i.examId]?.questions?.length || 0) > 0 ? (i.examDuration || 60) * 60 : 0), 0) - timeLeft.value)))
    : 60

  try {
    const allResults = []
    let lastRecordId = null
    let lastExamId = null

    for (const item of examItems.value) {
      const data = examDataMap.value[item.examId]
      if (!data || !data.questions.length) continue
      const examAnswers = {}
      for (const q of data.questions) {
        const ans = answers.value[q.globalKey]
        if (ans && String(ans).trim()) examAnswers[q.id] = ans
      }
      if (Object.keys(examAnswers).length === 0) continue
      try {
        const res = await request.post('/exams/submit', {
          examId: item.examId,
          answers: examAnswers,
          timeUsed,
        })
        const d = res.data
        allResults.push({ examId: item.examId, title: item.examTitle, ...d })
        if (d.recordId) { lastRecordId = d.recordId; lastExamId = item.examId }

        // Trigger AI writing grading for write questions
        const writeQuestions = (d.questions || []).filter(q => q.isWrite && examAnswers[q.id])
        if (writeQuestions.length > 0) {
          for (const wq of writeQuestions) {
            try {
              const gradeRes = await request.post('/exams/grade-writing', {
                taskPrompt: wq.questionText || wq.text || '',
                userEssay: examAnswers[wq.id],
                wordLimit: 250,
              })
              wq.aiGrade = gradeRes.data
              // Save AI feedback to backend
              if (d.recordId && gradeRes.data) {
                try {
                  await request.patch(`/exams/records/${d.recordId}/ai-feedback`, {
                    feedback: { [wq.id]: gradeRes.data },
                    band: gradeRes.data.overallBand || gradeRes.data.band || null,
                  })
                } catch { /* ignore */ }
              }
            } catch (e) {
              console.error('Writing grade failed:', e)
            }
          }
        }
      } catch (e) {
        console.error(`Submit failed for exam ${item.examId}:`, e)
      }
    }

    if (allResults.length > 0) {
      const totalCorrect = allResults.reduce((s, r) => s + (r.correct || 0), 0)
      const totalAll = allResults.reduce((s, r) => s + (r.total || 0), 0)

      // Merge all questions from all results into one examResult for ResultView
      const mergedQuestions = []
      for (const r of allResults) {
        for (const q of (r.questions || [])) {
          mergedQuestions.push({
            id: String(q.id),
            type: q.type,
            questionNumber: q.questionNumber,
            text: q.questionText || q.text || '',
            answer: q.answer || '',
            userAnswer: q.userAnswer || '',
            isCorrect: !!q.isCorrect,
            isWrite: !!q.isWrite,
            explanation: q.explanation || '',
            locatorText: q.locatorText || '',
            aiGrade: q.aiGrade || undefined,
          })
        }
      }

      // Collect passages from all submitted exams for ResultView display
      const collectionPassages = []
      for (const r of allResults) {
        const data = examDataMap.value[r.examId]
        if (data?.passage) {
          collectionPassages.push({
            examId: r.examId,
            title: r.title || data.title,
            passage: data.passage,
          })
        }
      }

      const collectionId = route.params.id
      const childRecordIds = allResults.map(r => r.recordId).filter(Boolean)

      // Set examStore.examResult so ResultView can display it
      examStore.examResult = {
        examId: lastExamId || allResults[0]?.examId,
        examTitle: collectionTitle.value,
        questions: mergedQuestions,
        correct: totalCorrect,
        total: totalAll,
        score: Math.round((totalCorrect / (totalAll || 1)) * 40),
        band: totalAll > 0 ? getBandScore(totalCorrect, totalAll) : 0,
        timeUsed,
        submittedAt: new Date().toISOString(),
        isCollection: true,
        collectionId,
        passages: collectionPassages,
        childRecordIds,
      }

      // Persist full result to localStorage for page refresh
      try {
        localStorage.setItem('ielts_last_result', JSON.stringify(examStore.examResult))
        localStorage.setItem(`ielts_collection_result_${collectionId}`, JSON.stringify(examStore.examResult))
      } catch { /* ignore quota errors */ }

      // Add consolidated collection history entry (replace individual entries)
      const historyEntry = {
        isCollection: true,
        collectionId,
        childRecordIds,
        examId: lastExamId || allResults[0]?.examId,
        examTitle: collectionTitle.value,
        band: examStore.examResult.band,
        correct: totalCorrect,
        total: totalAll,
        timeUsed,
        submittedAt: examStore.examResult.submittedAt,
      }
      examStore.examHistory.unshift(historyEntry)
      examStore.examHistory = examStore.examHistory.slice(0, 50)
      localStorage.setItem('ielts_exam_history', JSON.stringify(examStore.examHistory))

      // Save collection metadata for history merging after backend reload
      try {
        const collectionMeta = JSON.parse(localStorage.getItem('ielts_collection_entries') || '[]')
        collectionMeta.unshift({ collectionId, childRecordIds, submittedAt: examStore.examResult.submittedAt })
        localStorage.setItem('ielts_collection_entries', JSON.stringify(collectionMeta.slice(0, 50)))
      } catch { /* ignore */ }

      ElMessage.success(`提交完成！共 ${totalCorrect}/${totalAll} 题正确`)
      if (lastExamId) {
        router.push(`/exam/${lastExamId}/result`)
      } else {
        router.push('/exams')
      }
    } else {
      ElMessage.warning('没有可提交的答案')
    }
  } catch (e) {
    ElMessage.error('提交失败: ' + e.message)
  } finally {
    submitting.value = false
    showConfirm.value = false
  }
}
</script>

<style scoped>
/* ── Layout ──────────────────────────────────────────────────────────── */
.exam-layout { display: flex; flex-direction: column; min-height: 100vh; background: var(--bg-primary); }

.btn-primary {
  padding: 8px 20px;
  border-radius: var(--radius-full);
  border: none;
  background: var(--color-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-primary:hover:not(:disabled) { background: var(--color-primary-dark, #155e3d); }
.btn-primary:disabled { opacity: 0.6; cursor: default; }

.btn-secondary {
  padding: 8px 20px;
  border-radius: var(--radius-full);
  border: 1.5px solid var(--border-color);
  background: var(--bg-white);
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-secondary:hover:not(:disabled) { border-color: var(--color-primary); color: var(--color-primary); }
.btn-secondary:disabled { opacity: 0.6; cursor: default; }

.exam-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px;
  background: var(--bg-white);
  border-bottom: 1px solid var(--border-color);
  position: sticky;
  top: 0;
  z-index: 100;
}
.exam-topbar-left { display: flex; align-items: center; gap: 12px; }
.exam-topbar-center { display: flex; align-items: center; }
.exam-topbar-right { display: flex; align-items: center; gap: 12px; }
.back-btn { display: flex; align-items: center; justify-content: center; width: 32px; height: 32px; border-radius: 50%; color: var(--text-muted); transition: all 0.15s; }
.back-btn:hover { background: var(--bg-primary); color: var(--text-primary); }
.exam-info { display: flex; flex-direction: column; }
.exam-name { font-size: 14px; font-weight: 700; color: var(--text-primary); }
.exam-section { font-size: 11px; color: var(--text-muted); }

.timer { display: flex; align-items: center; gap: 6px; font-size: 16px; font-weight: 700; font-variant-numeric: tabular-nums; color: var(--text-primary); }
.timer-warning { color: #EF4444; }
.sheet-btn { display: flex; align-items: center; gap: 6px; padding: 6px 14px; border-radius: var(--radius-full); border: 1.5px solid var(--border-color); background: var(--bg-white); font-size: 13px; font-weight: 500; cursor: pointer; transition: all 0.15s; }
.sheet-btn:hover { border-color: var(--color-primary); color: var(--color-primary); }
.submit-btn-top { padding: 6px 18px; border-radius: var(--radius-full); border: none; background: var(--color-primary); color: white; font-size: 13px; font-weight: 600; cursor: pointer; transition: background 0.15s; }
.submit-btn-top:hover { background: var(--color-primary-dark, #155e3d); }

/* ── Exam Switcher ───────────────────────────────────────────────────── */
.exam-switcher {
  display: flex; gap: 4px; padding: 8px 24px; background: var(--bg-white);
  border-bottom: 1px solid var(--border-color); overflow-x: auto;
}
.switcher-tab {
  display: flex; align-items: center; gap: 6px; padding: 6px 14px;
  border-radius: var(--radius-full); border: 1.5px solid var(--border-color);
  background: var(--bg-white); font-size: 12px; cursor: pointer;
  transition: all 0.15s; white-space: nowrap; flex-shrink: 0;
}
.switcher-tab:hover { border-color: var(--color-primary); }
.switcher-tab.active { background: var(--color-primary); color: white; border-color: var(--color-primary); }
.switcher-tab.answered { border-color: #22C55E; }
.switcher-tab.active.answered { background: #16A34A; border-color: #16A34A; }
.sw-idx { font-weight: 700; }
.sw-title { max-width: 120px; overflow: hidden; text-overflow: ellipsis; }
.sw-badge { font-size: 10px; padding: 1px 6px; border-radius: 10px; }
.switcher-tab.active .sw-badge { background: rgba(255,255,255,0.25); color: white; }
.sw-count { font-size: 10px; opacity: 0.7; }

/* ── Main Body ───────────────────────────────────────────────────────── */
.exam-body {
  display: grid; grid-template-columns: 1fr 1fr; flex: 1;
  max-width: 1400px; margin: 0 auto; width: 100%;
}
.passage-col { padding: 24px; border-right: 1px solid var(--border-color); overflow-y: auto; max-height: calc(100vh - 120px); position: sticky; top: 56px; }
.passage-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.passage-title { font-size: 16px; font-weight: 700; color: var(--text-primary); }
.passage-controls { display: flex; gap: 4px; }
.ctrl-btn { width: 28px; height: 28px; border-radius: var(--radius-md); border: 1px solid var(--border-color); background: var(--bg-white); cursor: pointer; font-size: 12px; font-weight: 600; }
.passage-body { line-height: 1.8; color: var(--text-secondary); }
.passage-para { margin-bottom: 12px; text-indent: 0; }

.passage-divider {
  border: none;
  border-top: 1px dashed var(--border-color);
  margin: 14px 0;
}
.passage-section-header { display: flex; justify-content: center; margin: 12px 0; }
.passage-section-badge {
  font-size: 12px;
  font-weight: 700;
  color: var(--color-primary);
  background: rgba(27,67,50,0.06);
  border: 1px solid rgba(27,67,50,0.16);
  padding: 2px 10px;
  border-radius: 999px;
}

.passage-para :deep(.passage-keyword) {
  background: #D4F5E9;
  color: #0D5932;
  border-radius: 3px;
  padding: 1px 4px;
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 2px;
  transition: background 0.2s;
}

.questions-col { padding: 24px; overflow-y: auto; max-height: calc(100vh - 120px); }
.questions-inner { display: flex; flex-direction: column; gap: 16px; }

/* ── Question blocks ─────────────────────────────────────────────────── */
.question-block {
  padding: 16px; border-radius: var(--radius-lg); border: 1.5px solid var(--border-color);
  background: var(--bg-white); transition: all 0.15s;
}
.question-block.answered { border-color: #86EFAC; background: #F0FDF4; }
.question-block.active { border-color: var(--color-accent, #52B788); box-shadow: 0 8px 24px rgba(0,0,0,0.06); }
.q-number-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.q-number { width: 28px; height: 28px; border-radius: 50%; background: var(--color-primary); color: white; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 700; }
.q-text { font-size: 14px; line-height: 1.6; color: var(--text-primary); margin-bottom: 10px; }

.hint-btn {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 1.5px solid var(--border-color);
  background: var(--bg-white);
  cursor: pointer;
  font-size: 14px;
  transition: all 0.15s;
}
.hint-btn:hover { border-color: var(--color-primary); }
.hint-btn.active { background: rgba(27,67,50,0.08); border-color: var(--color-primary); }

.tfng-options { display: flex; gap: 8px; flex-wrap: wrap; }
.tfng-btn {
  padding: 6px 16px; border-radius: var(--radius-full); border: 1.5px solid var(--border-color);
  background: var(--bg-white); font-size: 13px; font-weight: 500; cursor: pointer; transition: all 0.15s;
}
.tfng-btn:hover { border-color: var(--color-primary); color: var(--color-primary); }
.tfng-btn.selected { background: var(--color-primary); color: white; border-color: var(--color-primary); }

.mcq-options { display: flex; flex-direction: column; gap: 6px; }
.mcq-collapse-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  font-size: 12px;
  color: var(--text-muted, #999);
  margin-bottom: 6px;
  user-select: none;
  transition: color 0.15s;
}
.mcq-collapse-toggle:hover { color: var(--color-primary); }
.mcq-btn {
  display: flex; align-items: flex-start; gap: 10px; padding: 10px 14px;
  border-radius: var(--radius-md); border: 1.5px solid var(--border-color);
  background: var(--bg-white); text-align: left; cursor: pointer; transition: all 0.15s;
}
.mcq-btn:hover { border-color: var(--color-primary); }
.mcq-btn.selected { background: var(--color-primary); color: white; border-color: var(--color-primary); }
.opt-label { font-weight: 700; flex-shrink: 0; }
.opt-text { font-size: 13px; line-height: 1.5; }

.fill-input-wrap { margin-top: 6px; }
.fill-input {
  width: 100%; padding: 8px 12px; border-radius: var(--radius-md);
  border: 1.5px solid var(--border-color); font-size: 14px; background: var(--bg-white);
  transition: border-color 0.15s; outline: none;
}
.fill-input:focus { border-color: var(--color-primary); }

.fill-blank {
  font-family: monospace;
  color: var(--text-muted);
  padding: 0 4px;
}

.write-task-label { font-size: 14px; font-weight: 600; color: var(--color-primary); margin-bottom: 8px; }
.word-limit { font-weight: 400; color: var(--text-muted); font-size: 12px; margin-left: 8px; }
.write-input-wrap { margin-top: 6px; }
.write-textarea {
  width: 100%; padding: 12px; border-radius: var(--radius-md);
  border: 1.5px solid var(--border-color); font-size: 14px; line-height: 1.7;
  resize: vertical; min-height: 200px; outline: none; font-family: inherit;
}
.write-textarea:focus { border-color: var(--color-primary); }
.word-count { font-size: 12px; color: var(--text-muted); margin-top: 4px; text-align: right; }
.wc-ok { color: #2E7D32; font-weight: 600; }
.wc-warn { color: #E65100; }

/* ── Navigation buttons ──────────────────────────────────────────────── */
.nav-buttons {
  display: flex; align-items: center; justify-content: center; gap: 16px;
  padding: 16px 24px; background: var(--bg-white); border-top: 1px solid var(--border-color);
  position: sticky; bottom: 0; z-index: 50;
}
.nav-btn { display: flex; align-items: center; gap: 6px; padding: 8px 20px; border-radius: var(--radius-full); font-size: 13px; font-weight: 600; cursor: pointer; }
.nav-label { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.submit-final { background: #16A34A; }
.submit-final:hover { background: #15803D; }

/* ── Answer Sheet ────────────────────────────────────────────────────── */
.answer-sheet {
  position: fixed; top: 0; right: 0; width: 320px; height: 100vh;
  background: var(--bg-white); border-left: 1px solid var(--border-color);
  box-shadow: -4px 0 20px rgba(0,0,0,0.05); z-index: 200;
  padding: 24px; overflow-y: auto;
}
.sheet-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.sheet-header h3 { font-size: 16px; font-weight: 700; }
.sheet-close { width: 28px; height: 28px; border-radius: 50%; background: var(--bg-primary); border: none; cursor: pointer; display: flex; align-items: center; justify-content: center; }
.sheet-progress { margin-bottom: 16px; }
.progress-bar { height: 6px; background: var(--border-color); border-radius: 3px; overflow: hidden; margin-bottom: 6px; }
.progress-fill { height: 100%; background: var(--color-primary); border-radius: 3px; transition: width 0.3s; }
.progress-text { font-size: 12px; color: var(--text-muted); }

.sheet-exam-group { margin-bottom: 16px; }
.sheet-exam-label { font-size: 12px; font-weight: 600; color: var(--text-secondary); margin-bottom: 8px; padding: 4px 8px; border-radius: var(--radius-md); cursor: pointer; transition: background 0.15s; }
.sheet-exam-label:hover { background: var(--bg-primary); }
.sheet-exam-active { color: var(--color-primary); background: rgba(27,67,50,0.06); }
.sheet-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 6px; }
.sheet-num {
  width: 100%; aspect-ratio: 1; border-radius: var(--radius-md); border: 1.5px solid var(--border-color);
  background: var(--bg-white); font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.15s;
}
.sheet-num.answered { background: #DCFCE7; border-color: #86EFAC; color: #16A34A; }

.sheet-legend {
  display: flex;
  gap: 16px;
  margin-top: 12px;
}
.legend-item { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-muted); }
.legend-dot { width: 10px; height: 10px; border-radius: 2px; border: 1.5px solid var(--border-color); }
.legend-dot.answered { background: var(--color-primary); border-color: var(--color-primary); }

.submit-btn-sheet {
  width: 100%;
  padding: 12px;
  border-radius: var(--radius-full);
  background: var(--color-primary);
  color: white;
  font-size: 14px;
  font-weight: 600;
  border: none;
  cursor: pointer;
  transition: all 0.15s;
  margin-top: 16px;
}
.submit-btn-sheet:hover { background: var(--color-primary-hover); }

.dialog-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
}
.dialog-header-icon { font-size: 16px; }

.confirm-content { text-align: center; padding: 4px 0; }
.confirm-progress-ring {
  width: 110px;
  height: 110px;
  border-radius: 50%;
  border: 8px solid rgba(27,67,50,0.12);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin: 8px auto 8px;
  color: var(--text-primary);
}
.confirm-answered { font-size: 24px; font-weight: 800; color: var(--color-primary); }
.confirm-slash { color: var(--text-muted); font-weight: 700; }
.confirm-total { font-size: 16px; font-weight: 700; color: var(--text-secondary); }
.confirm-label { margin: 0 0 10px; font-size: 12px; color: var(--text-muted); }
.confirm-warning, .confirm-ready {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
.confirm-warning { background: #FEF2F2; color: #B91C1C; border: 1px solid #FECACA; }
.confirm-ready { background: #ECFDF5; color: #047857; border: 1px solid #A7F3D0; }

/* ── Writing Visual Panel ─────────────────────────────────────────────── */
.write-task-body { line-height: 1.8; color: var(--text-secondary); }
.write-visual-panel {
  background: var(--bg-white); border: 1.5px solid var(--border-color); border-radius: var(--radius-lg);
  padding: 20px; margin-bottom: 20px;
}
.wvp-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.wvp-title { font-size: 15px; font-weight: 700; color: var(--text-primary); }
.wvp-sub { font-size: 12px; color: var(--text-muted); background: var(--bg-primary); padding: 2px 10px; border-radius: var(--radius-full); border: 1px solid var(--border-color); }
.wvp-summary { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 16px; }
.wvp-summary-item { font-size: 12px; padding: 4px 10px; background: var(--bg-primary); border-radius: var(--radius-full); border: 1px solid var(--border-color); color: var(--text-secondary); }
.wvp-chart-wrap { margin-bottom: 16px; }
.wvp-chart-canvas { width: 100%; height: 300px; }
.wvp-table-wrap { overflow-x: auto; }
.wvp-table-title { font-size: 14px; font-weight: 700; color: var(--text-primary); margin-bottom: 8px; padding: 8px 12px; background: var(--bg-primary); border-radius: var(--radius-md) var(--radius-md) 0 0; border: 1px solid var(--border-color); border-bottom: none; }
.wvp-table { width: 100%; border-collapse: collapse; font-size: 13px; border: 1px solid var(--border-color); }
.wvp-table th { background: var(--bg-primary); font-weight: 600; color: var(--text-primary); padding: 8px 12px; border: 1px solid var(--border-color); text-align: left; }
.wvp-table td { padding: 8px 12px; border: 1px solid var(--border-color); color: var(--text-secondary); }
.wvp-table tr:nth-child(even) td { background: rgba(27,67,50,0.02); }

/* Write Task Passage formatting (shared with ExamView) */
.wtp-gap { height: 10px; }
.wtp-title {
  font-size: 1.1em;
  font-weight: 700;
  color: var(--color-primary);
  margin: 0 0 16px;
  padding-bottom: 10px;
  border-bottom: 2px solid var(--color-accent-light, rgba(82,183,136,0.35));
}
.wtp-topic { margin: 8px 0; font-size: 0.95em; }
.wtp-topic-label { font-weight: 700; color: var(--color-primary); }
.wtp-directions {
  margin: 12px 0;
  font-size: 0.93em;
  color: var(--text-primary);
  background: #F0FFF4;
  border-left: 3px solid var(--color-primary);
  padding: 8px 12px;
  border-radius: 0 6px 6px 0;
}
.wtp-dir-label { font-weight: 700; color: var(--color-primary); }
.wtp-section {
  margin: 14px 0 4px;
  font-weight: 700;
  font-size: 0.9em;
  color: #555;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.wtp-block-tag {
  display: inline-block;
  margin: 10px 0 4px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid #CFE9DE;
  background: #F2FBF6;
  color: #2E6E55;
  font-weight: 700;
  font-size: 0.78em;
  letter-spacing: 0.02em;
}
.wtp-list { margin: 0 0 10px 0; padding-left: 20px; font-size: 0.93em; }
.wtp-list li { margin: 4px 0; color: var(--text-primary); }
.wtp-para { margin: 6px 0; font-size: 0.93em; color: var(--text-primary); }
.wtp-table-wrap { margin: 10px 0 14px; overflow-x: auto; border: 1px solid #E5E7EB; border-radius: 10px; background: #fff; }
.wtp-table { width: 100%; border-collapse: collapse; font-size: 0.9em; line-height: 1.5; }
.wtp-table th, .wtp-table td { border: 1px solid #E5E7EB; padding: 8px 10px; vertical-align: top; text-align: left; white-space: nowrap; }
.wtp-table th { background: #F8FAFC; color: #334155; font-weight: 700; }

/* Write Hint Panel */
.write-hint-panel {
  margin-top: 12px;
  border: 1.5px solid #F59E0B;
  border-radius: 8px;
  background: #FFFBEB;
  padding: 12px 14px;
  font-size: 13px;
}
.hint-panel-title { font-weight: 700; color: #92400E; margin-bottom: 10px; font-size: 13px; }
.hint-section-label {
  font-weight: 600;
  color: var(--text-secondary);
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 4px;
}
.hint-points, .hint-criteria {
  background: white;
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 8px;
  color: var(--text-primary);
  line-height: 1.6;
}
.hint-criteria { background: #F0FFF4; }
.hint-points p, .hint-criteria p { margin: 0; }

/* Translate bubble */
.translate-bubble {
  position: absolute;
  z-index: 120;
  width: 240px;
  background: var(--bg-white);
  border: 1.5px solid var(--border-color);
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(0,0,0,0.12);
  padding: 10px 12px;
}
.tb-actions { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 8px; }
.tb-btn {
  padding: 6px 10px;
  border-radius: 999px;
  border: none;
  background: var(--color-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}
.tb-btn:disabled { opacity: 0.7; cursor: default; }
.tb-close { border: none; background: none; cursor: pointer; color: var(--text-muted); font-size: 16px; line-height: 1; }
.tb-result { font-size: 12px; line-height: 1.6; color: var(--text-secondary); }
.tb-translation { font-weight: 700; color: var(--text-primary); margin-bottom: 4px; }
.tb-notes { color: var(--text-muted); }

/* Collect mode cursor */
.exam-body.collect-mode { cursor: crosshair; }
.exam-body.collect-mode .passage-body,
.exam-body.collect-mode .passage-para,
.exam-body.collect-mode .q-text,
.exam-body.collect-mode .write-task-body { cursor: text; user-select: text; }
.exam-body.collect-mode input,
.exam-body.collect-mode textarea,
.exam-body.collect-mode button { cursor: default; }

/* Highlight mode cursor */
.exam-body.highlight-mode { cursor: text; }
.exam-body.highlight-mode .passage-body,
.exam-body.highlight-mode .passage-para,
.exam-body.highlight-mode .q-text { cursor: text; user-select: text; }

/* FAB Group */
.exam-fabs {
  position: fixed;
  bottom: 28px;
  right: 28px;
  z-index: 200;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}
.fab-row { display: flex; align-items: center; gap: 8px; }
.fab-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  border-radius: 999px;
  border: none;
  background: var(--color-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(27,67,50,0.35);
  transition: all 0.2s;
  white-space: nowrap;
}
.fab-toggle:hover { background: #145229; box-shadow: 0 6px 20px rgba(27,67,50,0.45); }
.fab-icon { font-size: 15px; }

.highlight-fab { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }
.translate-fab { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }
.word-collector-fab { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }

.hl-toggle { background: #6B4226 !important; }
.hl-toggle:hover { background: #4e2f1a !important; }
.highlight-fab.active .hl-toggle { background: #374151 !important; }

.tl-toggle { background: #1D4ED8 !important; }
.tl-toggle:hover { background: #1E40AF !important; }
.translate-fab.active .tl-toggle { background: #374151 !important; }

.word-collector-fab.active .fab-toggle { background: #374151; box-shadow: 0 4px 16px rgba(0,0,0,0.25); }

.fab-badge {
  background: #F59E0B;
  color: #fff;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  min-width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
}

.fab-send-btn {
  padding: 10px 16px;
  border-radius: 999px;
  border: none;
  background: #2563EB;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(37,99,235,0.35);
  transition: all 0.2s;
  white-space: nowrap;
}
.fab-send-btn:hover:not(:disabled) { background: #1D4ED8; }
.fab-send-btn:disabled { opacity: 0.6; cursor: default; }

.collected-panel {
  background: var(--bg-white);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.15);
  padding: 12px 14px;
  min-width: 240px;
  max-width: 320px;
  max-height: 220px;
  overflow-y: auto;
}
.collected-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}
.clear-btn {
  background: none;
  border: none;
  font-size: 11px;
  color: #EF4444;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
}
.clear-btn:hover { background: #FEF2F2; }
.collected-tags { display: flex; flex-wrap: wrap; gap: 5px; }
.collected-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  background: #EFF6FF;
  border: 1px solid #BFDBFE;
  color: #1D4ED8;
  border-radius: 999px;
  padding: 3px 8px;
  font-size: 12px;
  font-weight: 500;
}
.tag-del {
  background: none;
  border: none;
  color: #93C5FD;
  cursor: pointer;
  font-size: 13px;
  padding: 0;
  line-height: 1;
  margin-left: 1px;
}
.tag-del:hover { color: #EF4444; }

/* Highlight span */
.text-highlight { border-radius: 3px; padding: 1px 0; cursor: pointer; transition: filter 0.15s; }
.text-highlight:hover { filter: brightness(0.88); }

/* Color picker */
.color-dot-btn {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2.5px solid #fff;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0,0,0,0.25);
  transition: transform 0.15s;
  flex-shrink: 0;
}
.color-dot-btn:hover { transform: scale(1.15); }
.color-picker-panel {
  background: var(--bg-white);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.15);
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 160px;
}
.cp-label { font-size: 11px; font-weight: 600; color: var(--text-secondary); letter-spacing: 0.04em; }
.color-swatches { display: flex; gap: 8px; flex-wrap: wrap; }
.color-swatch {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  transition: transform 0.15s, border-color 0.15s;
  box-shadow: 0 1px 4px rgba(0,0,0,0.18);
}
.color-swatch:hover { transform: scale(1.2); }
.color-swatch.selected { border-color: #1B4332; transform: scale(1.15); }

/* ── Loading ─────────────────────────────────────────────────────────── */
.loading-state { text-align: center; padding: 80px 0; display: flex; flex-direction: column; align-items: center; gap: 16px; color: var(--text-muted); font-size: 14px; }
.loading-spinner { width: 36px; height: 36px; border: 3px solid var(--border-color); border-top-color: var(--color-primary); border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.exam-loading {
  min-height: 60vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--text-muted);
}
.loading-icon { animation: spin 1s linear infinite; }

/* ── Transitions ─────────────────────────────────────────────────────── */
.slide-right-enter-active, .slide-right-leave-active { transition: transform 0.3s ease; }
.slide-right-enter-from, .slide-right-leave-to { transform: translateX(100%); }

.collector-expand-enter-active,
.collector-expand-leave-active { transition: opacity 0.2s, transform 0.2s; }
.collector-expand-enter-from,
.collector-expand-leave-to { opacity: 0; transform: translateY(8px) scale(0.97); }

/* ── Responsive ──────────────────────────────────────────────────────── */
@media (max-width: 900px) {
  .exam-body { grid-template-columns: 1fr; }
  .passage-col { max-height: none; position: static; border-right: none; border-bottom: 1px solid var(--border-color); }
  .questions-col { max-height: none; }
}
</style>
