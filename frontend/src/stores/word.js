import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@/api'

const REVIEW_INTERVALS_MS = [
  5 * 60 * 1000,
  30 * 60 * 1000,
  12 * 60 * 60 * 1000,
  24 * 60 * 60 * 1000,
  2 * 24 * 60 * 60 * 1000,
  4 * 24 * 60 * 60 * 1000,
  7 * 24 * 60 * 60 * 1000,
  15 * 24 * 60 * 60 * 1000,
]

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

function safeNumber(value, fallback = 0) {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

function createDefaultReviewState() {
  return {
    stage: 0,
    streak: 0,
    totalReviews: 0,
    correctReviews: 0,
    avgReactionMs: 0,
    lastReactionMs: 0,
    lastReviewedAt: null,
    nextReviewAt: null,
    ease: 1,
    lastResult: 'new',
  }
}

function normalizeReviewState(raw = {}) {
  const base = createDefaultReviewState()
  return {
    stage: clamp(Math.round(safeNumber(raw.stage, 0)), 0, REVIEW_INTERVALS_MS.length - 1),
    streak: Math.max(0, Math.round(safeNumber(raw.streak, 0))),
    totalReviews: Math.max(0, Math.round(safeNumber(raw.totalReviews, 0))),
    correctReviews: Math.max(0, Math.round(safeNumber(raw.correctReviews, 0))),
    avgReactionMs: Math.max(0, Math.round(safeNumber(raw.avgReactionMs, 0))),
    lastReactionMs: Math.max(0, Math.round(safeNumber(raw.lastReactionMs, 0))),
    lastReviewedAt: raw.lastReviewedAt || base.lastReviewedAt,
    nextReviewAt: raw.nextReviewAt || base.nextReviewAt,
    ease: clamp(safeNumber(raw.ease, 1), 0.55, 1.8),
    lastResult: raw.lastResult || base.lastResult,
  }
}

function getAccuracy(state) {
  if (!state?.totalReviews) return 0
  return state.correctReviews / state.totalReviews
}

function getSpeedFactor(reactionMs) {
  if (!reactionMs) return 1
  if (reactionMs <= 2500) return 1.15
  if (reactionMs <= 5000) return 1.02
  if (reactionMs <= 9000) return 0.92
  return 0.8
}

function getAccuracyFactor(accuracy) {
  if (accuracy >= 0.9) return 1.2
  if (accuracy >= 0.75) return 1.05
  if (accuracy >= 0.55) return 0.92
  return 0.75
}

function getDueScore(state, now) {
  if (!state?.nextReviewAt) return 2
  const nextReviewTs = new Date(state.nextReviewAt).getTime()
  if (!Number.isFinite(nextReviewTs)) return 2
  if (nextReviewTs <= now) {
    return 3 + Math.min(3, (now - nextReviewTs) / (12 * 60 * 60 * 1000))
  }
  return Math.max(0, 1 - ((nextReviewTs - now) / (24 * 60 * 60 * 1000)))
}

function getReviewPriority(state, now) {
  const accuracyPenalty = 1 - getAccuracy(state)
  const slowPenalty = state.avgReactionMs ? clamp((state.avgReactionMs - 4000) / 8000, 0, 1) : 0.2
  const streakPenalty = state.streak >= 3 ? 0 : (3 - state.streak) * 0.25
  return getDueScore(state, now) + accuracyPenalty + slowPenalty + streakPenalty
}

const IELTS_WORDS = [
  { id: 1, word: 'absolute', phonetic: '/ˈæb.sə.luːt/', pos: 'adj.', posType: 'adj', meaning: '绝对的，完全的', example: '"The results were absolutely conclusive."' },
  { id: 2, word: 'abstract', phonetic: '/ˈæb.strækt/', pos: 'adj.', posType: 'adj', meaning: '抽象的；摘要', example: '"The concept is too abstract for children to grasp."' },
  { id: 3, word: 'accumulate', phonetic: '/əˈkjuː.mjə.leɪt/', pos: 'v.', posType: 'v', meaning: '积累，积聚', example: '"Dust had accumulated in the corners of the room."' },
  { id: 4, word: 'acknowledge', phonetic: '/əkˈnɒl.ɪdʒ/', pos: 'v.', posType: 'v', meaning: '承认；致谢', example: '"She acknowledged that she had made a mistake."' },
  { id: 5, word: 'adjacent', phonetic: '/əˈdʒeɪ.sənt/', pos: 'adj.', posType: 'adj', meaning: '邻近的，毗连的', example: '"The library is adjacent to the town hall."' },
  { id: 6, word: 'advocate', phonetic: '/ˈæd.və.keɪt/', pos: 'v./n.', posType: 'v', meaning: '倡导，主张；提倡者', example: '"She advocates a more relaxed approach to education."' },
  { id: 7, word: 'ambiguous', phonetic: '/æmˈbɪɡ.ju.əs/', pos: 'adj.', posType: 'adj', meaning: '模糊的，含糊的', example: '"The instructions were ambiguous and confusing."' },
  { id: 8, word: 'analyse', phonetic: '/ˈæn.ə.laɪz/', pos: 'v.', posType: 'v', meaning: '分析，解析', example: '"Scientists are analysing the data carefully."' },
  { id: 9, word: 'appropriate', phonetic: '/əˈprəʊ.pri.ət/', pos: 'adj.', posType: 'adj', meaning: '适当的，合适的', example: '"This film is not appropriate for young children."' },
  { id: 10, word: 'approximate', phonetic: '/əˈprɒk.sɪ.mɪt/', pos: 'adj.', posType: 'adj', meaning: '大约的，近似的', example: '"The approximate cost of the project is £10,000."' },
  { id: 11, word: 'attribute', phonetic: '/ˈæt.rɪ.bjuːt/', pos: 'n./v.', posType: 'n', meaning: '属性，特质；归因于', example: '"She attributes her success to hard work."' },
  { id: 12, word: 'benefit', phonetic: '/ˈben.ɪ.fɪt/', pos: 'n./v.', posType: 'n', meaning: '好处，利益；受益', example: '"The benefits of exercise are well documented."' },
  { id: 13, word: 'capable', phonetic: '/ˈkeɪ.pə.bəl/', pos: 'adj.', posType: 'adj', meaning: '有能力的，有才能的', example: '"She is capable of handling any situation."' },
  { id: 14, word: 'coherent', phonetic: '/kəʊˈhɪə.rənt/', pos: 'adj.', posType: 'adj', meaning: '连贯的，有条理的', example: '"A coherent argument is essential for a good essay."' },
  { id: 15, word: 'contemporary', phonetic: '/kənˈtem.pər.ər.i/', pos: 'adj.', posType: 'adj', meaning: '当代的，同时代的', example: '"Contemporary art often challenges traditional values."' },
  { id: 16, word: 'controversial', phonetic: '/ˌkɒn.trəˈvɜː.ʃəl/', pos: 'adj.', posType: 'adj', meaning: '有争议的，引起争论的', example: '"The new policy has proved controversial."' },
  { id: 17, word: 'crucial', phonetic: '/ˈkruː.ʃəl/', pos: 'adj.', posType: 'adj', meaning: '至关重要的，关键的', example: '"It is crucial that we act immediately."' },
  { id: 18, word: 'deduce', phonetic: '/dɪˈdjuːs/', pos: 'v.', posType: 'v', meaning: '推断，演绎', example: '"From the evidence, we can deduce that he was there."' },
  { id: 19, word: 'diverse', phonetic: '/daɪˈvɜːs/', pos: 'adj.', posType: 'adj', meaning: '多样的，不同的', example: '"London is a city with a diverse population."' },
  { id: 20, word: 'dominate', phonetic: '/ˈdɒm.ɪ.neɪt/', pos: 'v.', posType: 'v', meaning: '支配，主导', example: '"Technology now dominates everyday life."' },
]

// Built-in IELTS book (no backend needed)
const BUILTIN_BOOK = { id: 'builtin', name: '雅思核心词汇', description: '20个雅思高频词汇', isDefault: 0, wordCount: IELTS_WORDS.length, isBuiltin: true }

export const useWordStore = defineStore('word', () => {
  // ── book management ──────────────────────────────────
  const books = ref([BUILTIN_BOOK])
  const currentBookId = ref('builtin')
  const loadingBooks = ref(false)
  const loadingEntries = ref(false)
  const uploadingWords = ref(false)

  // ── sort / batch / error-count settings ──────────────────
  const sortMode = ref('order')   // 'order' | 'alpha' | 'errors'
  const batchSize = ref(0)        // 0 = all words; or 10 / 20 / 50
  const batchIndex = ref(0)       // current batch (0-indexed)
  const errorCounts = ref({})     // { wordId: count } for current book
  const reviewStates = ref({})

  function _errorKey(bid)  { return `ielts_errors_${bid}` }
  function _sortKey(bid)   { return `ielts_sort_${bid}` }
  function _batchKey(bid)  { return `ielts_batch_${bid}` }
  function _reviewKey(bid) { return `ielts_review_${bid}` }

  function _loadBookSettings(bookId) {
    errorCounts.value = JSON.parse(localStorage.getItem(_errorKey(bookId)) || '{}')
    sortMode.value    = localStorage.getItem(_sortKey(bookId))  || 'order'
    batchSize.value   = parseInt(localStorage.getItem(_batchKey(bookId)) || '0')
    batchIndex.value  = 0
    reviewStates.value = JSON.parse(localStorage.getItem(_reviewKey(bookId)) || '{}')
  }
  function _saveErrorCounts() {
    localStorage.setItem(_errorKey(currentBookId.value), JSON.stringify(errorCounts.value))
  }
  function _saveReviewStates() {
    localStorage.setItem(_reviewKey(currentBookId.value), JSON.stringify(reviewStates.value))
  }

  // entries of currently selected book (from backend)
  const bookEntries = ref([])

  // active words = either builtin list or current book entries
  const words = computed(() => {
    if (currentBookId.value === 'builtin') return IELTS_WORDS
    return bookEntries.value.map(e => ({
      id: e.id,
      word: e.word,
      phonetic: e.phonetic || '',
      pos: e.pos || '',
      posType: e.posType || 'n',
      meaning: e.meaning || '',
      example: e.example || '',
    }))
  })

  const enrichedWords = computed(() => {
    const now = Date.now()
    return words.value.map((word, index) => {
      const state = normalizeReviewState(reviewStates.value[word.id])
      const nextReviewTs = state.nextReviewAt ? new Date(state.nextReviewAt).getTime() : null
      const due = !nextReviewTs || !Number.isFinite(nextReviewTs) || nextReviewTs <= now
      return {
        ...word,
        originalIndex: index,
        reviewState: state,
        accuracy: getAccuracy(state),
        due,
        nextReviewTs,
        priorityScore: getReviewPriority(state, now),
      }
    })
  })

  // ── sorted + batched views ───────────────────────────

  // 稳定排序：仅按用户选择的排序方式排列，不受复习状态影响。
  // 用于分批切片，保证每批单词成员固定，不会因标记后优先级变化而混入其他批次的词。
  const stableSortedWords = computed(() => {
    const list = [...enrichedWords.value]
    if (sortMode.value === 'alpha') {
      list.sort((a, b) => a.word.localeCompare(b.word))
    } else if (sortMode.value === 'errors') {
      list.sort((a, b) => (errorCounts.value[b.id] || 0) - (errorCounts.value[a.id] || 0))
    } else {
      list.sort((a, b) => a.originalIndex - b.originalIndex)
    }
    return list
  })

  // 展示排序：在批次成员固定的基础上，将待复习单词排到前面。
  // 只影响批次内部的卡片顺序，不跨批次重排。
  const sortedWords = computed(() => {
    const list = [...stableSortedWords.value]
    list.sort((a, b) => {
      if (a.due !== b.due) return a.due ? -1 : 1
      if (Math.abs(b.priorityScore - a.priorityScore) > 0.01) return b.priorityScore - a.priorityScore
      if (sortMode.value === 'alpha') return a.word.localeCompare(b.word)
      if (sortMode.value === 'errors') return (errorCounts.value[b.id] || 0) - (errorCounts.value[a.id] || 0)
      return a.originalIndex - b.originalIndex
    })
    return list
  })

  const totalBatches = computed(() => {
    if (!batchSize.value) return 1
    return Math.max(1, Math.ceil(stableSortedWords.value.length / batchSize.value))
  })

  const baseDisplayWords = computed(() => {
    if (!batchSize.value) return sortedWords.value
    // 先从稳定排序中取出本批次的单词 ID，再从展示排序中按 ID 筛选
    // 这样既保证批次成员固定，又保留批次内的复习优先级顺序
    const start = batchIndex.value * batchSize.value
    const batchIds = new Set(
      stableSortedWords.value.slice(start, start + batchSize.value).map(w => w.id)
    )
    return sortedWords.value.filter(w => batchIds.has(w.id))
  })

  // ── study state ──────────────────────────────────────
  const currentIndex = ref(0)
  const studyMode = ref('card')
  const focusUnknownOnly = ref(false)

  // Per-book known/unknown sets stored in localStorage
  function _storeKey(bookId, type) { return `ielts_${type}_${bookId}` }
  const knownIds = ref(new Set(JSON.parse(localStorage.getItem(_storeKey('builtin', 'known')) || '[]')))
  const unknownIds = ref(new Set(JSON.parse(localStorage.getItem(_storeKey('builtin', 'unknown')) || '[]')))

  const currentBatchUnknownCount = computed(() => (
    baseDisplayWords.value.filter(w => unknownIds.value.has(w.id)).length
  ))

  const canGoNextBatch = computed(() => currentBatchUnknownCount.value === 0)

  const displayWords = computed(() => {
    const base = baseDisplayWords.value
    if (!focusUnknownOnly.value) return base
    const unknownOnly = base.filter(w => unknownIds.value.has(w.id))
    return unknownOnly.length ? unknownOnly : base
  })

  const currentWord = computed(() => displayWords.value[currentIndex.value] || displayWords.value[0])
  const totalWords = computed(() => displayWords.value.length)
  const knownCount = computed(() => displayWords.value.filter(w => knownIds.value.has(w.id)).length)
  const progress = computed(() => {
    if (!totalWords.value) return 0
    const done = displayWords.value.filter(w => knownIds.value.has(w.id) || unknownIds.value.has(w.id)).length
    return Math.round((done / totalWords.value) * 100)
  })
  const remainingWords = computed(() => displayWords.value.filter(w => !knownIds.value.has(w.id) && !unknownIds.value.has(w.id)))
  const dueWordsCount = computed(() => displayWords.value.filter(w => unknownIds.value.has(w.id) || w.due || !w.reviewState?.nextReviewAt).length)
  const overdueWordsCount = computed(() => displayWords.value.filter(w => w.reviewState?.nextReviewAt && w.due).length)
  const avgAccuracy = computed(() => {
    const reviewed = displayWords.value.filter(w => w.reviewState?.totalReviews)
    if (!reviewed.length) return 0
    const total = reviewed.reduce((sum, word) => sum + word.accuracy, 0)
    return Math.round((total / reviewed.length) * 100)
  })
  const avgReactionMs = computed(() => {
    const reviewed = displayWords.value.filter(w => w.reviewState?.avgReactionMs)
    if (!reviewed.length) return 0
    return Math.round(reviewed.reduce((sum, word) => sum + word.reviewState.avgReactionMs, 0) / reviewed.length)
  })

  // ── book API ─────────────────────────────────────────
  async function loadBooks() {
    const token = localStorage.getItem('ielts_token') || ''
    if (!token || token.startsWith('mock_token_')) return
    loadingBooks.value = true
    try {
      const res = await request.get('/words/books')
      const serverBooks = (res.data || []).map(b => ({ ...b, isBuiltin: false }))
      books.value = [BUILTIN_BOOK, ...serverBooks]
    } catch { /* keep builtin only */ }
    loadingBooks.value = false
  }

  async function createBook(name, description) {
    const res = await request.post('/words/books', { name, description })
    const book = { ...res.data, isBuiltin: false }
    books.value.push(book)
    return book
  }

  async function deleteBook(bookId) {
    await request.delete(`/words/books/${bookId}`)
    books.value = books.value.filter(b => b.id !== bookId)
    if (currentBookId.value === bookId) switchBook('builtin')
  }

  async function loadEntries(bookId) {
    if (bookId === 'builtin') { bookEntries.value = []; return }
    loadingEntries.value = true
    try {
      const res = await request.get(`/words/books/${bookId}/entries`)
      bookEntries.value = res.data || []
    } catch { bookEntries.value = [] }
    loadingEntries.value = false
  }

  async function uploadWords(bookId, file) {
    uploadingWords.value = true
    try {
      const form = new FormData()
      form.append('file', file)
      // Returns immediately with { status: 'processing' }
      await request.post(`/words/books/${bookId}/upload`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      // Update book tab to show processing
      const idx = books.value.findIndex(b => b.id === bookId)
      if (idx !== -1) books.value[idx] = { ...books.value[idx], status: 'processing' }
      // Start background polling
      _pollBookStatus(bookId)
    } finally {
      uploadingWords.value = false
    }
  }

  const _pollTimers = {}
  function _pollBookStatus(bookId) {
    if (_pollTimers[bookId]) return // already polling
    let attempts = 0
    const maxAttempts = 40 // 40 × 3s = 2 min max
    _pollTimers[bookId] = setInterval(async () => {
      attempts++
      if (attempts > maxAttempts) {
        clearInterval(_pollTimers[bookId])
        delete _pollTimers[bookId]
        return
      }
      try {
        const token = localStorage.getItem('ielts_token') || ''
        if (!token || token.startsWith('mock_token_')) return
        const res = await request.get(`/words/books/${bookId}`)
        const book = res.data
        const idx = books.value.findIndex(b => b.id === bookId)
        if (idx !== -1) {
          books.value[idx] = { ...books.value[idx], status: book.status, wordCount: book.wordCount }
        }
        if (book.status === 'ready' || book.status === 'failed') {
          clearInterval(_pollTimers[bookId])
          delete _pollTimers[bookId]
          // If this is the active book, reload entries
          if (currentBookId.value === bookId) await loadEntries(bookId)
          // Notify via event so WordsView can show a toast
          window.dispatchEvent(new CustomEvent('word-book-updated', {
            detail: { bookId, status: book.status, wordCount: book.wordCount }
          }))
        }
      } catch { /* silently retry */ }
    }, 3000)
  }

  async function deleteEntry(entryId) {
    await request.delete(`/words/entries/${entryId}`)
    bookEntries.value = bookEntries.value.filter(e => e.id !== entryId)
    const idx = books.value.findIndex(b => b.id === currentBookId.value)
    if (idx !== -1) books.value[idx] = { ...books.value[idx], wordCount: bookEntries.value.length }
  }

  async function updateEntry(entryId, meaning, example) {
    const res = await request.put(`/words/entries/${entryId}`, { meaning, example })
    const updated = res.data
    const idx = bookEntries.value.findIndex(e => e.id === entryId)
    if (idx !== -1) bookEntries.value[idx] = { ...bookEntries.value[idx], ...updated }
    return updated
  }

  async function quickAddWords(words) {
    if (!words || words.length === 0) return
    const res = await request.post('/words/books/default/quick-add', { words })
    return res.data
  }

  async function addToDefaultBook(entry) {
    let res
    if (currentBook.value?.isBuiltin) {
      // Builtin IELTS words have no real DB id — send word data directly
      res = await request.post('/words/books/default/entries', {
        word: entry.word,
        phonetic: entry.phonetic,
        pos: entry.pos,
        posType: entry.posType,
        meaning: entry.meaning,
        example: entry.example,
      })
    } else {
      res = await request.post(`/words/entries/${entry.id}/copy-to-default`)
    }
    // Refresh default book word count in tabs
    const defaultIdx = books.value.findIndex(b => b.isDefault)
    if (defaultIdx !== -1) {
      books.value[defaultIdx] = { ...books.value[defaultIdx], wordCount: (books.value[defaultIdx].wordCount || 0) + 1 }
    }
    return res.data
  }

  function switchBook(bookId) {
    currentBookId.value = bookId
    currentIndex.value = 0
    focusUnknownOnly.value = false
    knownIds.value = new Set(JSON.parse(localStorage.getItem(_storeKey(bookId, 'known')) || '[]'))
    unknownIds.value = new Set(JSON.parse(localStorage.getItem(_storeKey(bookId, 'unknown')) || '[]'))
    _loadBookSettings(bookId)
    if (bookId !== 'builtin') loadEntries(bookId)
    else bookEntries.value = []
  }

  // Init settings for initial book
  _loadBookSettings('builtin')

  // ── study actions ─────────────────────────────────────
  function _updateReviewSchedule(wordId, remembered, reactionMs = 0) {
    const current = normalizeReviewState(reviewStates.value[wordId])
    const totalReviews = current.totalReviews + 1
    const correctReviews = current.correctReviews + (remembered ? 1 : 0)
    const safeReaction = Math.max(800, Math.round(safeNumber(reactionMs, current.avgReactionMs || 4000)))
    const avgReactionMs = current.avgReactionMs
      ? Math.round((current.avgReactionMs * current.totalReviews + safeReaction) / totalReviews)
      : safeReaction
    const accuracy = correctReviews / totalReviews
    const speedFactor = getSpeedFactor(safeReaction)
    const accuracyFactor = getAccuracyFactor(accuracy)
    const nextEase = clamp(
      remembered
        ? current.ease * 0.72 + speedFactor * accuracyFactor * 0.45
        : current.ease * 0.6,
      0.55,
      1.8,
    )
    const nextStage = remembered
      ? clamp(current.stage + (safeReaction <= 2500 && accuracy >= 0.85 ? 2 : 1), 0, REVIEW_INTERVALS_MS.length - 1)
      : Math.max(0, current.stage - (current.stage >= 3 ? 2 : 1))
    const baseInterval = REVIEW_INTERVALS_MS[nextStage]
    const interval = remembered
      ? Math.round(baseInterval * nextEase)
      : Math.round(Math.max(5 * 60 * 1000, REVIEW_INTERVALS_MS[Math.max(0, nextStage)] * 0.35))
    reviewStates.value = {
      ...reviewStates.value,
      [wordId]: {
        stage: nextStage,
        streak: remembered ? current.streak + 1 : 0,
        totalReviews,
        correctReviews,
        avgReactionMs,
        lastReactionMs: safeReaction,
        lastReviewedAt: new Date().toISOString(),
        nextReviewAt: new Date(Date.now() + interval).toISOString(),
        ease: nextEase,
        lastResult: remembered ? 'known' : 'unknown',
      },
    }
    _saveReviewStates()
  }

  function markKnown(payload) {
    const wordId = typeof payload === 'object' ? payload.id : payload
    const reactionMs = typeof payload === 'object' ? payload.reactionMs : 0
    knownIds.value.add(wordId)
    unknownIds.value.delete(wordId)
    _updateReviewSchedule(wordId, true, reactionMs)
    _saveProgress()
    if (focusUnknownOnly.value && currentBatchUnknownCount.value === 0) {
      focusUnknownOnly.value = false
    }
    _checkBatchComplete()
    nextWord()
  }

  function markUnknown(payload) {
    const wordId = typeof payload === 'object' ? payload.id : payload
    const reactionMs = typeof payload === 'object' ? payload.reactionMs : 0
    unknownIds.value.add(wordId)
    knownIds.value.delete(wordId)
    errorCounts.value = { ...errorCounts.value, [wordId]: (errorCounts.value[wordId] || 0) + 1 }
    _saveErrorCounts()
    _updateReviewSchedule(wordId, false, reactionMs)
    _saveProgress()
    _checkBatchComplete()
    nextWord()
  }

  function _checkBatchComplete() {
    if (!batchSize.value) return
    const currentBatchWords = baseDisplayWords.value
    const done = currentBatchWords.filter(w => knownIds.value.has(w.id) || unknownIds.value.has(w.id)).length
    // 本批全部学完时触发回调（包括最后一批）
    if (done === currentBatchWords.length && currentBatchWords.length > 0) {
      const unknownRemaining = currentBatchWords.filter(w => unknownIds.value.has(w.id)).length
      if (unknownRemaining > 0) {
        // Only reset index when ENTERING unknown-only focus mode;
        // avoid resetting on every subsequent answer, which would trap user at index 0.
        if (!focusUnknownOnly.value) {
          focusUnknownOnly.value = true
          currentIndex.value = 0
        }
        return
      }
      focusUnknownOnly.value = false
      batchCompleteCallback.value?.()
    }
  }

  const batchCompleteCallback = ref(null)

  function nextWord() {
    currentIndex.value = currentIndex.value < displayWords.value.length - 1 ? currentIndex.value + 1 : 0
  }

  function prevWord() {
    currentIndex.value = currentIndex.value > 0 ? currentIndex.value - 1 : displayWords.value.length - 1
  }

  function resetProgress() {
    knownIds.value = new Set()
    unknownIds.value = new Set()
    focusUnknownOnly.value = false
    reviewStates.value = {}
    currentIndex.value = 0
    batchIndex.value = 0
    _saveReviewStates()
    _saveProgress()
  }

  // ── sort / batch actions ──────────────────────────────
  function setSortMode(mode) {
    sortMode.value = mode
    currentIndex.value = 0
    focusUnknownOnly.value = false
    localStorage.setItem(_sortKey(currentBookId.value), mode)
  }

  function setBatchSize(size) {
    batchSize.value = size
    batchIndex.value = 0
    currentIndex.value = 0
    focusUnknownOnly.value = false
    localStorage.setItem(_batchKey(currentBookId.value), String(size))
  }

  function nextBatch() {
    if (!canGoNextBatch.value) return
    if (batchIndex.value < totalBatches.value - 1) {
      batchIndex.value++
      currentIndex.value = 0
      focusUnknownOnly.value = false
    }
  }

  function prevBatch() {
    if (batchIndex.value > 0) {
      batchIndex.value--
      currentIndex.value = 0
      focusUnknownOnly.value = false
    }
  }

  function _saveProgress() {
    const key = currentBookId.value
    localStorage.setItem(_storeKey(key, 'known'), JSON.stringify([...knownIds.value]))
    localStorage.setItem(_storeKey(key, 'unknown'), JSON.stringify([...unknownIds.value]))
  }

  function isKnown(id) { return knownIds.value.has(id) }
  function isUnknown(id) { return unknownIds.value.has(id) }
  function getReviewState(id) { return normalizeReviewState(reviewStates.value[id]) }

  const currentBook = computed(() => books.value.find(b => b.id === currentBookId.value) || BUILTIN_BOOK)

  return {
    words, enrichedWords, sortedWords, displayWords, baseDisplayWords, currentIndex, currentWord, totalWords, knownCount,
    progress, remainingWords, studyMode,
    dueWordsCount, overdueWordsCount, avgAccuracy, avgReactionMs,
    sortMode, batchSize, batchIndex, totalBatches, errorCounts, canGoNextBatch,
    books, currentBookId, currentBook, loadingBooks, loadingEntries, uploadingWords,
    bookEntries,
    loadBooks, createBook, deleteBook, loadEntries, uploadWords, deleteEntry, updateEntry, addToDefaultBook, quickAddWords, switchBook,
    markKnown, markUnknown, nextWord, prevWord, resetProgress,
    setSortMode, setBatchSize, nextBatch, prevBatch,
    isKnown, isUnknown, getReviewState,
    batchCompleteCallback,
  }
})
