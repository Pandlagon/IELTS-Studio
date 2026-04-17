<template>
  <div class="page-wrapper">
    <NavBar />
    <div class="words-page">
      <div class="container">

        <!-- Header -->
        <div class="words-header">
          <div>
            <h1 class="page-title">背单词</h1>
            <p class="page-sub">{{ wordStore.currentBook?.name || '雅思核心词汇' }}</p>
          </div>
          <div class="header-right">
            <button v-if="!wordStore.currentBook?.isBuiltin" class="btn-secondary btn-sm" @click="showUpload = true">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
              添加单词
            </button>
            <div class="header-controls">
              <button v-for="mode in modes" :key="mode.value" class="mode-btn"
                :class="{ active: wordStore.studyMode === mode.value }"
                @click="wordStore.studyMode = mode.value">
                <span class="mode-btn-icon" v-html="mode.icon"></span>
                {{ mode.label }}
              </button>
            </div>
          </div>
        </div>

        <!-- Book Selector -->
        <div class="book-selector">
          <div class="book-tabs">
            <div
              v-for="book in wordStore.books"
              :key="book.id"
              class="book-tab"
              :class="{ active: wordStore.currentBookId === book.id }"
              @click="wordStore.switchBook(book.id)"
            >
              <span class="book-tab-name">{{ book.name }}</span>
              <span v-if="book.status === 'processing'" class="book-tab-spin" title="AI 处理中"></span>
              <span v-else-if="book.status === 'failed'" class="book-tab-fail" title="处理失败">!</span>
              <span v-else class="book-tab-count">{{ book.wordCount || 0 }}</span>
              <button
                v-if="!book.isBuiltin && !book.isDefault"
                class="book-tab-del"
                title="删除词书"
                @click.stop="confirmDeleteBook(book)"
              >×</button>
            </div>
          </div>
          <button class="btn-ghost btn-sm" @click="showCreateBook = true">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            新建词书
          </button>
        </div>

        <!-- Settings + Progress (unified card) -->
        <div class="study-settings card-sm card">
          <!-- Row 1: sort / batch / batch-nav -->
          <div class="settings-row">
            <div class="setting-group">
              <span class="setting-label">排序</span>
              <div class="setting-opts">
                <button :class="['sopt', { active: wordStore.sortMode === 'order' }]" @click="wordStore.setSortMode('order')">添加顺序</button>
                <button :class="['sopt', { active: wordStore.sortMode === 'alpha' }]" @click="wordStore.setSortMode('alpha')">字母顺序</button>
                <button :class="['sopt', { active: wordStore.sortMode === 'errors' }]" @click="wordStore.setSortMode('errors')">出错次数</button>
              </div>
            </div>
            <div class="setting-divider"></div>
            <div class="setting-group">
              <span class="setting-label">每批</span>
              <div class="setting-opts">
                <button :class="['sopt', { active: wordStore.batchSize === 0 }]" @click="wordStore.setBatchSize(0)">全部</button>
                <button :class="['sopt', { active: wordStore.batchSize === 20 }]" @click="wordStore.setBatchSize(20)">20个</button>
                <button :class="['sopt', { active: wordStore.batchSize === 50 }]" @click="wordStore.setBatchSize(50)">50个</button>
                <button :class="['sopt', { active: wordStore.batchSize === 100 }]" @click="wordStore.setBatchSize(100)">100个</button>
              </div>
            </div>
            <div class="setting-spacer"></div>
            <!-- Batch nav inline at far right -->
            <div v-if="wordStore.batchSize > 0" class="batch-nav-inline">
              <button class="batch-btn" :disabled="wordStore.batchIndex === 0" @click="wordStore.prevBatch()">
                <el-icon><ArrowLeft /></el-icon>
              </button>
              <span class="batch-info">第 <strong>{{ wordStore.batchIndex + 1 }}</strong> 批 / 共 {{ wordStore.totalBatches }} 批
                <span class="batch-range">({{ wordStore.batchIndex * wordStore.batchSize + 1 }}–{{ Math.min((wordStore.batchIndex + 1) * wordStore.batchSize, wordStore.words.length) }} / {{ wordStore.words.length }})</span>
              </span>
              <button class="batch-btn" :disabled="wordStore.batchIndex === wordStore.totalBatches - 1 || !wordStore.canGoNextBatch" @click="wordStore.nextBatch()">
                <el-icon><ArrowRight /></el-icon>
              </button>
            </div>
          </div>
          <!-- Row 2: progress bar -->
          <div class="progress-inline">
            <div class="progress-row">
              <span class="progress-label">{{ wordStore.batchSize > 0 ? '当前批学习进度' : '整体学习进度' }}</span>
              <span class="progress-val">{{ batchKnownCount }} / {{ batchTotalWords }}</span>
            </div>
            <div class="progress-track">
              <div class="progress-known" :style="{ width: knownPct + '%' }"></div>
              <div class="progress-unknown" :style="{ width: unknownPct + '%', left: knownPct + '%' }"></div>
            </div>
            <div class="progress-legend">
              <span class="legend-item green"><span class="dot"></span>认识 {{ batchKnownCount }}</span>
              <span class="legend-item red"><span class="dot"></span>不认识 {{ unknownCount }}</span>
              <span class="legend-item gray"><span class="dot"></span>未学 {{ remaining }}</span>
            </div>
            <div class="review-metrics">
              <span class="metric-pill due">待复习 {{ wordStore.dueWordsCount }}</span>
              <span class="metric-pill overdue">已到期 {{ wordStore.overdueWordsCount }}</span>
            </div>
          </div>
        </div>

        <!-- Card Mode -->
        <div v-if="wordStore.studyMode === 'card' && !wordStore.loadingEntries && wordStore.totalWords > 0" class="card-mode">
          <div class="card-area">
            <button class="nav-arrow left" @click="wordStore.prevWord()">
              <el-icon><ArrowLeft /></el-icon>
            </button>

            <div class="main-word-card">
              <div class="card-num-indicator">
                {{ wordStore.currentIndex + 1 }} / {{ wordStore.totalWords }}
              </div>
              <WordCard
                :word="wordStore.currentWord"
                @know="handleKnow"
                @unknown="handleUnknown"
              />
              <div v-if="wordStore.currentWord?.reviewState" class="review-hint-bar">
                <span class="review-hint-item" :class="{ urgent: wordStore.currentWord?.due }">
                  {{ wordStore.currentWord?.due ? '应优先复习' : '记忆稳定中' }}
                </span>
                <span class="review-hint-item">连对 {{ wordStore.currentWord?.reviewState?.streak || 0 }} 次</span>
              </div>
              <!-- Known indicator -->
              <div v-if="wordStore.isKnown(wordStore.currentWord.id)" class="word-status known">
                <el-icon><CircleCheck /></el-icon> 已认识
              </div>
              <div v-else-if="wordStore.isUnknown(wordStore.currentWord.id)" class="word-status unknown">
                <el-icon><CircleClose /></el-icon> 待复习
              </div>
              <!-- Card action buttons -->
              <div class="card-actions">
                <button v-if="!wordStore.currentBook?.isBuiltin" class="card-action-btn edit-btn"
                  @click="openEditEntry(wordStore.currentWord)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                  编辑
                </button>
                <button v-if="!wordStore.currentBook?.isDefault" class="card-action-btn add-btn"
                  @click="doAddToDefault(wordStore.currentWord)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                  加入生词本
                </button>
              </div>
            </div>

            <button class="nav-arrow right" @click="wordStore.nextWord()">
              <el-icon><ArrowRight /></el-icon>
            </button>
          </div>

          <div class="keyboard-hint">
            <span>← →</span> 切换卡片 &nbsp;·&nbsp; <span>↑</span> 认识 &nbsp;·&nbsp; <span>↓</span> 不认识 &nbsp;·&nbsp; <span>Enter/Space</span> 下一个
          </div>
        </div>

        <!-- List Mode -->
        <div v-else-if="wordStore.studyMode !== 'card' && !wordStore.loadingEntries && wordStore.totalWords > 0" class="list-mode">
          <div class="list-controls">
            <input v-model="searchWord" class="form-input" placeholder="搜索单词..." style="max-width:240px" />
            <select v-model="filterStatus" class="form-input" style="max-width:140px">
              <option value="all">全部</option>
              <option value="known">已认识</option>
              <option value="unknown">不认识</option>
              <option value="new">未学习</option>
            </select>
          </div>
          <div class="word-list">
            <div
              v-for="word in filteredWordList"
              :key="word.id"
              class="word-row card-sm card"
            >
              <div class="word-row-left">
                <div class="word-row-word">{{ word.word }}</div>
                <div class="word-row-phonetic">{{ word.phonetic }}</div>
              </div>
              <div class="word-row-center">
                <span v-if="!parseMeaning(word.meaning).some(g => g.pos)" class="pos-badge" :class="`pos-${word.posType}`">{{ word.pos }}</span>
                <span class="word-row-meaning">
                  <template v-for="(grp, gi) in parseMeaning(word.meaning)" :key="gi">
                    <span v-if="grp.pos" class="row-pos-label" :class="`rpl-${grp.posType}`">{{ grp.pos }}</span>
                    <span>{{ grp.senses.join('；') }}</span>
                    <span v-if="gi < parseMeaning(word.meaning).length - 1" class="row-sep"> · </span>
                  </template>
                </span>
              </div>
              <div class="word-row-status">
                <span v-if="wordStore.isKnown(word.id)" class="status-chip known">认识</span>
                <span v-else-if="wordStore.isUnknown(word.id)" class="status-chip unknown">不认识</span>
                <span v-else class="status-chip new">未学</span>
                <span v-if="word.due" class="status-chip due">待复习</span>
                <span v-else-if="word.reviewState?.nextReviewAt" class="status-chip scheduled">{{ formatNextReview(word.reviewState.nextReviewAt) }}</span>
                <button v-if="!wordStore.currentBook?.isBuiltin" class="entry-icon-btn edit" title="编辑" @click="openEditEntry(word)">
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                </button>
                <button v-if="!wordStore.currentBook?.isDefault" class="entry-icon-btn add" title="加入生词本" @click="doAddToDefault(word)">
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                </button>
                <button v-if="!wordStore.currentBook?.isBuiltin" class="entry-icon-btn del" title="删除" @click="confirmDeleteEntry(word)">
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4h6v2"/></svg>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Loading entries -->
        <div v-if="wordStore.loadingEntries" class="loading-state">
          <div class="loading-spinner"></div>
          <p>加载中...</p>
        </div>

        <!-- Empty book state -->
        <div v-else-if="!wordStore.loadingEntries && wordStore.totalWords === 0" class="empty-book">
          <div class="empty-book-icon">📖</div>
          <p class="empty-book-title">词书暂无单词</p>
          <p class="empty-book-desc">上传 PDF 或 Word 文件，AI 将自动提取词汇并生成词条</p>
          <button class="btn-primary" @click="showUpload = true">上传单词文件</button>
        </div>

        <!-- Reset Button -->
        <div v-else class="reset-row">
          <button class="btn-ghost" @click="confirmReset">
            <el-icon><Refresh /></el-icon>
            重置学习进度
          </button>
        </div>

      </div>
    </div>
  </div>

  <!-- Create Book Dialog -->
  <el-dialog v-model="showCreateBook" width="420px" :close-on-click-modal="false">
    <template #header>
      <div class="dialog-header">
        <span class="dialog-header-icon">📚</span>
        <span>新建词书</span>
      </div>
    </template>
    <el-form :model="createBookForm" label-position="top">
      <el-form-item label="词书名称">
        <el-input v-model="createBookForm.name" placeholder="例如：托福高频词、写作必备词" maxlength="50" show-word-limit />
      </el-form-item>
      <el-form-item label="简介（可选）">
        <el-input v-model="createBookForm.desc" type="textarea" :rows="2" placeholder="简短描述这本词书的内容或用途" maxlength="100" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showCreateBook = false">取消</el-button>
      <el-button type="primary" :loading="creatingBook" @click="doCreateBook">创建词书</el-button>
    </template>
  </el-dialog>

  <!-- 编辑词条弹窗 -->
  <el-dialog v-model="showEditEntry" width="480px" :close-on-click-modal="false">
    <template #header>
      <div class="dialog-header">
        <span class="dialog-header-icon">✏️</span>
        <span>编辑「{{ editForm.word }}」</span>
      </div>
    </template>
    <el-form :model="editForm" label-position="top">
      <el-form-item label="中文释义">
        <el-input
          v-model="editForm.meaning"
          placeholder="多个释义用 ；分隔，如：能力；才能；本领"
          clearable
        />
        <div class="edit-hint">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
          多个释义请用中文分号「；」分隔
        </div>
      </el-form-item>
      <el-form-item label="英文例句（可选）">
        <el-input
          v-model="editForm.example"
          type="textarea"
          :rows="3"
          placeholder="输入一个帮助记忆的例句..."
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showEditEntry = false">取消</el-button>
      <el-button type="primary" :loading="savingEntry" @click="doSaveEntry">保存修改</el-button>
    </template>
  </el-dialog>

  <!-- 本组完成弹窗 -->
  <el-dialog
    v-model="showBatchComplete"
    width="360px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
  >
    <div class="batch-complete-content">
      <div class="batch-complete-ring" :class="{ gold: isAllKnownComplete }">
        <span class="batch-complete-emoji">{{ isAllKnownComplete ? '🏆' : '🎉' }}</span>
      </div>
      <h3 class="batch-complete-title">{{ isAllKnownComplete ? '全部完成！' : '本组完成！' }}</h3>
      <p class="batch-complete-text">{{ batchEncouragement }}</p>
      <div class="batch-complete-stats" v-if="!isAllKnownComplete">
        <span class="bcs-item">
          <span class="bcs-num">{{ wordStore.batchIndex + 1 }}</span>
          <span class="bcs-label">已完成</span>
        </span>
        <span class="bcs-divider">/</span>
        <span class="bcs-item">
          <span class="bcs-num">{{ wordStore.totalBatches }}</span>
          <span class="bcs-label">共几组</span>
        </span>
      </div>
      <div class="batch-complete-stats" v-else>
        <span class="bcs-item">
          <span class="bcs-num">{{ wordStore.words.length }}</span>
          <span class="bcs-label">单词</span>
        </span>
        <span class="bcs-divider">·</span>
        <span class="bcs-item">
          <span class="bcs-num">{{ wordStore.totalBatches }}</span>
          <span class="bcs-label">组</span>
        </span>
      </div>
    </div>
    <template #footer>
      <div class="batch-complete-footer">
        <el-button v-if="!isLastBatch" type="primary" @click="goToNextBatch" style="width:100%">
          进入第 {{ wordStore.batchIndex + 2 }} 组 →
        </el-button>
        <el-button v-else type="primary" @click="showBatchComplete = false" style="width:100%">
          太棒了，关闭 ✓
        </el-button>
      </div>
    </template>
  </el-dialog>

  <!-- 上传单词弹窗 -->
  <el-dialog v-model="showUpload" width="480px" :close-on-click-modal="false">
    <template #header>
      <div class="dialog-header">
        <span class="dialog-header-icon">✨</span>
        <span>AI 添加单词</span>
      </div>
    </template>
    <div class="upload-tip">
      <el-alert type="info" :closable="false" show-icon>
        <template #title>
          每次上传最多 <strong>30 个词汇</strong>，AI 将自动提取并生成词条（音标、词性、释义、例句）。支持 PDF 和 Word 文件。
        </template>
      </el-alert>
    </div>
    <div class="upload-drop-area" @dragover.prevent @drop.prevent="onFileDrop">
      <input ref="fileInputRef" type="file" accept=".pdf,.doc,.docx" style="display:none" @change="onFileSelect" />
      <div v-if="!uploadFile" class="upload-placeholder" @click="fileInputRef?.click()">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#1B4332" stroke-width="1.5"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
        <p>点击选择或拖拽文件</p>
        <p class="upload-hint">支持 .pdf / .doc / .docx</p>
      </div>
      <div v-else class="upload-file-info">
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#1B4332" stroke-width="1.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
        <div>
          <p class="upload-filename">{{ uploadFile.name }}</p>
          <p class="upload-filesize">{{ (uploadFile.size / 1024).toFixed(1) }} KB</p>
        </div>
        <button class="btn-ghost btn-sm" @click="uploadFile = null">更换</button>
      </div>
    </div>
    <template #footer>
      <el-button @click="showUpload = false; uploadFile = null">取消</el-button>
      <el-button type="primary" :loading="wordStore.uploadingWords" :disabled="!uploadFile" @click="doUpload">
        开始处理
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import NavBar from '@/components/NavBar.vue'
import WordCard from '@/components/WordCard.vue'
import { useWordStore } from '@/stores/word'

const wordStore = useWordStore()
const searchWord = ref('')
const filterStatus = ref('all')
const showBatchComplete = ref(false)
const batchEncouragement = ref('')

// dialogs
const showCreateBook = ref(false)
const showUpload = ref(false)
const creatingBook = ref(false)
const createBookForm = ref({ name: '', desc: '' })
const uploadFile = ref(null)
const fileInputRef = ref()

const modes = [
  { value: 'card', label: '卡片', icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="5" width="20" height="14" rx="2"/></svg>' },
  { value: 'list', label: '列表', icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg>' },
]

// Use baseDisplayWords for progress calculation to show entire batch progress
const batchWords = computed(() => wordStore.batchSize > 0 ? wordStore.baseDisplayWords || wordStore.displayWords : wordStore.displayWords)
const unknownCount = computed(() => batchWords.value.filter(w => wordStore.isUnknown(w.id)).length)
const remaining = computed(() => batchWords.value.filter(w => !wordStore.isKnown(w.id) && !wordStore.isUnknown(w.id)).length)
const batchKnownCount = computed(() => batchWords.value.filter(w => wordStore.isKnown(w.id)).length)
const batchTotalWords = computed(() => batchWords.value.length)
// 是否正处于最后一批
const isLastBatch = computed(() => wordStore.batchIndex >= wordStore.totalBatches - 1)
const isAllKnownComplete = computed(() => {
  if (!wordStore.words.length) return false
  return wordStore.words.every(w => wordStore.isKnown(w.id))
})
const knownPct = computed(() => batchTotalWords.value ? Math.round((batchKnownCount.value / batchTotalWords.value) * 100) : 0)
const unknownPct = computed(() => {
  if (!batchTotalWords.value) return 0
  return Math.round((unknownCount.value / batchTotalWords.value) * 100)
})

const filteredWordList = computed(() => {
  let list = wordStore.displayWords
  if (searchWord.value.trim()) {
    const q = searchWord.value.toLowerCase()
    list = list.filter(w => w.word.toLowerCase().includes(q) || w.meaning.includes(q))
  }
  if (filterStatus.value === 'known') list = list.filter(w => wordStore.isKnown(w.id))
  else if (filterStatus.value === 'unknown') list = list.filter(w => wordStore.isUnknown(w.id))
  else if (filterStatus.value === 'new') list = list.filter(w => !wordStore.isKnown(w.id) && !wordStore.isUnknown(w.id))
  return list
})

function formatReaction(ms) {
  if (!ms) return '--'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function formatNextReview(nextReviewAt) {
  if (!nextReviewAt) return '随时可学'
  const diff = new Date(nextReviewAt).getTime() - Date.now()
  if (!Number.isFinite(diff) || diff <= 0) return '现在复习'
  const minutes = Math.round(diff / 60000)
  if (minutes < 60) return `${minutes} 分钟后`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} 小时后`
  const days = Math.round(hours / 24)
  return `${days} 天后`
}

function handleKnow(payload) {
  wordStore.markKnown(payload)
}

function handleUnknown(payload) {
  wordStore.markUnknown(payload)
}

const encouragements = [
  '太棒了！继续保持！',
  '进步很大！加油！',
  '你真厉害！',
  '做得好！再接再厉！',
  '完美！坚持就是胜利！',
]

const finalEncouragements = [
  '所有单词全部搞定，你真棒！',
  '全部完成！词汇量又提升啦！',
  '太厉害了，一次全部学完！',
  '完美收官！继续加油备考！',
]

function showEncouragement() {
  const isLast = wordStore.batchIndex >= wordStore.totalBatches - 1
  const pool = isLast && isAllKnownComplete.value
    ? finalEncouragements
    : encouragements
  batchEncouragement.value = pool[Math.floor(Math.random() * pool.length)]
  showBatchComplete.value = true
}

function goToNextBatch() {
  showBatchComplete.value = false
  wordStore.nextBatch()
}

wordStore.batchCompleteCallback = showEncouragement

// ── meaning parser (shared with list mode) ──────────────────
function parseMeaning(raw) {
  if (!raw) return []
  return raw.split(/\s*·\s*/).map(g => {
    g = g.trim()
    const m = g.match(/^(n\.|v\.|adj\.|adv\.|prep\.|conj\.|pron\.|int\.|phrase)\s+/i)
    if (m) {
      const posLabel = m[1]
      const posType = posLabel.replace('.','').toLowerCase()
      return { pos: posLabel, posType, senses: g.slice(m[0].length).split('；').map(s => s.trim()).filter(Boolean) }
    }
    return { pos: '', posType: '', senses: g.split('；').map(s => s.trim()).filter(Boolean) }
  }).filter(g => g.senses.length)
}

// ── edit entry ────────────────────────────────────────────
const showEditEntry = ref(false)
const savingEntry = ref(false)
const editForm = ref({ id: null, word: '', meaning: '', example: '' })

function openEditEntry(word) {
  editForm.value = { id: word.id, word: word.word, meaning: word.meaning || '', example: word.example || '' }
  showEditEntry.value = true
}

async function doSaveEntry() {
  if (!editForm.value.meaning.trim()) { ElMessage.warning('请输入释义'); return }
  savingEntry.value = true
  try {
    await wordStore.updateEntry(editForm.value.id, editForm.value.meaning, editForm.value.example)
    showEditEntry.value = false
    ElMessage.success('已保存')
  } catch (e) {
    ElMessage.error('保存失败')
  }
  savingEntry.value = false
}

// ── add to 生词本 ──────────────────────────────────────────
const addingToDefault = ref(false)

async function doAddToDefault(word) {
  if (addingToDefault.value) return
  addingToDefault.value = true
  try {
    await wordStore.addToDefaultBook(word)
    ElMessage.success(`「${word.word}」已加入生词本`)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '添加失败')
  }
  addingToDefault.value = false
}

// ── book management ───────────────────────────────────────
async function doCreateBook() {
  if (!createBookForm.value.name.trim()) { ElMessage.warning('请输入词书名称'); return }
  creatingBook.value = true
  try {
    const book = await wordStore.createBook(createBookForm.value.name.trim(), createBookForm.value.desc)
    showCreateBook.value = false
    createBookForm.value = { name: '', desc: '' }
    wordStore.switchBook(book.id)
    ElMessage.success(`词书「${book.name}」已创建`)
  } catch (e) {
    ElMessage.error(e?.message || '创建失败')
  }
  creatingBook.value = false
}

async function confirmDeleteBook(book) {
  try {
    await ElMessageBox.confirm(`确定删除词书「${book.name}」？该词书内所有词条将被删除。`, '删除词书', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
    })
    await wordStore.deleteBook(book.id)
    ElMessage.success('已删除')
  } catch {}
}

// ── upload ────────────────────────────────────────────────
function onFileSelect(e) { uploadFile.value = e.target.files[0] || null }
function onFileDrop(e) {
  const f = e.dataTransfer?.files[0]
  if (f && /\.(pdf|doc|docx)$/i.test(f.name)) uploadFile.value = f
  else ElMessage.warning('仅支持 PDF 或 Word 文件')
}

async function doUpload() {
  if (!uploadFile.value) return
  try {
    await wordStore.uploadWords(wordStore.currentBookId, uploadFile.value)
    // Close immediately — AI processes in background
    showUpload.value = false
    uploadFile.value = null
    ElMessage.info({ message: 'AI 处理中，完成后自动刷新词书', duration: 4000 })
  } catch (e) {
    ElMessage.error(e?.message || '上传失败，请重试')
  }
}

// ── study ─────────────────────────────────────────────────
function handleKeydown(e) {
  if (wordStore.studyMode !== 'card') return
  if (e.ctrlKey || e.metaKey || e.altKey) return
  const target = e.target
  const tag = target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target?.isContentEditable) return
  if (e.key === 'ArrowLeft') wordStore.prevWord()
  else if (e.key === 'ArrowRight') wordStore.nextWord()
  // Note: ArrowUp/ArrowDown for know/unknown are now handled by the WordCard component
  // to work with the new reveal mode interaction
}

async function confirmReset() {
  try {
    await ElMessageBox.confirm('确定要重置当前词书的学习进度吗？', '重置进度', {
      confirmButtonText: '重置', cancelButtonText: '取消', type: 'warning',
    })
    wordStore.resetProgress()
  } catch {}
}

async function confirmDeleteEntry(entry) {
  try {
    await ElMessageBox.confirm(`删除词条「${entry.word}」？`, '删除', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
    })
    await wordStore.deleteEntry(entry.id)
  } catch {}
}

function onBookUpdated(e) {
  const { status, wordCount } = e.detail
  if (status === 'ready') ElMessage.success(`AI 处理完成，新增 ${wordCount} 个词条`)
  else if (status === 'failed') ElMessage.error('AI 处理失败，请重新上传')
}

onMounted(async () => {
  await wordStore.loadBooks()
  window.addEventListener('keydown', handleKeydown)
  window.addEventListener('word-book-updated', onBookUpdated)
})
onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('word-book-updated', onBookUpdated)
})
</script>

<style scoped>
.words-page { padding: 32px 0 64px; }

.words-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 24px;
}

.page-title { font-size: 28px; font-weight: 800; color: var(--text-primary); margin-bottom: 4px; }
.page-sub { font-size: 14px; color: var(--text-muted); }

.header-controls {
  display: flex;
  gap: 4px;
  background: var(--bg-white);
  padding: 4px;
  border-radius: var(--radius-full);
  border: 1px solid var(--border-color);
}

.mode-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 16px;
  border-radius: var(--radius-full);
  font-size: 13px;
  font-weight: 500;
  color: var(--text-muted);
  border: none;
  background: transparent;
  cursor: pointer;
  transition: all 0.15s;
}
.mode-btn:hover { color: var(--color-primary); }
.mode-btn.active { background: var(--color-primary); color: white; }

.mode-btn-icon {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  line-height: 1;
}

/* Progress */
.progress-section {
  margin-bottom: 32px;
  padding: 16px 20px;
}

.progress-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.progress-label { font-size: 13px; font-weight: 600; color: var(--text-secondary); }
.progress-val { font-size: 13px; color: var(--text-muted); }

.progress-track {
  position: relative;
  height: 6px;
  background: var(--border-color);
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 10px;
}

.progress-known {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  background: #22C55E;
  border-radius: 3px;
  transition: width 0.5s ease;
}

.progress-unknown {
  position: absolute;
  top: 0;
  height: 100%;
  background: #EF4444;
  border-radius: 3px;
  transition: all 0.5s ease;
}

.progress-legend {
  display: flex;
  gap: 16px;
}

.review-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.metric-pill {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: #F3F4F6;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.metric-pill.due {
  background: #EEF6FF;
  color: #1D4ED8;
}

.metric-pill.overdue {
  background: #FEF2F2;
  color: #DC2626;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--text-muted);
}

.legend-item .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--border-color);
}

.legend-item.green .dot { background: #22C55E; }
.legend-item.red .dot { background: #EF4444; }

/* Card Mode */
.card-mode {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
}

.card-area {
  display: flex;
  align-items: center;
  gap: 20px;
  width: 100%;
  justify-content: center;
}

.nav-arrow {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 1.5px solid var(--border-color);
  background: var(--bg-white);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--text-secondary);
  font-size: 18px;
  transition: all 0.15s;
  flex-shrink: 0;
}
.nav-arrow:hover { border-color: var(--color-primary); color: var(--color-primary); }

.main-word-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.card-num-indicator {
  font-size: 13px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.word-status {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 500;
  padding: 5px 14px;
  border-radius: var(--radius-full);
}
.word-status.known { background: #E8F5E9; color: #2E7D32; }
.word-status.unknown { background: #FFEBEE; color: #C62828; }

.review-hint-bar {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.review-hint-item {
  font-size: 12px;
  color: var(--text-secondary);
  background: #F8F9FA;
  border-radius: 999px;
  padding: 4px 10px;
}

.review-hint-item.urgent {
  background: #FEF2F2;
  color: #DC2626;
  font-weight: 700;
}

.keyboard-hint {
  font-size: 12px;
  color: var(--text-muted);
  text-align: center;
}
.keyboard-hint span {
  display: inline-block;
  background: var(--border-color);
  padding: 1px 7px;
  border-radius: 4px;
  font-size: 11px;
  color: var(--text-secondary);
}

/* List Mode */
.list-mode { display: flex; flex-direction: column; gap: 16px; }

.list-controls { display: flex; gap: 12px; }

.word-list { display: flex; flex-direction: column; gap: 8px; }

.word-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 18px;
}

.word-row-left { width: 160px; flex-shrink: 0; }
.word-row-word { font-size: 16px; font-weight: 700; color: var(--text-primary); }
.word-row-phonetic { font-size: 11px; color: var(--text-muted); font-style: italic; }

.word-row-center { flex: 1; display: flex; align-items: center; gap: 10px; }
.word-row-meaning { font-size: 14px; color: var(--text-secondary); }

.word-row-status { flex-shrink: 0; }

.pos-badge {
  display: inline-flex;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}
.pos-adj { background: #FFF3CD; color: #8B6A00; }
.pos-n   { background: #DBEAFE; color: #1D4ED8; }
.pos-v   { background: #D1FAE5; color: #065F46; }
.pos-adv { background: #EDE9FE; color: #5B21B6; }

.status-chip {
  display: inline-block;
  padding: 3px 10px;
  border-radius: var(--radius-full);
  font-size: 11px;
  font-weight: 600;
}
.status-chip.known { background: #E8F5E9; color: #2E7D32; }
.status-chip.unknown { background: #FFEBEE; color: #C62828; }
.status-chip.new { background: var(--bg-primary); color: var(--text-muted); border: 1px solid var(--border-color); }
.status-chip.due { background: #EEF6FF; color: #1D4ED8; }
.status-chip.scheduled { background: #F3F4F6; color: var(--text-secondary); }

.reset-row {
  display: flex;
  justify-content: center;
  padding-top: 16px;
}

/* Header right */
.header-right { display: flex; align-items: center; gap: 10px; }
.btn-sm { padding: 6px 14px; font-size: 13px; display: flex; align-items: center; gap: 5px; }

/* Book Selector */
.book-selector {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.book-tabs {
  display: flex;
  gap: 6px;
  flex: 1;
  flex-wrap: wrap;
}

.book-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: var(--radius-full);
  border: 1.5px solid var(--border-color);
  background: var(--bg-white);
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
  position: relative;
}
.book-tab:hover { border-color: var(--color-primary); color: var(--color-primary); }
.book-tab.active { background: var(--color-primary); border-color: var(--color-primary); color: white; }

.book-tab-name { font-weight: 600; }

.book-tab-count {
  font-size: 11px;
  background: rgba(0,0,0,0.08);
  padding: 1px 6px;
  border-radius: 10px;
  font-weight: 500;
}
.book-tab.active .book-tab-count { background: rgba(255,255,255,0.25); }

.book-tab-del {
  margin-left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: none;
  background: rgba(0,0,0,0.12);
  color: inherit;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  opacity: 0.7;
  transition: opacity 0.15s, background 0.15s;
}
.book-tab-del:hover { opacity: 1; background: #EF4444; color: white; }
.book-tab.active .book-tab-del { background: rgba(255,255,255,0.25); color: white; }
.book-tab.active .book-tab-del:hover { background: rgba(255,255,255,0.5); }

.book-tab-spin {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(0,0,0,0.15);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  flex-shrink: 0;
}
.book-tab.active .book-tab-spin { border-color: rgba(255,255,255,0.3); border-top-color: white; }

.book-tab-fail {
  font-size: 11px;
  font-weight: 700;
  color: #EF4444;
  background: #FEE2E2;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

/* Unified study-settings card */
.study-settings {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 0;
}
.settings-row {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  padding: 10px 16px;
}
.setting-spacer { flex: 1; }
.setting-group {
  display: flex;
  align-items: center;
  gap: 8px;
}
.setting-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  white-space: nowrap;
}
.setting-opts {
  display: flex;
  gap: 4px;
}
.sopt {
  padding: 4px 12px;
  border-radius: var(--radius-full);
  border: 1.5px solid var(--border-color);
  background: var(--bg-white);
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.sopt:hover { border-color: var(--color-primary); color: var(--color-primary); }
.sopt.active { background: var(--color-primary); border-color: var(--color-primary); color: #fff; }
.setting-divider {
  width: 1px;
  height: 22px;
  background: var(--border-color);
  flex-shrink: 0;
}

/* Inline batch nav (inside settings row, far right) */
.batch-nav-inline {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.batch-btn {
  width: 26px;
  height: 26px;
  border-radius: 7px;
  border: 1.5px solid var(--border-color);
  background: var(--bg-white);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
  color: var(--text-secondary);
}
.batch-btn:hover:not(:disabled) { border-color: var(--color-primary); color: var(--color-primary); }
.batch-btn:disabled { opacity: 0.35; cursor: default; }
.batch-info { font-size: 12px; color: var(--text-secondary); white-space: nowrap; }
.batch-range { font-size: 11px; color: var(--text-muted); margin-left: 3px; }

/* Progress bar row inside the unified card */
.progress-inline {
  border-top: 1px solid var(--border-color);
  padding: 10px 16px 12px;
}

/* Card action buttons */
.card-actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}
.card-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border-radius: var(--radius-full);
  border: 1.5px solid var(--border-color);
  background: var(--bg-white);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  color: var(--text-secondary);
}
.card-action-btn.edit-btn:hover { border-color: #1B4332; color: #1B4332; background: #F0F7F4; }
.card-action-btn.add-btn:hover { border-color: #2563EB; color: #2563EB; background: #EFF6FF; }

/* Word row action buttons */
.word-row-status { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }

.entry-icon-btn {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  border: 1px solid var(--border-color);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
  padding: 0;
  flex-shrink: 0;
}
.entry-icon-btn.edit:hover { background: #F0F7F4; border-color: #1B4332; color: #1B4332; }
.entry-icon-btn.add:hover  { background: #EFF6FF; border-color: #2563EB; color: #2563EB; }
.entry-icon-btn.del:hover  { background: #FEF2F2; border-color: #EF4444; color: #EF4444; }

/* Edit hint */
.edit-hint { font-size: 11px; color: var(--text-muted); margin-top: 4px; }

/* List row inline POS labels */
.row-pos-label {
  display: inline-block;
  font-size: 10px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 3px;
  margin-right: 3px;
  vertical-align: middle;
}
.rpl-n    { background: #DBEAFE; color: #1D4ED8; }
.rpl-v    { background: #D1FAE5; color: #065F46; }
.rpl-adj  { background: #FFF3CD; color: #8B6A00; }
.rpl-adv  { background: #EDE9FE; color: #5B21B6; }
.rpl-prep { background: #FEE2E2; color: #991B1B; }
.rpl-phrase { background: #F3F4F6; color: #4B5563; }
.row-sep  { color: var(--text-muted); font-size: 12px; }

/* Empty book state */
.empty-book {
  text-align: center;
  padding: 80px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.empty-book-icon { font-size: 48px; }
.empty-book-title { font-size: 18px; font-weight: 700; color: var(--text-primary); }
.empty-book-desc { font-size: 14px; color: var(--text-muted); max-width: 320px; line-height: 1.6; }

/* Loading state */
.loading-state {
  text-align: center;
  padding: 60px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--text-muted);
  font-size: 13px;
}
.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--border-color);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* Upload dialog */
.upload-tip { margin-bottom: 16px; }


/* Batch Complete Dialog — styles moved to main.css */
.upload-drop-area {
  border: 2px dashed var(--border-color);
  border-radius: 12px;
  min-height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 0.15s;
}
.upload-drop-area:hover { border-color: var(--color-primary); }

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  font-size: 14px;
  padding: 24px;
  text-align: center;
  width: 100%;
}
.upload-hint { font-size: 12px; color: var(--text-muted); }

.upload-file-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  width: 100%;
}
.upload-filename { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.upload-filesize { font-size: 12px; color: var(--text-muted); }
</style>
