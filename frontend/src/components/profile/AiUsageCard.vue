<template>
  <div class="card ai-usage-card" v-loading="loading">
    <!-- Header -->
    <div class="ai-header">
      <div class="ai-header-text">
        <h3 class="ai-title">AI 额度</h3>
        <p class="ai-subtitle">当前周期 credits 与最近使用记录</p>
      </div>
      <div class="header-actions">
        <span class="badge badge-green" v-if="usage.keyMode === 'BUILTIN'">内置模式</span>
        <span class="badge badge-blue" v-else>自填 Key</span>
        <el-button text size="small" :icon="Refresh" @click="loadData" :loading="loading">刷新</el-button>
      </div>
    </div>

    <!-- 错误提示 -->
    <el-alert
      v-if="loadError"
      type="error"
      :closable="false"
      show-icon
      class="mode-alert"
      title="AI 额度信息加载失败，请稍后重试"
    />

    <template v-else>
      <!-- BUILTIN 模式：展示本周额度 -->
      <template v-if="usage.builtinQuotaEnabled">
        <div class="quota-grid">
          <div class="quota-stat quota-stat-main">
            <div class="quota-label">本周 AI 额度</div>
            <div class="quota-value">
              <span class="quota-used">{{ usage.creditsUsed }}</span>
              <span class="quota-divider">/</span>
              <span class="quota-total">{{ usage.creditsTotal }}</span>
            </div>
            <div class="quota-remaining">
              剩余 <strong>{{ usage.creditsRemaining }}</strong> credits
            </div>
          </div>

          <div class="quota-stat">
            <el-progress
              :percentage="usedPercentage"
              :stroke-width="10"
              :show-text="false"
              :color="progressColor"
            />
            <div class="quota-period">
              <span class="period-label">周期</span>
              <span class="period-range">{{ formatDateTime(usage.periodStart) }} ~ {{ formatDateTime(usage.periodEnd) }}</span>
            </div>
          </div>
        </div>
      </template>

      <!-- USER 模式：说明不消耗站点额度 -->
      <template v-else>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="mode-alert"
        >
          <template #title>
            当前为自填 API Key 模式，不消耗站点内置额度。仍会受到基础请求频率限制。
          </template>
        </el-alert>

        <!-- 站点额度参考（视觉弱化） -->
        <div class="quota-ref">
          <span class="ref-label">站点额度参考：</span>
          <span class="ref-value">{{ usage.creditsUsed }} / {{ usage.creditsTotal }}（不会消耗）</span>
        </div>
      </template>

      <!-- 最近使用记录 -->
      <div class="records-section">
        <div class="section-hd">
          <h4 class="records-title">最近使用记录</h4>
          <span class="records-count badge badge-gray" v-if="usage.recentRecords?.length">
            {{ usage.recentRecords.length }} 条
          </span>
        </div>

        <el-table
          v-if="usage.recentRecords?.length"
          :data="usage.recentRecords"
          size="small"
          class="records-table"
          :max-height="320"
        >
          <el-table-column label="时间" width="160">
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.createdAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="功能" min-width="120">
            <template #default="{ row }">
              <span>{{ featureLabel(row.feature) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="模式" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.keyMode === 'BUILTIN'" size="small" type="success">内置</el-tag>
              <el-tag v-else size="small" type="warning">自填</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="cost" width="70" align="center">
            <template #default="{ row }">
              <span class="cell-cost">{{ row.cost }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.status === 'SUCCESS'" size="small" type="success">成功</el-tag>
              <el-tag v-else-if="row.status === 'FAILED'" size="small" type="danger">失败</el-tag>
              <el-tag v-else-if="row.status === 'REJECTED'" size="small" type="info">已拒绝</el-tag>
              <el-tag v-else size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div v-else class="empty-records">
          <i class="fa-solid fa-inbox"></i>
          <span>暂无使用记录</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { aiUsageApi } from '@/api/aiUsage'

/**
 * 用户中心 - AI 额度展示卡片。
 *
 * 只读视图：
 *  - 调用 GET /users/me/ai-usage 获取当前周期 credits 与最近 20 条 records
 *  - 查询接口不创建 quota 行、不扣费
 *  - 不写入本地存储
 *
 * 安全：不打印 payload；errorMessage 已由后端脱敏，前端不再额外处理。
 */
const loading = ref(false)
const loadError = ref(false)

const usage = ref({
  keyMode: 'BUILTIN',
  periodStart: null,
  periodEnd: null,
  creditsTotal: 0,
  creditsUsed: 0,
  creditsRemaining: 0,
  builtinQuotaEnabled: true,
  recentRecords: [],
})

onMounted(loadData)

async function loadData() {
  loading.value = true
  loadError.value = false
  try {
    const data = await aiUsageApi.getAiUsage()
    if (data) usage.value = { ...usage.value, ...data }
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const usedPercentage = computed(() => {
  const total = usage.value.creditsTotal || 0
  const used = usage.value.creditsUsed || 0
  if (total <= 0) return 0
  return Math.min(100, Math.round((used / total) * 100))
})

const progressColor = computed(() => {
  const pct = usedPercentage.value
  if (pct >= 90) return '#EF4444'
  if (pct >= 70) return '#F59E0B'
  return 'var(--color-primary)'
})

/** feature 中文映射（与后端 AiFeature 枚举对齐） */
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

function featureLabel(feature) {
  if (!feature) return ''
  return FEATURE_LABELS[feature] || feature
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

defineExpose({ refresh: loadData })
</script>

<style scoped>
.ai-usage-card {
  margin-top: 14px;
}

.ai-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.ai-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.ai-subtitle {
  font-size: 12px;
  color: var(--text-muted);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mode-alert {
  margin: 4px 0 18px;
}

.quota-grid {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 24px;
  padding: 18px 20px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  background: var(--bg-primary);
  margin-bottom: 20px;
  align-items: center;
}

.quota-stat-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.quota-label {
  font-size: 12px;
  color: var(--text-muted);
}

.quota-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
  line-height: 1;
}

.quota-used {
  font-size: 36px;
  font-weight: 800;
  color: var(--color-primary);
}

.quota-divider {
  font-size: 22px;
  color: var(--text-muted);
}

.quota-total {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-secondary);
}

.quota-remaining {
  font-size: 13px;
  color: var(--text-secondary);
}

.quota-remaining strong {
  color: var(--color-primary);
  font-weight: 700;
}

.quota-period {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 8px;
}

.period-label {
  font-size: 12px;
  color: var(--text-muted);
}

.period-range {
  font-size: 12px;
  color: var(--text-secondary);
  font-family: var(--font-mono, monospace);
}

.quota-ref {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
  padding: 10px 14px;
  background: var(--bg-primary);
  border-radius: var(--radius-sm);
  margin-bottom: 20px;
}

.ref-label {
  font-weight: 600;
}

/* Records */
.records-section {
  margin-top: 4px;
}

.section-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.records-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}

.records-count {
  font-size: 11px;
}

.records-table {
  width: 100%;
}

.cell-time {
  font-size: 12px;
  color: var(--text-muted);
  font-family: var(--font-mono, monospace);
}

.cell-cost {
  font-weight: 600;
  color: var(--text-primary);
}

.empty-records {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 0;
  color: var(--text-muted);
  font-size: 13px;
}

.empty-records i {
  font-size: 28px;
  opacity: 0.4;
}

.badge-gray {
  background: #F1F5F9;
  color: #475569;
}

/* Element Plus table 暗色模式微调 */
:deep(.el-table) {
  font-size: 13px;
}

:deep(.el-table th.el-table__cell) {
  background: var(--bg-primary);
  font-weight: 600;
  color: var(--text-secondary);
}

@media (max-width: 768px) {
  .quota-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  .quota-stat-main {
    align-items: flex-start;
  }
}

/* 暗色模式：quota-grid 浅色背景改深色 */
html.dark .quota-grid {
  background: var(--bg-card);
  border-color: var(--border-color);
}

html.dark .quota-ref {
  background: var(--bg-card);
}

html.dark .badge-gray {
  background: rgba(148, 163, 184, 0.18);
  color: #CBD5E1;
}
</style>
