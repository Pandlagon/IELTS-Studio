<template>
  <div class="admin-audit-logs-page" v-loading="loading">
    <!-- 页头 -->
    <div class="page-header">
      <div class="header-text">
        <h2 class="page-title">
          <el-button :icon="ArrowLeft" circle @click="router.back()" class="back-btn" />
          <i class="fa-solid fa-clipboard-list"></i>
          审计日志
        </h2>
        <p class="page-subtitle">管理端高风险写操作审计记录（创建/修改/禁用/启用/重置密码/quota/权限）</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" @click="loadLogs" :loading="loading">刷新</el-button>
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
      title="无权限访问：本页面需要 ADMIN_AUDIT_LOG_VIEW 权限。"
    />

    <template v-if="!forbidden">
      <!-- 顶部筛选 -->
      <div class="toolbar">
        <el-input
          v-model="filters.actorUserId"
          placeholder="操作者 ID"
          clearable
          style="width: 130px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-input
          v-model="filters.targetUserId"
          placeholder="目标用户 ID"
          clearable
          style="width: 140px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-select v-model="filters.action" placeholder="Action" clearable style="width: 200px" @change="onSearch">
          <el-option v-for="a in actionOptions" :key="a" :value="a" :label="a" />
        </el-select>
        <el-select v-model="filters.resourceType" placeholder="Resource" clearable style="width: 130px" @change="onSearch">
          <el-option value="USER" label="USER" />
          <el-option value="QUOTA" label="QUOTA" />
          <el-option value="PERMISSION" label="PERMISSION" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 120px" @change="onSearch">
          <el-option value="SUCCESS" label="SUCCESS" />
          <el-option value="FAILED" label="FAILED" />
        </el-select>
        <el-date-picker
          v-model="filters.dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 380px"
          @change="onSearch"
        />
        <el-button type="primary" @click="onSearch">搜索</el-button>
      </div>

      <!-- 审计日志表格 -->
      <el-card class="table-card" shadow="never">
        <el-table :data="logs" style="width: 100%" :max-height="560">
          <el-table-column label="时间" width="160">
            <template #default="{ row }">
              <span>{{ formatDateTime(row.createdAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作者 ID" prop="actorUserId" width="100" align="center" />
          <el-table-column label="操作者用户名" prop="actorUsername" min-width="130" show-overflow-tooltip />
          <el-table-column label="Action" prop="action" width="180" />
          <el-table-column label="Resource" prop="resourceType" width="110" align="center" />
          <el-table-column label="资源 ID" prop="resourceId" width="90" align="center" />
          <el-table-column label="目标用户 ID" prop="targetUserId" width="120" align="center" />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.status === 'SUCCESS'" size="small" type="success">SUCCESS</el-tag>
              <el-tag v-else size="small" type="danger">FAILED</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Summary" prop="summary" min-width="280" show-overflow-tooltip />
          <el-table-column label="IP" prop="ipAddress" width="140" show-overflow-tooltip />
          <el-table-column label="User-Agent" prop="userAgent" width="200" show-overflow-tooltip />
        </el-table>

        <!-- 分页 -->
        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.pageSize"
            :page-sizes="[20, 50, 100]"
            :total="pagination.total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="onSizeChange"
            @current-change="loadLogs"
          />
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Refresh, ArrowLeft } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { adminAuditLogsApi } from '@/api/adminAuditLogs'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const logs = ref([])

/** 与后端 AdminOperationAction 枚举对齐 */
const actionOptions = [
  'USER_CREATE',
  'USER_UPDATE_ROLE',
  'USER_DISABLE',
  'USER_ENABLE',
  'USER_RESET_PASSWORD',
  'QUOTA_SET_TOTAL',
  'QUOTA_GRANT',
  'QUOTA_RESET_USED',
  'PERMISSION_UPDATE',
]

const filters = reactive({
  actorUserId: '',
  targetUserId: '',
  action: '',
  resourceType: '',
  status: '',
  dateRange: null,
})

const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0,
})

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

/** 把字符串数字解析为 number，空串返回 undefined（不传给后端） */
function toNumOrUndef(s) {
  if (s === '' || s === null || s === undefined) return undefined
  const n = Number(s)
  return Number.isNaN(n) ? undefined : n
}

async function loadLogs() {
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const params = {
      page: pagination.page,
      pageSize: pagination.pageSize,
      actorUserId: toNumOrUndef(filters.actorUserId),
      targetUserId: toNumOrUndef(filters.targetUserId),
      action: filters.action || undefined,
      resourceType: filters.resourceType || undefined,
      status: filters.status || undefined,
      dateFrom: filters.dateRange?.[0] || undefined,
      dateTo: filters.dateRange?.[1] || undefined,
    }
    const data = await adminAuditLogsApi.listLogs(params)
    logs.value = data?.records || []
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
  loadLogs()
}

function onSizeChange(size) {
  pagination.pageSize = size
  pagination.page = 1
  loadLogs()
}

onMounted(async () => {
  // 进入页面时先确保 ADMIN 权限已加载（用于 403 之外的本地提示）
  if (authStore.isLoggedIn && authStore.isAdmin && !authStore.adminPermissionsLoaded) {
    await authStore.loadAdminPermissions()
  }
  loadLogs()
})
</script>

<style scoped>
.admin-audit-logs-page {
  max-width: 1400px;
  margin: 0 auto;
  padding: 24px 16px 48px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.page-title .back-btn {
  width: 32px;
  height: 32px;
  padding: 0;
}

.page-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-subtitle {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.error-alert {
  margin-bottom: 16px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
  align-items: center;
}

.table-card {
  border-radius: var(--radius-lg, 12px);
  border: 1px solid var(--border-light);
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
