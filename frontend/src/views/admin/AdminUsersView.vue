<template>
  <div class="admin-users-page" v-loading="loading">
    <!-- 页头 -->
    <div class="page-header">
      <div class="header-text">
        <h2 class="page-title">
          <i class="fa-solid fa-users-gear"></i>
          用户管理
        </h2>
        <p class="page-subtitle">管理端用户列表、角色修改、禁用/启用、重置密码</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增用户</el-button>
        <el-button :icon="Refresh" @click="loadUsers" :loading="loading">刷新</el-button>
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

      <!-- 用户表格 -->
      <el-card class="table-card" shadow="never">
        <el-table :data="users" style="width: 100%" :max-height="560">
          <el-table-column label="ID" prop="id" width="70" align="center" />
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
          <el-table-column label="创建时间" width="160">
            <template #default="{ row }">
              <span>{{ formatDateTime(row.createdAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="160">
            <template #default="{ row }">
              <span>{{ formatDateTime(row.updatedAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="openRoleDialog(row)">修改角色</el-button>
              <el-button
                v-if="row.deleted === 0"
                size="small"
                link
                type="warning"
                @click="confirmDisable(row)"
              >禁用</el-button>
              <el-button
                v-else
                size="small"
                link
                type="success"
                @click="confirmEnable(row)"
              >启用</el-button>
              <el-button size="small" link type="danger" @click="openResetPasswordDialog(row)">重置密码</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="pagination.total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="onSizeChange"
            @current-change="loadUsers"
          />
        </div>
      </el-card>
    </template>

    <!-- 新增用户 Dialog -->
    <el-dialog v-model="createDialog.visible" title="新增用户" width="460px" :close-on-click-modal="false">
      <div class="dialog-body">
        <el-form label-position="top">
          <el-form-item label="用户名">
            <el-input v-model="createDialog.username" placeholder="3~32 位" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="createDialog.email" placeholder="user@example.com" />
          </el-form-item>
          <el-form-item label="初始密码">
            <el-input
              v-model="createDialog.password"
              type="password"
              show-password
              placeholder="至少 8 位"
              autocomplete="new-password"
            />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input
              v-model="createDialog.confirmPassword"
              type="password"
              show-password
              placeholder="再次输入初始密码"
              autocomplete="new-password"
            />
          </el-form-item>
          <el-form-item label="角色">
            <el-radio-group v-model="createDialog.role">
              <el-radio value="USER">USER</el-radio>
              <el-radio value="ADMIN">ADMIN</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <p class="dialog-hint">密码将使用 BCrypt 加密存储，不会以明文返回。</p>
      </div>
      <template #footer>
        <el-button @click="closeCreateDialog">取消</el-button>
        <el-button type="primary" :loading="createDialog.submitting" @click="submitCreate">确认创建</el-button>
      </template>
    </el-dialog>

    <!-- 修改角色 Dialog -->
    <el-dialog v-model="roleDialog.visible" title="修改角色" width="380px" :close-on-click-modal="false">
      <div class="dialog-body">
        <p class="dialog-tip">用户：<b>{{ roleDialog.user?.username }}</b>（ID: {{ roleDialog.user?.id }}）</p>
        <el-radio-group v-model="roleDialog.newRole">
          <el-radio value="USER">USER</el-radio>
          <el-radio value="ADMIN">ADMIN</el-radio>
        </el-radio-group>
        <p class="dialog-hint">不能降级自己或最后一个管理员，后端会再次校验。</p>
      </div>
      <template #footer>
        <el-button @click="roleDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="roleDialog.submitting" @click="submitRole">确认修改</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 Dialog -->
    <el-dialog v-model="pwdDialog.visible" title="重置密码" width="420px" :close-on-click-modal="false">
      <div class="dialog-body">
        <p class="dialog-tip">用户：<b>{{ pwdDialog.user?.username }}</b>（ID: {{ pwdDialog.user?.id }}）</p>
        <el-form label-position="top">
          <el-form-item label="新密码">
            <el-input
              v-model="pwdDialog.newPassword"
              type="password"
              show-password
              placeholder="至少 8 位"
              autocomplete="new-password"
            />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input
              v-model="pwdDialog.confirmPassword"
              type="password"
              show-password
              placeholder="再次输入新密码"
              autocomplete="new-password"
            />
          </el-form-item>
        </el-form>
        <p class="dialog-hint">密码将使用 BCrypt 加密存储，不会以明文返回。</p>
      </div>
      <template #footer>
        <el-button @click="closePwdDialog">取消</el-button>
        <el-button type="danger" :loading="pwdDialog.submitting" @click="submitResetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Refresh, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminUsersApi } from '@/api/adminUsers'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const users = ref([])

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

const roleDialog = reactive({
  visible: false,
  user: null,
  newRole: 'USER',
  submitting: false,
})

const createDialog = reactive({
  visible: false,
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  role: 'USER',
  submitting: false,
})

const pwdDialog = reactive({
  visible: false,
  user: null,
  newPassword: '',
  confirmPassword: '',
  submitting: false,
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

async function loadUsers() {
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const data = await adminUsersApi.listUsers({
      page: pagination.page,
      pageSize: pagination.pageSize,
      keyword: filters.keyword || undefined,
      role: filters.role || undefined,
      status: filters.status || undefined,
    })
    users.value = data?.records || []
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
  loadUsers()
}

function onSizeChange(size) {
  pagination.pageSize = size
  pagination.page = 1
  loadUsers()
}

// ─── 新增用户 ───────────────────────────────────────────────────────────────

function openCreateDialog() {
  createDialog.username = ''
  createDialog.email = ''
  createDialog.password = ''
  createDialog.confirmPassword = ''
  createDialog.role = 'USER'
  createDialog.visible = true
}

function closeCreateDialog() {
  createDialog.visible = false
  createDialog.username = ''
  createDialog.email = ''
  createDialog.password = ''
  createDialog.confirmPassword = ''
  createDialog.role = 'USER'
}

async function submitCreate() {
  if (!createDialog.username || createDialog.username.trim().length < 3) {
    ElMessage.warning('用户名至少 3 位')
    return
  }
  if (!createDialog.email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(createDialog.email)) {
    ElMessage.warning('邮箱格式不正确')
    return
  }
  if (!createDialog.password || createDialog.password.length < 8) {
    ElMessage.warning('初始密码长度至少 8 位')
    return
  }
  if (createDialog.password !== createDialog.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  createDialog.submitting = true
  try {
    const created = await adminUsersApi.createUser({
      username: createDialog.username.trim(),
      email: createDialog.email.trim(),
      password: createDialog.password,
      role: createDialog.role,
    })
    ElMessage.success('用户已创建')
    closeCreateDialog()
    // 跳到第一页并刷新，确保新用户可见
    pagination.page = 1
    await loadUsers()
    // created 不含 password，无需额外处理
    void created
  } catch (err) {
    ElMessage.error(err?.response?.data?.message || err?.message || '创建失败')
  } finally {
    createDialog.submitting = false
  }
}

// ─── 修改角色 ───────────────────────────────────────────────────────────────

function openRoleDialog(user) {
  roleDialog.user = user
  roleDialog.newRole = user.role || 'USER'
  roleDialog.visible = true
}

async function submitRole() {
  if (!roleDialog.user) return
  roleDialog.submitting = true
  try {
    const updated = await adminUsersApi.updateRole(roleDialog.user.id, roleDialog.newRole)
    ElMessage.success('角色已更新')
    // 局部刷新该行
    const idx = users.value.findIndex(u => u.id === roleDialog.user.id)
    if (idx >= 0) users.value[idx] = updated
    roleDialog.visible = false
    // 如果改的是自己，刷新本地 authStore 的 role（避免前端状态过期）
    if (roleDialog.user.id === authStore.user?.id) {
      authStore.updateUser({ role: roleDialog.newRole })
    }
  } catch (err) {
    ElMessage.error(err?.response?.data?.message || err?.message || '修改失败')
  } finally {
    roleDialog.submitting = false
  }
}

// ─── 禁用 / 启用 ────────────────────────────────────────────────────────────

function confirmDisable(user) {
  ElMessageBox.confirm(
    `确认禁用用户「${user.username}」？禁用后该用户将无法登录。`,
    '确认禁用',
    { type: 'warning', confirmButtonText: '确认禁用', cancelButtonText: '取消' }
  )
    .then(async () => {
      try {
        const updated = await adminUsersApi.disableUser(user.id)
        ElMessage.success('已禁用')
        const idx = users.value.findIndex(u => u.id === user.id)
        if (idx >= 0) users.value[idx] = updated
      } catch (err) {
        ElMessage.error(err?.response?.data?.message || err?.message || '禁用失败')
      }
    })
    .catch(() => {})
}

function confirmEnable(user) {
  ElMessageBox.confirm(
    `确认启用用户「${user.username}」？`,
    '确认启用',
    { type: 'info', confirmButtonText: '确认启用', cancelButtonText: '取消' }
  )
    .then(async () => {
      try {
        const updated = await adminUsersApi.enableUser(user.id)
        ElMessage.success('已启用')
        const idx = users.value.findIndex(u => u.id === user.id)
        if (idx >= 0) users.value[idx] = updated
      } catch (err) {
        ElMessage.error(err?.response?.data?.message || err?.message || '启用失败')
      }
    })
    .catch(() => {})
}

// ─── 重置密码 ───────────────────────────────────────────────────────────────

function openResetPasswordDialog(user) {
  pwdDialog.user = user
  pwdDialog.newPassword = ''
  pwdDialog.confirmPassword = ''
  pwdDialog.visible = true
}

function closePwdDialog() {
  pwdDialog.visible = false
  // 清空输入，不保存密码
  pwdDialog.newPassword = ''
  pwdDialog.confirmPassword = ''
  pwdDialog.user = null
}

async function submitResetPassword() {
  if (!pwdDialog.user) return
  if (!pwdDialog.newPassword || pwdDialog.newPassword.length < 8) {
    ElMessage.warning('新密码长度至少 8 位')
    return
  }
  if (pwdDialog.newPassword !== pwdDialog.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  pwdDialog.submitting = true
  try {
    await adminUsersApi.resetPassword(pwdDialog.user.id, pwdDialog.newPassword)
    ElMessage.success('密码已重置')
    closePwdDialog()
  } catch (err) {
    ElMessage.error(err?.response?.data?.message || err?.message || '重置失败')
  } finally {
    pwdDialog.submitting = false
  }
}

onMounted(loadUsers)
</script>

<style scoped>
.admin-users-page {
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
  color: var(--color-text-secondary, #888);
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

.dialog-hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--color-text-secondary, #888);
}

:deep(.el-card__body) {
  padding: 12px;
}
</style>
