<template>
  <div class="exam-layout" v-if="exam">
    <!-- Top Bar -->
    <header class="exam-topbar">
      <div class="exam-topbar-left">
        <router-link to="/exams" class="back-btn">
          <el-icon><ArrowLeft /></el-icon>
        </router-link>
        <div class="exam-info">
          <span class="exam-name">{{ exam.title }}</span>
          <span class="exam-section">{{ currentSection?.title }}</span>
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
          答题卡 ({{ examStore.answeredCount }}/{{ examStore.totalQuestions }})
        </button>
        <button class="submit-btn-top" @click="confirmSubmit">提交答卷</button>
      </div>
    </header>

    <!-- Main Content: Two Columns -->
    <div class="exam-body" ref="examBodyRef" :class="{ 'collect-mode': collectMode, 'highlight-mode': highlightMode }">
      <!-- LEFT: Reading Passage / Writing Task -->
      <div class="passage-col" ref="passageRef">
        <div class="passage-header">
          <h2 class="passage-title">{{ currentSection?.title }}</h2>
          <div class="passage-controls">
            <button class="ctrl-btn" @click="fontSize = Math.max(12, fontSize - 1)">A-</button>
            <button class="ctrl-btn" @click="fontSize = Math.min(20, fontSize + 1)">A+</button>
          </div>
        </div>

        <div
          v-if="translateBubble.show && translateBubble.host === 'passage'"
          class="translate-bubble"
          :style="{ left: translateBubble.x + 'px', top: translateBubble.y + 'px' }"
        >
          <div class="tb-actions">
            <button class="tb-btn" :disabled="translateBubble.loading" @click="doTranslate">
              {{ translateBubble.loading ? '翻译中...' : '翻译' }}
            </button>
            <button class="tb-close" @click="hideTranslateBubble"><i class="fa-solid fa-xmark"></i></button>
          </div>
          <div v-if="translateBubble.translation" class="tb-result">
            <div class="tb-translation">{{ translateBubble.translation }}</div>
            <div v-if="translateBubble.notes" class="tb-notes">{{ translateBubble.notes }}</div>
          </div>
        </div>
        <!-- Write task: structured layout -->
        <div v-if="isWriteSection" class="write-task-body" :style="{ fontSize: fontSize + 'px' }">
          <div v-html="renderWritePassage(writePassageDisplay)"></div>

          <div v-if="writeVisual.hasVisual" class="write-visual-panel">
            <div class="wvp-header">
              <div class="wvp-title"><i class="fa-solid fa-chart-column"></i> 图表数据可视化</div>
            </div>

            <!-- Write instruction shown above charts (left side) -->
            <div v-if="leftWriteInstruction" class="write-instruction" v-html="renderMd(escapeHtml(leftWriteInstruction))" style="margin: 8px 0 12px;"></div>

            <div v-if="writeVisual.summary.length" class="wvp-summary">
              <div class="wvp-summary-item" v-for="(s, i) in writeVisual.summary" :key="`sum-${i}`">{{ s }}</div>
            </div>

            <div class="wvp-charts">
              <div v-for="(seg, sIdx) in (writeVisual.segments || [])" :key="`wvp-chart-${sIdx}`" class="wvp-chart-wrap">
                <div class="wvp-summary-item" v-if="seg.chartTitle">{{ seg.chartTitle }}</div>
                <div v-if="seg.chartType && seg.chartType.toLowerCase().includes('bar')" :ref="el => setBarRef(sIdx, el)" class="wvp-chart-canvas"></div>
                <div v-if="seg.chartType && seg.chartType.toLowerCase().includes('pie')" :ref="el => setPieRef(sIdx, el)" class="wvp-chart-canvas"></div>
              </div>
            </div>

            <div v-for="(t, tIdx) in (writeVisual.tables || [])" :key="`wvp-table-${tIdx}`" v-if="t && t.headers && t.headers.length && t.rows && t.rows.length" class="wvp-table-wrap">
              <div v-if="t.title" class="wvp-table-title">{{ t.title }}</div>
              <table class="wvp-table">
                <thead>
                  <tr>
                    <th v-for="(h, idx) in (t.headers || [])" :key="`h-${tIdx}-${idx}`">{{ h }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, rIdx) in (t.rows || [])" :key="`r-${tIdx}-${rIdx}`">
                    <td v-for="(cell, cIdx) in (row || [])" :key="`c-${tIdx}-${rIdx}-${cIdx}`">{{ cell }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
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
            <p v-else :class="['passage-para', { 'labeled-para': para.paragraphLabel }]" :data-para-idx="idx">
              <span v-if="para.paragraphLabel" class="passage-letter">{{ para.paragraphLabel }}</span>
              <span class="passage-text" v-html="para.html"></span>
            </p>
          </template>
        </div>
      </div>

      <!-- RIGHT: Questions -->
      <div class="questions-col" ref="questionsColRef">
        <div
          v-if="translateBubble.show && translateBubble.host === 'questions'"
          class="translate-bubble"
          :style="{ left: translateBubble.x + 'px', top: translateBubble.y + 'px' }"
        >
          <div class="tb-actions">
            <button class="tb-btn" :disabled="translateBubble.loading" @click="doTranslate">
              {{ translateBubble.loading ? '翻译中...' : '翻译' }}
            </button>
            <button class="tb-close" @click="hideTranslateBubble"><i class="fa-solid fa-xmark"></i></button>
          </div>
          <div v-if="translateBubble.translation" class="tb-result">
            <div class="tb-translation">{{ translateBubble.translation }}</div>
            <div v-if="translateBubble.notes" class="tb-notes">{{ translateBubble.notes }}</div>
          </div>
        </div>
        <div class="questions-inner" ref="questionsRef">
          <template v-for="(question, qIdx) in displayQuestions" :key="question.id">
            <!-- Passage section divider on right side -->
            <div
              v-if="qIdx === 0 || getQuestionPassageLabel(question) !== getQuestionPassageLabel(displayQuestions[qIdx - 1])"
              class="q-passage-divider"
            >
              <span class="q-passage-label"><i class="fa-solid fa-file-lines"></i> {{ getQuestionPassageLabel(question) || '文章' }}</span>
            </div>
          <div
            :id="`q-${question.id}`"
            class="question-block"
            :class="{ answered: !!examStore.getAnswer(question.id), active: currentQId === question.id }"
            @click.capture="selectQuestion(question.id)"
          >
            <div class="q-number-row">
              <div class="q-number">{{ question.questionNumber }}</div>
              <button
                class="hint-btn"
                :class="{ active: hintQId === question.id }"
                @click.stop="toggleHint(question.id)"
                :title="question.type === 'write' ? '写作提示' : '查看原文定位'"
              ><i class="fa-solid fa-lightbulb"></i></button>
            </div>

            <!-- TFNG -->
            <template v-if="question.type === 'tfng'">
              <p class="q-text">{{ question.text }}</p>
              <div class="tfng-options">
                <button
                  v-for="opt in tfngOptions"
                  :key="opt.value"
                  class="tfng-btn"
                  :class="{ selected: examStore.getAnswer(question.id) === opt.value }"
                  @click="setAnswer(question.id, opt.value)"
                >
                  {{ opt.label }}
                </button>
              </div>
            </template>

            <!-- MCQ -->
            <template v-else-if="question.type === 'mcq'">
              <p class="q-text">{{ question.text }}</p>
              <div class="mcq-collapse-toggle" @click="toggleMcqCollapse(question.id)">
                <span><i :class="collapsedMcq.has(question.id) ? 'fa-solid fa-caret-right' : 'fa-solid fa-caret-down'"></i> {{ collapsedMcq.has(question.id) ? '展开选项' : '收起选项' }}</span>
              </div>
              <div class="mcq-options" v-show="!collapsedMcq.has(question.id) || examStore.getAnswer(question.id)">
                <button
                  v-for="opt in visibleOptions(question)"
                  :key="opt.label"
                  class="mcq-btn"
                  :class="{ selected: examStore.getAnswer(question.id) === opt.label }"
                  @click="collapsedMcq.has(question.id) ? toggleMcqCollapse(question.id) : setAnswer(question.id, opt.label)"
                >
                  <span class="opt-label">{{ opt.label }}</span>
                  <span class="opt-text">{{ opt.text }}</span>
                </button>
              </div>
            </template>

            <!-- Fill in Blank -->
            <template v-else-if="question.type === 'fill'">
              <p class="q-text fill-text" v-html="renderFill(escapeHtml(question.text), question.id)"></p>
              <div v-if="question.options?.length" class="mcq-options fill-options">
                <button
                  v-for="opt in displayOptions(question.options)"
                  :key="opt.label"
                  class="mcq-btn"
                  :class="{ selected: examStore.getAnswer(question.id) === opt.label }"
                  @click="setAnswer(question.id, opt.label)"
                >
                  <span class="opt-label">{{ opt.label }}</span>
                  <span class="opt-text">{{ opt.text }}</span>
                </button>
              </div>
              <div v-else class="fill-input-wrap">
                <input
                  class="fill-input"
                  :value="examStore.getAnswer(question.id)"
                  @input="setAnswer(question.id, $event.target.value)"
                  placeholder="输入答案..."
                  spellcheck="false"
                />
              </div>
            </template>

            <!-- Writing Task -->
            <template v-else-if="question.type === 'write'">
              <div class="write-task-label">
                {{ question.taskType || 'Writing Task' }}
                <span class="word-limit">字数要求：{{ question.wordLimit || 250 }}词以上</span>
              </div>
              <div class="write-input-wrap">
                <textarea
                  class="write-textarea"
                  :value="examStore.getAnswer(question.id)"
                  @input="setAnswer(question.id, $event.target.value)"
                  :placeholder="`在此输入你的${question.taskType === 'Task1' ? '图表描述' : '作文'}（至少${question.wordLimit || 250}词）...`"
                  spellcheck="false"
                  rows="12"
                ></textarea>
                <div class="word-count">
                  已输入 {{ wordCount(examStore.getAnswer(question.id)) }} 词
                  <span :class="wordCount(examStore.getAnswer(question.id)) >= (question.wordLimit || 250) ? 'wc-ok' : 'wc-warn'">
                    / {{ question.wordLimit || 250 }} 词要求
                  </span>
                </div>
              </div>
              <!-- Write hint panel -->
              <div v-if="hintQId === question.id" class="write-hint-panel">
                <div class="hint-panel-title"><i class="fa-solid fa-lightbulb"></i> 写作思路提示</div>
                <div v-if="question.answer" class="hint-points">
                  <div class="hint-section-label"><i class="fa-solid fa-pen-to-square"></i> 写作思路与要点</div>
                  <p style="white-space: pre-line;">{{ question.answer }}</p>
                </div>
                <div v-if="question.explanation" class="hint-criteria">
                  <div class="hint-section-label"><i class="fa-solid fa-circle-check"></i> 评分维度提示</div>
                  <p style="white-space: pre-line;">{{ question.explanation }}</p>
                </div>
              </div>
            </template>

            <!-- Fallback: any other type → text input -->
            <template v-else>
              <p class="q-text fill-text">{{ question.text }}</p>
              <div class="fill-input-wrap">
                <input
                  class="fill-input"
                  :value="examStore.getAnswer(question.id)"
                  @input="setAnswer(question.id, $event.target.value)"
                  placeholder="输入答案..."
                  spellcheck="false"
                />
              </div>
            </template>
          </div>
          </template>
        </div>
      </div>
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
          <span class="progress-text">{{ examStore.answeredCount }} / {{ examStore.totalQuestions }} 已作答</span>
        </div>
        <div class="sheet-grid">
          <button
            v-for="q in displayQuestions"
            :key="q.id"
            class="sheet-num"
            :class="{
              answered: !!examStore.getAnswer(q.id),
              current: currentQId === q.id
            }"
            @click="scrollToQuestion(q.id)"
          >
            {{ q.questionNumber }}
          </button>
        </div>
        <div class="sheet-legend">
          <span class="legend-item"><span class="legend-dot answered"></span>已答</span>
          <span class="legend-item"><span class="legend-dot"></span>未答</span>
        </div>
        <button class="submit-btn-sheet" @click="confirmSubmit">提交答卷</button>
      </div>
    </transition>

    <!-- FAB Group -->
    <div class="exam-fabs">

    <!-- Toggle FABs visibility -->
    <button class="fab-minimize-btn" @click.stop="showFabs = !showFabs" :title="showFabs ? '隐藏工具栏' : '显示工具栏'">
      <i :class="showFabs ? 'fa-solid fa-xmark' : 'fa-solid fa-screwdriver-wrench'"></i>
    </button>

    <template v-if="showFabs">
    <!-- 1. Translate FAB -->
    <div class="translate-fab" :class="{ active: translateMode }">
      <div class="fab-row">
        <button class="fab-toggle tl-toggle" @click.stop="toggleTranslate" :title="translateMode ? '退出翻译模式' : '翻译'">
          <span class="fab-icon"><i class="fa-solid fa-globe"></i></span>
          <span class="fab-label">{{ translateMode ? '退出翻译' : '翻译' }}</span>
        </button>
      </div>
    </div>

    <!-- 2. AI Assistant FAB -->
    <div class="ai-assistant-fab">
      <transition name="collector-expand">
        <div v-if="aiChatOpen" class="ai-chat-panel" ref="aiPanelRef"
          :class="{ 'ai-maximized': aiMaximized }"
          :style="!aiMaximized ? { left: aiPos.x + 'px', top: aiPos.y + 'px', width: aiSize.w + 'px', height: aiSize.h + 'px' } : {}">
          <div class="ai-chat-header" @mousedown.prevent="startDrag">
            <span><i class="fa-solid fa-robot"></i> AI 助手</span>
            <div class="ai-header-actions">
              <button class="ai-chat-btn" @click.stop="aiMaximized = !aiMaximized" :title="aiMaximized ? '还原' : '放大'">
                <svg v-if="!aiMaximized" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><rect x="3" y="3" width="18" height="18" rx="2"/></svg>
                <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><rect x="5" y="7" width="14" height="14" rx="1"/><path d="M9 7V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2"/></svg>
              </button>
              <button class="ai-chat-btn" @click.stop="aiChatOpen = false"><i class="fa-solid fa-xmark"></i></button>
            </div>
          </div>
          <div class="ai-chat-messages" ref="aiChatMessagesRef">
            <div v-if="aiMessages.length === 0" class="ai-chat-empty">
              <p>你好！我是 AI 助手，可以回答关于本试卷的问题。</p>
              <p class="ai-chat-hint">例如：第3题为什么选A？/ 这段话的主旨是什么？</p>
            </div>
            <div v-for="(msg, idx) in aiMessages" :key="idx" :class="['ai-msg', msg.role]">
              <div class="ai-msg-bubble" v-html="msg.role === 'assistant' ? renderMd(msg.content) : escapeHtml(msg.content)"></div>
            </div>
            <div v-if="aiLoading" class="ai-msg assistant">
              <div class="ai-msg-bubble ai-typing">
                <span class="dot"></span><span class="dot"></span><span class="dot"></span>
              </div>
            </div>
          </div>
          <div class="ai-chat-input">
            <input
              v-model="aiQuestion"
              @keyup.enter="sendAiChat"
              placeholder="输入你的问题..."
              :disabled="aiLoading"
            />
            <button @click="sendAiChat" :disabled="aiLoading || !aiQuestion.trim()">发送</button>
          </div>
          <div v-if="!aiMaximized" class="ai-resize-handle" @mousedown.prevent="startResize"></div>
        </div>
      </transition>
      <div class="fab-row">
        <button class="fab-toggle ai-toggle" @click.stop="aiChatOpen = !aiChatOpen" title="AI 助手">
          <span class="fab-icon"><i class="fa-solid fa-robot"></i></span>
          <span class="fab-label">{{ aiChatOpen ? '关闭助手' : 'AI 助手' }}</span>
        </button>
      </div>
    </div>

    <!-- 3. Highlight FAB -->
    <div class="highlight-fab" :class="{ active: highlightMode }">
      <transition name="collector-expand">
        <div v-if="highlightMode && showColorPicker" class="color-picker-panel">
          <span class="cp-label">高亮颜色</span>
          <div class="color-swatches">
            <button
              v-for="c in highlightColors" :key="c.value"
              class="color-swatch"
              :class="{ selected: highlightColor === c.value, eraser: c.value === ERASER_VALUE }"
              :style="c.value !== ERASER_VALUE ? { background: c.value } : {}"
              :title="c.name"
              @click.stop="highlightColor = c.value; showColorPicker = false"
            ><i v-if="c.value === ERASER_VALUE" class="fa-solid fa-eraser"></i></button>
          </div>
        </div>
      </transition>
      <div class="fab-row">
        <button v-if="highlightMode" class="color-dot-btn" :class="{ eraser: highlightColor === ERASER_VALUE }" :style="highlightColor !== ERASER_VALUE ? { background: highlightColor } : {}" @click.stop="showColorPicker = !showColorPicker" title="选择颜色"><i v-if="highlightColor === ERASER_VALUE" class="fa-solid fa-eraser"></i></button>
        <button class="fab-toggle hl-toggle" @click.stop="toggleHighlight" :title="highlightMode ? '退出高亮模式 (F2)' : '划重点 (F2)'">
          <span class="fab-icon"><i class="fa-solid fa-thumbtack"></i></span>
          <span class="fab-label">{{ highlightMode ? '退出划线' : '划重点' }}</span>
        </button>
      </div>
    </div>

    <!-- 4. Word Collector FAB -->
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
              <button class="tag-del" @click.stop="removeCollected(w)"><i class="fa-solid fa-xmark"></i></button>
            </span>
          </div>
        </div>
      </transition>
      <div class="fab-row">
        <button v-if="collectMode && collectedWords.size > 0" class="fab-send-btn" @click.stop="flushWords" :disabled="flushing">
          {{ flushing ? '发送中...' : `加入生词本 (${collectedWords.size})` }}
        </button>
        <button class="fab-toggle" @click.stop="toggleCollect" :title="collectMode ? '退出选词模式' : '点击选词加入生词本'">
          <span class="fab-icon"><i class="fa-solid fa-bookmark"></i></span>
          <span class="fab-label">{{ collectMode ? '退出选词' : '选词入册' }}</span>
          <span v-if="collectMode && collectedWords.size > 0" class="fab-badge">{{ collectedWords.size }}</span>
        </button>
      </div>
    </div>
    </template>

    </div><!-- /exam-fabs -->

    <!-- 确认提交弹窗 -->
    <el-dialog v-model="showConfirm" width="400px" :close-on-click-modal="false" align-center>
      <template #header>
        <div class="dialog-header">
          <i class="fa-solid fa-pen-to-square dialog-header-icon"></i>
          <span>确认提交</span>
        </div>
      </template>
      <div class="confirm-content">
        <div class="confirm-progress-ring">
          <span class="confirm-answered">{{ examStore.answeredCount }}</span>
          <span class="confirm-slash">/</span>
          <span class="confirm-total">{{ examStore.totalQuestions }}</span>
        </div>
        <p class="confirm-label">已作答题数</p>
        <div v-if="examStore.answeredCount < examStore.totalQuestions" class="confirm-warning">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
          还有 {{ examStore.totalQuestions - examStore.answeredCount }} 题未作答
        </div>
        <div v-else class="confirm-ready">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
          全部题目已完成，可以提交
        </div>
      </div>
      <template #footer>
        <el-button @click="showConfirm = false">继续作答</el-button>
        <el-button type="primary" @click="doSubmit" :loading="examStore.isSubmitting">确认提交</el-button>
      </template>
    </el-dialog>
  </div>

  <!-- Loading / Not Found -->
  <div v-else class="exam-loading">
    <el-icon class="loading-icon" size="32"><Loading /></el-icon>
    <p>加载中...</p>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useExamStore } from '@/stores/exam'
import { useWordStore } from '@/stores/word'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { translateApi } from '@/api/translate'
import request from '@/api/index'

const route = useRoute()
const router = useRouter()
const examStore = useExamStore()
const wordStore = useWordStore()

const exam = computed(() => examStore.currentExam)
const currentSection = computed(() => exam.value?.sections?.[0])
const isWriteSection = computed(() => {
  const questions = currentSection.value?.questions || []
  let result = questions.some(q => q.type === 'write' || q.taskType === 'Task1' || q.taskType === 'Task2')
  if (!result) {
    result = questions.some(q => {
      const text = (q.text || '').toLowerCase().replace(/\s+/g, ' ')
      return text.includes('writing task') ||
             text.includes('task 1') ||
             text.includes('task 2') ||
             text.includes('summarise the information') ||
             text.includes('summarize the information') ||
             text.includes('report the main features') ||
             text.includes('write an essay') ||
             text.includes('to what extent do you agree or disagree') ||
             text.includes('discuss both views') ||
             text.includes('give your own opinion') ||
             text.includes('advantages outweigh the disadvantages') ||
             text.includes('[visual data summary]') ||
             text.includes('[table data]')
    })
    if (result) {
      console.log('[isWriteSection] Auto-detected writing task from question text')
    }
  }
  console.log('[isWriteSection] questions:', questions, 'result:', result)
  return result
})
// Task1/Task2 的判断完全交给 AI 解析给出的 taskType 字段，前端不再依赖 passage 里的
// "WRITING TASK 2" 文本标记做二次过滤。若 AI 误识别 Task2，应在后端解析阶段
// （prompt / postProcess）修正，而不是在前端启发式过滤。
const displayQuestions = computed(() => {
  return currentSection.value?.questions || []
})
// Left-side instruction: prefer Task1 question text; fallback to section passage; strip visual blocks
const leftWriteInstruction = computed(() => {
  if (!isWriteSection.value) return ''
  const qs = currentSection.value?.questions || []
  const task1 = qs.find(q => q?.type === 'write' && (String(q?.taskType).toLowerCase() === 'task1' || q?.questionNumber === 1))
  const source = String(task1?.text || currentSection.value?.passage || '')
  return stripVisualBlocks(source)
})
const writeVisual = computed(() => {
  const section = currentSection.value
  const raw = section?.passage || ''
  const structured = buildWriteVisualPreferStructured(section, exam.value)
  if (structured?.hasVisual) return structured
  const result = buildWriteVisual(raw)
  console.log('[writeVisual] (fallback text) passage length:', raw.length, 'hasVisual:', result.hasVisual, 'chartData:', result.chartData)
  return result
})
// Multiple chart refs (per segment)
const barRefs = ref([])
const pieRefs = ref([])
const setBarRef = (idx, el) => { barRefs.value[idx] = el }
const setPieRef = (idx, el) => { pieRefs.value[idx] = el }
const writePassageDisplay = computed(() => {
  const raw = currentSection.value?.passage || ''
  if (!writeVisual.value?.hasVisual) return raw
  // Remove Visual/Table blocks first
  let txt = stripVisualBlocks(raw)
  // Also remove [Task Prompt] block from the left passage to avoid duplication
  txt = txt.replace(/\n?\[Task Prompt\][\s\S]*?(?=\n\[[^\]]+\]|$)/i, '\n')
  return txt.replace(/\n{3,}/g, '\n\n').trim()
})
let barCharts = []
let pieCharts = []

function buildWriteVisual(raw) {
  const text = (raw || '').replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  const visualBlock = extractTaggedBlock(text, 'Visual Data Summary')
  const tableBlock = extractTaggedBlock(text, 'Table Data')
  const chartType = extractChartType(visualBlock)
  const summary = extractSummary(visualBlock)

  // Extract tableTitle from tableBlock before parsing
  let tableTitle = ''
  if (tableBlock) {
    const titleLine = tableBlock.split('\n').map(l => l.trim()).find(l => /^tableTitle\s*[:：]/i.test(l))
    if (titleLine) {
      tableTitle = titleLine.replace(/^tableTitle\s*[:：]\s*/i, '').trim()
    }
  }

  // Only parse tables when a [Table Data] block exists; avoid mis-detecting pipes in series lines
  const tableLines = findLongestTableBlock(tableBlock || '')
  const table = parseVisualTable(tableLines, tableTitle)

  let chartResult = extractChartData(visualBlock || text)
  let chartData = chartResult.data
  let chartTitle = chartResult.title
  if (!chartData.length) {
    chartData = extractChartDataFromLooseLines(visualBlock || text)
  }
  if (!chartData.length && table.rows.length) {
    chartData = extractChartDataFromTable(table)
  }

  const segments = chartData.length ? [{ chartType, chartTitle, chartData }] : []
  return {
    hasVisual: chartData.length > 0 || table.rows.length > 0,
    chartType,
    summary,
    chartData,
    chartTitle,
    tables: table.rows.length ? [table] : [],
    segments,
  }
}

// Prefer backend structured charts/tables from section/exam; fallback to text parsing
function buildWriteVisualPreferStructured(section, examObj) {
  const secCharts = Array.isArray(section?.charts) ? section.charts : []
  const secTables = Array.isArray(section?.tables) ? section.tables : []
  const topCharts = Array.isArray(examObj?.charts) ? examObj.charts : []
  const topTables = Array.isArray(examObj?.tables) ? examObj.tables : []
  const charts = secCharts.length ? secCharts : topCharts
  const tables = secTables.length ? secTables : topTables
  if (!charts.length && !tables.length) return { hasVisual: false, chartType: '', summary: [], chartData: [], chartTitle: '', segments: [], tables: [] }

  // Build segments from all charts
  const hasData = (c) => {
    const series = Array.isArray(c?.series) ? c.series : []
    for (const s of series) {
      const arr = Array.isArray(s?.data) ? s.data : []
      if (arr.some(d => d && (Number(d.value) || Number(d.y) || Number(d.v)) && !isNaN(Number(d.value ?? d.y ?? d.v)))) return true
    }
    return false
  }
  // dedupe by title, keep the one with data
  const byTitle = new Map()
  for (const c of charts) {
    const key = String(c.title || '').trim()
    if (!byTitle.has(key)) {
      byTitle.set(key, c)
    } else {
      const prev = byTitle.get(key)
      if (!hasData(prev) && hasData(c)) byTitle.set(key, c)
    }
  }
  const cleanedCharts = [...byTitle.values()].filter(hasData)
  const segments = cleanedCharts.map((c) => {
    const type = String(c.type || '').toLowerCase()
    const chartType = type.includes('pie') ? 'pie chart' : (type.includes('line') ? 'line graph' : 'bar chart')
    const seriesArr = Array.isArray(c?.series) ? c.series : []
    const s0 = seriesArr[0] || { data: [] }
    const data0 = Array.isArray(s0.data) ? s0.data : []
    const chartData = data0.map(d => ({ label: String(d.label ?? d.name ?? ''), value: Number(d.value) || 0 }))
    const categories = Array.isArray(c?.dimensions?.categories) ? c.dimensions.categories : chartData.map(d => d.label)
    const fullSeries = seriesArr.map(s => ({ name: s?.name || '', data: Array.isArray(s?.data) ? s.data : [] }))
    return { chartTitle: c.title || '', chartType, chartData, categories, series: fullSeries }
  })
  const summary = cleanedCharts.slice(1).map(x => x.title || '')

  const tableObjs = tables.map((t) => ({
    title: String(t.title || ''),
    headers: Array.isArray(t.headers) ? t.headers : [],
    rows: Array.isArray(t.rows) ? t.rows : [],
  })).filter(t => t.headers.length && t.rows.length)
  const first = segments[0] || { chartType: '', chartTitle: '', chartData: [] }
  return {
    hasVisual: Boolean(segments.some(s => s.chartData.length) || tableObjs.length),
    chartType: first.chartType,
    chartData: first.chartData,
    chartTitle: first.chartTitle,
    segments,
    summary,
    tables: tableObjs,
  }
}

function stripVisualBlocks(text) {
  if (!text) return ''
  const removedTags = text
    .replace(/\n?\[Visual Data Summary\][\s\S]*?(?=\n\[[^\]]+\]|$)/i, '\n')
    .replace(/\n?\[Table Data\][\s\S]*?(?=\n\[[^\]]+\]|$)/i, '\n')
  // If visual has been structured, also strip inline chart hints (chartTitle/chartType/xAxis/yAxis/series/data rows)
  return removedTags
    .split('\n')
    .filter(line => !/^(chartTitle|chartType|xAxis|yAxis|series)\s*[:：]/i.test(line.trim()))
    .filter(line => !/^[-–—]{2,}\s*chartTitle/i.test(line.trim()))
    .filter(line => !/^\d{4}\b/.test(line.trim()))
    .filter(line => !/^[A-Za-z].+?:\s*-?\d+(?:\.\d+)?/.test(line.trim()))
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function extractTaggedBlock(text, tag) {
  const escaped = tag.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const re = new RegExp(`\\[${escaped}\\]([\\s\\S]*?)(?=\\n\\[[^\\]]+\\]|$)`, 'i')
  const m = text.match(re)
  return m ? m[1].trim() : ''
}

function logVisualDebug(label) {
  const sec = currentSection.value
  const charts = sec?.charts || exam.value?.charts || []
  const tables = sec?.tables || exam.value?.tables || []
  // avoid huge logs by shallow copy of series lengths
  const chartBrief = charts.map(c => ({ title: c.title, type: c.type, seriesLens: (c.series || []).map(s => ({ name: s.name, len: Array.isArray(s.data) ? s.data.length : 0 })) }))
  // writeVisual segments summary
  const segs = writeVisual.value?.segments || []
  const segBrief = segs.map(s => ({ title: s.chartTitle, type: s.chartType, len: (s.chartData || []).length }))
  // eslint-disable-next-line no-console
  console.log('[writeVisual debug]', label, {
    sectionCharts: chartBrief,
    tablesLen: tables.length,
    segments: segBrief,
    rawSegments: segs,
    rawSectionCharts: charts,
    rawSectionTables: tables,
  })
}

function extractChartType(text) {
  if (!text) return ''
  const line = text.split('\n').map(l => l.trim()).find(l => /chartType\s*[:：]/i.test(l))
  return line ? line.replace(/.*chartType\s*[:：]\s*/i, '').trim() : ''
}

function extractSummary(text) {
  if (!text) return []
  return text
    .split('\n')
    .map(l => l.trim())
    .filter(Boolean)
    .filter(l => !/^chartType\s*[:：]/i.test(l))
    .map(l => l.replace(/^[-*]\s*/, ''))
    .slice(0, 5)
}

function findLongestTableBlock(text) {
  const lines = (text || '').split('\n')
  let best = []
  let cur = []
  for (const line of lines) {
    const t = line.trim()
    if (isMarkdownTableLine(t)) {
      cur.push(t)
      continue
    }
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

  // Find which columns actually have data (skip completely empty columns)
  const maxCols = Math.max(...rows.map(r => r.length))
  const nonEmptyCols = []
  for (let col = 0; col < maxCols; col++) {
    const hasData = rows.some(row => row[col] && row[col].trim() !== '')
    if (hasData) nonEmptyCols.push(col)
  }

  // Filter rows to only include non-empty columns
  const filteredRows = rows.map(row => nonEmptyCols.map(col => row[col] || ''))

  // Normalize rows: remove leading/trailing empty cells
  const normalizeRow = (row) => {
    let start = 0
    let end = row.length
    while (start < row.length && (!row[start] || row[start].trim() === '')) start++
    while (end > start && (!row[end - 1] || row[end - 1].trim() === '')) end--
    return row.slice(start, end)
  }

  const normalizedRows = filteredRows.map(normalizeRow).filter(r => r.length >= 2)
  if (!normalizedRows.length) return { headers: [], rows: [], title: tableTitle }

  let headers = []
  let body = []
  
  // Find separator row to split headers from body
  let separatorRowIndex = -1
  for (let i = 0; i < normalizedRows.length; i++) {
    if (isMarkdownSeparatorRow(normalizedRows[i])) {
      separatorRowIndex = i
      break
    }
  }
  
  if (separatorRowIndex > 0) {
    const headerRows = normalizedRows.slice(0, separatorRowIndex)
    body = normalizedRows.slice(separatorRowIndex + 1)
    
    // Merge multi-level headers by combining non-empty cells column-wise
    if (headerRows.length > 1) {
      const maxCols = Math.max(...headerRows.map(r => r.length))
      headers = []
      for (let col = 0; col < maxCols; col++) {
        let headerParts = []
        for (let row = 0; row < headerRows.length; row++) {
          const cell = headerRows[row][col]
          if (cell && cell.trim() !== '') {
            headerParts.push(cell.trim())
          }
        }
        headers[col] = headerParts.length > 0 ? headerParts.join(' ') : `列${col + 1}`
      }
    } else {
      headers = headerRows[0]
    }
  } else {
    headers = Array.from({ length: Math.max(...normalizedRows.map(r => r.length)) }, (_, i) => `列${i + 1}`)
    body = normalizedRows
  }

  // Align all rows to the same column count
  const finalMaxCols = Math.max(headers.length, ...body.map(r => r.length))
  const fill = (row) => [...row, ...Array(Math.max(0, finalMaxCols - row.length)).fill('')]
  return {
    headers: fill(headers),
    rows: body.map(fill),
    title: tableTitle
  }
}

function extractChartData(text) {
  if (!text) return { title: '', data: [] }
  console.log('[extractChartData] Input text length:', text.length, 'preview:', text.substring(0, 200))
  const MAX_POINTS = 10
  const lines = text.split('\n').map(l => l.trim()).filter(Boolean)
  const out = []
  const seen = new Set()
  
  // Extract chartTitle
  let chartTitle = ''
  const titleLine = lines.find(l => /^chartTitle\s*[:：]/i.test(l))
  if (titleLine) {
    chartTitle = titleLine.replace(/^chartTitle\s*[:：]\s*/i, '').trim()
    console.log('[extractChartData] chartTitle:', chartTitle)
  }

  const normalizeUnit = (raw = '') => {
    const unit = raw.trim().toLowerCase()
    if (!unit) return ''
    return unit === 'percent' ? '%' : unit
  }

  const pushItem = (label, value, unit = '') => {
    if (out.length >= MAX_POINTS) return
    const cleanLabel = (label || '').replace(/\s+/g, ' ').trim()
    if (!cleanLabel || /^(overall|total)$/i.test(cleanLabel)) return
    const num = Number(value)
    if (!Number.isFinite(num)) return
    const unitNorm = normalizeUnit(unit)
    const key = `${cleanLabel}__${num}__${unitNorm}`
    if (seen.has(key)) return
    seen.add(key)
    out.push({ label: cleanLabel, value: num, unit: unitNorm })
    console.log('[extractChartData] Parsed item:', cleanLabel, num, unitNorm)
  }

  const keyValuesLine = lines.find(l => /^[-*]?\s*keyValues\s*[:：]/i.test(l))
  console.log('[extractChartData] keyValuesLine:', keyValuesLine)
  if (keyValuesLine) {
    const body = keyValuesLine.replace(/^[-*]?\s*keyValues\s*[:：]\s*/i, '')
    body.split(/[,;；、]/).forEach(chunk => {
      const part = chunk.trim()
      if (!part) return
      const paren = part.match(/^(.+?)\s*\(([-\d]+(?:\.\d+)?)\s*(%|percent|百分点|million|billion|bn|thousand|k)?\)$/i)
      if (paren) {
        pushItem(paren[1], paren[2], paren[3] || '')
        return
      }
      const colon = part.match(/^(.+?)\s*[:：]\s*(-?\d+(?:\.\d+)?)(?:\s*(%|percent|百分点|million|billion|bn|thousand|k))?$/i)
      if (colon) {
        pushItem(colon[1], colon[2], colon[3] || '')
        return
      }
      const space = part.match(/^([A-Za-z][A-Za-z\s\-()]{1,50})\s+(-?\d+(?:\.\d+)?)(?:\s*(%|percent|百分点|million|billion|bn|thousand|k))?$/i)
      if (space) {
        pushItem(space[1], space[2], space[3] || '')
      }
    })
  }

  const colonRe = /([A-Za-z][A-Za-z\s\-()]{1,50})\s*[:：]\s*(-?\d+(?:\.\d+)?)(?:\s*(%|percent|个百分点|million|billion|bn|thousand|k))?/ig
  let match
  while ((match = colonRe.exec(text)) && out.length < MAX_POINTS) {
    pushItem(match[1], match[2], match[3] || '')
  }

  if (out.length < 3) {
    const parenRe = /([A-Za-z][A-Za-z\s\-()]{1,50})\s*\(([-\d]+(?:\.\d+)?)\s*(%|percent|百分点|million|billion|bn|thousand|k)?\)/ig
    let pm
    while ((pm = parenRe.exec(text)) && out.length < MAX_POINTS) {
      pushItem(pm[1], pm[2], pm[3] || '')
    }
  }

  console.log('[extractChartData] Final chartData:', out)
  return { title: chartTitle, data: normalizeChartData(out) }
}

function extractChartDataFromLooseLines(text) {
  if (!text) return []
  const lines = text.split('\n').map(l => l.trim()).filter(Boolean)
  const out = []
  for (let i = 0; i < lines.length - 1 && out.length < 10; i++) {
    const label = lines[i]
    const next = lines[i + 1]
    if (!/^[A-Za-z][A-Za-z\s\-()]{1,40}$/.test(label)) continue
    const m = next.match(/-?\d+(?:\.\d+)?\s*(%|percent|million|billion|bn|k|thousand)?/i)
    if (!m) continue
    out.push({ label, value: Number(m[0].match(/-?\d+(?:\.\d+)?/)?.[0] || 0), unit: /%|percent/i.test(m[0]) ? '%' : '' })
    i += 1
  }
  return normalizeChartData(out)
}

function extractChartDataFromTable(table) {
  const headers = table.headers || []
  const rows = table.rows || []
  if (!headers.length || !rows.length) return []

  let targetCol = headers.findIndex(h => /total\s*land\s*degraded|overall|total|sum|总计|合计/i.test(String(h || '')))
  if (targetCol <= 0) {
    let bestCol = -1
    let bestScore = -1
    for (let c = 1; c < headers.length; c++) {
      let score = 0
      for (const row of rows) {
        const valText = String(row[c] || '').trim()
        if (/-?\d+(?:\.\d+)?/.test(valText)) score++
      }
      if (score > bestScore) {
        bestScore = score
        bestCol = c
      }
    }
    targetCol = bestCol > 0 ? bestCol : Math.min(1, headers.length - 1)
  }

  const out = []
  for (const row of rows) {
    const label = String(row[0] || '').trim()
    if (!label || /region|table|header/i.test(label)) continue
    const valText = String(row[targetCol] || '').trim()
    const m = valText.match(/-?\d+(?:\.\d+)?/)
    if (!m) continue
    out.push({ label, value: Number(m[0]), unit: /%/.test(valText) ? '%' : '' })
    if (out.length >= 10) break
  }
  return normalizeChartData(out)
}

function normalizeChartData(items) {
  if (!items.length) return []
  const max = Math.max(...items.map(i => Math.abs(i.value)), 0)
  const base = max > 0 ? max : 1
  return items.map((i, idx) => ({
    ...i,
    key: `${idx}-${i.label}`,
    ratio: Math.max(4, Math.round((Math.abs(i.value) / base) * 100)),
    displayValue: `${i.value}${i.unit || ''}`,
  }))
}

function disposeWriteCharts() {
  if (barCharts?.length) barCharts.forEach(ch => { try { ch?.dispose?.() } catch(e){} })
  if (pieCharts?.length) pieCharts.forEach(ch => { try { ch?.dispose?.() } catch(e){} })
  barCharts = []
  pieCharts = []
}

function renderWriteCharts() {
  disposeWriteCharts()
  if (!isWriteSection.value) return
  const segs = writeVisual.value?.segments || []
  segs.forEach((seg, idx) => {
    const data = Array.isArray(seg?.chartData) ? seg.chartData : []
    if (!data.length) return
    const labels = data.map(d => d.label)
    const values = data.map(d => Number(d.value) || 0)
    const type = String(seg?.chartType || '').toLowerCase()
    if (type.includes('bar') && barRefs.value[idx]) {
      const ch = echarts.init(barRefs.value[idx])
      barCharts[idx] = ch
      const hasMulti = Array.isArray(seg?.series) && seg.series.length >= 2 && Array.isArray(seg?.categories) && seg.categories.length
      if (hasMulti) {
        const cats = seg.categories
        const palette = ['#4AA36F', '#60A5FA', '#F59E0B', '#EF4444', '#8B5CF6', '#10B981']
        const series = seg.series.map((s, si) => ({
          name: String(s?.name || `Series ${si+1}`),
          type: 'bar',
          barMaxWidth: 28,
          itemStyle: { borderRadius: [6,6,0,0] },
          data: (Array.isArray(s?.data) ? s.data : []).map(d => Number(d?.value) || 0)
        }))
        ch.setOption({
          animationDuration: 450,
          color: palette,
          legend: { top: 0, textStyle: { color: '#475569', fontSize: 11 } },
          grid: { left: 12, right: 12, top: 28, bottom: 36, containLabel: true },
          tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
          xAxis: { type: 'category', data: cats, axisLabel: { color: '#475569', fontSize: 11, interval: 0, rotate: cats.length > 4 ? 25 : 0 }, axisTick: { alignWithLabel: true } },
          yAxis: { type: 'value', axisLabel: { color: '#64748B', fontSize: 11 }, splitLine: { lineStyle: { color: '#E2E8F0' } } },
          series,
        }, true)
      } else {
        ch.setOption({
          animationDuration: 450,
          color: ['#4AA36F'],
          grid: { left: 12, right: 12, top: 28, bottom: 36, containLabel: true },
          tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
          xAxis: { type: 'category', data: labels, axisLabel: { color: '#475569', fontSize: 11, interval: 0, rotate: labels.length > 4 ? 25 : 0 }, axisTick: { alignWithLabel: true } },
          yAxis: { type: 'value', axisLabel: { color: '#64748B', fontSize: 11 }, splitLine: { lineStyle: { color: '#E2E8F0' } } },
          series: [{ type: 'bar', data: values, barMaxWidth: 28, itemStyle: { borderRadius: [6, 6, 0, 0] } }],
        }, true)
      }
    }
    if (type.includes('pie') && pieRefs.value[idx]) {
      const ch = echarts.init(pieRefs.value[idx])
      pieCharts[idx] = ch
      ch.setOption({
        animationDuration: 450,
        tooltip: { trigger: 'item' },
        series: [{ type: 'pie', radius: ['45%', '70%'], center: ['50%', '42%'], itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 1 }, label: { formatter: '{b}: {c}' }, data: data.map(d => ({ name: d.label, value: Math.max(Math.abs(Number(d.value) || 0), 0.0001) })) }],
      }, true)
    }
  })
}

function resizeWriteCharts() {
  barCharts.forEach(ch => ch?.resize?.())
  pieCharts.forEach(ch => ch?.resize?.())
}

watch(
  () => [isWriteSection.value, JSON.stringify(writeVisual.value?.segments || [])],
  async () => {
    await nextTick()
    requestAnimationFrame(() => renderWriteCharts())
  },
  { immediate: true }
)

watch(writeVisual, () => logVisualDebug('writeVisual-changed'))
// Parse passage text into labeled sections: [{label, text}]
const passageSections = computed(() => {
  const text = currentSection.value?.passage || ''
  const sections = []
  // Match P11-style or 【阅读题1】-style markers at start of a paragraph
  const MARKER = /^(P\d+\b|【[^】]+】)/
  let currentLabel = null
  let currentChunks = []
  for (const chunk of text.split('\n\n').filter(Boolean)) {
    const m = chunk.match(MARKER)
    if (m) {
      if (currentLabel !== null) sections.push({ label: currentLabel, text: currentChunks.join(' ') })
      currentLabel = m[1]
      currentChunks = [chunk.slice(m[0].length).trim()]
    } else {
      currentChunks.push(chunk)
    }
  }
  if (currentLabel !== null) sections.push({ label: currentLabel, text: currentChunks.join(' ') })
  return sections
})

// Assign passage label by question position (proportional split across sections).
// More reliable than locatorText matching for multi-section IELTS exams.
function getQuestionPassageLabel(question) {
  const sections = passageSections.value
  if (!sections.length || sections.length === 1) return null
  const allQuestions = currentSection.value?.questions || []
  if (!allQuestions.length) return sections[0].label
  const qIdx = allQuestions.findIndex(q => q.id === question.id)
  if (qIdx === -1) return sections[0].label
  const sIdx = Math.min(
    Math.floor(qIdx * sections.length / allQuestions.length),
    sections.length - 1
  )
  return sections[sIdx].label
}

const passageParagraphs = computed(() => {
  const text = currentSection.value?.passage || ''
  const MARKER = /^(P\d+\b|【[^】]+】)/
  const paragraphLabelSet = detectParagraphLabels(text)

  return text.split('\n\n').filter(Boolean).flatMap(para => {
    const markerMatch = para.match(MARKER)
    const isHeader = !!markerMatch
    const label = markerMatch ? markerMatch[1] : null
    // Strip the entire first line (e.g. "P13 LAA" or "P11 ZKRIN5E") to avoid
    // leaving stray passage-code identifiers in the rendered body text.
    // Fallback: strip just the P-number prefix if there is no newline.
    const bodyText = isHeader
      ? (para.includes('\n') ? para.replace(/^[^\n]*\n/, '').trim()
                              : para.replace(/^P\d+\s+\S*\s*/, '').trim())
      : para
    const parsed = extractParagraphLabel(bodyText, paragraphLabelSet)
    const html = escapeHtml(parsed.text).replace(/\n/g, ' ')
    if (isHeader) {
      return [
        { html: '', isHeader: true, label, paragraphLabel: '' },
        ...(html ? [{ html, isHeader: false, label: null, paragraphLabel: parsed.label }] : [])
      ]
    }
    return [{ html, isHeader: false, label: null, paragraphLabel: parsed.label }]
  })
})

function detectParagraphLabels(text) {
  const labels = text.split('\n\n').filter(Boolean)
    .map(p => String(p || '').trim().match(/^([A-Z])(?:[.)])?\s+/)?.[1])
    .filter(Boolean)
  const unique = new Set(labels)
  return unique.has('A') && unique.has('B') ? unique : new Set()
}

function extractParagraphLabel(text, labelSet) {
  const raw = String(text || '').trim()
  const match = raw.match(/^([A-Z])(?:[.)])?\s+(.+)$/s)
  if (!match || !labelSet?.has(match[1])) return { label: '', text: raw }
  return { label: match[1], text: match[2].trim() }
}

function applyHintLocator() {
  const passageBody = passageRef.value?.querySelector('.passage-body')
  if (!passageBody) return
  // Remove existing keyword marks (unwrap to preserve highlights)
  passageBody.querySelectorAll('.passage-keyword').forEach(mark => {
    const parent = mark.parentNode
    while (mark.firstChild) parent.insertBefore(mark.firstChild, mark)
    mark.remove()
    parent.normalize()
  })
  const qId = hintQId.value
  if (!qId) return
  const questions = currentSection.value?.questions || []
  const hintQ = questions.find(q => q.id === qId)
  const locator = hintQ?.locatorText?.trim().replace(/\s+/g, ' ') || null
  if (!locator || locator.length <= 3) return
  const escaped = locator.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const regex = new RegExp(escaped, 'i')
  const walker = document.createTreeWalker(passageBody, NodeFilter.SHOW_TEXT)
  let node
  while ((node = walker.nextNode())) {
    const match = node.textContent.match(regex)
    if (!match) continue
    try {
      const range = document.createRange()
      range.setStart(node, match.index)
      range.setEnd(node, match.index + match[0].length)
      const mark = document.createElement('mark')
      mark.className = 'passage-keyword'
      range.surroundContents(mark)
      mark.scrollIntoView({ behavior: 'smooth', block: 'center' })
    } catch { /* boundary issue, skip */ }
    break
  }
}

function escapeHtml(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function renderMd(text) {
  if (!text) return ''
  let html = escapeHtml(text)
  html = html.replace(/```[\s\S]*?```/g, m => {
    const inner = m.slice(3, -3).replace(/^\w*\n/, '')
    return `<pre class="md-code-block">${inner}</pre>`
  })
  html = html.replace(/`([^`]+)`/g, '<code class="md-code">$1</code>')
  html = html.replace(/\*\*\*(.+?)\*\*\*/g, '<strong><em>$1</em></strong>')
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\*(.+?)\*/g, '<em>$1</em>')
  const lines = html.split('\n')
  const result = []
  let inList = false
  for (const line of lines) {
    const trimmed = line.trim()
    if (/^#{1,3}\s/.test(trimmed)) {
      if (inList) { result.push('</ul>'); inList = false }
      const level = trimmed.match(/^(#+)/)[1].length
      result.push(`<h${level + 2} class="md-h">${trimmed.replace(/^#+\s*/, '')}</h${level + 2}>`)
    } else if (/^&gt;\s/.test(trimmed)) {
      if (inList) { result.push('</ul>'); inList = false }
      result.push(`<blockquote class="md-quote">${trimmed.replace(/^&gt;\s*/, '')}</blockquote>`)
    } else if (/^[-*]\s/.test(trimmed)) {
      if (!inList) { result.push('<ul class="md-list">'); inList = true }
      result.push(`<li>${trimmed.replace(/^[-*]\s*/, '')}</li>`)
    } else if (/^\d+\.\s/.test(trimmed)) {
      if (!inList) { result.push('<ol class="md-list">'); inList = true }
      result.push(`<li>${trimmed.replace(/^\d+\.\s*/, '')}</li>`)
    } else {
      if (inList) { result.push('</ul>'); inList = false }
      if (trimmed) result.push(`<p class="md-p">${trimmed}</p>`)
      else result.push('<br/>')
    }
  }
  if (inList) result.push('</ul>')
  return result.join('')
}

const fontSize = ref(15)
const showSheet = ref(false)
const showConfirm = ref(false)
const timeLeft = ref(3600)
const currentQId = ref(null)
const hintQId = ref(null)
const passageRef = ref()
const questionsRef = ref()
const questionsColRef = ref()

let timer = null

const tfngOptions = [
  { value: 'TRUE', label: 'TRUE' },
  { value: 'FALSE', label: 'FALSE' },
  { value: 'NOT GIVEN', label: 'NOT GIVEN' },
]

// MCQ collapse state: collapsed questions only show selected option (or none)
const collapsedMcq = ref(new Set())
function toggleMcqCollapse(qId) {
  const s = new Set(collapsedMcq.value)
  if (s.has(qId)) s.delete(qId); else s.add(qId)
  collapsedMcq.value = s
}
function visibleOptions(question) {
  const opts = displayOptions(question.options || [])
  if (!collapsedMcq.value.has(question.id)) return opts
  const ans = examStore.getAnswer(question.id)
  if (!ans) return []
  return opts.filter(o => o.label === ans)
}

function displayOptions(options) {
  if (!Array.isArray(options) || options.length < 4) return options
  const letterOptions = options.every(o => /^[A-I]$/i.test(String(o.label || '').trim()))
  const paragraphOptions = options.filter(o => String(o.text || '').trim().length > 80).length >= Math.ceil(options.length * 0.6)
  if (!letterOptions || !paragraphOptions) return options
  return options.map(o => ({ ...o, text: '' }))
}

const progressPct = computed(() => {
  if (!examStore.totalQuestions) return 0
  return Math.round((examStore.answeredCount / examStore.totalQuestions) * 100)
})

function formatTime(secs) {
  const m = Math.floor(secs / 60).toString().padStart(2, '0')
  const s = (secs % 60).toString().padStart(2, '0')
  return `${m}:${s}`
}

function setAnswer(qId, val) {
  examStore.setAnswer(qId, val)
  selectQuestion(qId)
}

function selectQuestion(qId) {
  currentQId.value = qId
}

function toggleHint(qId) {
  if (hintQId.value === qId) {
    hintQId.value = null
  } else {
    hintQId.value = qId
  }
  nextTick(() => applyHintLocator())
}

function rebreakCollapsedWriteTask(text) {
  // If text has very few line breaks but is long, AI likely collapsed the formatting
  const realLines = text.split('\n').filter(l => l.trim()).length
  if (realLines > 3 || text.length < 80) return text
  return text
    .replace(/(\?)\s+(?=[A-Z])/g, '$1\n')
    .replace(/(\.)\s+(Give reasons|Discuss both|To what extent|Do you agree|Some people|In many|You should spend)/g, '$1\n\n$2')
    .replace(/(\.)\s+(Write at least \d+)/gi, '$1\n\n$2')
    .replace(/(\.)\s+(Write about the following topic:?)/gi, '$1\n\n$2')
    .replace(/(topic:?)\s+/gi, '$1\n\n')
    .replace(/(\.)\s+(About \d+[–\-]\d+\s*words)/gi, '$1\n\n$2')
    .replace(/(\.)\s+(Requirements:?|Directions:?)/gi, '$1\n\n$2')
    .replace(/^\s*(WRITING\s+TASK\s*\d?)/i, '$1\n\n')
}

function joinWrappedLines(text) {
  // Join mid-sentence line breaks from PDF wrapping:
  // if a line does NOT end with sentence-ending punctuation and the next line
  // starts with a lowercase letter (or continues a sentence), merge them.
  const lines = text.split('\n')
  const merged = []
  for (let i = 0; i < lines.length; i++) {
    const cur = lines[i]
    const trimmed = cur.trimEnd()
    const next = i + 1 < lines.length ? lines[i + 1] : null
    if (next !== null && trimmed && !/[.?!:。？！：]$/.test(trimmed) && /^\s*[a-z]/.test(next)) {
      merged.push(trimmed + ' ' + next.trimStart())
      i++ // skip next line since we merged it
    } else {
      merged.push(cur)
    }
  }
  return merged.join('\n')
}

function renderWritePassage(raw) {
  let normalized = (raw || '').replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  normalized = joinWrappedLines(normalized)
  normalized = rebreakCollapsedWriteTask(normalized)
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

    // Markdown-style table block generated by AI
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

    // Structured block tags from backend prompt: [Task Prompt] [Visual Data Summary] [Table Data]
    if (/^\[(Task Prompt|Visual Data Summary|Table Data)\]$/i.test(line)) {
      closeList()
      html += `<div class="wtp-block-tag">${escapeHtml(line)}</div>`
      continue
    }

    // Section headers: Requirements, Notes, Instructions etc.
    if (/^(Requirements?|Instructions?|Notes?|Marks?):?$/i.test(line)) {
      closeList()
      html += `<div class="wtp-section">${escapeHtml(line)}</div>`
      inList = true
      html += '<ul class="wtp-list">'
      continue
    }
    // Directions label at start
    if (/^Directions?:/i.test(line)) {
      closeList()
      const body = line.replace(/^Directions?:\s*/i, '')
      html += `<p class="wtp-directions"><span class="wtp-dir-label">Directions:</span> ${escapeHtml(body)}</p>`
      continue
    }
    // Topic / Task label lines like "Topic: ..."
    if (/^(Topic|Subject|Title):/i.test(line)) {
      closeList()
      const [label, ...rest] = line.split(':')
      html += `<p class="wtp-topic"><span class="wtp-topic-label">${escapeHtml(label)}:</span> ${escapeHtml(rest.join(':').trim())}</p>`
      continue
    }
    // Title line: first non-empty line that looks like a heading (all-caps or "WRITING TASK X")
    if (i === 0 && /^[A-Z\s\d]+$/.test(line) && line.length < 60) {
      closeList()
      html += `<h3 class="wtp-title">${escapeHtml(line)}</h3>`
      continue
    }
    // Explicit bullet items: lines starting with - or • or numbered like "1." "2."
    const isExplicitBullet = /^[-•]\s+/.test(line) || /^\d+[.)]\s+/.test(line)
    // Continue existing list only if already inList and line is short
    const isContinuedBullet = inList && line.length < 100 && !line.endsWith(':')
    if (isExplicitBullet || isContinuedBullet) {
      if (!inList) {
        inList = true
        html += '<ul class="wtp-list">'
      }
      const cleanLine = line.replace(/^[-•]\s+/, '').replace(/^\d+[.)]\s+/, '')
      html += `<li>${escapeHtml(cleanLine)}</li>`
      continue
    }
    // Paragraph
    closeList()
    html += `<p class="wtp-para">${escapeHtml(line)}</p>`
  }
  closeList()
  return html.replace(/(?:<div class="wtp-gap"><\/div>){2,}/g, '<div class="wtp-gap"></div>')
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
    for (const cell of fillCols(header)) {
      html += `<th>${escapeHtml(cell)}</th>`
    }
    html += '</tr></thead>'
  }
  html += '<tbody>'
  for (const row of body) {
    html += '<tr>'
    for (const cell of fillCols(row)) {
      html += `<td>${escapeHtml(cell)}</td>`
    }
    html += '</tr>'
  }
  html += '</tbody></table></div>'
  return html
}

function scrollToQuestion(qId) {
  const el = document.getElementById(`q-${qId}`)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  currentQId.value = qId
  showSheet.value = false
}

function renderFill(text, qId) {
  const BLANK = `<span class="fill-blank">[    ]</span>`
  const replaced = text.replace(/_{3,}/g, BLANK)
  if (replaced === text) {
    if (/[.…]{2,}/.test(text)) return text.replace(/\n/g, '<br/>')
    return `${text} ${BLANK}`.replace(/\n/g, '<br/>')
  }
  return replaced.replace(/\n/g, '<br/>')
}

function wordCount(text) {
  if (!text) return 0
  return text.trim().split(/\s+/).filter(Boolean).length
}

const STOP_WORDS = new Set(['what','which','why','how','when','where','who','is','are','was','were','do','does','did','the','a','an','in','of','to','for','on','at','by','with','and','or','but','not','according','kind','can','could','would','have','has','had','that','this','their','its','they','them','been','being','some','any'])

function renderQuestionText(text, locatorText, isAnswered = false) {
  if (!text) return ''
  const html = escapeHtml(text)
  if (!isAnswered || !locatorText || locatorText.length < 3) return html
  const locatorWords = new Set(
    locatorText.toLowerCase().split(/\W+/).filter(w => w.length > 3 && !STOP_WORDS.has(w))
  )
  if (!locatorWords.size) return html
  return html.replace(/\b([A-Za-z]{4,})\b/g, (match) => {
    return locatorWords.has(match.toLowerCase())
      ? `<mark class="question-keyword">${match}</mark>`
      : match
  })
}

function confirmSubmit() {
  showConfirm.value = true
}

async function doSubmit() {
  await examStore.submitExam()
  showConfirm.value = false
  router.push(`/exam/${route.params.id}/result`)
}

// ── word collector ─────────────────────────────────────
const collectMode = ref(false)
const collectedWords = ref(new Set())
const examBodyRef = ref()
const flushing = ref(false)

// ── FAB toolbar visibility ──────────────────────────────
const showFabs = ref(true)

// ── AI assistant ────────────────────────────────────────
const aiChatOpen = ref(false)
const aiMessages = ref([])
const aiQuestion = ref('')
const aiLoading = ref(false)
const aiPanelRef = ref(null)
const aiMaximized = ref(false)
const aiPos = reactive({ x: window.innerWidth - 420, y: 80 })
const aiSize = reactive({ w: 400, h: 520 })

function startDrag(e) {
  if (aiMaximized.value) return
  const startX = e.clientX, startY = e.clientY
  const origX = aiPos.x, origY = aiPos.y
  function onMove(ev) {
    aiPos.x = Math.max(0, Math.min(window.innerWidth - aiSize.w, origX + ev.clientX - startX))
    aiPos.y = Math.max(0, Math.min(window.innerHeight - 56, origY + ev.clientY - startY))
  }
  function onUp() { document.removeEventListener('mousemove', onMove); document.removeEventListener('mouseup', onUp) }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

function startResize(e) {
  const startX = e.clientX, startY = e.clientY
  const origW = aiSize.w, origH = aiSize.h
  function onMove(ev) {
    aiSize.w = Math.max(320, origW + ev.clientX - startX)
    aiSize.h = Math.max(300, origH + ev.clientY - startY)
  }
  function onUp() { document.removeEventListener('mousemove', onMove); document.removeEventListener('mouseup', onUp) }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}
const aiChatMessagesRef = ref()

function buildExamContext() {
  const sec = currentSection.value
  if (!sec) return ''
  const passages = sec.passage || ''
  const questions = (sec.questions || []).map(q =>
    `Q${q.questionNumber} [${q.type}]: ${q.text}${q.answer ? ' (答案: ' + q.answer + ')' : ''}`
  ).join('\n')
  return `【文章】\n${passages}\n\n【题目】\n${questions}`
}

async function sendAiChat() {
  const q = aiQuestion.value.trim()
  if (!q || aiLoading.value) return
  aiMessages.value.push({ role: 'user', content: q })
  aiQuestion.value = ''
  aiLoading.value = true
  await nextTick()
  if (aiChatMessagesRef.value) {
    aiChatMessagesRef.value.scrollTop = aiChatMessagesRef.value.scrollHeight
  }
  try {
    const res = await request.post('/exams/ai-chat', {
      examContext: buildExamContext(),
      question: q
    }, { timeout: 60000 })
    if (res && res.code === 200 && res.data?.answer) {
      aiMessages.value.push({ role: 'assistant', content: res.data.answer })
    } else {
      aiMessages.value.push({ role: 'assistant', content: res?.message || 'AI 回答失败，请重试' })
    }
  } catch (e) {
    aiMessages.value.push({ role: 'assistant', content: '请求失败: ' + (e?.message || '网络错误') })
  } finally {
    aiLoading.value = false
    await nextTick()
    if (aiChatMessagesRef.value) {
      aiChatMessagesRef.value.scrollTop = aiChatMessagesRef.value.scrollHeight
    }
  }
}

// ── translate overlay ───────────────────────────────────
const translateMode = ref(false)
const translateBubble = ref({
  show: false,
  host: 'passage',
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
  if (!passageRef.value?.contains(el) && !questionsColRef.value?.contains(el)) hideTranslateBubble()
}

function onPassageMouseUp(e) {
  if (!translateMode.value) return
  if (highlightMode.value) return
  if (collectMode.value) return
  const sel = window.getSelection()
  if (!sel || sel.isCollapsed || !sel.rangeCount) return
  const text = sel.toString().trim()
  if (!text) return

  // Determine host container (passage or questions)
  let hostEl = null
  let hostName = 'passage'
  if (passageRef.value?.contains(sel.anchorNode)) {
    hostEl = passageRef.value
    hostName = 'passage'
  } else if (questionsColRef.value?.contains(sel.anchorNode)) {
    hostEl = questionsColRef.value
    hostName = 'questions'
  }
  if (!hostEl) return

  const range = sel.getRangeAt(0)
  const rect = range.getBoundingClientRect()
  const hostRect = hostEl.getBoundingClientRect()
  const scrollTop = hostEl.scrollTop || 0

  // position relative to host container (account for scroll)
  const x = Math.min(Math.max(rect.left - hostRect.left, 8), hostRect.width - 120)
  const y = Math.max(rect.bottom - hostRect.top + scrollTop + 6, 8)

  translateBubble.value = {
    ...translateBubble.value,
    show: true,
    host: hostName,
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
  const passageText = currentSection.value?.passage || ''
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

function captureWordAt(e) {
  // Prefer user selection (double-click selects a word)
  const sel = window.getSelection()
  let word = sel?.toString().trim()
  if (!word) {
    // Fallback: expand caret position to word boundary
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
  // Keep only letters + apostrophe/hyphen, lowercase, min 2 chars
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
    // 互斥：关闭划重点和翻译
    if (highlightMode.value) {
      highlightMode.value = false
      showColorPicker.value = false
      examBodyRef.value?.removeEventListener('mouseup', onHighlightMouseUp)
    }
    if (translateMode.value) {
      translateMode.value = false
      passageRef.value?.removeEventListener('mouseup', onPassageMouseUp)
      questionsColRef.value?.removeEventListener('mouseup', onPassageMouseUp)
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
  } catch (e) {
    ElMessage.error('添加失败，请稍后重试')
  }
  flushing.value = false
}

// ── text highlighter ───────────────────────────────────
const highlightMode = ref(false)
const highlightColor = ref('rgba(166,110,60,0.32)')
const showColorPicker = ref(false)

const ERASER_VALUE = '__eraser__'
const highlightColors = [
  { name: '棕色（默认）', value: 'rgba(166,110,60,0.32)' },
  { name: '黄色', value: 'rgba(250,204,21,0.45)' },
  { name: '绿色', value: 'rgba(74,222,128,0.40)' },
  { name: '蓝色', value: 'rgba(96,165,250,0.40)' },
  { name: '粉色', value: 'rgba(244,114,182,0.40)' },
  { name: '橙色', value: 'rgba(251,146,60,0.40)' },
  { name: '橡皮擦', value: ERASER_VALUE },
]

function makeHighlightClickHandler(span) {
  span.addEventListener('click', (ev) => {
    if (!highlightMode.value) return
    ev.stopPropagation()
    const parent = span.parentNode
    if (!parent) return
    while (span.firstChild) parent.insertBefore(span.firstChild, span)
    parent.removeChild(span)
    parent.normalize()
  })
}

function createHighlightSpan() {
  return createHighlightSpanWithColor(highlightColor.value)
}

function createHighlightSpanWithColor(color) {
  const span = document.createElement('span')
  span.className = 'text-highlight'
  span.style.backgroundColor = color
  span.title = '点击可移除高亮'
  makeHighlightClickHandler(span)
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

function eraseHighlightsInRange(range) {
  const container = range.commonAncestorContainer
  const root = container.nodeType === Node.TEXT_NODE ? container.parentElement : container
  const hlSpans = Array.from((root || document).querySelectorAll('.text-highlight')).filter(s => range.intersectsNode(s))
  for (const span of hlSpans) {
    const parent = span.parentNode
    if (!parent) continue
    while (span.firstChild) parent.insertBefore(span.firstChild, span)
    parent.removeChild(span)
    parent.normalize()
  }
}

function onHighlightMouseUp(e) {
  const sel = window.getSelection()
  if (!sel || sel.isCollapsed || !sel.rangeCount) return
  const selectedText = sel.toString().trim()
  if (!selectedText) return
  if (e.target.closest?.('.exam-fabs')) return
  try {
    const range = sel.getRangeAt(0)
    // Eraser mode: remove highlights in selection
    if (highlightColor.value === ERASER_VALUE) {
      eraseHighlightsInRange(range)
      sel.removeAllRanges()
      return
    }
    // Check if selection is entirely inside an existing highlight
    const startEl = range.startContainer.nodeType === Node.TEXT_NODE
      ? range.startContainer.parentElement : range.startContainer
    const existingHL = startEl?.closest?.('.text-highlight')
    if (existingHL && existingHL.contains(range.endContainer)) {
      splitAndHighlight(range, existingHL)
      sel.removeAllRanges()
      return
    }
    // Simple case: selection within one element, not inside existing highlight
    try {
      const span = createHighlightSpan()
      range.surroundContents(span)
      sel.removeAllRanges()
      return
    } catch { /* crosses element boundary, fall through */ }
    // Cross-element: wrap each text node individually
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
    sel?.removeAllRanges()
  }
}

function splitAndHighlight(range, existingHL) {
  const textNode = range.startContainer
  // Only handle the common case: selection within a single text node directly inside the highlight
  if (textNode.nodeType !== Node.TEXT_NODE || textNode.parentNode !== existingHL
      || range.startContainer !== range.endContainer) {
    // Complex nested case — fall back to wrapping (creates nesting, acceptable)
    try {
      const span = createHighlightSpan()
      range.surroundContents(span)
    } catch {
      const textNodes = getTextNodesInRange(range)
      for (const info of textNodes) {
        const subRange = document.createRange()
        subRange.setStart(info.node, info.start)
        subRange.setEnd(info.node, info.end)
        const s = createHighlightSpan()
        subRange.surroundContents(s)
      }
    }
    return
  }
  const oldColor = existingHL.style.backgroundColor
  const parent = existingHL.parentNode
  if (!parent) return
  const fullText = textNode.textContent
  const beforeText = fullText.substring(0, range.startOffset)
  const selText = fullText.substring(range.startOffset, range.endOffset)
  const afterText = fullText.substring(range.endOffset)
  // Collect all sibling nodes in the existing highlight (preserve other nested highlights)
  const childNodes = Array.from(existingHL.childNodes)
  const idx = childNodes.indexOf(textNode)
  const frag = document.createDocumentFragment()
  // Siblings before the selected text node → wrap in old-color span
  const beforeChildren = childNodes.slice(0, idx)
  if (beforeChildren.length || beforeText) {
    const span = createHighlightSpanWithColor(oldColor)
    beforeChildren.forEach(c => span.appendChild(c.cloneNode(true)))
    if (beforeText) span.appendChild(document.createTextNode(beforeText))
    frag.appendChild(span)
  }
  // The selected text → wrap in new-color span
  const newSpan = createHighlightSpan()
  newSpan.textContent = selText
  frag.appendChild(newSpan)
  // Siblings after the selected text node → wrap in old-color span
  const afterChildren = childNodes.slice(idx + 1)
  if (afterText || afterChildren.length) {
    const span = createHighlightSpanWithColor(oldColor)
    if (afterText) span.appendChild(document.createTextNode(afterText))
    afterChildren.forEach(c => span.appendChild(c.cloneNode(true)))
    frag.appendChild(span)
  }
  parent.replaceChild(frag, existingHL)
}

function toggleTranslate() {
  translateMode.value = !translateMode.value
  if (translateMode.value) {
    // 互斥：关闭划重点和选词
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
    questionsColRef.value?.addEventListener('mouseup', onPassageMouseUp)
    ElMessage.info({ message: '翻译模式已开启：选中文章或题目中的句子/短语后点击翻译', duration: 2500 })
  } else {
    passageRef.value?.removeEventListener('mouseup', onPassageMouseUp)
    questionsColRef.value?.removeEventListener('mouseup', onPassageMouseUp)
    hideTranslateBubble()
  }
}

function toggleHighlight() {
  highlightMode.value = !highlightMode.value
  showColorPicker.value = false
  if (highlightMode.value) {
    // 互斥：关闭翻译和选词
    if (translateMode.value) {
      translateMode.value = false
      passageRef.value?.removeEventListener('mouseup', onPassageMouseUp)
      questionsColRef.value?.removeEventListener('mouseup', onPassageMouseUp)
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

function handleHighlightShortcut(e) {
  if (e.ctrlKey || e.metaKey || e.altKey) return
  const target = e.target
  const tag = target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target?.isContentEditable) return
  if (e.key !== 'F2') return
  e.preventDefault()
  toggleHighlight()
}

onMounted(() => {
  const loaded = examStore.loadExam(route.params.id)
  if (!loaded) {
    router.push('/exams')
    return
  }
  if (exam.value?.duration) {
    timeLeft.value = exam.value.duration * 60
  }
  timer = setInterval(() => {
    if (timeLeft.value > 0) {
      timeLeft.value--
    } else {
      clearInterval(timer)
      doSubmit()
    }
  }, 1000)
  window.addEventListener('resize', resizeWriteCharts)
  window.addEventListener('keydown', handleHighlightShortcut)
  nextTick(() => requestAnimationFrame(() => renderWriteCharts()))

  // click outside to close translate bubble
  document.addEventListener('mousedown', onDocMouseDownForTranslate)

  // Debug: log backend payload and parsed visual
  logVisualDebug('mounted')
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
  window.removeEventListener('keydown', handleHighlightShortcut)
  disposeWriteCharts()

  passageRef.value?.removeEventListener('mouseup', onPassageMouseUp)
  questionsColRef.value?.removeEventListener('mouseup', onPassageMouseUp)
  document.removeEventListener('mousedown', onDocMouseDownForTranslate)
  translateMode.value = false
})
</script>

<style scoped>
.exam-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-primary);
  overflow: hidden;
}

/* Top Bar */
.exam-topbar {
  height: 54px;
  background: var(--bg-white);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  flex-shrink: 0;
  z-index: 50;
}

.exam-topbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.back-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  border: 1.5px solid var(--border-color);
  color: var(--text-secondary);
  text-decoration: none;
  transition: all 0.15s;
  flex-shrink: 0;
}
.back-btn:hover { border-color: var(--color-primary); color: var(--color-primary); }

.exam-info {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.exam-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 280px;
}

.exam-section {
  font-size: 11px;
  color: var(--text-muted);
}

.exam-topbar-center {
  flex: 0 0 auto;
}

.timer {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 18px;
  font-weight: 700;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
  padding: 6px 16px;
  background: rgba(27,67,50,0.06);
  border-radius: var(--radius-full);
}

.timer.timer-warning {
  color: #DC2626;
  background: #FEF2F2;
  animation: blink 1s ease infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.exam-topbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  justify-content: flex-end;
}

.sheet-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: var(--radius-full);
  border: 1.5px solid var(--border-color);
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  background: var(--bg-white);
  cursor: pointer;
  transition: all 0.15s;
}
.sheet-btn:hover { border-color: var(--color-primary); color: var(--color-primary); }

.submit-btn-top {
  padding: 8px 20px;
  border-radius: var(--radius-full);
  background: var(--color-primary);
  color: white;
  font-size: 13px;
  font-weight: 600;
  border: none;
  cursor: pointer;
  transition: all 0.15s;
}
.submit-btn-top:hover { background: var(--color-primary-hover); }

/* Body */
.exam-body {
  flex: 1;
  display: flex;
  overflow: hidden;
  position: relative;
}

/* Passage Column */
.passage-col {
  width: 55%;
  flex-shrink: 0;
  border-right: 1px solid var(--border-color);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  position: relative;
}

.translate-bubble {
  position: absolute;
  z-index: 20;
  min-width: 220px;
  max-width: 360px;
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid rgba(148, 163, 184, 0.45);
  border-radius: 12px;
  box-shadow: 0 14px 40px rgba(15, 23, 42, 0.12);
  padding: 10px 12px;
}

.tb-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.tb-btn {
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid rgba(59, 130, 246, 0.35);
  background: rgba(59, 130, 246, 0.12);
  color: #1D4ED8;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
}

.tb-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.tb-close {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  border: 1px solid rgba(148, 163, 184, 0.45);
  background: #fff;
  cursor: pointer;
  color: #475569;
  font-size: 16px;
  line-height: 1;
}

.tb-result { margin-top: 10px; }
.tb-translation { font-size: 13px; color: #0F172A; line-height: 1.55; font-weight: 600; }
.tb-notes { margin-top: 6px; font-size: 12px; color: #64748B; line-height: 1.5; }

.passage-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--border-light);
  position: sticky;
  top: 0;
  background: var(--bg-white);
  z-index: 1;
}

.passage-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}

.passage-controls {
  display: flex;
  gap: 4px;
}

.ctrl-btn {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  border: 1.5px solid var(--border-color);
  background: var(--bg-white);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  color: var(--text-secondary);
  transition: all 0.15s;
}
.ctrl-btn:hover { border-color: var(--color-primary); color: var(--color-primary); }

.passage-body {
  padding: 24px 28px;
  line-height: 1.9;
  color: var(--text-primary);
}

.passage-divider {
  border: none;
  border-top: 2px dashed #D1E7D7;
  margin: 28px 0 20px;
}

.passage-section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.passage-section-badge {
  display: inline-block;
  background: #1B4332;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  padding: 3px 10px;
  border-radius: 20px;
}

.q-passage-divider {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 18px 0 10px;
  padding: 0 2px;
}

.q-passage-divider::before,
.q-passage-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #D1E7D7;
}

.q-passage-label {
  font-size: 11px;
  font-weight: 700;
  color: #1B4332;
  background: #E8F5E9;
  border: 1px solid #A8D5B5;
  padding: 2px 10px;
  border-radius: 20px;
  white-space: nowrap;
}

.passage-para {
  margin-bottom: 16px;
  text-align: justify;
}

.passage-para.labeled-para {
  display: grid;
  grid-template-columns: 24px 1fr;
  column-gap: 6px;
  align-items: start;
}

.passage-letter {
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 18px;
  font-weight: 800;
  color: var(--text-primary);
  line-height: 1.9;
  text-align: center;
}

.passage-text {
  min-width: 0;
}

.passage-para :deep(.passage-keyword) {
  background: #D4F5E9;
  color: #0D5932;
  border-radius: 3px;
  padding: 1px 3px;
  font-weight: 700;
  border-bottom: 2px solid #1B9E6E;
  transition: background 0.2s;
}

.q-text :deep(.question-keyword) {
  background: transparent;
  color: #1565C0;
  font-weight: 600;
  border-bottom: 2px dotted #1565C0;
  padding-bottom: 1px;
}

.question-block.active {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(27, 67, 50, 0.08);
}

.passage-para :deep(.highlight-red) {
  background: #FFEBEE;
  color: #C62828;
  border-radius: 3px;
  padding: 0 2px;
}

/* Questions Column */
.questions-col {
  flex: 1;
  overflow-y: auto;
  background: var(--bg-primary);
  position: relative;
}

.questions-inner {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.question-block {
  background: var(--bg-white);
  border-radius: var(--radius-lg);
  padding: 20px;
  border: 2px solid transparent;
  transition: all 0.15s;
}

.question-block.answered {
  border-color: var(--color-accent-light);
}

.q-number-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.hint-btn {
  background: none;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 2px 7px;
  font-size: 14px;
  cursor: pointer;
  color: var(--text-secondary);
  transition: all 0.15s;
  line-height: 1.6;
}
.hint-btn:hover { border-color: #F59E0B; background: #FFFBEB; }
.hint-btn.active { border-color: #F59E0B; background: #FEF3C7; }

.q-number {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--bg-primary);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
}

.question-block.answered .q-number {
  background: var(--color-primary);
  color: white;
}

.q-text {
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-primary);
  margin-bottom: 14px;
}

/* TFNG */
.tfng-options {
  display: flex;
  gap: 8px;
}

.tfng-btn {
  flex: 1;
  padding: 8px 10px;
  border-radius: var(--radius-md);
  border: 1.5px solid var(--border-color);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  background: var(--bg-white);
  color: var(--text-secondary);
  transition: all 0.15s;
  text-align: center;
}

.tfng-btn:hover { border-color: var(--color-primary); color: var(--color-primary); }

.tfng-btn.selected {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: white;
}

/* MCQ collapse toggle */
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

/* MCQ */
.mcq-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mcq-btn {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 14px;
  border-radius: var(--radius-md);
  border: 1.5px solid var(--border-color);
  background: var(--bg-white);
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;
}

.mcq-btn:hover { border-color: var(--color-primary); }

.mcq-btn.selected {
  border-color: var(--color-primary);
  background: rgba(27,67,50,0.04);
}

.opt-label {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--bg-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  flex-shrink: 0;
}

.mcq-btn.selected .opt-label {
  background: var(--color-primary);
  color: white;
}

.opt-text {
  font-size: 13px;
  line-height: 1.5;
  color: var(--text-primary);
}

/* Fill */
.fill-blank {
  font-family: monospace;
  color: var(--text-muted);
  padding: 0 4px;
}

.fill-input-wrap {
  margin-top: 10px;
}

.fill-input {
  width: 100%;
  padding: 10px 14px;
  border: 1.5px solid var(--border-color);
  border-radius: var(--radius-md);
  font-size: 14px;
  font-family: var(--font-sans);
  outline: none;
  transition: border-color 0.15s;
  background: var(--bg-white);
  color: var(--text-primary);
}

.fill-input:focus { border-color: var(--color-primary); }

/* Write Task Passage (left column) */
.write-task-body {
  padding: 24px 28px;
  line-height: 1.8;
}
.write-visual-panel {
  margin: 0 0 16px;
  border: 1px solid #DDEFE6;
  border-radius: 12px;
  background: linear-gradient(180deg, #F7FFFB 0%, #FFFFFF 100%);
  padding: 14px;
}
.wvp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}
.wvp-title {
  font-weight: 700;
  color: #1B5E46;
  font-size: 0.92em;
}
.wvp-sub {
  font-size: 0.8em;
  color: #5F7A6F;
  background: #EBF7F1;
  border: 1px solid #D4EBDD;
  border-radius: 999px;
  padding: 2px 8px;
}
.wvp-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.wvp-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.wvp-tab {
  padding: 8px 12px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #334155;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.15s ease;
}

.wvp-tab.active {
  background: #e0f2fe;
  border-color: #38bdf8;
  color: #0f172a;
  box-shadow: 0 4px 12px rgba(56, 189, 248, 0.25);
}

.wvp-tab:hover {
  transform: translateY(-1px);
}
.wvp-summary-item {
  font-size: 0.78em;
  color: #2F4B3F;
  background: #F3F8F5;
  border: 1px solid #E3EDE8;
  border-radius: 8px;
  padding: 4px 8px;
}
.wvp-charts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 12px;
}
.wvp-chart-canvas {
  height: 260px;
  border: 1px solid #E2E8F0;
  border-radius: 10px;
  background: #fff;
}
.wvp-table-wrap {
  overflow-x: auto;
  border: 1px solid #000;
  border-radius: 0;
  background: #fff;
  margin-top: 16px;
  margin-bottom: 16px;
  padding: 2px;
}
.wvp-table-title {
  font-weight: bold;
  font-size: 0.95em;
  margin-bottom: 8px;
  padding: 4px 8px;
  background: #f5f5f5;
  border-bottom: 1px solid #ddd;
}
.wvp-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85em;
  line-height: 1.4;
  font-family: Arial, sans-serif;
}
.wvp-table th,
.wvp-table td {
  border: 1px solid #000;
  padding: 6px 10px;
  text-align: center;
  vertical-align: middle;
  font-size: 0.85em;
}
.wvp-table th {
  background: #f0f0f0;
  font-weight: bold;
  font-size: 0.8em;
  color: #000;
  text-align: center;
}
.wvp-table td {
  background: #fff;
}
.wvp-table tr:hover td {
  background: #f9f9f9;
}
.wtp-gap {
  height: 10px;
}
.wtp-title {
  font-size: 1.1em;
  font-weight: 700;
  color: var(--color-primary);
  margin: 0 0 16px;
  padding-bottom: 10px;
  border-bottom: 2px solid var(--color-accent-light);
}
.wtp-topic {
  margin: 8px 0;
  font-size: 0.95em;
}
.wtp-topic-label {
  font-weight: 700;
  color: var(--color-primary);
}
.wtp-directions {
  margin: 12px 0;
  font-size: 0.93em;
  color: var(--text-primary);
  background: #F0FFF4;
  border-left: 3px solid var(--color-primary);
  padding: 8px 12px;
  border-radius: 0 6px 6px 0;
}
.wtp-dir-label {
  font-weight: 700;
  color: var(--color-primary);
}
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
.wtp-list {
  margin: 0 0 10px 0;
  padding-left: 20px;
  font-size: 0.93em;
}
.wtp-list li {
  margin: 4px 0;
  color: var(--text-primary);
}
.wtp-para {
  margin: 6px 0;
  font-size: 0.93em;
  color: var(--text-primary);
}
.wtp-table-wrap {
  margin: 10px 0 14px;
  overflow-x: auto;
  border: 1px solid #E5E7EB;
  border-radius: 10px;
  background: #fff;
}
.wtp-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9em;
  line-height: 1.5;
}
.wtp-table th,
.wtp-table td {
  border: 1px solid #E5E7EB;
  padding: 8px 10px;
  vertical-align: top;
  text-align: left;
  white-space: nowrap;
}
.wtp-table th {
  background: #F8FAFC;
  color: #334155;
  font-weight: 700;
}

/* Write Hint Panel */
.write-hint-panel {
  margin-top: 12px;
  border: 1.5px solid #F59E0B;
  border-radius: 8px;
  background: #FFFBEB;
  padding: 12px 14px;
  font-size: 13px;
}
.hint-panel-title {
  font-weight: 700;
  color: #92400E;
  margin-bottom: 10px;
  font-size: 13px;
}
.hint-section-label {
  font-weight: 600;
  color: var(--text-secondary);
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 4px;
}
.hint-points {
  background: white;
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 8px;
  color: var(--text-primary);
  line-height: 1.6;
}
.hint-criteria {
  background: #F0FFF4;
  border-radius: 6px;
  padding: 8px 10px;
  color: var(--text-primary);
  line-height: 1.6;
}
.hint-points p, .hint-criteria p { margin: 0; }

.write-task-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 700;
  color: var(--color-primary);
  background: #E8F5E9;
  border-radius: 6px;
  padding: 4px 10px;
  margin-bottom: 10px;
}
.word-limit { font-weight: 400; color: var(--text-secondary); }

.write-prompt {
  font-size: 13px;
  line-height: 1.7;
  margin-bottom: 10px;
  color: var(--text-primary);
}

.write-input-wrap { margin-top: 8px; }

.write-textarea {
  width: 100%;
  padding: 12px 14px;
  border: 1.5px solid var(--border-color);
  border-radius: var(--radius-md);
  font-size: 13px;
  line-height: 1.7;
  resize: vertical;
  font-family: inherit;
  color: var(--text-primary);
  background: var(--bg-white);
  transition: border-color 0.15s;
  box-sizing: border-box;
}
.write-textarea:focus { outline: none; border-color: var(--color-primary); }

.word-count {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-secondary);
  text-align: right;
}
.wc-ok { color: #2E7D32; font-weight: 600; }
.wc-warn { color: #E65100; }

/* Answer Sheet */
.answer-sheet {
  position: fixed;
  right: 0;
  top: 54px;
  bottom: 0;
  width: 280px;
  background: var(--bg-white);
  border-left: 1px solid var(--border-color);
  z-index: 100;
  display: flex;
  flex-direction: column;
  padding: 20px;
  gap: 16px;
  box-shadow: -4px 0 24px rgba(0,0,0,0.08);
}

.sheet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sheet-header h3 { font-size: 15px; font-weight: 700; }

.sheet-close {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--bg-primary);
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
}

.progress-bar {
  height: 4px;
  background: var(--border-color);
  border-radius: 2px;
  overflow: hidden;
  margin-bottom: 6px;
}

.progress-fill {
  height: 100%;
  background: var(--color-primary);
  border-radius: 2px;
  transition: width 0.3s;
}

.progress-text {
  font-size: 12px;
  color: var(--text-muted);
}

.sheet-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 6px;
}

.sheet-num {
  width: 100%;
  aspect-ratio: 1;
  border-radius: var(--radius-sm);
  border: 1.5px solid var(--border-color);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  background: var(--bg-white);
  color: var(--text-muted);
  transition: all 0.15s;
}

.sheet-num:hover { border-color: var(--color-primary); color: var(--color-primary); }
.sheet-num.answered { background: var(--color-primary); color: white; border-color: var(--color-primary); }
.sheet-num.current { border-color: var(--color-accent); outline: 2px solid rgba(82,183,136,0.3); }

.sheet-legend {
  display: flex;
  gap: 16px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  border: 1.5px solid var(--border-color);
}

.legend-dot.answered {
  background: var(--color-primary);
  border-color: var(--color-primary);
}

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
  margin-top: auto;
}
.submit-btn-sheet:hover { background: var(--color-primary-hover); }

/* Transitions */
.slide-right-enter-active, .slide-right-leave-active {
  transition: transform 0.25s ease, opacity 0.25s ease;
}
.slide-right-enter-from, .slide-right-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

/* Loading */
.exam-loading {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--text-muted);
}

/* confirm-content styles moved to main.css */

/* ── Collect mode ─────────────────────────────────────── */
.exam-body.collect-mode { cursor: crosshair; }
.exam-body.collect-mode .passage-body,
.exam-body.collect-mode .passage-para,
.exam-body.collect-mode .q-text,
.exam-body.collect-mode .write-task-body { cursor: text; user-select: text; }
.exam-body.collect-mode input,
.exam-body.collect-mode textarea,
.exam-body.collect-mode button { cursor: default; }

/* ── FAB Group container ───────────────────────────────── */
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

/* ── Highlight FAB ─────────────────────────────────────── */
.highlight-fab {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.hl-toggle { background: #6B4226 !important; }
.hl-toggle:hover { background: #4e2f1a !important; }
.highlight-fab.active .hl-toggle { background: #374151 !important; }

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

.cp-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-secondary);
  letter-spacing: 0.04em;
}

.color-swatches {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

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
.color-swatch.eraser {
  background: #f5f5f5;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.color-dot-btn.eraser {
  background: #f5f5f5 !important;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ── Translate FAB ─────────────────────────────────────── */
.translate-fab {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.tl-toggle { background: #1D4ED8 !important; }
.tl-toggle:hover { background: #1E40AF !important; }
.translate-fab.active .tl-toggle { background: #374151 !important; }

/* ── Word Collector FAB ────────────────────────────────── */
.word-collector-fab {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

/* ── Text highlight span ───────────────────────────────── */
.text-highlight {
  border-radius: 3px;
  padding: 1px 0;
  cursor: pointer;
  transition: filter 0.15s;
}
.text-highlight:hover { filter: brightness(0.88); }

/* ── Highlight mode cursor ─────────────────────────────── */
.exam-body.highlight-mode { cursor: text; }
.exam-body.highlight-mode .passage-body,
.exam-body.highlight-mode .passage-para,
.exam-body.highlight-mode .q-text { cursor: text; user-select: text; }

.fab-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

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
.word-collector-fab.active .fab-toggle { background: #374151; box-shadow: 0 4px 16px rgba(0,0,0,0.25); }

.fab-icon { font-size: 15px; }
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

.collected-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

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

.collector-expand-enter-active,
.collector-expand-leave-active { transition: opacity 0.2s, transform 0.2s; }
.collector-expand-enter-from,
.collector-expand-leave-to { opacity: 0; transform: translateY(8px) scale(0.97); }

.fab-spacer { height: 6px; }

.fab-minimize-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid #D1D5DB;
  background: #fff;
  color: #6B7280;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  transition: all 0.2s;
  align-self: flex-end;
}
.fab-minimize-btn:hover { background: #F3F4F6; border-color: #9CA3AF; }

/* ── AI Assistant FAB & Chat Panel ──────────────────────── */
.ai-assistant-fab {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}
.ai-toggle { background: #2D6A4F !important; }
.ai-toggle:hover { background: #1B4332 !important; }

.ai-chat-panel {
  position: fixed; z-index: 1000;
  background: var(--bg-white, #fff); border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.18); display: flex; flex-direction: column;
  overflow: hidden; border: 1px solid var(--border-color, #E5E7EB);
}
.ai-chat-panel.ai-maximized {
  left: 5vw !important; top: 5vh !important;
  width: 90vw !important; height: 90vh !important;
  border-radius: 16px;
}
.ai-chat-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 14px; background: #2D6A4F; color: #fff; font-size: 14px; font-weight: 600;
  cursor: grab; user-select: none; flex-shrink: 0;
}
.ai-chat-header:active { cursor: grabbing; }
.ai-header-actions { display: flex; align-items: center; gap: 4px; }
.ai-chat-btn { background: none; border: none; color: #fff; font-size: 18px; cursor: pointer; padding: 2px 6px; opacity: 0.8; border-radius: 4px; display: flex; align-items: center; justify-content: center; }
.ai-chat-btn:hover { opacity: 1; background: rgba(255,255,255,0.15); }
.ai-chat-messages {
  flex: 1; overflow-y: auto; padding: 12px;
  display: flex; flex-direction: column; gap: 10px;
}
.ai-resize-handle {
  position: absolute; right: 0; bottom: 0; width: 16px; height: 16px; cursor: nwse-resize;
  background: linear-gradient(135deg, transparent 50%, rgba(0,0,0,0.15) 50%);
  border-radius: 0 0 12px 0;
}
.ai-chat-empty {
  text-align: center;
  color: #9CA3AF;
  font-size: 13px;
  padding: 30px 10px;
}
.ai-chat-empty p { margin: 4px 0; }
.ai-chat-hint { font-size: 12px; color: #D1D5DB; }

.ai-msg { display: flex; }
.ai-msg.user { justify-content: flex-end; }
.ai-msg.assistant { justify-content: flex-start; }

.ai-msg-bubble {
  max-width: 85%;
  padding: 8px 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.5;
}
.ai-msg-bubble p { margin: 0; }

.ai-msg.user .ai-msg-bubble {
  background: #2D6A4F;
  color: #fff;
  border-bottom-right-radius: 3px;
}
.ai-msg.assistant .ai-msg-bubble {
  background: #F3F4F6;
  color: #1F2937;
  border-bottom-left-radius: 3px;
}

.ai-typing {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 10px 16px;
}
.ai-typing .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #9CA3AF;
  animation: ai-dot-bounce 1.2s infinite ease-in-out;
}
.ai-typing .dot:nth-child(2) { animation-delay: 0.2s; }
.ai-typing .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes ai-dot-bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

.ai-chat-input {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid #E5E7EB;
  background: #FAFAFA;
}
.ai-chat-input input {
  flex: 1;
  border: 1px solid #D1D5DB;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s;
}
.ai-chat-input input:focus { border-color: #2D6A4F; }
.ai-chat-input button {
  padding: 8px 16px;
  background: #2D6A4F;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s;
  white-space: nowrap;
}
.ai-chat-input button:hover:not(:disabled) { background: #1B4332; }
.ai-chat-input button:disabled { opacity: 0.5; cursor: not-allowed; }

/* Markdown in AI bubbles */
.ai-msg-bubble :deep(.md-p) { margin: 2px 0; }
.ai-msg-bubble :deep(.md-h) { font-size: 14px; font-weight: 700; margin: 6px 0 2px; }
.ai-msg-bubble :deep(.md-list) { margin: 4px 0; padding-left: 18px; }
.ai-msg-bubble :deep(.md-list li) { margin: 2px 0; }
.ai-msg-bubble :deep(.md-quote) { margin: 4px 0; padding: 4px 10px; border-left: 3px solid #9CA3AF; color: #6B7280; font-style: italic; }
.ai-msg-bubble :deep(.md-code) { background: rgba(0,0,0,0.06); padding: 1px 5px; border-radius: 3px; font-family: monospace; font-size: 12px; }
.ai-msg-bubble :deep(.md-code-block) { background: rgba(0,0,0,0.06); padding: 8px 10px; border-radius: 6px; font-family: monospace; font-size: 12px; overflow-x: auto; margin: 4px 0; white-space: pre-wrap; }
.ai-msg-bubble :deep(strong) { font-weight: 700; }
.ai-msg-bubble :deep(em) { font-style: italic; }

@media (max-width: 1024px) {
  .write-visual-panel {
    padding: 10px;
  }
  .wvp-charts {
    grid-template-columns: 1fr;
  }
  .wvp-chart-canvas {
    height: 240px;
  }
  .ai-chat-panel:not(.ai-maximized) {
    width: 300px !important;
    height: 400px !important;
  }
}
</style>
