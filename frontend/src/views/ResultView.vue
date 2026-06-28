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
              <!-- Visual Data Panel (charts + tables) -->
              <div v-if="passageVisual.hasVisual" class="result-visual-panel">
                <div class="rvp-header">
                  <span class="rvp-title"><i class="fa-solid fa-chart-column"></i> 图表数据可视化</span>
                  <div class="rvp-header-meta" v-if="activeVisualChart">
                    <span class="rvp-chart-title" v-if="activeVisualChart.chartTitle">{{ activeVisualChart.chartTitle }}</span>
                    <span class="rvp-sub" v-if="activeVisualChart.chartType">{{ activeVisualChart.chartType }}</span>
                  </div>
                </div>
                <div v-if="passageVisual.segments.length > 1" class="rvp-tabs">
                  <button
                    v-for="(seg, segIdx) in passageVisual.segments"
                    :key="`seg-${segIdx}`"
                    :class="['rvp-tab', { active: segIdx === activeVisualIdx }]"
                    @click="activeVisualIdx = segIdx"
                  >
                    {{ seg.chartTitle || `图表 ${segIdx + 1}` }}
                  </button>
                </div>
                <div v-if="activeVisualChart?.summary?.length" class="rvp-summary">
                  <div class="rvp-summary-item" v-for="(s, i) in activeVisualChart.summary" :key="`sum-${i}`">{{ s }}</div>
                </div>
                <div v-if="activeVisualChart?.chartData?.length" class="rvp-charts">
                  <div v-if="activeVisualChart.chartType && activeVisualChart.chartType.toLowerCase().includes('bar')" ref="writeBarChartRef" class="rvp-chart-canvas"></div>
                  <div v-if="activeVisualChart.chartType && activeVisualChart.chartType.toLowerCase().includes('pie')" ref="writePieChartRef" class="rvp-chart-canvas"></div>
                </div>
                <div v-for="(t, ti) in (passageVisual.tables || [])" :key="`rvp-table-${ti}`" v-if="t && t.headers && t.rows && t.headers.length && t.rows.length" class="rvp-table-wrap">
                  <div v-if="t.title" class="rvp-table-title">{{ t.title }}</div>
                  <table class="rvp-table">
                    <thead><tr><th v-for="(h, hi) in (t.headers || [])" :key="`h-${ti}-${hi}`">{{ h }}</th></tr></thead>
                    <tbody><tr v-for="(row, ri) in (t.rows || [])" :key="`r-${ti}-${ri}`"><td v-for="(cell, ci) in (row || [])" :key="`c-${ti}-${ri}-${ci}`">{{ cell }}</td></tr></tbody>
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
                <p v-else :class="['result-para', { 'labeled-para': para.paragraphLabel }]">
                  <span v-if="para.paragraphLabel" class="passage-letter">{{ para.paragraphLabel }}</span>
                  <span class="passage-text" v-html="para.html"></span>
                </p>
              </template>
            </div>
          </div>

          <!-- Question Review -->
          <div class="result-questions" ref="questionsColRef">
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
                      <div v-else-if="q.userAnswer" class="ai-grade-pending"><i class="fa-solid fa-hourglass-half"></i> AI评分中...</div>
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
                    <p v-if="q.explanation" class="review-explanation">
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
        <div v-else class="questions-compact card" ref="compactQuestionsRef">
          <div
            v-if="translateBubble.show && translateBubble.host === 'compact'"
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
                    <div v-else-if="q.userAnswer" class="ai-grade-pending"><i class="fa-solid fa-hourglass-half"></i> AI评分中...</div>
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
                  <p v-if="q.explanation" class="review-explanation">
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

    <!-- FAB Group -->
    <div class="result-fabs">
      <button class="fab-minimize-btn" @click.stop="showFabs = !showFabs" :title="showFabs ? '隐藏工具栏' : '显示工具栏'">
        <i :class="showFabs ? 'fa-solid fa-xmark' : 'fa-solid fa-screwdriver-wrench'"></i>
      </button>
      <template v-if="showFabs">
        <!-- Translate FAB -->
        <div class="translate-fab" :class="{ active: translateMode }">
          <div class="fab-row">
            <button class="fab-toggle tl-toggle" @click.stop="toggleTranslate" :title="translateMode ? '退出翻译模式' : '翻译'">
              <span class="fab-icon"><i class="fa-solid fa-globe"></i></span>
              <span class="fab-label">{{ translateMode ? '退出翻译' : '翻译' }}</span>
            </button>
          </div>
        </div>
        <!-- AI Assistant FAB -->
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
              <div v-if="result?.isCollection && aiContextOptions.length" class="ai-context-selector">
                <span class="ai-context-label">上下文</span>
                <select v-model="aiContextExamId" :disabled="aiLoading">
                  <option value="all">全部试卷</option>
                  <option v-for="opt in aiContextOptions" :key="opt.value" :value="opt.value">
                    {{ opt.label }}
                  </option>
                </select>
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
                <input v-model="aiQuestion" placeholder="输入问题..." @keyup.enter="sendAiChat" :disabled="aiLoading" />
                <button @click="sendAiChat" :disabled="aiLoading || !aiQuestion.trim()">发送</button>
              </div>
              <div class="ai-resize-handle" @mousedown.prevent="startResize"></div>
            </div>
          </transition>
          <div class="fab-row">
            <button class="fab-toggle ai-toggle" @click.stop="aiChatOpen = !aiChatOpen" title="AI 助手">
              <span class="fab-icon"><i class="fa-solid fa-robot"></i></span>
              <span class="fab-label">{{ aiChatOpen ? '关闭助手' : 'AI 助手' }}</span>
            </button>
          </div>
        </div>
      </template>
    </div>
  </div>
  <div v-else class="no-result">
    <p>暂无考试结果</p>
    <router-link to="/exams" class="btn-primary">返回试卷列表</router-link>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useExamStore } from '@/stores/exam'
import { useThemeStore } from '@/stores/theme'
import NavBar from '@/components/NavBar.vue'
import * as echarts from 'echarts'
import { translateApi } from '@/api/translate'
import request from '@/api/index'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const examStore = useExamStore()
const themeStore = useThemeStore()

// 从 :root / html.dark 读取 CSS 变量，保证 ECharts 配色跟随主题
function cssVar(name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

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
const questionsColRef = ref()
const compactQuestionsRef = ref()
const showFabs = ref(true)
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
  if (result.value?.passages?.length) {
    return result.value.passages.map(p => `【${p.title}】\n${p.passage}`).join('\n\n')
  }
  const section = examStore.currentExam?.sections?.[0]
  if (section?.passage) return section.passage
  return ''
})

const passageVisual = computed(() => buildVisualPreferStructured(result.value, rawPassageText.value))
const activeVisualIdx = ref(0)
const activeVisualChart = computed(() => {
  const segments = passageVisual.value?.segments || []
  if (!segments.length) return null
  const idx = Math.min(activeVisualIdx.value, segments.length - 1)
  return segments[idx]
})

const passageParagraphs = computed(() => {
  if (result.value?.isCollection && result.value?.passages?.length) {
    const paragraphs = []
    result.value.passages.forEach((p, passageIdx) => {
      paragraphs.push({
        html: '',
        isHeader: true,
        label: p.title || `试卷 ${passageIdx + 1}`,
      })
      const raw = passageVisual.value?.hasVisual
        ? stripVisualBlocks(p.passage || '', passageVisual.value?.segments)
        : (p.passage || '')
      raw.split('\n\n').filter(Boolean).forEach(para => {
        const parsed = extractParagraphLabel(para)
        const normalised = parsed.text.replace(/\n/g, ' ')
        paragraphs.push({
          html: highlightPassage(normalised, p.examId),
          isHeader: false,
          label: null,
          paragraphLabel: parsed.label,
        })
      })
    })
    return paragraphs
  }
  if (!rawPassageText.value) return []
  const cleaned = passageVisual.value?.hasVisual
    ? stripVisualBlocks(rawPassageText.value, passageVisual.value?.segments)
    : rawPassageText.value
  return cleaned.split('\n\n').filter(Boolean).map((para, idx) => {
    const markerMatch = para.match(PASSAGE_MARKER)
    const isHeader = !!markerMatch
    const label = markerMatch ? markerMatch[1] : null
    // Strip header-line codes (e.g. "P11 ZKRIN5E") from body
    const bodyText = isHeader
      ? (para.includes('\n') ? para.replace(/^[^\n]*\n/, '').trim()
                              : para.replace(/^P\d+\s+\S*\s*/, '').trim())
      : para
    const parsed = extractParagraphLabel(bodyText)
    // Normalise single newlines to spaces, then highlight
    const normalised = parsed.text.replace(/\n/g, ' ')
    return { html: highlightPassage(normalised), isHeader, label, paragraphLabel: parsed.label }
  })
})

function extractParagraphLabel(text) {
  const raw = String(text || '').trim()
  const match = raw.match(/^([A-Z])(?:[.)])?\s+(.+)$/s)
  if (!match) return { label: '', text: raw }
  return { label: match[1], text: match[2].trim() }
}

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

function highlightPassage(rawText, examId = null) {
  if (!rawText) return ''
  // HTML-escape first so injected marks are safe
  let html = escapeHtml(rawText)
  if (!result.value) return html

  const sourceQuestions = examId == null
    ? result.value.questions
    : result.value.questions.filter(q => !q.examId || String(q.examId) === String(examId))
  const wrongQuestions = sourceQuestions.filter(q => !q.isCorrect && q.locatorText)
  const correctQuestions = sourceQuestions.filter(q => q.isCorrect && q.locatorText)

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
  const segments = parseVisualSegments(text)
  const tableBlock = extractTaggedBlock(text, 'Table Data')
  let tableTitle = ''
  if (tableBlock) {
    const titleLine = tableBlock.split('\n').map(l => l.trim()).find(l => /^tableTitle\s*[:：]/i.test(l))
    if (titleLine) tableTitle = titleLine.replace(/^tableTitle\s*[:：]\s*/i, '').trim()
  }
  // Only parse tables when a [Table Data] block exists; avoid mis-detecting pipes in series lines
  const tableLines = findLongestTableBlock(tableBlock || '')
  const table = parseVisualTable(tableLines, tableTitle)
  if (!segments.length && table.rows.length) {
    const fallbackData = extractChartDataFromTable(table)
    if (fallbackData.length) {
      segments.push({
        chartTitle: table.title || 'Table Data',
        chartType: 'table',
        chartData: fallbackData,
        summary: table.rows.map(row => row.join(' • ')).slice(0, 6),
        removalLines: [],
      })
    }
  }
  const hasVisual = segments.some(seg => seg.chartData?.length) || table.rows.length > 0
  return { hasVisual, segments, tables: table.rows.length ? [table] : [] }
}

// Prefer backend structured charts/tables, fallback to text parsing
function buildVisualPreferStructured(res, rawText) {
  const charts = res?.charts || []
  const tables = res?.tables || []
  if (charts.length || tables.length) {
    const segs = []
    for (const c of charts) {
      const type = String(c.type || '').toLowerCase()
      const title = c.title || ''
      if (type.includes('pie')) {
        const s0 = (c.series && c.series[0]) ? c.series[0] : { data: [] }
        const data = (s0.data || []).map(d => ({ label: String(d.label ?? d.name ?? ''), value: Number(d.value) || 0 }))
        segs.push({ chartTitle: title, chartType: 'pie chart', chartData: data, summary: [], removalLines: [], meta: { structured: true } })
      } else if (type.includes('bar') || type.includes('line')) {
        // Flatten structured multi-series into heuristic-friendly labels for existing renderer
        const series = Array.isArray(c.series) ? c.series : []
        const flat = []
        for (const s of series) {
          const name = String(s.name || 'value')
          const arr = Array.isArray(s.data) ? s.data : []
          for (const dp of arr) {
            const x = (dp && (dp.x != null)) ? dp.x : (dp?.label ?? '')
            const xStr = String(x)
            flat.push({ label: `${/^\d{4}$/.test(xStr) ? xStr + ' ' + name : name + ' ' + xStr}`, value: Number(dp?.value) || 0 })
          }
        }
        segs.push({ chartTitle: title, chartType: type.includes('line') ? 'line graph' : 'bar chart', chartData: flat, summary: [], removalLines: [], meta: { structured: true } })
      }
    }
    // For tables: return all
    const tableObjs = tables.map(t => ({
      title: String(t.title || ''),
      headers: Array.isArray(t.headers) ? t.headers : [],
      rows: Array.isArray(t.rows) ? t.rows : [],
    })).filter(t => t.headers.length && t.rows.length)
    return { hasVisual: Boolean(segs.length || tableObjs.length), segments: segs, tables: tableObjs }
  }
  // Fallback to legacy text parsing
  return buildWriteVisual(rawText)
}

function stripVisualBlocks(text, segments = []) {
  if (!text) return ''
  let cleaned = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
    .replace(/\n?\[Visual Data Summary\][\s\S]*?(?=\n\[[^\]]+\]|\n\n【|$)/gi, '\n')
    .replace(/\n?\[Table Data\][\s\S]*?(?=\n\[[^\]]+\]|\n\n【|$)/gi, '\n')
  if (segments?.length) {
    const removal = new Set()
    segments.forEach(seg => {
      seg?.removalLines?.forEach(line => {
        const trimmed = String(line || '').trim()
        if (trimmed) removal.add(trimmed)
      })
    })
    removal.forEach(line => {
      const escaped = line.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
      cleaned = cleaned.replace(new RegExp(`(^|\\n)${escaped}(?=\\n|$)`, 'gi'), '\n')
    })
  }
  // Fallback: remove any remaining chart meta/data-like lines to avoid泄漏到正文
  cleaned = cleaned
    .replace(/(^|\n)\s*chartTitle\s*[:：].*(?=\n|$)/gi, '\n')
    .replace(/(^|\n)\s*chartType\s*[:：].*(?=\n|$)/gi, '\n')
    .replace(/(^|\n)\s*[^\n:]{1,120}:\s*-?\d+(?:\.\d+)?(?:\s*(%|percent|percentage|million|millions|billion|billions|thousand|thousands|hundred|hundreds|m|k|bn).*)?(?=\n|$)/gi, '\n')
  return cleaned.replace(/\n{3,}/g, '\n\n').trim()
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

function parseVisualSegments(text) {
  const normalized = (text || '').replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  const lines = normalized.split('\n')
  const segments = []
  let current = null

  const flush = () => {
    if (!current) return
    if (!current.data.length) { current = null; return }
    segments.push({
      chartTitle: current.title,
      chartType: current.type,
      chartData: current.data.map(d => ({ label: d.label, value: d.value, unit: d.unit })),
      summary: current.summary.slice(0, 8),
      removalLines: [...current.removalLines],
      meta: { xAxis: current.xAxis || '', yAxis: current.yAxis || '', series: current.seriesDecl ? [...current.seriesDecl] : [] },
    })
    current = null
  }

  const ensureCurrent = (hintTitle = '') => {
    if (!current) {
      current = {
        title: hintTitle || '',
        type: '',
        summary: [],
        data: [],
        removalLines: [],
        unitType: '',
        xAxis: '',
        yAxis: '',
        seriesDecl: null,
      }
    }
    if (hintTitle && !current.title) current.title = hintTitle
    return current
  }

  for (const rawLine of lines) {
    const line = rawLine.trim()
    if (!line) continue
    if (/^\[table data\]/i.test(line)) { flush(); continue }
    if (/^\[visual data summary\]/i.test(line)) { continue }
    if (/^chartTitle\s*[:：]/i.test(line)) {
      flush()
      current = ensureCurrent(line.replace(/^chartTitle\s*[:：]\s*/i, '').trim())
      current.removalLines.push(line)
      continue
    }
    if (!current) continue
    if (/^chartType\s*[:：]/i.test(line)) {
      current.type = line.replace(/^chartType\s*[:：]\s*/i, '').trim()
      current.removalLines.push(line)
      continue
    }
    if (/^xAxis\s*[:：]/i.test(line)) {
      current.xAxis = line.replace(/^xAxis\s*[:：]\s*/i, '').trim().toLowerCase()
      current.removalLines.push(line)
      continue
    }
    if (/^yAxis\s*[:：]/i.test(line)) {
      current.yAxis = line.replace(/^yAxis\s*[:：]\s*/i, '').trim().toLowerCase()
      current.removalLines.push(line)
      continue
    }
    if (/^\|?\s*series\s*[:：]/i.test(line)) {
      const raw = line.replace(/^\|?\s*series\s*[:：]\s*/i, '').replace(/\|/g, ' ').trim()
      const parts = raw.split(/[\s]*[|][\s]*|\s+/).filter(Boolean)
      const list = parts.length > 1 ? parts : raw.split(/\s*\|\s*/).filter(Boolean)
      current.seriesDecl = list.map(s => s.trim())
      current.removalLines.push(line)
      continue
    }
    if (/^\[.+\]/.test(line)) continue
    const parsed = parseVisualDataLine(line)
    if (parsed) {
      const unitType = classifyUnit(parsed.unit)
      if (current.unitType && unitType && current.unitType !== unitType && current.data.length >= 2) {
        flush()
        current = ensureCurrent(guessTitleFromLabel(parsed.label))
      }
      ensureCurrent(guessTitleFromLabel(parsed.label))
      if (!current.unitType && unitType) current.unitType = unitType
      current.data.push(parsed)
      current.removalLines.push(line)
    } else if (line) {
      current.summary.push(line.replace(/^[ -*]\s*/, ''))
    }
  }
  flush()
  return segments
}

function parseVisualDataLine(line) {
  if (/^\|?\s*series\s*[:：]/i.test(line)) return null
  const match = line.match(/^([^:]+):\s*(-?\d+(?:\.\d+)?)(.*)$/)
  if (!match) return null
  let label = match[1].trim()
  const value = parseFloat(match[2])
  if (!label || Number.isNaN(value)) return null
  const unit = match[3].trim()
  // Normalise label: "Name (1970)" -> "1970 Name"
  label = label.replace(/\((\d{4})\)/, '$1').replace(/^\s+|\s+$/g, '')
  label = label.replace(/^(.*)\s(\d{4})$/, '$2 $1')
  return { label, value, unit }
}

function classifyUnit(unit = '') {
  const lower = unit.toLowerCase()
  if (/%|percent|percentage/.test(lower)) return 'percent'
  return 'count'
}

function guessTitleFromLabel(label = '') {
  const lower = label.toLowerCase()
  if (/marriage|divorce/.test(lower)) return 'Marriages & Divorces'
  if (/never married|widowed|divorced/.test(lower)) return 'Marital Status Distribution'
  return ''
}

function buildGroupedFromChart(seg) {
  const items = (seg?.chartData || []).map(d => ({ label: String(d.label || ''), value: Number(d.value) }))
  // Try Year First pattern: "YYYY Name"
  const yearCat = items.map(it => {
    const m = it.label.match(/^(\d{4})\s+(.+)$/)
    return m ? { year: m[1], cat: m[2].trim(), value: it.value } : null
  }).filter(Boolean)
  if (yearCat.length >= 4) {
    const years = Array.from(new Set(yearCat.map(p => p.year))).sort()
    const cats = Array.from(new Set(yearCat.map(p => p.cat)))
    if (years.length >= 2 && cats.length >= 2) {
      const series = cats.map(cat => ({ name: cat, type: 'bar', data: years.map(y => {
        const rec = yearCat.find(p => p.year === y && p.cat === cat)
        return rec ? rec.value : 0
      }) }))
      return { x: years, legend: cats, series }
    }
  }
  // Try Category First pattern: "Category YYYY"
  const catYear = items.map(it => {
    const m = it.label.match(/^(.+?)\s(\d{4})$/)
    return m ? { cat: m[1].trim(), year: m[2], value: it.value } : null
  }).filter(Boolean)
  if (catYear.length >= 4) {
    const cats = Array.from(new Set(catYear.map(p => p.cat)))
    const years = Array.from(new Set(catYear.map(p => p.year))).sort()
    if (years.length >= 2 && cats.length >= 2) {
      const series = years.map(year => ({ name: year, type: 'bar', data: cats.map(c => {
        const rec = catYear.find(p => p.year === year && p.cat === c)
        return rec ? rec.value : 0
      }) }))
      return { x: cats, legend: years, series }
    }
  }
  return null
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
  const seg = activeVisualChart.value
  const data = seg?.chartData || []
  const chartType = (activeVisualChart.value?.chartType || '').toLowerCase()
  if (!data.length) { disposeWriteCharts(); return }
  const shouldRenderBar = chartType.includes('bar')
  const shouldRenderPie = chartType.includes('pie')
  if (!shouldRenderBar && !shouldRenderPie) return

  // 跟随主题的动态配色（CSS 变量）
  const textSecondary = cssVar('--text-secondary') || '#475569'
  const textMuted = cssVar('--text-muted') || '#64748B'
  const textPrimary = cssVar('--text-primary') || '#334155'
  const borderLight = cssVar('--border-light') || '#E2E8F0'
  const bgCard = cssVar('--bg-card') || '#fff'
  const colorAccent = cssVar('--color-accent') || '#4AA36F'

  if (shouldRenderBar && writeBarChartRef.value) {
    if (!writeBarChart) writeBarChart = echarts.init(writeBarChartRef.value)
    const grouped = buildGroupedFromChart(seg)
    writeBarChart.setOption({
      animationDuration: 450, color: [colorAccent],
      grid: { left: 12, right: 12, top: 28, bottom: 36, containLabel: true },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: grouped ? { top: 0, textStyle: { color: textSecondary, fontSize: 11 } } : undefined,
      xAxis: grouped
        ? { type: 'category', data: grouped.x, axisLabel: { color: textSecondary, fontSize: 11, interval: 0, rotate: grouped.x.length > 4 ? 25 : 0 }, axisTick: { alignWithLabel: true } }
        : { type: 'category', data: data.map(d => d.label), axisLabel: { color: textSecondary, fontSize: 11, interval: 0, rotate: data.length > 4 ? 25 : 0 }, axisTick: { alignWithLabel: true } },
      yAxis: { type: 'value', name: (seg?.meta?.yAxis || '').toUpperCase(), nameTextStyle: { color: textMuted }, axisLabel: { color: textMuted, fontSize: 11 }, splitLine: { lineStyle: { color: borderLight } } },
      series: grouped
        ? grouped.series.map(s => ({ ...s, barMaxWidth: 28, itemStyle: { borderRadius: [6, 6, 0, 0] } }))
        : [{ type: 'bar', data: data.map(d => Number(d.value) || 0), barMaxWidth: 28, itemStyle: { borderRadius: [6, 6, 0, 0] } }],
    }, true)
  }

  if (shouldRenderPie && writePieChartRef.value) {
    if (!writePieChart) writePieChart = echarts.init(writePieChartRef.value)
    writePieChart.setOption({
      animationDuration: 450,
      color: ['#2E8B57', '#3CAEA3', '#F6C85F', '#F08A5D', '#6A89CC', '#B8DE6F', '#7DCEA0', '#5DADE2'],
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, left: 'center', textStyle: { color: textSecondary, fontSize: 11 } },
      series: [{
        type: 'pie', radius: ['45%', '70%'], center: ['50%', '42%'],
        itemStyle: { borderRadius: 6, borderColor: bgCard, borderWidth: 1 },
        label: { formatter: ({ name, percent }) => `${name}\n${Math.round(percent)}%`, color: textPrimary, fontSize: 10 },
        data: data.map(d => ({ name: d.label, value: Math.max(Math.abs(Number(d.value) || 0), 0.0001) })),
      }],
    }, true)
  }
}

watch(
  () => [
    showPassage.value,
    activeVisualIdx.value,
    (activeVisualChart.value?.chartType || '').toLowerCase(),
    activeVisualChart.value?.chartData?.map(i => `${i.label}:${i.value}`).join('|'),
    themeStore.isDark,
  ],
  async () => {
    if (!showPassage.value) return
    await nextTick()
    requestAnimationFrame(() => renderWriteCharts())
  },
  { immediate: true }
)

watch(
  () => passageVisual.value?.segments?.length || 0,
  () => {
    activeVisualIdx.value = 0
    disposeWriteCharts()
  }
)

// ── Translate overlay ──────────────────────────────────────
const translateMode = ref(false)
const translateBubble = ref({
  show: false,
  host: 'passage',
  x: 0, y: 0,
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
  if (!passageRef.value?.contains(el) && !questionsColRef.value?.contains(el) && !compactQuestionsRef.value?.contains(el)) hideTranslateBubble()
}

function onTranslateMouseUp() {
  if (!translateMode.value) return
  const sel = window.getSelection()
  if (!sel || sel.isCollapsed || !sel.rangeCount) return
  const text = sel.toString().trim()
  if (!text) return

  let hostEl = null
  let hostName = 'passage'
  if (passageRef.value?.contains(sel.anchorNode)) {
    hostEl = passageRef.value
    hostName = 'passage'
  } else if (questionsColRef.value?.contains(sel.anchorNode)) {
    hostEl = questionsColRef.value
    hostName = 'questions'
  } else if (compactQuestionsRef.value?.contains(sel.anchorNode)) {
    hostEl = compactQuestionsRef.value
    hostName = 'compact'
  }
  if (!hostEl) return

  const range = sel.getRangeAt(0)
  const rect = range.getBoundingClientRect()
  const hostRect = hostEl.getBoundingClientRect()
  const scrollTop = hostEl.scrollTop || 0

  const x = Math.min(Math.max(rect.left - hostRect.left, 8), hostRect.width - 120)
  const y = Math.max(rect.bottom - hostRect.top + scrollTop + 6, 8)

  translateBubble.value = {
    ...translateBubble.value,
    show: true,
    host: hostName,
    x, y,
    selectedText: text,
    loading: false,
    translation: '',
    notes: '',
  }
}

async function doTranslate() {
  const selText = translateBubble.value.selectedText
  if (!selText) return
  const passageText = rawPassageText.value || ''
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
    passageRef.value?.addEventListener('mouseup', onTranslateMouseUp)
    questionsColRef.value?.addEventListener('mouseup', onTranslateMouseUp)
    compactQuestionsRef.value?.addEventListener('mouseup', onTranslateMouseUp)
    ElMessage.info({ message: '翻译模式已开启：选中文章或题目中的句子/短语后点击翻译', duration: 2500 })
  } else {
    passageRef.value?.removeEventListener('mouseup', onTranslateMouseUp)
    questionsColRef.value?.removeEventListener('mouseup', onTranslateMouseUp)
    compactQuestionsRef.value?.removeEventListener('mouseup', onTranslateMouseUp)
    hideTranslateBubble()
  }
}

// ── AI Assistant ──────────────────────────────────────
const aiChatOpen = ref(false)
const aiMessages = ref([])
const aiQuestion = ref('')
const aiLoading = ref(false)
const aiChatMessagesRef = ref()
const aiPanelRef = ref(null)
const aiMaximized = ref(false)
const aiContextExamId = ref('all')
const aiPos = reactive({ x: Math.max(window.innerWidth - 420, 20), y: 80 })
const aiSize = reactive({ w: 400, h: 520 })

const aiContextOptions = computed(() => {
  if (!result.value?.isCollection || !result.value?.passages?.length) return []
  return result.value.passages.map((p, idx) => ({
    value: String(p.examId || idx),
    label: `试卷 ${idx + 1}${p.title ? `：${p.title}` : ''}`,
    passage: p,
  }))
})

watch(aiContextOptions, (opts) => {
  if (opts.length && (aiContextExamId.value === 'all' || !opts.some(o => o.value === aiContextExamId.value))) {
    aiContextExamId.value = opts[0].value
  }
}, { immediate: true })

function startDrag(e) {
  if (aiMaximized.value) return
  const startX = e.clientX - aiPos.x
  const startY = e.clientY - aiPos.y
  function onMove(ev) {
    aiPos.x = Math.max(0, ev.clientX - startX)
    aiPos.y = Math.max(0, ev.clientY - startY)
  }
  function onUp() {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

function startResize(e) {
  const startX = e.clientX
  const startY = e.clientY
  const startW = aiSize.w
  const startH = aiSize.h
  function onMove(ev) {
    aiSize.w = Math.max(300, startW + ev.clientX - startX)
    aiSize.h = Math.max(300, startH + ev.clientY - startY)
  }
  function onUp() {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

function buildExamContext() {
  if (!result.value) return ''
  const formatQuestion = q =>
    `Q${q.questionNumber} [${q.type || ''}]: ${q.text}${q.answer ? ' (正确答案: ' + q.answer + ')' : ''}${q.userAnswer ? ' (用户答案: ' + q.userAnswer + ')' : ''}`

  if (result.value.isCollection && result.value.passages?.length) {
    const questions = result.value.questions || []
    const selectedPassages = aiContextExamId.value === 'all'
      ? result.value.passages
      : result.value.passages.filter((p, idx) => String(p.examId || idx) === String(aiContextExamId.value))
    const sections = selectedPassages.map((p, idx) => {
      const related = questions.filter(q => String(q.examId || '') === String(p.examId || ''))
      return `【试卷 ${idx + 1}：${p.title || ''}】\n【文章】\n${p.passage || ''}\n\n【题目与答案】\n${related.map(formatQuestion).join('\n') || '（该试卷题目缺少 examId 关联，见下方汇总题目）'}`
    }).join('\n\n')
    const ungrouped = aiContextExamId.value === 'all' ? questions.filter(q => !q.examId) : []
    const ungroupedText = ungrouped.length
      ? `\n\n【未分组题目汇总】\n${ungrouped.map(formatQuestion).join('\n')}`
      : ''
    const scope = aiContextExamId.value === 'all' ? '全部试卷' : '当前选择试卷'
    return `【试卷集：${result.value.examTitle || ''}｜上下文范围：${scope}】\n${sections}${ungroupedText}`
  }

  const passageText = rawPassageText.value || ''
  const questions = (result.value.questions || []).map(formatQuestion).join('\n')
  return `【文章】\n${passageText}\n\n【题目与答案】\n${questions}`
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
  const parts = []
  let inList = false
  for (const line of lines) {
    const trimmed = line.trim()
    if (/^#{1,3}\s/.test(trimmed)) {
      if (inList) { parts.push('</ul>'); inList = false }
      const level = trimmed.match(/^(#+)/)[1].length
      parts.push(`<h${level + 2} class="md-h">${trimmed.replace(/^#+\s*/, '')}</h${level + 2}>`)
    } else if (/^&gt;\s/.test(trimmed)) {
      if (inList) { parts.push('</ul>'); inList = false }
      parts.push(`<blockquote class="md-quote">${trimmed.replace(/^&gt;\s*/, '')}</blockquote>`)
    } else if (/^[-*]\s/.test(trimmed)) {
      if (!inList) { parts.push('<ul class="md-list">'); inList = true }
      parts.push(`<li>${trimmed.replace(/^[-*]\s*/, '')}</li>`)
    } else if (/^\d+\.\s/.test(trimmed)) {
      if (!inList) { parts.push('<ol class="md-list">'); inList = true }
      parts.push(`<li>${trimmed.replace(/^\d+\.\s*/, '')}</li>`)
    } else {
      if (inList) { parts.push('</ul>'); inList = false }
      if (trimmed) parts.push(`<p class="md-p">${trimmed}</p>`)
      else parts.push('<br/>')
    }
  }
  if (inList) parts.push('</ul>')
  return parts.join('')
}

onBeforeUnmount(() => {
  disposeWriteCharts()
  passageRef.value?.removeEventListener('mouseup', onTranslateMouseUp)
  questionsColRef.value?.removeEventListener('mouseup', onTranslateMouseUp)
  compactQuestionsRef.value?.removeEventListener('mouseup', onTranslateMouseUp)
  document.removeEventListener('mousedown', onDocMouseDownForTranslate)
})

onMounted(() => {
  document.addEventListener('mousedown', onDocMouseDownForTranslate)
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
  position: relative;
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

.result-para.labeled-para {
  display: grid;
  grid-template-columns: 34px 1fr;
  column-gap: 10px;
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
.rvp-header-meta {
  margin-left: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: flex-end;
}
.rvp-title {
  font-weight: 700;
  font-size: 14px;
  color: #1B4332;
}
.rvp-chart-title {
  font-size: 13px;
  font-weight: 600;
  color: #14532D;
}
.rvp-sub {
  font-size: 12px;
  color: #2D6A4F;
  background: #D4F5E9;
  border-radius: 12px;
  padding: 2px 10px;
}
.rvp-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}
.rvp-tab {
  border: 1px solid #CFE9DA;
  background: #fff;
  color: #2D6A4F;
  font-size: 12px;
  padding: 4px 12px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.rvp-tab.active {
  background: #2D6A4F;
  color: #fff;
  border-color: #1B4332;
}
.rvp-summary {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}
.rvp-summary-item {
  font-size: 13px;
  color: var(--text-primary);
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
  border-bottom: 1px solid var(--border-light);
  color: var(--text-primary);
}
.rvp-table tr:nth-child(even) td {
  background: var(--bg-primary);
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
  position: relative;
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
  position: relative;
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

/* ── Translate Bubble ──────────────────────────────────── */
.translate-bubble {
  position: absolute;
  z-index: 20;
  min-width: 220px;
  max-width: 360px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  box-shadow: var(--shadow-lg);
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
.tb-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.tb-close {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  border: 1px solid var(--border-color);
  background: var(--bg-white);
  cursor: pointer;
  color: var(--text-secondary);
  font-size: 16px;
  line-height: 1;
}
.tb-result { margin-top: 10px; }
.tb-translation { font-size: 13px; color: var(--text-primary); line-height: 1.55; font-weight: 600; }
.tb-notes { margin-top: 6px; font-size: 12px; color: var(--text-secondary); line-height: 1.5; }

/* ── FAB Group ──────────────────────────────────────────── */
.result-fabs {
  position: fixed;
  bottom: 28px;
  right: 28px;
  z-index: 200;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}
.fab-minimize-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid var(--border-color);
  background: var(--bg-white);
  color: var(--text-muted);
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-sm);
  transition: all 0.2s;
  align-self: flex-end;
}
.fab-minimize-btn:hover { background: var(--bg-primary); border-color: var(--text-muted); }
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
.fab-icon { font-size: 15px; }

/* Translate FAB */
.translate-fab {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}
.tl-toggle { background: #1D4ED8 !important; }
.tl-toggle:hover { background: #1E40AF !important; }
.translate-fab.active .tl-toggle { background: #374151 !important; }

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
.ai-context-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-primary);
  flex-shrink: 0;
}
.ai-context-label {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 700;
}
.ai-context-selector select {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 6px 8px;
  font-size: 12px;
  color: var(--text-primary);
  background: var(--bg-white);
  outline: none;
}
.ai-context-selector select:focus { border-color: var(--color-primary); }
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
  text-align: center; color: var(--text-muted); font-size: 13px; padding: 30px 10px;
}
.ai-chat-empty p { margin: 4px 0; }
.ai-chat-hint { font-size: 12px; color: var(--text-muted); }
.ai-msg { display: flex; }
.ai-msg.user { justify-content: flex-end; }
.ai-msg.assistant { justify-content: flex-start; }
.ai-msg-bubble {
  max-width: 85%; padding: 8px 12px; border-radius: 10px; font-size: 13px; line-height: 1.5;
}
.ai-msg-bubble p { margin: 0; }
.ai-msg.user .ai-msg-bubble {
  background: #2D6A4F; color: #fff; border-bottom-right-radius: 3px;
}
.ai-msg.assistant .ai-msg-bubble {
  background: var(--bg-primary); color: var(--text-primary); border-bottom-left-radius: 3px;
}
.ai-typing {
  display: flex; align-items: center; gap: 4px; padding: 10px 16px;
}
.ai-typing .dot {
  width: 7px; height: 7px; border-radius: 50%; background: #9CA3AF;
  animation: ai-dot-bounce 1.2s infinite ease-in-out;
}
.ai-typing .dot:nth-child(2) { animation-delay: 0.2s; }
.ai-typing .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes ai-dot-bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}
.ai-chat-input {
  display: flex; gap: 8px; padding: 10px 12px; border-top: 1px solid var(--border-light); background: var(--bg-primary);
}
.ai-chat-input input {
  flex: 1; border: 1px solid var(--border-color); border-radius: 8px; padding: 8px 12px;
  font-size: 13px; color: var(--text-primary); background: var(--bg-white); outline: none; transition: border-color 0.2s;
}
.ai-chat-input input::placeholder { color: var(--text-muted); }
.ai-chat-input input:focus { border-color: var(--color-primary); }
.ai-chat-input button {
  padding: 8px 16px; background: #2D6A4F; color: #fff; border: none; border-radius: 8px;
  font-size: 13px; cursor: pointer; transition: background 0.2s; white-space: nowrap;
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

.collector-expand-enter-active,
.collector-expand-leave-active { transition: opacity 0.2s, transform 0.2s; }
.collector-expand-enter-from,
.collector-expand-leave-to { opacity: 0; transform: translateY(8px) scale(0.97); }

@media (max-width: 1024px) {
  .result-body { grid-template-columns: 1fr; }
  .result-passage { position: static; max-height: 50vh; }
  .ai-chat-panel:not(.ai-maximized) { width: 300px !important; height: 400px !important; }
}
</style>
