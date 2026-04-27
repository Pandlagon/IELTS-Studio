<template>
  <div class="page-wrapper" v-if="result">
    <NavBar />
    <div class="result-page">
      <div class="container">

        <!-- Score Header -->
        <div class="score-header card fade-in-up">
          <div class="score-main">
            <div class="score-ring">
              <svg viewBox="0 0 120 120" class="ring-svg">
                <circle cx="60" cy="60" r="52" fill="none" stroke="var(--border-color)" stroke-width="8"/>
                <circle
                  cx="60" cy="60" r="52" fill="none"
                  stroke="var(--color-primary)" stroke-width="8"
                  stroke-linecap="round"
                  stroke-dasharray="326.7"
                  :stroke-dashoffset="326.7 * (1 - correctRate)"
                  transform="rotate(-90 60 60)"
                  style="transition: stroke-dashoffset 1.2s ease"
                />
              </svg>
              <div class="ring-inner">
                <template v-if="isWritingExam">
                  <span class="score-num" style="font-size:1.4em">{{ result.band }}</span>
                  <span class="score-total">Band</span>
                </template>
                <template v-else>
                  <span class="score-num">{{ result.correct }}</span>
                  <span class="score-total">/{{ result.total }}</span>
                </template>
              </div>
            </div>
            <div class="score-info">
              <div class="band-badge">
                <span class="band-label">Band</span>
                <span class="band-value">{{ result.band }}</span>
              </div>
              <h2 class="result-title">{{ result.examTitle }}</h2>
              <p class="result-subtitle">
                <template v-if="isWritingExam">写作评分 · </template>
                <template v-else>正确率 {{ Math.round(correctRate * 100) }}% · </template>
                用时 {{ formatTime(result.timeUsed) }} ·
                {{ new Date(result.submittedAt).toLocaleDateString() }}
              </p>
              <p v-if="isWritingExam && writingBandDescription" class="writing-band-desc">{{ writingBandDescription }}</p>
              <div class="score-stats">
                <template v-if="isWritingExam">
                  <div class="stat-box high">
                    <span class="stat-val">{{ result.band }}</span>
                    <span class="stat-key">Band分</span>
                  </div>
                  <div class="stat-box">
                    <span class="stat-val">{{ result.questions.filter(q=>q.isWrite).length }}</span>
                    <span class="stat-key">写作题数</span>
                  </div>
                  <div class="stat-box">
                    <span class="stat-val">{{ result.questions.filter(q=>q.isWrite&&q.aiGrade).length }}</span>
                    <span class="stat-key">已AI批改</span>
                  </div>
                </template>
                <template v-else>
                  <div class="stat-box" :class="getScoreClass(correctRate)">
                    <span class="stat-val">{{ Math.round(correctRate * 100) }}%</span>
                    <span class="stat-key">正确率</span>
                  </div>
                  <div class="stat-box">
                    <span class="stat-val">{{ result.correct }}</span>
                    <span class="stat-key">正确题数</span>
                  </div>
                  <div class="stat-box">
                    <span class="stat-val" style="color:#EF4444">{{ result.total - result.correct }}</span>
                    <span class="stat-key">错误题数</span>
                  </div>
                </template>
              </div>
            </div>
          </div>
          <div class="header-actions">
            <router-link to="/exams" class="btn-secondary">返回试卷列表</router-link>
            <button v-if="!isWritingExam || rawPassageText" class="btn-primary" @click="showPassage = !showPassage">
              {{ showPassage ? '隐藏原文' : '查看原文高亮' }}
            </button>
          </div>
        </div>

        <!-- Two Column: Passage + Questions -->
        <div v-if="showPassage" class="result-body">
          <!-- Passage with Highlights -->
          <div class="result-passage card">
            <div class="passage-header-r">
              <h3>阅读原文</h3>
              <span class="highlight-legend">
                <span class="hl-red-dot"></span> 错题定位段落
                <span class="hl-green-dot"></span> 正确答案句
              </span>
            </div>
            <div class="passage-content" ref="passageRef">
              <!-- Visual Data Panel (charts + tables) -->
              <div v-if="passageVisual.hasVisual" class="result-visual-panel">
                <div class="rvp-header">
                  <span class="rvp-title">📊 图表数据可视化</span>
                  <span class="rvp-sub" v-if="passageVisual.chartType">{{ passageVisual.chartType }}</span>
                </div>
                <div v-if="passageVisual.summary.length" class="rvp-summary">
                  <div class="rvp-summary-item" v-for="(s, i) in passageVisual.summary" :key="`sum-${i}`">{{ s }}</div>
                </div>
                <div v-if="passageVisual.chartData.length" class="rvp-charts">
                  <div v-if="passageVisual.chartType && passageVisual.chartType.toLowerCase().includes('bar')" ref="writeBarChartRef" class="rvp-chart-canvas"></div>
                  <div v-if="passageVisual.chartType && passageVisual.chartType.toLowerCase().includes('pie')" ref="writePieChartRef" class="rvp-chart-canvas"></div>
                </div>
                <div v-if="passageVisual.table.headers.length && passageVisual.table.rows.length" class="rvp-table-wrap">
                  <div v-if="passageVisual.table.title" class="rvp-table-title">{{ passageVisual.table.title }}</div>
                  <table class="rvp-table">
                    <thead><tr><th v-for="(h, hi) in passageVisual.table.headers" :key="hi">{{ h }}</th></tr></thead>
                    <tbody><tr v-for="(row, ri) in passageVisual.table.rows" :key="ri"><td v-for="(cell, ci) in row" :key="ci">{{ cell }}</td></tr></tbody>
                  </table>
                </div>
              </div>
              <template v-for="(para, idx) in passageParagraphs" :key="idx">
                <template v-if="para.isHeader">
                  <hr v-if="idx > 0" class="result-passage-divider" />
                  <div class="result-passage-section-header">
                    <span class="result-passage-section-badge">{{ para.label }}</span>
                  </div>
                </template>
                <p class="result-para" v-html="para.html"></p>
              </template>
            </div>
          </div>

          <!-- Question Review -->
          <div class="result-questions">
            <div class="questions-review-header">
              <h3>答题详情</h3>
              <div class="review-tabs">
                <button
                  v-for="tab in reviewTabs"
                  :key="tab.value"
                  class="review-tab"
                  :class="{ active: reviewTab === tab.value }"
                  @click="reviewTab = tab.value"
                >
                  {{ tab.label }}
                  <span class="tab-cnt">{{ getTabCnt(tab.value) }}</span>
                </button>
              </div>
            </div>

            <div class="question-reviews">
              <div
                v-for="q in filteredQuestions"
                :key="q.id"
                class="review-item"
                :class="{ correct: q.isCorrect, wrong: !q.isCorrect && !q.isWrite, write: q.isWrite }"
                @click="scrollToLocator(q)"
              >
                <div class="review-num" :class="{ correct: q.isCorrect, wrong: !q.isCorrect && !q.isWrite, write: q.isWrite }">
                  {{ q.questionNumber }}
                </div>
                <div class="review-content">
                  <p class="review-q" v-html="renderQuestionText(q.text, q.locatorText)"></p>
                  <!-- Write type: show model answer + criteria -->
                  <template v-if="q.isWrite">
                    <div class="write-result-section">
                      <!-- AI Band Score -->
                      <div v-if="q.aiGrade" class="ai-grade-header">
                        <div class="ai-band-badge">Band {{ q.aiGrade.band }}</div>
                        <span class="ai-grade-label">AI评分</span>
                      </div>
                      <div v-else-if="q.userAnswer" class="ai-grade-pending">⏳ AI评分中...</div>
                      <div v-if="q.aiGrade && (q.aiGrade.bandDescription || hasCriterionBands(q.aiGrade))" class="ai-overview-panel">
                        <p v-if="q.aiGrade.bandDescription" class="ai-overview-text">{{ q.aiGrade.bandDescription }}</p>
                        <div v-if="hasCriterionBands(q.aiGrade)" class="criterion-bands">
                          <span v-if="q.aiGrade.taskAchievementBand != null" class="criterion-chip">TR {{ q.aiGrade.taskAchievementBand }}</span>
                          <span v-if="q.aiGrade.coherenceBand != null" class="criterion-chip">CC {{ q.aiGrade.coherenceBand }}</span>
                          <span v-if="q.aiGrade.vocabularyBand != null" class="criterion-chip">LR {{ q.aiGrade.vocabularyBand }}</span>
                          <span v-if="q.aiGrade.grammarBand != null" class="criterion-chip">GRA {{ q.aiGrade.grammarBand }}</span>
                        </div>
                      </div>
                      <!-- User essay preview -->
                      <div class="write-user-answer" v-if="q.userAnswer">
                        <span class="answer-label">你的作文</span>
                        <p class="write-preview">{{ q.userAnswer.slice(0, 300) }}{{ q.userAnswer.length > 300 ? '...' : '' }}</p>
                        <span class="wc-badge">已输入 {{ wordCount(q.userAnswer) }} 词</span>
                      </div>
                      <!-- AI Feedback Dimensions -->
                      <div v-if="q.aiGrade" class="ai-feedback-grid">
                        <div class="ai-fb-item" v-if="q.aiGrade.taskAchievement">
                          <span class="ai-fb-dim">任务回应 / 完成度</span>
                          <p>{{ q.aiGrade.taskAchievement }}</p>
                        </div>
                        <div class="ai-fb-item" v-if="q.aiGrade.coherence">
                          <span class="ai-fb-dim">连贯与衔接</span>
                          <p>{{ q.aiGrade.coherence }}</p>
                        </div>
                        <div class="ai-fb-item" v-if="q.aiGrade.vocabulary">
                          <span class="ai-fb-dim">词汇资源</span>
                          <p>{{ q.aiGrade.vocabulary }}</p>
                        </div>
                        <div class="ai-fb-item" v-if="q.aiGrade.grammar">
                          <span class="ai-fb-dim">语法范围与准确性</span>
                          <p>{{ q.aiGrade.grammar }}</p>
                        </div>
                        <div class="ai-fb-strengths" v-if="q.aiGrade.strengths">
                          <span class="ai-fb-dim">亮点</span> {{ q.aiGrade.strengths }}
                        </div>
                        <div class="ai-fb-improve" v-if="q.aiGrade.improvements">
                          <span class="ai-fb-dim">改进建议</span> {{ q.aiGrade.improvements }}
                        </div>
                      </div>
                      <!-- Writing approach hint -->
                      <div class="write-model-answer" v-if="q.answer">
                        <span class="answer-label">写作思路</span>
                        <p>{{ q.answer }}</p>
                      </div>
                    </div>
                  </template>
                  <!-- Regular types -->
                  <template v-else>
                    <div class="review-answers">
                      <span class="answer-item user-answer" :class="{ correct: q.isCorrect, wrong: !q.isCorrect }">
                        <span class="answer-label">你的答案</span>
                        {{ q.userAnswer || '（未作答）' }}
                      </span>
                      <span v-if="!q.isCorrect" class="answer-item correct-answer">
                        <span class="answer-label">正确答案</span>
                        {{ q.answer }}
                      </span>
                    </div>
                    <p v-if="!q.isCorrect" class="review-explanation">
                      <el-icon><InfoFilled /></el-icon>
                      {{ q.explanation }}
                    </p>
                    <p v-if="!q.isCorrect && q.locatorText" class="locator-text" @click.stop="scrollToLocator(q)">
                      <el-icon><Location /></el-icon>
                      定位句：<em>"{{ q.locatorText }}"</em>
                    </p>
                  </template>
                </div>
                <el-icon v-if="q.isCorrect" class="review-icon correct"><CircleCheck /></el-icon>
                <el-icon v-else-if="q.isWrite" class="review-icon write"><EditPen /></el-icon>
                <el-icon v-else class="review-icon wrong"><CircleClose /></el-icon>
              </div>
            </div>
          </div>
        </div>

        <!-- All Questions (compact) when passage hidden -->
        <div v-else class="questions-compact card">
          <div class="compact-header">
            <h3>答题详情</h3>
            <div class="review-tabs">
              <button
                v-for="tab in reviewTabs"
                :key="tab.value"
                class="review-tab"
                :class="{ active: reviewTab === tab.value }"
                @click="reviewTab = tab.value"
              >
                {{ tab.label }}
                <span class="tab-cnt">{{ getTabCnt(tab.value) }}</span>
              </button>
            </div>
          </div>
          <div class="question-reviews">
            <div
              v-for="q in filteredQuestions"
              :key="q.id"
              class="review-item"
              :class="{ correct: q.isCorrect, wrong: !q.isCorrect && !q.isWrite, write: q.isWrite }"
            >
              <div class="review-num" :class="{ correct: q.isCorrect, wrong: !q.isCorrect && !q.isWrite, write: q.isWrite }">
                {{ q.questionNumber }}
              </div>
              <div class="review-content">
                <p class="review-q" v-html="renderQuestionText(q.text, q.locatorText)"></p>
                <template v-if="q.isWrite">
                  <div class="write-result-section">
                    <div v-if="q.aiGrade" class="ai-grade-header">
                      <div class="ai-band-badge">Band {{ q.aiGrade.band }}</div>
                      <span class="ai-grade-label">AI评分</span>
                    </div>
                    <div v-else-if="q.userAnswer" class="ai-grade-pending">⏳ AI评分中...</div>
                    <div v-if="q.aiGrade && (q.aiGrade.bandDescription || hasCriterionBands(q.aiGrade))" class="ai-overview-panel">
                      <p v-if="q.aiGrade.bandDescription" class="ai-overview-text">{{ q.aiGrade.bandDescription }}</p>
                      <div v-if="hasCriterionBands(q.aiGrade)" class="criterion-bands">
                        <span v-if="q.aiGrade.taskAchievementBand != null" class="criterion-chip">TR {{ q.aiGrade.taskAchievementBand }}</span>
                        <span v-if="q.aiGrade.coherenceBand != null" class="criterion-chip">CC {{ q.aiGrade.coherenceBand }}</span>
                        <span v-if="q.aiGrade.vocabularyBand != null" class="criterion-chip">LR {{ q.aiGrade.vocabularyBand }}</span>
                        <span v-if="q.aiGrade.grammarBand != null" class="criterion-chip">GRA {{ q.aiGrade.grammarBand }}</span>
                      </div>
                    </div>
                    <div class="write-user-answer" v-if="q.userAnswer">
                      <span class="answer-label">你的作文</span>
                      <p class="write-preview">{{ q.userAnswer.slice(0, 300) }}{{ q.userAnswer.length > 300 ? '...' : '' }}</p>
                      <span class="wc-badge">已输入 {{ wordCount(q.userAnswer) }} 词</span>
                    </div>
                    <div v-if="q.aiGrade" class="ai-feedback-grid">
                      <div class="ai-fb-item" v-if="q.aiGrade.taskAchievement">
                        <span class="ai-fb-dim">任务回应 / 完成度</span>
                        <p>{{ q.aiGrade.taskAchievement }}</p>
                      </div>
                      <div class="ai-fb-item" v-if="q.aiGrade.coherence">
                        <span class="ai-fb-dim">连贯与衔接</span>
                        <p>{{ q.aiGrade.coherence }}</p>
                      </div>
                      <div class="ai-fb-item" v-if="q.aiGrade.vocabulary">
                        <span class="ai-fb-dim">词汇资源</span>
                        <p>{{ q.aiGrade.vocabulary }}</p>
                      </div>
                      <div class="ai-fb-item" v-if="q.aiGrade.grammar">
                        <span class="ai-fb-dim">语法范围与准确性</span>
                        <p>{{ q.aiGrade.grammar }}</p>
                      </div>
                      <div class="ai-fb-strengths" v-if="q.aiGrade.strengths">
                        <span class="ai-fb-dim">亮点</span> {{ q.aiGrade.strengths }}
                      </div>
                      <div class="ai-fb-improve" v-if="q.aiGrade.improvements">
                        <span class="ai-fb-dim">改进建议</span> {{ q.aiGrade.improvements }}
                      </div>
                    </div>
                    <div class="write-model-answer" v-if="q.answer">
                      <span class="answer-label">写作思路</span>
                      <p>{{ q.answer }}</p>
                    </div>
                  </div>
                </template>
                <template v-else>
                  <div class="review-answers">
                    <span class="answer-item user-answer" :class="{ correct: q.isCorrect, wrong: !q.isCorrect }">
                      <span class="answer-label">你的答案</span>
                      {{ q.userAnswer || '（未作答）' }}
                    </span>
                    <span v-if="!q.isCorrect" class="answer-item correct-answer">
                      <span class="answer-label">正确答案</span>
                      {{ q.answer }}
                    </span>
                  </div>
                  <p v-if="!q.isCorrect" class="review-explanation">
                    <el-icon><InfoFilled /></el-icon>
                    {{ q.explanation }}
                  </p>
                </template>
              </div>
              <el-icon v-if="q.isCorrect" class="review-icon correct"><CircleCheck /></el-icon>
              <el-icon v-else-if="q.isWrite" class="review-icon write"><EditPen /></el-icon>
              <el-icon v-else class="review-icon wrong"><CircleClose /></el-icon>
            </div>
          </div>
        </div>

      </div>
    </div>
  </div>
  <div v-else class="no-result">
    <p>暂无考试结果</p>
    <router-link to="/exams" class="btn-primary">返回试卷列表</router-link>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useExamStore } from '@/stores/exam'
import NavBar from '@/components/NavBar.vue'
import * as echarts from 'echarts'

const route = useRoute()
const router = useRouter()
const examStore = useExamStore()

const result = computed(() => examStore.examResult)
const isWritingExam = computed(() => result.value?.questions?.every(q => q.isWrite))
const writingBandDescription = computed(() => {
  if (!result.value?.questions?.length) return ''
  const firstWithDescription = result.value.questions.find(q => q.aiGrade?.bandDescription)
  return firstWithDescription?.aiGrade?.bandDescription || ''
})
const correctRate = computed(() => {
  if (!result.value) return 0
  if (!result.value.total) return 0
  return result.value.correct / result.value.total
})
const showPassage = ref(false)
const reviewTab = ref('all')
const passageRef = ref()
const writeBarChartRef = ref(null)
const writePieChartRef = ref(null)
let writeBarChart = null
let writePieChart = null

const reviewTabs = [
  { label: '全部', value: 'all' },
  { label: '错题', value: 'wrong' },
  { label: '正确', value: 'correct' },
]

const filteredQuestions = computed(() => {
  if (!result.value) return []
  if (reviewTab.value === 'wrong') return result.value.questions.filter(q => !q.isCorrect)
  if (reviewTab.value === 'correct') return result.value.questions.filter(q => q.isCorrect)
  return result.value.questions
})

const PASSAGE_MARKER = /^(P\d+\b|【[^】]+】)/

const rawPassageText = computed(() => {
  const section = examStore.currentExam?.sections?.[0]
  if (section?.passage) return section.passage
  if (result.value?.passages?.length) {
    return result.value.passages.map(p => `【${p.title}】\n${p.passage}`).join('\n\n')
  }
  return ''
})

const passageVisual = computed(() => buildWriteVisual(rawPassageText.value))

const passageParagraphs = computed(() => {
  if (!rawPassageText.value) return []
  const cleaned = passageVisual.value?.hasVisual ? stripVisualBlocks(rawPassageText.value) : rawPassageText.value
  return cleaned.split('\n\n').filter(Boolean).map((para, idx) => {
    const markerMatch = para.match(PASSAGE_MARKER)
    const isHeader = !!markerMatch
    const label = markerMatch ? markerMatch[1] : null
    // Strip header-line codes (e.g. "P11 ZKRIN5E") from body
    const bodyText = isHeader
      ? (para.includes('\n') ? para.replace(/^[^\n]*\n/, '').trim()
                              : para.replace(/^P\d+\s+\S*\s*/, '').trim())
      : para
    // Normalise single newlines to spaces, then highlight
    const normalised = bodyText.replace(/\n/g, ' ')
    return { html: highlightPassage(normalised), isHeader, label }
  })
})

function getTabCnt(tab) {
  if (!result.value) return 0
  if (tab === 'all') return result.value.questions.length
  if (tab === 'wrong') return result.value.questions.filter(q => !q.isCorrect).length
  return result.value.questions.filter(q => q.isCorrect).length
}

function formatTime(secs) {
  const m = Math.floor(secs / 60)
  const s = secs % 60
  return `${m}分${s}秒`
}

function getScoreClass(rate) {
  if (rate >= 0.8) return 'high'
  if (rate >= 0.6) return 'mid'
  return 'low'
}

function highlightPassage(rawText) {
  if (!rawText) return ''
  // HTML-escape first so injected marks are safe
  let html = escapeHtml(rawText)
  if (!result.value) return html

  const wrongQuestions = result.value.questions.filter(q => !q.isCorrect && q.locatorText)
  const correctQuestions = result.value.questions.filter(q => q.isCorrect && q.locatorText)

  function applyMark(text, locator, cls) {
    // Normalise locator whitespace to match the \n→space processed HTML
    const norm = escapeHtml(locator.trim().replace(/\s+/g, ' '))
    if (!norm || norm.length < 4) return text
    const escaped = norm.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    return text.replace(new RegExp(escaped, 'i'), m => `<mark class="${cls}">${m}</mark>`)
  }

  wrongQuestions.forEach(q => { html = applyMark(html, q.locatorText, 'hl-red') })
  correctQuestions.forEach(q => { html = applyMark(html, q.locatorText, 'hl-green') })

  return html
}

const STOP_WORDS = new Set(['what','which','why','how','when','where','who','is','are','was','were','do','does','did','the','a','an','in','of','to','for','on','at','by','with','and','or','but','not','according','kind','can','could','would','have','has','had','that','this','their','its','they','them','been','being','some','any'])

function escapeHtml(str) {
  return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function renderQuestionText(text, locatorText) {
  if (!text) return ''
  const html = escapeHtml(text)
  if (!locatorText || locatorText.length < 3) return html
  const locatorWords = new Set(
    locatorText.toLowerCase().split(/\W+/).filter(w => w.length > 3 && !STOP_WORDS.has(w))
  )
  if (!locatorWords.size) return html
  return html.replace(/\b([A-Za-z]{4,})\b/g, (match) =>
    locatorWords.has(match.toLowerCase())
      ? `<mark class="question-keyword">${match}</mark>`
      : match
  )
}

function wordCount(text) {
  if (!text) return 0
  return text.trim().split(/\s+/).filter(Boolean).length
}

function hasCriterionBands(aiGrade) {
  return aiGrade?.taskAchievementBand != null ||
    aiGrade?.coherenceBand != null ||
    aiGrade?.vocabularyBand != null ||
    aiGrade?.grammarBand != null
}

function scrollToLocator(q) {
  if (!q.locatorText || !passageRef.value) return
  const marks = passageRef.value.querySelectorAll('.hl-red, .hl-green')
  for (const mark of marks) {
    if (mark.textContent.includes(q.locatorText.substring(0, 20))) {
      mark.scrollIntoView({ behavior: 'smooth', block: 'center' })
      break
    }
  }
}

// ── Visual data parsing ──────────────────────────────────────
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
  let chartType = ''
  if (visualBlock) {
    const line = visualBlock.split('\n').map(l => l.trim()).find(l => /chartType\s*[:：]/i.test(l))
    if (line) chartType = line.replace(/.*chartType\s*[:：]\s*/i, '').trim()
  }
  const summary = visualBlock
    ? visualBlock.split('\n').map(l => l.trim()).filter(Boolean)
        .filter(l => !/^chartType\s*[:：]/i.test(l) && !/^chartTitle\s*[:：]/i.test(l))
        .map(l => l.replace(/^[-*]\s*/, '')).slice(0, 5)
    : []
  let tableTitle = ''
  if (tableBlock) {
    const titleLine = tableBlock.split('\n').map(l => l.trim()).find(l => /^tableTitle\s*[:：]/i.test(l))
    if (titleLine) tableTitle = titleLine.replace(/^tableTitle\s*[:：]\s*/i, '').trim()
  }
  const tableLines = findLongestTableBlock(tableBlock || text)
  const table = parseVisualTable(tableLines, tableTitle)
  let chartData = extractVisualChartData(visualBlock || text)
  if (!chartData.length && table.rows.length) chartData = extractChartDataFromTable(table)
  return { hasVisual: chartData.length > 0 || table.rows.length > 0, chartType, summary, chartData, table }
}

function stripVisualBlocks(text) {
  if (!text) return ''
  return text
    .replace(/\n?\[Visual Data Summary\][\s\S]*?(?=\n\[[^\]]+\]|\n\n【|$)/i, '\n')
    .replace(/\n?\[Table Data\][\s\S]*?(?=\n\[[^\]]+\]|\n\n【|$)/i, '\n')
    .replace(/\n{3,}/g, '\n\n').trim()
}

function isMarkdownTableLine(line) {
  if (!line || !line.includes('|')) return false
  return parseMarkdownRow(line).length >= 2
}

function parseMarkdownRow(line) {
  return line.split('|').map(c => c.trim()).filter((_, i, a) => i > 0 && i < a.length - (a[a.length - 1] === '' ? 1 : 0))
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
  const isSep = (cells) => cells.every(c => /^[-:]+$/.test(c.trim()) || c.trim() === '')
  const rows = tableLines.map(parseMarkdownRow).filter(r => r.length >= 2)
  if (!rows.length) return { headers: [], rows: [], title: tableTitle }
  const sepIdx = rows.findIndex(r => isSep(r))
  if (sepIdx > 0) {
    return { headers: rows[0], rows: rows.slice(sepIdx + 1).filter(r => !isSep(r)), title: tableTitle }
  }
  return { headers: rows[0], rows: rows.slice(1).filter(r => !isSep(r)), title: tableTitle }
}

function extractVisualChartData(text) {
  if (!text) return []
  const items = []
  const lines = text.split('\n').map(l => l.trim()).filter(Boolean)
  for (const line of lines) {
    const m = line.match(/^(.+?)\s*[:：]\s*([\d.]+)\s*%?$/i)
    if (m && !/chartType|tableTitle|chartTitle/i.test(m[1])) {
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

// ── Chart rendering ──────────────────────────────────────────
function disposeWriteCharts() {
  if (writeBarChart) { writeBarChart.dispose(); writeBarChart = null }
  if (writePieChart) { writePieChart.dispose(); writePieChart = null }
}

function renderWriteCharts() {
  const data = passageVisual.value?.chartData || []
  const chartType = (passageVisual.value?.chartType || '').toLowerCase()
  if (!data.length) { disposeWriteCharts(); return }
  const shouldRenderBar = chartType.includes('bar')
  const shouldRenderPie = chartType.includes('pie')
  if (!shouldRenderBar && !shouldRenderPie) return

  if (shouldRenderBar && writeBarChartRef.value) {
    if (!writeBarChart) writeBarChart = echarts.init(writeBarChartRef.value)
    writeBarChart.setOption({
      animationDuration: 450, color: ['#4AA36F'],
      grid: { left: 12, right: 12, top: 28, bottom: 36, containLabel: true },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      xAxis: { type: 'category', data: data.map(d => d.label), axisLabel: { color: '#475569', fontSize: 11, interval: 0, rotate: data.length > 4 ? 25 : 0 }, axisTick: { alignWithLabel: true } },
      yAxis: { type: 'value', axisLabel: { color: '#64748B', fontSize: 11 }, splitLine: { lineStyle: { color: '#E2E8F0' } } },
      series: [{ type: 'bar', data: data.map(d => Number(d.value) || 0), barMaxWidth: 28, itemStyle: { borderRadius: [6, 6, 0, 0] } }],
    }, true)
  }

  if (shouldRenderPie && writePieChartRef.value) {
    if (!writePieChart) writePieChart = echarts.init(writePieChartRef.value)
    writePieChart.setOption({
      animationDuration: 450,
      color: ['#2E8B57', '#3CAEA3', '#F6C85F', '#F08A5D', '#6A89CC', '#B8DE6F', '#7DCEA0', '#5DADE2'],
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, left: 'center', textStyle: { color: '#475569', fontSize: 11 } },
      series: [{
        type: 'pie', radius: ['45%', '70%'], center: ['50%', '42%'],
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 1 },
        label: { formatter: ({ name, percent }) => `${name}\n${Math.round(percent)}%`, color: '#334155', fontSize: 10 },
        data: data.map(d => ({ name: d.label, value: Math.max(Math.abs(Number(d.value) || 0), 0.0001) })),
      }],
    }, true)
  }
}

watch(
  () => [showPassage.value, passageVisual.value?.chartData?.map(i => `${i.label}:${i.value}`).join('|')],
  async () => {
    if (!showPassage.value) return
    await nextTick()
    requestAnimationFrame(() => renderWriteCharts())
  },
  { immediate: true }
)

onBeforeUnmount(() => { disposeWriteCharts() })

onMounted(() => {
  if (!result.value) {
    // Try to restore persisted result (survives refresh, includes AI grades)
    try {
      const saved = localStorage.getItem('ielts_last_result')
      if (saved) {
        const parsed = JSON.parse(saved)
        if (parsed && String(parsed.examId) === String(route.params.id)) {
          examStore.examResult = parsed
          return
        }
      }
    } catch { /* ignore */ }
    const loaded = examStore.loadExam(route.params.id)
    if (!loaded) router.push('/exams')
  }
})
</script>

<style scoped>
.result-page {
  padding: 32px 0 64px;
}

/* Score Header */
.score-header {
  margin-bottom: 24px;
  padding: 28px 32px;
}

.score-main {
  display: flex;
  align-items: center;
  gap: 32px;
  margin-bottom: 20px;
}

.score-ring {
  position: relative;
  width: 120px;
  height: 120px;
  flex-shrink: 0;
}

.ring-svg {
  width: 100%;
  height: 100%;
}

.ring-inner {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.score-num {
  font-size: 32px;
  font-weight: 800;
  color: var(--color-primary);
}

.score-total {
  font-size: 14px;
  color: var(--text-muted);
}

.band-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--color-primary);
  color: white;
  padding: 4px 14px;
  border-radius: var(--radius-full);
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 8px;
}

.band-label { font-weight: 400; opacity: 0.8; }
.band-value { font-size: 18px; }

.result-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.result-subtitle {
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 16px;
}

.writing-band-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin: -8px 0 16px;
  line-height: 1.6;
}

.score-stats {
  display: flex;
  gap: 12px;
}

.stat-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 10px 16px;
  background: var(--bg-primary);
  border-radius: var(--radius-md);
  min-width: 70px;
}

.stat-val {
  font-size: 20px;
  font-weight: 800;
  color: var(--color-primary);
}

.stat-box.high .stat-val { color: #22C55E; }
.stat-box.mid .stat-val { color: #F59E0B; }
.stat-box.low .stat-val { color: #EF4444; }

.stat-key {
  font-size: 11px;
  color: var(--text-muted);
}

.header-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

/* Result Body */
.result-body {
  display: grid;
  grid-template-columns: 55% 1fr;
  gap: 20px;
  align-items: start;
}

.result-passage {
  padding: 0;
  overflow: hidden;
  position: sticky;
  top: 20px;
  max-height: calc(100vh - 100px);
  display: flex;
  flex-direction: column;
}

.passage-header-r {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.passage-header-r h3 {
  font-size: 15px;
  font-weight: 700;
}

.highlight-legend {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-muted);
}

.hl-red-dot, .hl-green-dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  display: inline-block;
}
.hl-red-dot { background: #FFEBEE; border: 1px solid #FFCDD2; }
.hl-green-dot { background: #E8F5E9; border: 1px solid #C8E6C9; margin-left: 8px; }

.passage-content {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
  line-height: 1.9;
  font-size: 14px;
}

.result-passage-divider {
  border: none;
  border-top: 2px dashed #D1E7D7;
  margin: 24px 0 16px;
}

.result-passage-section-header {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.result-passage-section-badge {
  display: inline-block;
  background: #1B4332;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  padding: 3px 10px;
  border-radius: 20px;
}

.result-para {
  margin-bottom: 14px;
  text-align: justify;
}

/* Visual Data Panel */
.result-visual-panel {
  margin-bottom: 20px;
  padding: 16px;
  background: #F0FAF4;
  border-radius: 12px;
  border: 1px solid #D1E7D7;
}
.rvp-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.rvp-title {
  font-weight: 700;
  font-size: 14px;
  color: #1B4332;
}
.rvp-sub {
  font-size: 12px;
  color: #2D6A4F;
  background: #D4F5E9;
  border-radius: 12px;
  padding: 2px 10px;
}
.rvp-summary {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}
.rvp-summary-item {
  font-size: 13px;
  color: #334155;
  padding-left: 12px;
  position: relative;
}
.rvp-summary-item::before {
  content: '•';
  position: absolute;
  left: 0;
  color: #2D6A4F;
}
.rvp-charts {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.rvp-chart-canvas {
  width: 100%;
  height: 260px;
  min-width: 240px;
}
.rvp-table-wrap {
  overflow-x: auto;
}
.rvp-table-title {
  font-weight: 600;
  font-size: 13px;
  color: #1B4332;
  margin-bottom: 6px;
}
.rvp-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.rvp-table th {
  background: #1B4332;
  color: white;
  padding: 6px 10px;
  text-align: left;
  font-weight: 600;
}
.rvp-table td {
  padding: 5px 10px;
  border-bottom: 1px solid #E2E8F0;
}
.rvp-table tr:nth-child(even) td {
  background: #F8FAFC;
}

:deep(.hl-red) {
  background: #FFEBEE;
  padding: 1px 3px;
  border-radius: 3px;
  border-bottom: 2px solid #EF9A9A;
}

:deep(.hl-green) {
  background: #E8F5E9;
  padding: 1px 3px;
  border-radius: 3px;
  border-bottom: 2px solid #A5D6A7;
}

/* Questions Review */
.result-questions {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.questions-review-header {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.questions-review-header h3 {
  font-size: 16px;
  font-weight: 700;
}

.review-tabs {
  display: flex;
  gap: 4px;
}

.review-tab {
  padding: 5px 14px;
  border-radius: var(--radius-full);
  font-size: 13px;
  font-weight: 500;
  color: var(--text-muted);
  border: 1.5px solid var(--border-color);
  background: var(--bg-white);
  cursor: pointer;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  gap: 5px;
}

.review-tab:hover { border-color: var(--color-primary); color: var(--color-primary); }
.review-tab.active { background: var(--color-primary); color: white; border-color: var(--color-primary); }

.tab-cnt {
  font-size: 11px;
  padding: 1px 6px;
  background: rgba(0,0,0,0.1);
  border-radius: 10px;
}
.review-tab.active .tab-cnt { background: rgba(255,255,255,0.25); }

.question-reviews {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.review-item {
  background: var(--bg-white);
  border-radius: var(--radius-lg);
  padding: 16px;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  border: 2px solid transparent;
  cursor: pointer;
  transition: all 0.15s;
}

.review-item.correct { border-color: #C8E6C9; }
.review-item.wrong { border-color: #FFCDD2; }
.review-item:hover { box-shadow: var(--shadow-md); }

.review-num {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.review-num.correct { background: #E8F5E9; color: #2E7D32; }
.review-num.wrong { background: #FFEBEE; color: #C62828; }
.review-num.write { background: #E3F2FD; color: #1565C0; }
.review-item.write { border-color: #BBDEFB; }
.review-icon.write { color: #1565C0; }

.review-content {
  flex: 1;
  min-width: 0;
}

.write-result-section {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ai-grade-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0 4px;
}
.ai-band-badge {
  background: var(--color-primary);
  color: white;
  font-weight: 700;
  font-size: 14px;
  padding: 4px 14px;
  border-radius: 20px;
}
.ai-grade-label {
  font-size: 11px;
  color: var(--text-secondary);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.ai-grade-pending {
  font-size: 12px;
  color: var(--text-secondary);
  padding: 4px 0;
}
.ai-overview-panel {
  background: #F4F8FF;
  border: 1px solid #D6E4FF;
  border-radius: 8px;
  padding: 10px 12px;
}
.ai-overview-text {
  margin: 0;
  font-size: 12px;
  color: var(--text-primary);
  line-height: 1.6;
}
.criterion-bands {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.criterion-chip {
  background: white;
  color: var(--color-primary);
  border: 1px solid #C7D7FE;
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 11px;
  font-weight: 700;
}
.ai-feedback-grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ai-fb-item {
  background: #F8F9FA;
  border-radius: 6px;
  padding: 7px 10px;
  font-size: 12px;
  line-height: 1.5;
}
.ai-fb-item p { margin: 2px 0 0; color: var(--text-primary); }
.ai-fb-dim {
  font-weight: 700;
  font-size: 11px;
  color: var(--color-primary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  display: block;
  margin-bottom: 2px;
}
.ai-fb-strengths {
  background: #E8F5E9;
  border-radius: 6px;
  padding: 7px 10px;
  font-size: 12px;
  line-height: 1.5;
}
.ai-fb-improve {
  background: #FFF3E0;
  border-radius: 6px;
  padding: 7px 10px;
  font-size: 12px;
  line-height: 1.5;
}
.ai-fb-strengths .ai-fb-dim, .ai-fb-improve .ai-fb-dim { display: inline; margin-bottom: 0; }
.write-user-answer, .write-model-answer {
  background: #F8F9FA;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 12px;
}
.write-model-answer { background: #E8F5E9; }
.write-preview {
  margin: 4px 0;
  color: var(--text-primary);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
}
.wc-badge {
  font-size: 11px;
  color: var(--text-secondary);
  background: var(--bg-primary);
  border-radius: 10px;
  padding: 1px 7px;
  display: inline-block;
  margin-top: 4px;
}

.review-q :deep(.question-keyword) {
  background: transparent;
  color: #1565C0;
  font-weight: 600;
  border-bottom: 2px dotted #1565C0;
  padding-bottom: 1px;
}

.review-q {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.5;
  margin-bottom: 8px;
}

.review-answers {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.answer-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: var(--radius-full);
  font-weight: 500;
}

.answer-label {
  font-weight: 400;
  opacity: 0.7;
  font-size: 11px;
}

.user-answer.correct { background: #E8F5E9; color: #2E7D32; }
.user-answer.wrong { background: #FFEBEE; color: #C62828; }
.correct-answer { background: #E8F5E9; color: #2E7D32; }

.review-explanation {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.5;
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin-bottom: 6px;
  padding: 6px 10px;
  background: #FFF8E1;
  border-radius: var(--radius-md);
}

.locator-text {
  font-size: 12px;
  color: var(--color-primary);
  display: flex;
  align-items: flex-start;
  gap: 5px;
  cursor: pointer;
  padding: 4px 0;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.locator-text em { font-style: italic; }

.review-icon {
  font-size: 20px;
  flex-shrink: 0;
  margin-top: 2px;
}

.review-icon.correct { color: #22C55E; }
.review-icon.wrong { color: #EF4444; }

/* Compact */
.questions-compact {
  padding: 0;
  overflow: hidden;
}

.compact-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.compact-header h3 {
  font-size: 16px;
  font-weight: 700;
}

.questions-compact .question-reviews {
  padding: 16px 20px;
}

/* No Result */
.no-result {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--text-muted);
}

@media (max-width: 1024px) {
  .result-body { grid-template-columns: 1fr; }
  .result-passage { position: static; max-height: 50vh; }
}
</style>
