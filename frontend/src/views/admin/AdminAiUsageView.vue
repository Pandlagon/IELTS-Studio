<template>
  <div class="admin-ai-usage-page" v-loading="loading">
    <!-- 页头 -->
    <div class="page-header">
      <div class="header-text">
        <h2 class="page-title">
          <el-button :icon="ArrowLeft" circle @click="router.back()" class="back-btn" />
          <i class="fa-solid fa-chart-line"></i>
          AI 使用统计
        </h2>
        <p class="page-subtitle">管理端 AI 调用统计与最近记录排查</p>
      </div>
      <div class="header-actions">
        <el-select v-model="days" size="default" style="width: 110px" @change="loadSummary">
          <el-option :value="7" label="最近 7 天" />
          <el-option :value="30" label="最近 30 天" />
          <el-option :value="90" label="最近 90 天" />
        </el-select>
        <el-button :icon="Refresh" @click="loadAll" :loading="loading">刷新</el-button>
      </div>
    </div>

    <!-- 错误提示 -->
    <el-alert
      v-if="loadError"
      type="error"
      :closable="false"
      show-icon
      class="error-alert"
      :title="loadError"
    />

    <!-- 无权限 -->
    <el-alert
      v-if="forbidden"
      type="warning"
      :closable="false"
      show-icon
      class="error-alert"
      title="无权限访问：本页面仅管理员可用。"
    />

    <template v-if="!loadError && !forbidden && summary">
      <!-- 顶部统计卡片 -->
      <div class="stat-grid">
        <div class="stat-card">
          <div class="stat-label">总调用</div>
          <div class="stat-value">{{ summary.totalCalls }}</div>
        </div>
        <div class="stat-card stat-success">
          <div class="stat-label">成功</div>
          <div class="stat-value">{{ summary.successCalls }}</div>
        </div>
        <div class="stat-card stat-danger">
          <div class="stat-label">失败</div>
          <div class="stat-value">{{ summary.failedCalls }}</div>
        </div>
        <div class="stat-card stat-info">
          <div class="stat-label">已拒绝</div>
          <div class="stat-value">{{ summary.rejectedCalls }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">总 cost</div>
          <div class="stat-value">{{ summary.totalCost }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">独立用户</div>
          <div class="stat-value">{{ summary.uniqueUsers }}</div>
        </div>
      </div>

      <!-- 分组统计 -->
      <div class="bucket-grid">
        <el-card class="bucket-card" shadow="never">
          <template #header>
            <span class="bucket-title">按状态</span>
          </template>
          <el-table :data="summary.byStatus" size="small" :max-height="240">
            <el-table-column label="状态" min-width="100">
              <template #default="{ row }">
                <el-tag v-if="row.name === 'SUCCESS'" size="small" type="success">成功</el-tag>
                <el-tag v-else-if="row.name === 'FAILED'" size="small" type="danger">失败</el-tag>
                <el-tag v-else-if="row.name === 'REJECTED'" size="small" type="info">已拒绝</el-tag>
                <el-tag v-else size="small">{{ row.name }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="调用数" prop="count" width="90" align="right" />
            <el-table-column label="cost" prop="cost" width="80" align="right" />
          </el-table>
        </el-card>

        <el-card class="bucket-card" shadow="never">
          <template #header>
            <span class="bucket-title">按 Provider</span>
          </template>
          <el-table :data="summary.byProvider" size="small" :max-height="240">
            <el-table-column label="Provider" min-width="140">
              <template #default="{ row }">
                <span>{{ providerLabel(row.name) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="调用数" prop="count" width="90" align="right" />
            <el-table-column label="cost" prop="cost" width="80" align="right" />
          </el-table>
        </el-card>

        <el-card class="bucket-card" shadow="never">
          <template #header>
            <span class="bucket-title">按功能</span>
          </template>
          <el-table :data="summary.byFeature" size="small" :max-height="240">
            <el-table-column label="功能" min-width="140">
              <template #default="{ row }">
                <span>{{ featureLabel(row.name) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="调用数" prop="count" width="90" align="right" />
            <el-table-column label="cost" prop="cost" width="80" align="right" />
          </el-table>
        </el-card>

        <el-card class="bucket-card" shadow="never">
          <template #header>
            <span class="bucket-title">按 Key 模式</span>
          </template>
          <el-table :data="summary.byKeyMode" size="small" :max-height="240">
            <el-table-column label="Key 模式" min-width="120">
              <template #default="{ row }">
                <el-tag v-if="row.name === 'BUILTIN'" size="small" type="success">内置</el-tag>
                <el-tag v-else-if="row.name === 'USER'" size="small" type="warning">自填</el-tag>
                <el-tag v-else size="small">{{ row.name }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="调用数" prop="count" width="90" align="right" />
            <el-table-column label="cost" prop="cost" width="80" align="right" />
          </el-table>
        </el-card>

        <el-card class="bucket-card" shadow="never">
          <template #header>
            <span class="bucket-title">按任务类型</span>
          </template>
          <el-table :data="summary.byTaskType" size="small" :max-height="240">
            <el-table-column label="任务类型" min-width="120">
              <template #default="{ row }">
                <span>{{ taskTypeLabel(row.name) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="调用数" prop="count" width="90" align="right" />
            <el-table-column label="cost" prop="cost" width="80" align="right" />
          </el-table>
        </el-card>

        <!-- 每日趋势 -->
        <el-card class="bucket-card" shadow="never">
          <template #header>
            <span class="bucket-title">每日趋势</span>
          </template>
          <el-table :data="summary.dailyTrend" size="small" :max-height="240">
            <el-table-column label="日期" prop="name" min-width="120" />
            <el-table-column label="调用数" prop="count" width="90" align="right" />
            <el-table-column label="cost" prop="cost" width="80" align="right" />
          </el-table>
        </el-card>
      </div>

      <!-- 最近记录 -->
      <el-card class="recent-card" shadow="never">
        <template #header>
          <div class="recent-header">
            <span class="bucket-title">最近使用记录</span>
            <el-select v-model="recentLimit" size="small" style="width: 100px" @change="loadRecent">
              <el-option :value="20" label="20 条" />
              <el-option :value="50" label="50 条" />
              <el-option :value="100" label="100 条" />
            </el-select>
          </div>
        </template>
        <el-table :data="recentRecords" size="small" :max-height="420">
          <el-table-column label="时间" width="150">
            <template #default="{ row }">
              <span>{{ formatDateTime(row.createdAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="用户ID" prop="userId" width="80" align="center" />
          <el-table-column label="功能" min-width="120">
            <template #default="{ row }">
              <span>{{ featureLabel(row.feature) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="Provider" width="140">
            <template #default="{ row }">
              <span>{{ providerLabel(row.provider) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="模式" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.keyMode === 'BUILTIN'" size="small" type="success">内置</el-tag>
              <el-tag v-else-if="row.keyMode === 'USER'" size="small" type="warning">自填</el-tag>
              <el-tag v-else size="small">{{ row.keyMode }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="cost" prop="cost" width="60" align="center" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.status === 'SUCCESS'" size="small" type="success">成功</el-tag>
              <el-tag v-else-if="row.status === 'FAILED'" size="small" type="danger">失败</el-tag>
              <el-tag v-else-if="row.status === 'REJECTED'" size="small" type="info">已拒绝</el-tag>
              <el-tag v-else size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="错误信息" min-width="180">
            <template #default="{ row }">
              <span class="cell-error">{{ row.errorMessage || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Refresh, ArrowLeft } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { adminAiUsageApi } from '@/api/adminAiUsage'

const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const days = ref(7)
const recentLimit = ref(50)
const summary = ref(null)
const recentRecords = ref([])

/** feature 中文映射（与后端 AiFeature 枚举对齐，与 AiUsageCard.vue 保持一致） */
const FEATURE_LABELS = {
  WRITING_GRADE: '写作评分',
  AI_CHAT: 'AI 助手',
  TRANSLATE: '翻译',
  CLOZE_GENERATE: '完形生成',
  CLOZE_CHECK: '完形批改',
  WORD_GENERATE: '词汇生成',
  EXAM_PARSE: '普通试卷解析',
  EXAM_PRECISE_PARSE: '精准视觉解析',
  WRITING_GUIDANCE: '写作思路生成',
  HEADING_EXTRACT: '标题列表抽取',
}

const PROVIDER_LABELS = {
  DEEPSEEK: 'DeepSeek',
  QWEN: 'Qwen',
  MIMO: 'MiMO',
  OPENAI_COMPATIBLE: 'OpenAI-compatible',
}

const TASK_TYPE_LABELS = {
  TEXT: '文本',
  VISION: '视觉',
}

function featureLabel(feature) {
  if (!feature) return '-'
  return FEATURE_LABELS[feature] || feature
}

function providerLabel(provider) {
  if (!provider) return '-'
  return PROVIDER_LABELS[provider] || provider
}

function taskTypeLabel(taskType) {
  if (!taskType) return '-'
  return TASK_TYPE_LABELS[taskType] || taskType
}

function formatDateTime(iso) {
  if (!iso) return ''
  const dt = new Date(iso)
  if (Number.isNaN(dt.getTime())) return iso
  const yyyy = dt.getFullYear()
  const mm = String(dt.getMonth() + 1).padStart(2, '0')
  const dd = String(dt.getDate()).padStart(2, '0')
  const hh = String(dt.getHours()).padStart(2, '0')
  const mi = String(dt.getMinutes()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}`
}

async function loadSummary() {
  try {
    const data = await adminAiUsageApi.getSummary(days.value)
    summary.value = data
  } catch (err) {
    handleErr(err)
  }
}

async function loadRecent() {
  try {
    const data = await adminAiUsageApi.getRecent(recentLimit.value)
    recentRecords.value = data || []
  } catch (err) {
    handleErr(err)
  }
}

async function loadAll() {
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  await Promise.all([loadSummary(), loadRecent()])
  loading.value = false
}

function handleErr(err) {
  const status = err?.response?.status
  if (status === 403) {
    forbidden.value = true
  } else {
    loadError.value = err?.response?.data?.message || err?.message || '加载失败，请稍后重试'
  }
}

onMounted(loadAll)
</script>

<style scoped>
.admin-ai-usage-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 20px 48px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.page-title .back-btn {
  width: 32px;
  height: 32px;
  padding: 0;
}

.page-title {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-subtitle {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.error-alert {
  margin-bottom: 16px;
}

/* 顶部统计卡片 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.stat-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 14px 16px;
}

.stat-label {
  color: var(--text-secondary);
  font-size: 13px;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-primary);
}

.stat-success .stat-value { color: #22C55E; }
.stat-danger .stat-value { color: #EF4444; }
.stat-info .stat-value { color: #3B82F6; }

/* 分组统计 */
.bucket-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.bucket-card {
  border-radius: 8px;
}

.bucket-title {
  font-weight: 600;
  font-size: 14px;
}

.recent-card {
  border-radius: 8px;
}

.recent-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.cell-error {
  color: var(--text-secondary);
  font-size: 12px;
  word-break: break-all;
}

:deep(.el-card__header) {
  padding: 12px 16px;
}
</style>
