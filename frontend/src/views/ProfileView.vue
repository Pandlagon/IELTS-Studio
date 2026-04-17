<template>
  <div class="page-wrapper">
    <NavBar />
    <div class="profile-page">
      <div class="container">

        <!-- Profile Header -->
        <div class="profile-header card">
          <div class="avatar-section">
            <div class="avatar-large">{{ authStore.username.charAt(0).toUpperCase() }}</div>
            <div class="user-info">
              <h2 class="username">{{ authStore.username }}</h2>
              <p class="user-email">{{ authStore.user?.email || '' }}</p>
              <div class="user-badges">
                <span class="badge badge-green">雅思备考中</span>
                <span class="badge badge-amber">目标 Band 7.0</span>
              </div>
            </div>
          </div>
          <button class="edit-btn btn-secondary" @click="showEdit = true">编辑资料</button>
        </div>

        <!-- Stats Row -->
        <div class="stats-row">
          <div v-for="stat in stats" :key="stat.label" class="stat-card card">
            <div class="stat-icon-wrap" :style="{ background: stat.iconBg }">
              <span v-html="stat.icon"></span>
            </div>
            <div class="stat-content">
              <div class="stat-number">{{ stat.value }}</div>
              <div class="stat-label">{{ stat.label }}</div>
            </div>
          </div>
        </div>

        <div class="profile-body">
          <!-- Left: Exam History -->
          <div class="history-section">
            <div class="section-hd">
              <h3>考试记录</h3>
              <router-link to="/exams" class="see-all">查看全部 →</router-link>
            </div>
            <div v-if="loadingHistory" class="history-loading">加载中...</div>
            <div v-else-if="examHistory.length" class="history-list">
              <div
                v-for="record in examHistory.slice(0, 10)"
                :key="record.submittedAt"
                class="history-item card-sm card"
                :class="{ clickable: !!record.recordId }"
                @click="openRecord(record)"
              >
                <div class="history-item-left">
                  <div class="history-exam-title">{{ record.examTitle || '模拟考试' }}</div>
                  <div class="history-date">{{ formatDate(record.submittedAt) }}</div>
                </div>
                <div class="history-item-right">
                  <div class="history-score-row">
                    <span class="history-band">Band {{ record.band }}</span>
                    <span class="history-correct" v-if="record.total > 0">{{ record.correct }}/{{ record.total }}</span>
                    <span class="history-correct" v-else>写作</span>
                  </div>
                  <div class="history-bar" v-if="record.total > 0">
                    <div class="history-fill" :style="{ width: Math.round((record.correct || 0) / record.total * 100) + '%', background: getBandColor(record.band) }"></div>
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="empty-section">
              <p>暂无考试记录</p>
              <router-link to="/exams" class="btn-primary" style="font-size:13px;padding:8px 18px">开始模拟考试</router-link>
            </div>
          </div>

          <!-- Right: Word Progress + Error Book -->
          <div class="right-section">
            <!-- Word Progress -->
            <div class="word-progress-card card">
              <div class="section-hd">
                <h3>单词进度</h3>
                <router-link to="/words" class="see-all">去背单词 →</router-link>
              </div>
              <div class="word-donut">
                <svg viewBox="0 0 80 80" class="donut-svg">
                  <circle cx="40" cy="40" r="32" fill="none" stroke="var(--border-color)" stroke-width="6"/>
                  <circle cx="40" cy="40" r="32" fill="none" stroke="var(--color-primary)" stroke-width="6"
                    stroke-linecap="round"
                    stroke-dasharray="201"
                    :stroke-dashoffset="201 * (1 - wordStore.progress / 100)"
                    transform="rotate(-90 40 40)"
                    style="transition: stroke-dashoffset 1s ease"
                  />
                </svg>
                <div class="donut-label">
                  <span class="donut-pct">{{ wordStore.progress }}%</span>
                  <span class="donut-sub">完成</span>
                </div>
              </div>
              <div class="word-stats-row">
                <div class="word-stat">
                  <span class="ws-num green">{{ wordStore.knownCount }}</span>
                  <span class="ws-lbl">已掌握</span>
                </div>
                <div class="word-stat">
                  <span class="ws-num red">{{ unknownCount }}</span>
                  <span class="ws-lbl">待复习</span>
                </div>
                <div class="word-stat">
                  <span class="ws-num">{{ wordStore.totalWords }}</span>
                  <span class="ws-lbl">总词汇</span>
                </div>
              </div>
            </div>

            <!-- Error Book Preview -->
            <div class="errorbook-card card">
              <div class="section-hd">
                <h3>错题本</h3>
                <span class="error-count badge badge-red">{{ errorQuestions.length }} 题</span>
              </div>
              <div v-if="errorQuestions.length" class="error-list">
                <div v-for="(q, i) in errorQuestions.slice(0, 5)" :key="i" class="error-item">
                  <span class="error-type-tag" :class="q.type">{{ typeLabel(q.type) }}</span>
                  <span class="error-q-text">{{ q.text?.substring(0, 45) }}{{ q.text?.length > 45 ? '...' : '' }}</span>
                </div>
                <p v-if="errorQuestions.length > 5" class="more-errors">
                  还有 {{ errorQuestions.length - 5 }} 题...
                </p>
              </div>
              <div v-else class="empty-section small">
                <p>暂无错题，继续加油！🎉</p>
              </div>
            </div>
          </div>
        </div>

      </div>
    </div>

    <!-- 编辑个人资料弹窗 -->
    <el-dialog v-model="showEdit" width="420px" :close-on-click-modal="false">
      <template #header>
        <div class="dialog-header">
          <span class="dialog-header-icon">👤</span>
          <span>编辑资料</span>
        </div>
      </template>
      <el-form :model="editForm" label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="editForm.username" placeholder="输入新用户名" />
        </el-form-item>
        <el-form-item label="目标分数">
          <el-select v-model="editForm.targetBand" style="width:100%" placeholder="选择目标 Band 分">
            <el-option v-for="b in [5.0,5.5,6.0,6.5,7.0,7.5,8.0,8.5,9.0]" :key="b" :label="`Band ${b}`" :value="b" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import NavBar from '@/components/NavBar.vue'
import { useAuthStore } from '@/stores/auth'
import { useWordStore } from '@/stores/word'
import { useExamStore } from '@/stores/exam'
import { authApi } from '@/api/auth'

const router = useRouter()
const authStore = useAuthStore()
const wordStore = useWordStore()
const examStore = useExamStore()

const showEdit = ref(false)
const editForm = ref({ username: authStore.username, targetBand: 7.0 })
const loadingHistory = ref(false)

const examHistory = computed(() => examStore.examHistory)

onMounted(async () => {
  loadingHistory.value = true
  await examStore.loadHistory()
  loadingHistory.value = false
})

async function openRecord(record) {
  if (!record.recordId) return
  const ok = await examStore.loadRecord(record.recordId)
  if (ok) {
    router.push(`/exam/${record.examId}/result`)
  } else {
    ElMessage.warning('记录加载失败，请重试')
  }
}

const unknownCount = computed(() => wordStore.words.filter(w => wordStore.isUnknown(w.id)).length)

const errorQuestions = computed(() => {
  const history = examStore.getExamHistory()
  const all = []
  history.forEach(record => {
    if (record.wrongQuestions) all.push(...record.wrongQuestions)
  })
  const stored = JSON.parse(localStorage.getItem('ielts_error_book') || '[]')
  return stored
})

const stats = computed(() => [
  {
    label: '模拟考试次数',
    value: examHistory.value.length,
    icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>',
    iconBg: 'linear-gradient(135deg, #1B4332, #2D6A4F)',
  },
  {
    label: '平均 Band 分',
    value: examHistory.value.length
      ? (examHistory.value.reduce((s, r) => s + r.band, 0) / examHistory.value.length).toFixed(1)
      : '--',
    icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>',
    iconBg: 'linear-gradient(135deg, #1D4ED8, #3B82F6)',
  },
  {
    label: '已掌握单词',
    value: wordStore.knownCount,
    icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>',
    iconBg: 'linear-gradient(135deg, #B45309, #D97706)',
  },
  {
    label: '错题积累',
    value: errorQuestions.value.length,
    icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>',
    iconBg: 'linear-gradient(135deg, #7C3AED, #A78BFA)',
  },
])

function formatDate(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function getBandColor(band) {
  if (band >= 8) return '#22C55E'
  if (band >= 7) return '#3B82F6'
  if (band >= 6) return '#F59E0B'
  return '#EF4444'
}

function typeLabel(type) {
  return { tfng: 'T/F/NG', mcq: '选择', fill: '填空' }[type] || type
}

async function saveEdit() {
  try {
    const updated = await authApi.updateProfile({ username: editForm.value.username })
    authStore.updateUser(updated)
    showEdit.value = false
    ElMessage.success('资料已更新')
  } catch (e) {
    ElMessage.error(e.message || '更新失败')
  }
}
</script>

<style scoped>
.profile-page { padding: 32px 0 64px; }

/* Header */
.profile-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  padding: 28px 32px;
}

.avatar-section { display: flex; align-items: center; gap: 20px; }

.avatar-large {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: var(--color-primary);
  color: white;
  font-size: 28px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.username { font-size: 22px; font-weight: 800; margin-bottom: 4px; }
.user-email { font-size: 13px; color: var(--text-muted); margin-bottom: 10px; }
.user-badges { display: flex; gap: 8px; }

/* Stats Row */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
}

.stat-icon-wrap {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-number { font-size: 24px; font-weight: 800; color: var(--text-primary); }
.stat-label { font-size: 12px; color: var(--text-muted); }

/* Body */
.profile-body {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 20px;
  align-items: start;
}

.section-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-hd h3 { font-size: 16px; font-weight: 700; }
.see-all { font-size: 13px; color: var(--color-primary); text-decoration: none; font-weight: 500; }
.see-all:hover { text-decoration: underline; }

/* History */
.history-section { display: flex; flex-direction: column; }

.history-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  transition: box-shadow 0.15s, transform 0.15s;
}
.history-item.clickable {
  cursor: pointer;
}
.history-item.clickable:hover {
  box-shadow: 0 4px 16px rgba(27,67,50,0.12);
  transform: translateY(-1px);
}
.history-loading {
  font-size: 13px;
  color: var(--text-muted);
  padding: 16px 0;
  text-align: center;
}

.history-exam-title { font-size: 14px; font-weight: 600; color: var(--text-primary); margin-bottom: 3px; }
.history-date { font-size: 12px; color: var(--text-muted); }

.history-item-right { display: flex; flex-direction: column; align-items: flex-end; gap: 6px; min-width: 140px; }

.history-score-row { display: flex; align-items: center; gap: 10px; }

.history-band {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-primary);
  background: rgba(27,67,50,0.08);
  padding: 2px 8px;
  border-radius: var(--radius-full);
}

.history-correct { font-size: 13px; color: var(--text-muted); }

.history-bar {
  width: 120px;
  height: 4px;
  background: var(--border-color);
  border-radius: 2px;
  overflow: hidden;
}

.history-fill { height: 100%; border-radius: 2px; transition: width 0.5s; }

/* Right Section */
.right-section { display: flex; flex-direction: column; gap: 16px; }

/* Word Progress */
.word-progress-card { padding: 20px; }

.word-donut {
  position: relative;
  width: 80px;
  height: 80px;
  margin: 0 auto 16px;
}

.donut-svg { width: 100%; height: 100%; }

.donut-label {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.donut-pct { font-size: 16px; font-weight: 800; color: var(--color-primary); }
.donut-sub { font-size: 10px; color: var(--text-muted); }

.word-stats-row { display: flex; justify-content: space-around; }

.word-stat { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.ws-num { font-size: 20px; font-weight: 800; color: var(--text-primary); }
.ws-num.green { color: #22C55E; }
.ws-num.red { color: #EF4444; }
.ws-lbl { font-size: 11px; color: var(--text-muted); }

/* Error Book */
.errorbook-card { padding: 20px; }

.error-list { display: flex; flex-direction: column; gap: 8px; }

.error-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg-primary);
  border-radius: var(--radius-md);
}

.error-type-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: var(--radius-full);
  flex-shrink: 0;
}

.error-type-tag.tfng { background: #EDE9FE; color: #5B21B6; }
.error-type-tag.mcq { background: #DBEAFE; color: #1D4ED8; }
.error-type-tag.fill { background: #FEF3C7; color: #92400E; }

.error-q-text { font-size: 12px; color: var(--text-secondary); }
.more-errors { font-size: 12px; color: var(--text-muted); text-align: center; padding-top: 4px; }

.empty-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 32px 0;
  color: var(--text-muted);
  font-size: 14px;
}

.empty-section.small { padding: 16px 0; }
.error-count { font-size: 12px; }

@media (max-width: 1024px) {
  .stats-row { grid-template-columns: repeat(2, 1fr); }
  .profile-body { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .stats-row { grid-template-columns: repeat(2, 1fr); }
  .profile-header { flex-direction: column; align-items: flex-start; gap: 16px; }
}
</style>
