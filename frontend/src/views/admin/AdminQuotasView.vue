<template>
  <div class="admin-quotas-page" v-loading="loading">
    <!-- 页头 -->
    <div class="page-header">
      <div class="header-text">
        <h2 class="page-title">
          <el-button :icon="ArrowLeft" circle @click="router.back()" class="back-btn" />
          <i class="fa-solid fa-coins"></i>
          额度管理
        </h2>
        <p class="page-subtitle">管理端用户当前周期 AI 额度：设置总额度、补充额度、重置已用</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" @click="loadQuotas" :loading="loading">刷新</el-button>
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

    <template v-if="!forbidden">
      <!-- 顶部工具栏 -->
      <div class="toolbar">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索用户名 / 邮箱"
          clearable
          style="width: 240px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-select v-model="filters.role" placeholder="角色" clearable style="width: 120px" @change="onSearch">
          <el-option value="" label="全部角色" />
          <el-option value="USER" label="USER" />
          <el-option value="ADMIN" label="ADMIN" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 120px" @change="onSearch">
          <el-option value="" label="全部状态" />
          <el-option value="ACTIVE" label="启用" />
          <el-option value="DISABLED" label="禁用" />
        </el-select>
        <el-button type="primary" @click="onSearch">搜索</el-button>
      </div>

      <!-- quota 表格 -->
      <el-card class="table-card" shadow="never">
        <el-table :data="quotas" style="width: 100%" :max-height="560">
          <el-table-column label="用户ID" prop="userId" width="80" align="center" />
          <el-table-column label="用户名" prop="username" min-width="120" />
          <el-table-column label="邮箱" prop="email" min-width="200" show-overflow-tooltip />
          <el-table-column label="角色" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.role === 'ADMIN'" size="small" type="danger">ADMIN</el-tag>
              <el-tag v-else size="small" type="info">USER</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.deleted === 0" size="small" type="success">启用</el-tag>
              <el-tag v-else size="small" type="warning">禁用</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="周期" min-width="180">
            <template #default="{ row }">
              <span class="period-text">{{ formatDateTime(row.periodStart) }} ~ {{ formatDateTime(row.periodEnd) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="总额度" prop="creditsTotal" width="90" align="center" />
          <el-table-column label="已用" prop="creditsUsed" width="80" align="center" />
          <el-table-column label="剩余" prop="creditsRemaining" width="80" align="center" />
          <el-table-column label="Quota 行" width="110" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.quotaRowExists" size="small" type="success">实际行</el-tag>
              <el-tag v-else size="small" type="info" effect="plain">默认视图</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="openSetTotalDialog(row)">设置总额度</el-button>
              <el-button size="small" link type="success" @click="openGrantDialog(row)">补充额度</el-button>
              <el-button size="small" link type="warning" @click="confirmResetUsed(row)">重置已用</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="pagination.total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="onSizeChange"
            @current-change="loadQuotas"
          />
        </div>
      </el-card>
    </template>

    <!-- 设置总额度 Dialog -->
    <el-dialog v-model="setTotalDialog.visible" title="设置总额度" width="420px" :close-on-click-modal="false">
      <div class="dialog-body">
        <p class="dialog-tip">
          用户：<b>{{ setTotalDialog.user?.username }}</b>
          （当前已用 {{ setTotalDialog.user?.creditsUsed }}）
        </p>
        <el-form label-position="top">
          <el-form-item label="新总额度（0~100000）">
            <el-input-number v-model="setTotalDialog.creditsTotal" :min="0" :max="100000" :step="10" style="width: 100%" />
          </el-form-item>
        </el-form>
        <p class="dialog-hint">不能小于当前已用额度 {{ setTotalDialog.user?.creditsUsed }}；不修改已用额度。</p>
      </div>
      <template #footer>
        <el-button @click="setTotalDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="setTotalDialog.submitting" @click="submitSetTotal">确认设置</el-button>
      </template>
    </el-dialog>

    <!-- 补充额度 Dialog -->
    <el-dialog v-model="grantDialog.visible" title="补充额度" width="420px" :close-on-click-modal="false">
      <div class="dialog-body">
        <p class="dialog-tip">
          用户：<b>{{ grantDialog.user?.username }}</b>
          （当前总额度 {{ grantDialog.user?.creditsTotal }}）
        </p>
        <el-form label-position="top">
          <el-form-item label="补充 credits（1~100000）">
            <el-input-number v-model="grantDialog.credits" :min="1" :max="100000" :step="10" style="width: 100%" />
          </el-form-item>
        </el-form>
        <p class="dialog-hint">将累加到当前总额度，不修改已用额度。无 quota 行时按 30 + credits 创建。</p>
      </div>
      <template #footer>
        <el-button @click="grantDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="grantDialog.submitting" @click="submitGrant">确认补充</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Refresh, ArrowLeft } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminQuotasApi } from '@/api/adminQuotas'

const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const quotas = ref([])

const filters = reactive({
  keyword: '',
  role: '',
  status: '',
})

const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0,
})

const setTotalDialog = reactive({
  visible: false,
  user: null,
  creditsTotal: 30,
  submitting: false,
})

const grantDialog = reactive({
  visible: false,
  user: null,
  credits: 10,
  submitting: false,
})

async function loadQuotas() {
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const data = await adminQuotasApi.listQuotas({
      page: pagination.page,
      pageSize: pagination.pageSize,
      keyword: filters.keyword || undefined,
      role: filters.role || undefined,
      status: filters.status || undefined,
    })
    quotas.value = data?.records || []
    pagination.total = data?.total || 0
  } catch (err) {
    const status = err?.response?.status
    if (status === 403) {
      forbidden.value = true
    } else {
      loadError.value = err?.response?.data?.message || err?.message || '加载失败，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pagination.page = 1
  loadQuotas()
}

function onSizeChange(size) {
  pagination.pageSize = size
  pagination.page = 1
  loadQuotas()
}

// ─── 设置总额度 ─────────────────────────────────────────────────────────────

function openSetTotalDialog(row) {
  setTotalDialog.user = row
  setTotalDialog.creditsTotal = row.creditsTotal ?? 30
  setTotalDialog.visible = true
}

async function submitSetTotal() {
  const user = setTotalDialog.user
  if (!user) return
  const used = user.creditsUsed ?? 0
  if (setTotalDialog.creditsTotal < used) {
    ElMessage.warning(`总额度不能小于当前已用额度 ${used}`)
    return
  }
  setTotalDialog.submitting = true
  try {
    const updated = await adminQuotasApi.setTotal(user.userId, setTotalDialog.creditsTotal)
    ElMessage.success('总额度已设置')
    replaceRow(updated)
    setTotalDialog.visible = false
  } catch (err) {
    ElMessage.error(err?.response?.data?.message || err?.message || '设置失败')
  } finally {
    setTotalDialog.submitting = false
  }
}

// ─── 补充额度 ───────────────────────────────────────────────────────────────

function openGrantDialog(row) {
  grantDialog.user = row
  grantDialog.credits = 10
  grantDialog.visible = true
}

async function submitGrant() {
  const user = grantDialog.user
  if (!user) return
  if (!grantDialog.credits || grantDialog.credits < 1) {
    ElMessage.warning('补充额度必须 ≥ 1')
    return
  }
  grantDialog.submitting = true
  try {
    const updated = await adminQuotasApi.grantCredits(user.userId, grantDialog.credits)
    ElMessage.success('额度已补充')
    replaceRow(updated)
    grantDialog.visible = false
  } catch (err) {
    ElMessage.error(err?.response?.data?.message || err?.message || '补充失败')
  } finally {
    grantDialog.submitting = false
  }
}

// ─── 重置已用 ───────────────────────────────────────────────────────────────

function confirmResetUsed(row) {
  ElMessageBox.confirm(`确认重置用户 ${row.username} 的当前周期已用额度为 0？`, '重置已用', {
    type: 'warning',
    confirmButtonText: '确认重置',
    cancelButtonText: '取消',
  })
    .then(async () => {
      try {
        const updated = await adminQuotasApi.resetUsed(row.userId)
        ElMessage.success('已重置')
        replaceRow(updated)
      } catch (err) {
        ElMessage.error(err?.response?.data?.message || err?.message || '重置失败')
      }
    })
    .catch(() => {})
}

// ─── 辅助 ───────────────────────────────────────────────────────────────────

function replaceRow(updated) {
  if (!updated) return
  const idx = quotas.value.findIndex(q => q.userId === updated.userId)
  if (idx >= 0) quotas.value[idx] = updated
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

onMounted(loadQuotas)
</script>

<style scoped>
.admin-quotas-page {
  max-width: 1280px;
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

.toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.table-card {
  border-radius: 8px;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.dialog-body {
  padding: 4px 0;
}

.dialog-tip {
  margin: 0 0 12px;
  font-size: 14px;
}

.period-text {
  font-size: 12px;
  color: var(--text-secondary);
}

.dialog-hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--text-secondary);
}

:deep(.el-card__body) {
  padding: 12px;
}
</style>
