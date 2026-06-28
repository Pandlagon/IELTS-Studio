<template>
  <div class="card ai-settings-card" v-loading="loading">
    <!-- Header -->
    <div class="ai-header">
      <div class="ai-header-text">
        <h3 class="ai-title">AI 设置</h3>
        <p class="ai-subtitle">配置写作评分、AI 助手、翻译、试卷解析等功能使用的 AI Provider</p>
      </div>
      <span class="badge badge-green" v-if="form.keyMode === 'BUILTIN'">内置模式</span>
      <span class="badge badge-blue" v-else>自填 Key</span>
    </div>

    <el-form label-position="top" class="ai-form" @submit.prevent>
      <!-- 使用模式 -->
      <el-form-item label="使用模式">
        <el-radio-group v-model="form.keyMode">
          <el-radio label="BUILTIN">使用站点内置额度</el-radio>
          <el-radio label="USER">使用自己的 API Key</el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- BUILTIN 说明 -->
      <el-alert
        v-if="form.keyMode === 'BUILTIN'"
        type="info"
        :closable="false"
        show-icon
        class="mode-alert"
      >
        <template #title>
          当前使用站点内置 AI 配置，无需填写 API Key。后续会按周发放 credits 限制用量。
        </template>
      </el-alert>

      <!-- USER 模式配置 -->
      <template v-if="form.keyMode === 'USER'">
        <!-- Text Provider -->
        <div class="provider-section">
          <div class="provider-section-hd">
            <h4 class="provider-section-title">Text Provider</h4>
            <span class="provider-section-tag">文本任务</span>
          </div>
          <p class="provider-section-desc">用于写作评分、AI 助手、翻译、普通试卷解析、完形填空等</p>

          <el-form-item label="Provider">
            <el-select
              v-model="form.text.provider"
              style="width: 100%"
              placeholder="选择文本 Provider"
              @change="onProviderChange('text')"
            >
              <el-option
                v-for="p in providers.text"
                :key="p.provider"
                :label="p.displayName"
                :value="p.provider"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="Base URL">
            <el-input v-model="form.text.baseUrl" placeholder="https://api.example.com" />
            <div class="field-hint" v-if="!isCustom('text') && form.text.baseUrl">
              预设 Provider 通常无需修改 Base URL
            </div>
            <div class="field-hint custom-hint" v-else-if="isCustom('text')">
              请填入兼容 OpenAI 的 Base URL（不含尾部 /chat/completions）
            </div>
          </el-form-item>

          <el-form-item label="Model">
            <el-input v-model="form.text.model" placeholder="例如 deepseek-chat" />
          </el-form-item>

          <el-form-item label="API Key">
            <div class="key-row">
              <div class="saved-key">
                <span class="saved-key-label">已保存：</span>
                <el-tag v-if="savedKeys.text.hasApiKey" type="success" size="small">
                  {{ savedKeys.text.maskedApiKey }}
                </el-tag>
                <el-tag v-else type="info" size="small">未保存 API Key</el-tag>
              </div>
              <el-checkbox v-model="form.text.clearApiKey" @change="onClearToggle('text')">
                清空已保存 Key
              </el-checkbox>
            </div>
            <el-input
              v-model="form.text.apiKey"
              show-password
              placeholder="留空表示不修改已保存 Key"
              :disabled="form.text.clearApiKey"
              autocomplete="off"
            />
          </el-form-item>
        </div>

        <!-- Vision Provider -->
        <div class="provider-section">
          <div class="provider-section-hd">
            <h4 class="provider-section-title">Vision Provider</h4>
            <span class="provider-section-tag">多模态任务</span>
          </div>
          <p class="provider-section-desc">用于 PDF / 图片精准解析、扫描版试卷、写作 Task 1 图表识别等</p>

          <el-form-item label="Provider">
            <el-select
              v-model="form.vision.provider"
              style="width: 100%"
              placeholder="选择视觉 Provider"
              @change="onProviderChange('vision')"
            >
              <el-option
                v-for="p in providers.vision"
                :key="p.provider"
                :label="p.displayName"
                :value="p.provider"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="Base URL">
            <el-input v-model="form.vision.baseUrl" placeholder="https://api.example.com" />
            <div class="field-hint" v-if="!isCustom('vision') && form.vision.baseUrl">
              预设 Provider 通常无需修改 Base URL
            </div>
            <div class="field-hint custom-hint" v-else-if="isCustom('vision')">
              请填入支持 vision 的 Base URL（不含尾部 /chat/completions）
            </div>
          </el-form-item>

          <el-form-item label="Model">
            <el-input v-model="form.vision.model" placeholder="例如 qwen-vl-max" />
          </el-form-item>

          <el-form-item label="API Key">
            <div class="key-row">
              <div class="saved-key">
                <span class="saved-key-label">已保存：</span>
                <el-tag v-if="savedKeys.vision.hasApiKey" type="success" size="small">
                  {{ savedKeys.vision.maskedApiKey }}
                </el-tag>
                <el-tag v-else type="info" size="small">未保存 API Key</el-tag>
              </div>
              <el-checkbox v-model="form.vision.clearApiKey" @change="onClearToggle('vision')">
                清空已保存 Key
              </el-checkbox>
            </div>
            <el-input
              v-model="form.vision.apiKey"
              show-password
              placeholder="留空表示不修改已保存 Key"
              :disabled="form.vision.clearApiKey"
              autocomplete="off"
            />
          </el-form-item>
        </div>

        <el-alert
          v-if="isCustom('text') || isCustom('vision')"
          type="warning"
          :closable="false"
          show-icon
          class="mode-alert"
        >
          <template #title>
            请确认自定义服务兼容 OpenAI /chat/completions 协议；保存时 Base URL 需以 http:// 或 https:// 开头。
          </template>
        </el-alert>
      </template>

      <!-- 保存按钮 -->
      <div class="ai-footer">
        <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { aiSettingsApi } from '@/api/aiSettings'

/**
 * 用户中心 - AI 设置卡片。
 *
 * 仅做前端 UI + API 调用，不接入现有 AI 业务调用链。
 *
 * 安全约束：
 *  - API Key 输入框只用于提交新 key，永不回填已保存的 key；
 *  - 保存成功后清空 apiKey 输入；
 *  - 只展示后端返回的 maskedApiKey；
 *  - 不 console.log 表单（其中可能含 apiKey）；
 *  - 不写入 localStorage / sessionStorage / Pinia。
 */
const loading = ref(false)
const saving = ref(false)

// Provider 预设：{ text: [...], vision: [...] }
const providers = ref({ text: [], vision: [] })

// 表单（apiKey 字段只用于新输入，永不回填）
const form = reactive({
  keyMode: 'BUILTIN',
  text: { provider: '', baseUrl: '', model: '', apiKey: '', clearApiKey: false },
  vision: { provider: '', baseUrl: '', model: '', apiKey: '', clearApiKey: false },
})

// 已保存 Key 的脱敏展示（来自后端，仅展示用）
const savedKeys = reactive({
  text: { hasApiKey: false, maskedApiKey: '' },
  vision: { hasApiKey: false, maskedApiKey: '' },
})

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    // 并行拉取预设与当前设置
    const [providersData, settingsData] = await Promise.all([
      aiSettingsApi.getProviders(),
      aiSettingsApi.getSettings(),
    ])
    providers.value = providersData || { text: [], vision: [] }
    applySettings(settingsData)
  } catch (e) {
    ElMessage.error('加载 AI 设置失败')
  } finally {
    loading.value = false
  }
}

function applySettings(data) {
  if (!data) return
  form.keyMode = data.keyMode || 'BUILTIN'

  if (data.text) {
    form.text.provider = data.text.provider || ''
    form.text.baseUrl = data.text.baseUrl || ''
    form.text.model = data.text.model || ''
    savedKeys.text.hasApiKey = !!data.text.hasApiKey
    savedKeys.text.maskedApiKey = data.text.maskedApiKey || ''
  }
  if (data.vision) {
    form.vision.provider = data.vision.provider || ''
    form.vision.baseUrl = data.vision.baseUrl || ''
    form.vision.model = data.vision.model || ''
    savedKeys.vision.hasApiKey = !!data.vision.hasApiKey
    savedKeys.vision.maskedApiKey = data.vision.maskedApiKey || ''
  }
  // 注意：永不回填 apiKey 到输入框
  form.text.apiKey = ''
  form.vision.apiKey = ''
  form.text.clearApiKey = false
  form.vision.clearApiKey = false
}

/** 切换 Provider 时：预设 Provider 若 baseUrl/model 为空，自动填入默认值 */
function onProviderChange(taskType) {
  const list = providers.value[taskType] || []
  const cfg = form[taskType]
  const preset = list.find(p => p.provider === cfg.provider)
  if (!preset) return
  if (!preset.custom) {
    if (!cfg.baseUrl && preset.defaultBaseUrl) cfg.baseUrl = preset.defaultBaseUrl
    if (!cfg.model && preset.defaultModel) cfg.model = preset.defaultModel
  }
}

/** 切换"清空已保存 Key"时：勾选则清空输入框，避免提交无意义的新 key */
function onClearToggle(taskType) {
  if (form[taskType].clearApiKey) {
    form[taskType].apiKey = ''
  }
}

function isCustom(taskType) {
  const cfg = form[taskType]
  const list = providers.value[taskType] || []
  const preset = list.find(p => p.provider === cfg.provider)
  return !!(preset && preset.custom)
}

/** 轻量校验，返回错误消息或 null */
function validate() {
  if (form.keyMode !== 'USER') return null

  for (const taskType of ['text', 'vision']) {
    const cfg = form[taskType]
    if (!cfg.provider) {
      return taskType === 'text' ? '请选择 Text Provider' : '请选择 Vision Provider'
    }
    if (isCustom(taskType)) {
      if (!cfg.baseUrl || !cfg.baseUrl.trim()) {
        return taskType === 'text' ? 'Text Provider 为自定义时 Base URL 必填' : 'Vision Provider 为自定义时 Base URL 必填'
      }
      if (!cfg.model || !cfg.model.trim()) {
        return taskType === 'text' ? 'Text Provider 为自定义时 Model 必填' : 'Vision Provider 为自定义时 Model 必填'
      }
    }
    if (cfg.baseUrl && cfg.baseUrl.trim()) {
      const lower = cfg.baseUrl.trim().toLowerCase()
      if (!lower.startsWith('http://') && !lower.startsWith('https://')) {
        return 'Base URL 必须以 http:// 或 https:// 开头'
      }
    }
  }
  return null
}

/** 构造保存请求体（不打印，避免 key 泄露） */
function buildPayload() {
  if (form.keyMode === 'BUILTIN') {
    return { keyMode: 'BUILTIN' }
  }
  return {
    keyMode: 'USER',
    text: buildProviderPayload('text'),
    vision: buildProviderPayload('vision'),
  }
}

function buildProviderPayload(taskType) {
  const cfg = form[taskType]
  const clear = !!cfg.clearApiKey
  const newKey = cfg.apiKey && cfg.apiKey.trim() ? cfg.apiKey : null
  return {
    provider: cfg.provider,
    baseUrl: cfg.baseUrl && cfg.baseUrl.trim() ? cfg.baseUrl.trim() : null,
    model: cfg.model && cfg.model.trim() ? cfg.model.trim() : null,
    // clearApiKey=true 时忽略 apiKey；否则 null 表示不修改
    apiKey: clear ? null : newKey,
    clearApiKey: clear,
  }
}

async function save() {
  const err = validate()
  if (err) {
    ElMessage.warning(err)
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    const updated = await aiSettingsApi.updateSettings(payload)
    applySettings(updated)
    ElMessage.success('AI 设置已保存')
  } catch (e) {
    // 不显示任何 key；后端通用错误经拦截器已转成 message
    ElMessage.error('保存 AI 设置失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.ai-settings-card {
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

.ai-form {
  max-width: 640px;
}

.mode-alert {
  margin: 4px 0 18px;
}

.provider-section {
  padding: 16px 18px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  background: var(--bg-primary);
  margin-bottom: 18px;
}

.provider-section-hd {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 2px;
}

.provider-section-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-primary);
}

.provider-section-tag {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  background: rgba(82, 183, 136, 0.12);
  color: var(--color-primary);
}

.provider-section-desc {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 6px;
}

.field-hint {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
  line-height: 1.5;
}

.field-hint.custom-hint {
  color: #b45309;
}

.key-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  width: 100%;
  flex-wrap: wrap;
}

.saved-key {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.saved-key-label {
  color: var(--text-muted);
}

.ai-footer {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}

/* Element Plus 暗色模式下 select dropdown 与 form-item 调整 */
:deep(.el-form-item) {
  margin-bottom: 14px;
}

:deep(.el-form-item__label) {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  padding-bottom: 4px !important;
}

:deep(.el-input__wrapper),
:deep(.el-select__wrapper) {
  border-radius: var(--radius-sm);
}

@media (max-width: 768px) {
  .key-row {
    flex-direction: column;
    align-items: flex-start;
  }
}

/* 暗色模式：provider-section 浅色背景改为深色 */
html.dark .provider-section {
  background: var(--bg-card);
  border-color: var(--border-color);
}

html.dark .field-hint.custom-hint {
  color: #d4a017;
}

html.dark .provider-section-tag {
  background: rgba(82, 183, 136, 0.18);
  color: #6BC99A;
}
</style>
