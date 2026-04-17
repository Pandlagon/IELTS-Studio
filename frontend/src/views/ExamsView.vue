<template>
  <div class="page-wrapper">
    <NavBar />
    <div class="exams-page">
      <div class="container">

        <!-- Page Header -->
        <div class="page-header">
          <div>
            <h1 class="page-title">模拟考试</h1>
            <p class="page-sub">管理您的试卷，开始模拟练习</p>
          </div>
          <button class="btn-primary" @click="showUpload = true">
            <el-icon><Plus /></el-icon>
            上传试卷
          </button>
        </div>

        <!-- Upload Panel -->
        <transition name="fade-in-up">
          <div v-if="showUpload" class="upload-panel card">
            <div class="upload-panel-header">
              <h3>上传新试卷</h3>
              <button class="close-btn" @click="showUpload = false">
                <el-icon><Close /></el-icon>
              </button>
            </div>

            <div
              class="upload-zone"
              :class="{ 'is-dragging': isDragging, 'has-file': uploadFiles.length > 0 }"
              @dragover.prevent="isDragging = true"
              @dragleave="isDragging = false"
              @drop.prevent="handleDrop"
              @click="triggerFileInput"
            >
              <input ref="fileInputRef" type="file" multiple accept=".pdf,.doc,.docx,.png,.jpg,.jpeg,.webp" style="display:none" @change="handleFileChange" />
              <template v-if="uploadFiles.length === 0">
                <div class="upload-icon">
                  <el-icon size="32"><UploadFilled /></el-icon>
                </div>
                <p class="upload-tip">拖拽文件到此处，或<span class="upload-link">点击选择</span></p>
                <p class="upload-hint">支持 PDF、Word（.doc/.docx）与图片（.png/.jpg/.jpeg/.webp），最大 20MB</p>
              </template>
              <template v-else>
                <div class="upload-file-info">
                  <el-icon size="28" color="var(--color-primary)"><Document /></el-icon>
                  <div>
                    <p class="file-name">{{ uploadFiles.length === 1 ? uploadFiles[0].name : `已选择 ${uploadFiles.length} 个文件` }}</p>
                    <p class="file-size">{{ uploadFiles.length === 1 ? formatSize(uploadFiles[0].size) : formatSize(totalUploadBytes) }}</p>
                  </div>
                  <button class="remove-file" @click.stop="uploadFiles = []">
                    <el-icon><Close /></el-icon>
                  </button>
                </div>
              </template>
            </div>

            <div class="upload-meta">
              <div class="form-group">
                <label class="form-label">试卷名称</label>
                <input v-model="uploadForm.title" class="form-input" placeholder="例：Cambridge IELTS 18 Test 1" />
              </div>
              <div class="form-group">
                <label class="form-label">题型</label>
                <select v-model="uploadForm.type" class="form-input">
                  <option value="reading">阅读</option>
                  <option value="listening" disabled>听力（暂不支持）</option>
                  <option value="writing">写作</option>
                </select>
                <span v-if="uploadForm.type === 'listening'" class="field-warn">⚠️ 听力功能暂不可用，请选择其他题型</span>
              </div>
              <div class="form-group">
                <label class="form-label">答题时间（分钟）</label>
                <input
                  v-model.number="uploadForm.duration"
                  type="number"
                  min="1"
                  max="300"
                  class="form-input"
                  placeholder="例：60"
                />
              </div>
              <div class="form-group">
                <label class="parse-mode-label">
                  <input 
                    type="checkbox" 
                    v-model="uploadForm.parsePrecise" 
                    class="parse-mode-cb" 
                    :disabled="uploadForm.type !== 'writing'"
                  />
                  <span class="parse-mode-text">
                    <strong>精准解析（Qwen 视觉解析）</strong>
                    <span class="parse-mode-hint">适合扫描版、多栏布局、图形图片较多的试卷，解析耗时较长</span>
                  </span>
                </label>
                <span v-if="uploadForm.type !== 'writing'" class="field-warn">⚠️ 精准解析仅支持写作题型</span>
              </div>
            </div>

            <div class="upload-tip">
              💡 推荐上传 <strong>Word (.docx)</strong> 或文字可选中的 PDF；扫描版、图片版试卷建议开启<strong>精准解析</strong>
            </div>

            <div class="upload-tip upload-tip-warning">
              ⚠️ 普通解析更适合<strong>纯文字型试卷</strong>；若文件包含表格、流程图、插图、坐标图或扫描页面，请优先开启<strong>精准解析</strong>
            </div>

            <div class="upload-tip upload-tip-info">
              📌 建议按<strong>题型分别上传</strong>：例如阅读单独上传、写作单独上传，不要将多种题型混在同一文件中
            </div>

            <div class="upload-actions">
              <button class="btn-secondary" @click="showUpload = false">取消</button>
              <button
                class="btn-primary"
                :disabled="uploadFiles.length === 0 || !uploadForm.title || uploading"
                @click="handleUpload"
              >
                <el-icon v-if="uploading"><Loading /></el-icon>
                {{ ocrStatus || (uploading ? '解析中...' : '上传并解析') }}
              </button>
            </div>
          </div>
        </transition>

        <!-- Filter Bar -->
        <div class="filter-bar">
          <div class="filter-tabs">
            <button
              v-for="tab in tabs"
              :key="tab.value"
              class="filter-tab"
              :class="{ active: activeTab === tab.value }"
              @click="activeTab = tab.value"
            >
              {{ tab.label }}
              <span class="tab-count">{{ getTabCount(tab.value) }}</span>
            </button>
          </div>
          <div class="search-box">
            <el-icon class="search-icon"><Search /></el-icon>
            <input v-model="searchQ" class="search-input" placeholder="搜索试卷..." />
          </div>
        </div>

        <!-- Loading State -->
        <div v-if="loadingExams" class="loading-state">
          <div class="loading-spinner"></div>
          <p>正在加载试卷...</p>
        </div>

        <!-- Exam Grid -->
        <div v-else-if="filteredExams.length" class="exam-grid">
          <div v-for="exam in filteredExams" :key="exam.id" class="exam-card card">
            <div class="exam-card-header">
              <div class="exam-type-badge" :class="`type-${exam.type}`">
                {{ typeLabel(exam.type) }}
              </div>
              <div class="exam-status-badge" :class="`status-${exam.status}`">
                <span class="status-dot"></span>
                {{ statusLabel(exam.status) }}
              </div>
            </div>

            <h3 class="exam-title">{{ exam.title }}</h3>
            <p class="exam-desc">{{ exam.description }}</p>

            <div class="exam-meta">
              <span class="meta-item">
                <el-icon><DocumentChecked /></el-icon>
                {{ exam.questionCount }} 题
              </span>
              <span class="meta-item">
                <el-icon><Clock /></el-icon>
                {{ exam.duration }} 分钟
              </span>
              <span class="meta-item difficulty" :class="`diff-${exam.difficulty}`">
                {{ exam.difficulty }}
              </span>
            </div>

            <div class="exam-tags">
              <span v-for="tag in exam.tags" :key="tag" class="tag-chip">{{ tag }}</span>
            </div>

            <div class="exam-card-footer">
              <span class="exam-date">{{ exam.createdAt }}</span>
              <div class="exam-actions">
                <div v-if="confirmDeleteId === exam.id" class="delete-confirm-wrapper">
                  <div class="delete-warning">⚠️删除后将清除该试卷的答题和历史记录</div>
                  <div class="delete-buttons">
                    <button
                      class="btn-danger-confirm"
                      @click.stop="doDeleteExam(exam.id)"
                    >确认删除</button>
                    <button
                      class="btn-cancel-delete"
                      @click.stop="confirmDeleteId = null"
                    >取消</button>
                  </div>
                </div>
                <button
                  v-else
                  class="btn-icon-delete"
                  :title="exam.isMock ? '移除试卷' : '删除试卷'"
                  @click.stop="confirmDeleteId = exam.id"
                >
                  <el-icon><Delete /></el-icon>
                </button>
                <button
                  class="btn-primary"
                  :disabled="exam.status !== 'ready'"
                  @click="startExam(exam.id)"
                >
                  {{ exam.status === 'processing' ? '解析中' : '开始答题' }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty State -->
        <div v-else class="empty-state" style="margin-top:0">
          <div class="empty-icon">📄</div>
          <p class="empty-title">暂无试卷</p>
          <p class="empty-desc">上传您的第一份试卷开始练习</p>
          <button class="btn-primary" @click="showUpload = true">上传试卷</button>
        </div>

      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import NavBar from '@/components/NavBar.vue'
import { useExamStore } from '@/stores/exam'
import request from '@/api'
import { extractTextFromPdf, extractTextFromImages } from '@/utils/pdfExtract'

const router = useRouter()
const examStore = useExamStore()

const loadingExams = ref(false)
const showUpload = ref(false)

onMounted(async () => {
  loadingExams.value = true
  await examStore.loadExams()
  loadingExams.value = false
})
const isDragging = ref(false)
const uploading = ref(false)
const ocrStatus = ref('')
const fileInputRef = ref()
const uploadFiles = ref([])
const searchQ = ref('')
const activeTab = ref('all')

const uploadForm = ref({ title: '', type: 'reading', duration: 60, parsePrecise: false })

const tabs = [
  { label: '全部', value: 'all' },
  { label: '阅读', value: 'reading' },
  { label: '听力', value: 'listening' },
  { label: '写作', value: 'writing' },
]

const filteredExams = computed(() => {
  let list = examStore.exams
  if (activeTab.value !== 'all') list = list.filter(e => e.type === activeTab.value)
  if (searchQ.value.trim()) {
    const q = searchQ.value.toLowerCase()
    list = list.filter(e => e.title.toLowerCase().includes(q) || e.description?.toLowerCase().includes(q))
  }
  return list
})

function getTabCount(tab) {
  if (tab === 'all') return examStore.exams.length
  return examStore.exams.filter(e => e.type === tab).length
}

function typeLabel(type) {
  return { reading: '阅读', listening: '听力', writing: '写作' }[type] || type
}

function statusLabel(status) {
  return { ready: '可用', processing: '解析中', error: '解析失败' }[status] || status
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function triggerFileInput() {
  fileInputRef.value.click()
}

function handleFileChange(e) {
  const files = Array.from(e.target.files || [])
  if (!files.length) return
  uploadFiles.value = files
}

function handleDrop(e) {
  isDragging.value = false
  const files = Array.from(e.dataTransfer.files || [])
  if (!files.length) return
  const ok = files.every(f => {
    const lower = f?.name?.toLowerCase?.() || ''
    return f.type.includes('pdf') ||
      lower.endsWith('.doc') || lower.endsWith('.docx') ||
      lower.endsWith('.png') || lower.endsWith('.jpg') || lower.endsWith('.jpeg') || lower.endsWith('.webp')
  })
  if (!ok) {
    ElMessage.warning('只支持 PDF、Word、PNG、JPG、JPEG、WEBP 格式文件')
    return
  }
  uploadFiles.value = files
}

const totalUploadBytes = computed(() => uploadFiles.value.reduce((sum, f) => sum + (f?.size || 0), 0))

async function handleUpload() {
  if (!uploadFiles.value.length || !uploadForm.value.title) return
  const names = uploadFiles.value.map(f => (f?.name || '').toLowerCase())
  const isAllImages = names.every(n => /\.(png|jpg|jpeg|webp)$/i.test(n))
  const isSingleNonImage = uploadFiles.value.length === 1 && !isAllImages
  if (!isAllImages && uploadFiles.value.length > 1) {
    ElMessage.warning('仅支持多图片上传；PDF/Word 请只选择一个文件')
    return
  }
  uploading.value = true
  try {
    const fd = new FormData()
    if (isAllImages) {
      uploadFiles.value.forEach(f => fd.append('files', f))
    } else {
      fd.append('file', uploadFiles.value[0])
    }
    fd.append('title', uploadForm.value.title)
    fd.append('type', uploadForm.value.type)
    fd.append('duration', String(uploadForm.value.duration || 60))
    fd.append('parsePrecise', String(uploadForm.value.parsePrecise || false))

    // For non-precise PDF uploads: extract text client-side with pdf.js
    // and send it to the backend so PDFBox is skipped entirely
    const lowerName = uploadFiles.value[0]?.name?.toLowerCase?.() || ''
    const isPdf = isSingleNonImage && lowerName.endsWith('.pdf')
    if (isPdf && !uploadForm.value.parsePrecise) {
      ocrStatus.value = 'PDF文字提取中...'
      const extracted = await extractTextFromPdf(uploadFiles.value[0], ({ stage, page, total }) => {
        if (stage === 'ocr') ocrStatus.value = `OCR识别中 ${page}/${total} 页...`
      })
      ocrStatus.value = ''
      if (extracted && extracted.trim().length > 0) {
        fd.append('extractedText', extracted)
      }
    }

    if (isAllImages && !uploadForm.value.parsePrecise) {
      ocrStatus.value = 'OCR识别中...'
      const extracted = await extractTextFromImages(uploadFiles.value, ({ stage, page, total }) => {
        if (stage === 'ocr') ocrStatus.value = `OCR识别中 ${page}/${total} 页...`
      })
      ocrStatus.value = ''
      if (extracted && extracted.trim().length > 0) {
        fd.append('extractedText', extracted)
      }
    }
    const res = await request.post('/exams/upload', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    const examId = res.data?.id
    if (!examId) throw new Error('未获取到试卷ID')
    examStore.addPendingExam(examId, uploadForm.value.title, uploadForm.value.type, uploadForm.value.duration || 60)
    showUpload.value = false
    uploadFiles.value = []
    uploadForm.value = { title: '', type: 'reading', duration: 60, parsePrecise: false }
    ElMessage.success('上传成功，正在后台解析，请稍候...')
    pollExamStatus(examId)
  } catch (e) {
    ElMessage.warning('后端连接失败，已使用本地模式添加试卷')
    examStore.addExam({
      title: uploadForm.value.title,
      description: `${typeLabel(uploadForm.value.type)} - 上传于 ${new Date().toLocaleDateString()}`,
      type: uploadForm.value.type,
      questionCount: 0,
      duration: uploadForm.value.duration || 60,
      difficulty: '中等',
      tags: [typeLabel(uploadForm.value.type)],
    })
    showUpload.value = false
    uploadFiles.value = []
    uploadForm.value = { title: '', type: 'reading', duration: 60, parsePrecise: false }
  } finally {
    uploading.value = false
  }
}

function pollExamStatus(examId) {
  let attempts = 0
  const timer = setInterval(async () => {
    try {
      attempts++
      const res = await request.get(`/exams/${examId}`)
      const exam = res.data
      if (exam.status === 'ready') {
        clearInterval(timer)
        const qRes = await request.get(`/exams/${examId}/questions`)
        examStore.updateParsedExam(examId, exam, qRes.data || [])
        ElMessage.success(`「${exam.title}」解析完成，共 ${(qRes.data || []).length} 道题`)
        // Reload full list to pick up any split exams auto-created from same PDF
        await examStore.loadExams()
      } else if (exam.status === 'error' || attempts >= 60) {
        clearInterval(timer)
        examStore.markExamError(examId)
        if (exam.status === 'error') ElMessage.error('试卷解析失败，请检查文件格式后重新上传')
      }
    } catch {
      clearInterval(timer)
    }
  }, 5000)
}

function startExam(id) {
  router.push(`/exam/${id}`)
}

const confirmDeleteId = ref(null)

async function doDeleteExam(id) {
  await examStore.deleteExam(id)
  confirmDeleteId.value = null
  ElMessage.success('试卷已删除')
}
</script>

<style scoped>
.exams-page {
  padding: 32px 0 64px;
}

.btn-icon-delete {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-md);
  border: 1.5px solid var(--border-color);
  background: var(--bg-white);
  color: var(--text-muted);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
  flex-shrink: 0;
}
.btn-icon-delete:hover { border-color: #EF4444; color: #EF4444; background: #FEF2F2; }

.upload-tip-warning {
  background: #FFF7ED;
  border: 1px solid #FED7AA;
  color: #9A3412;
}

.upload-tip-info {
  background: #EFF6FF;
  border: 1px solid #BFDBFE;
  color: #1D4ED8;
}

.btn-danger-confirm {
  padding: 6px 14px;
  border-radius: var(--radius-full);
  border: none;
  background: #EF4444;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}
.btn-danger-confirm:hover { background: #DC2626; }

.btn-cancel-delete {
  padding: 6px 12px;
  border-radius: var(--radius-full);
  border: 1.5px solid var(--border-color);
  background: var(--bg-white);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-cancel-delete:hover { border-color: var(--text-secondary); }

.delete-confirm-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
}

.delete-warning {
  font-size: 11px;
  color: #B45309;
  background: #FFFBEB;
  border: 1px solid #FCD34D;
  padding: 6px 10px;
  border-radius: var(--radius-md);
  text-align: right;
  line-height: 1.4;
}

.delete-buttons {
  display: flex;
  gap: 8px;
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 32px;
}

.page-title {
  font-size: 28px;
  font-weight: 800;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.page-sub {
  font-size: 14px;
  color: var(--text-muted);
}

/* Upload Panel */
.upload-panel {
  margin-bottom: 28px;
  padding: 24px;
}

.upload-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.upload-panel-header h3 {
  font-size: 16px;
  font-weight: 700;
}

.close-btn {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--bg-primary);
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  transition: all 0.15s;
}
.close-btn:hover { background: var(--border-color); color: var(--text-primary); }

.upload-zone {
  border: 2px dashed var(--border-color);
  border-radius: var(--radius-lg);
  padding: 40px 24px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 20px;
}

.upload-zone:hover, .upload-zone.is-dragging {
  border-color: var(--color-primary);
  background: rgba(27, 67, 50, 0.02);
}

.upload-zone.has-file {
  border-style: solid;
  border-color: var(--color-accent);
  background: rgba(82, 183, 136, 0.04);
}

.upload-icon {
  margin-bottom: 12px;
  color: var(--text-muted);
}

.upload-tip {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.upload-link { color: var(--color-primary); font-weight: 500; }

.upload-hint {
  font-size: 12px;
  color: var(--text-muted);
}

.upload-file-info {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: center;
}

.file-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.file-size {
  font-size: 12px;
  color: var(--text-muted);
}

.remove-file {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #FEE2E2;
  color: #DC2626;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.upload-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 20px;
}

.upload-tip {
  font-size: 12px;
  color: var(--text-secondary);
  background: rgba(27,67,50,0.06);
  border-radius: var(--radius-md);
  padding: 8px 12px;
  margin-bottom: 16px;
  line-height: 1.5;
}

.field-warn {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #B45309;
}

.parse-mode-label {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  cursor: pointer;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  border: 1.5px solid var(--border-color);
  background: var(--bg-white);
  transition: border-color 0.15s, background 0.15s;
  user-select: none;
}
.parse-mode-label:has(.parse-mode-cb:checked) {
  border-color: var(--primary);
  background: rgba(27,67,50,0.05);
}
.parse-mode-cb {
  margin-top: 2px;
  accent-color: var(--primary);
  width: 15px;
  height: 15px;
  flex-shrink: 0;
  cursor: pointer;
}
.parse-mode-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 13px;
  color: var(--text-primary);
}
.parse-mode-hint {
  font-size: 11px;
  color: var(--text-muted);
  font-weight: 400;
}

.upload-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* Filter Bar */
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  gap: 16px;
}

.filter-tabs {
  display: flex;
  gap: 4px;
  background: var(--bg-white);
  padding: 4px;
  border-radius: var(--radius-full);
  border: 1px solid var(--border-color);
}

.filter-tab {
  padding: 6px 16px;
  border-radius: var(--radius-full);
  font-size: 13px;
  font-weight: 500;
  color: var(--text-muted);
  border: none;
  background: transparent;
  cursor: pointer;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  gap: 6px;
}

.filter-tab:hover { color: var(--color-primary); }
.filter-tab.active { background: var(--color-primary); color: white; }

.tab-count {
  background: rgba(0,0,0,0.1);
  padding: 1px 6px;
  border-radius: 10px;
  font-size: 11px;
}

.filter-tab.active .tab-count {
  background: rgba(255,255,255,0.25);
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--bg-white);
  border: 1.5px solid var(--border-color);
  border-radius: var(--radius-full);
  padding: 8px 16px;
  width: 240px;
  transition: border-color 0.15s;
}

.search-box:focus-within { border-color: var(--color-primary); }

.search-icon { color: var(--text-muted); font-size: 14px; }

.search-input {
  border: none;
  outline: none;
  font-size: 14px;
  background: transparent;
  color: var(--text-primary);
  width: 100%;
  font-family: var(--font-sans);
}

/* Exam Grid */
.exam-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.exam-card {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: all 0.2s;
}

.exam-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.exam-type-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: var(--radius-full);
}

.type-reading { background: #DBEAFE; color: #1D4ED8; }
.type-listening { background: #D1FAE5; color: #065F46; }
.type-writing { background: #FEF3C7; color: #92400E; }

.exam-status-badge {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 500;
  color: var(--text-muted);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.status-ready .status-dot { color: #22C55E; }
.status-processing .status-dot { color: #F59E0B; animation: pulse 1.5s infinite; }
.status-error .status-dot { color: #EF4444; }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.exam-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.4;
}

.exam-desc {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.5;
}

.exam-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-muted);
}

.difficulty { font-weight: 600; }
.diff-简单 { color: #22C55E; }
.diff-中等 { color: #F59E0B; }
.diff-较难 { color: #EF4444; }

.exam-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag-chip {
  font-size: 11px;
  padding: 2px 8px;
  background: var(--bg-primary);
  border-radius: var(--radius-full);
  color: var(--text-muted);
  border: 1px solid var(--border-color);
}

.exam-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
  padding-top: 12px;
  border-top: 1px solid var(--border-light);
}

.exam-date {
  font-size: 12px;
  color: var(--text-muted);
}

/* Loading State */
.loading-state {
  text-align: center;
  padding: 80px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  color: var(--text-muted);
  font-size: 14px;
}
.loading-spinner {
  width: 36px;
  height: 36px;
  border: 3px solid var(--border-color);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* Empty State */
.empty-state {
  text-align: center;
  padding: 80px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.empty-icon { font-size: 48px; }
.empty-title { font-size: 18px; font-weight: 700; color: var(--text-primary); }
.empty-desc { font-size: 14px; color: var(--text-muted); margin-bottom: 8px; }

@media (max-width: 1024px) {
  .exam-grid { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .exam-grid { grid-template-columns: 1fr; }
  .page-header { flex-direction: column; align-items: flex-start; gap: 16px; }
  .filter-bar { flex-direction: column; align-items: flex-start; }
  .search-box { width: 100%; }
  .upload-meta { grid-template-columns: 1fr; }
}
</style>
