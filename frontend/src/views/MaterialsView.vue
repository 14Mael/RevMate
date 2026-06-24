<script setup lang="ts">
/**
 * 资料管理页
 * 功能：
 * - 按课程分组展示资料
 * - 上传时选择/创建课程
 * - 点击卡片预览，点击删除按钮删除
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  PhCaretDown,
  PhEye,
  PhFileDoc,
  PhFilePdf,
  PhFileText,
  PhFolderOpen,
  PhImage,
  PhPlus,
  PhTrash,
  PhUploadSimple
} from '@/components/icons'
import {
  deleteMaterial,
  listCourses,
  listMaterials,
  uploadMaterial
} from '@/api/material'
import { deleteSubject, findOrCreateSubject, listSubjects, updateSubject } from '@/api/subject'
import type { Material, MaterialStatus, MaterialType, Subject } from '@/api/types'

defineOptions({ name: 'MaterialsView' })

const router = useRouter()

const materials = ref<Material[]>([])
const subjects = ref<Subject[]>([])
const loading = ref(false)
const uploading = ref(false)
const fileInputRef = ref<HTMLInputElement>()

// ---------- 上传弹窗 ----------
const uploadDialogVisible = ref(false)
const courseDialogVisible = ref(false)
const courseSavingId = ref<number | null>(null)
const courseDeletingId = ref<number | null>(null)
const courseDrafts = ref<Record<number, string>>({})
const uploadForm = reactive<{
  file: File | null
  course: string
  customCourse: string
}>({
  file: null,
  course: '',
  customCourse: ''
})

// ---------- 分组折叠 ----------
const foldedGroups = ref<Set<string>>(new Set())

function isFolded(course: string) {
  return foldedGroups.value.has(course)
}
function toggleGroup(course: string) {
  if (foldedGroups.value.has(course)) {
    foldedGroups.value.delete(course)
  } else {
    foldedGroups.value.add(course)
  }
}

const statusMeta: Record<MaterialStatus, { label: string; className: string }> = {
  READY: { label: '已入库', className: 'status-ready' },
  PROCESSING: { label: '处理中', className: 'status-processing' },
  FAILED: { label: '处理失败', className: 'status-failed' }
}

const typeMeta: Record<MaterialType, { label: string; className: string; icon: typeof PhFileText }> = {
  pdf: { label: 'PDF', className: 'type-pdf', icon: PhFilePdf },
  word: { label: 'DOC', className: 'type-word', icon: PhFileDoc },
  ppt: { label: 'PPT', className: 'type-word', icon: PhFileDoc },
  excel: { label: 'XLS', className: 'type-word', icon: PhFileDoc },
  txt: { label: 'TXT', className: 'type-txt', icon: PhFileText },
  image: { label: 'IMG', className: 'type-image', icon: PhImage },
  audio: { label: 'AUD', className: 'type-audio', icon: PhFileText },
  unknown: { label: 'FILE', className: 'type-txt', icon: PhFileText }
}

// 课程标签下拉选项（现有课程 + "新建课程"）
const courseOptions = computed(() => {
  const names = new Set(subjects.value.map((subject) => subject.name))
  listCourses(materials.value).forEach((name) => names.add(name))
  return [...names].map((name) => ({ label: name, value: name }))
})

// 按课程分组
const groupedMaterials = computed(() => {
  const groups = new Map<string, Material[]>()
  const uncategorized: Material[] = []
  materials.value.forEach((m) => {
    if (m.course) {
      if (!groups.has(m.course)) groups.set(m.course, [])
      groups.get(m.course)!.push(m)
    } else {
      uncategorized.push(m)
    }
  })
  const result: { course: string; list: Material[]; isUncategorized?: boolean }[] = []
  ;[...groups.entries()]
    .sort((a, b) => b[1].length - a[1].length)
    .forEach(([course, list]) => result.push({ course, list }))
  if (uncategorized.length > 0) {
    result.push({ course: '未分类', list: uncategorized, isUncategorized: true })
  }
  return result
})

const hasMaterials = computed(() => materials.value.length > 0)

const effectiveCourse = computed(() => uploadForm.customCourse.trim() || uploadForm.course)

const courseRows = computed(() => subjects.value.map((subject) => ({
  subject,
  materialCount: materials.value.filter((material) => material.subjectId === subject.id).length
})))

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value))
}

function resetAndOpenDialog() {
  // 顶部"上传资料"按钮：重置表单后打开弹窗，让用户在弹窗里再选文件
  uploadForm.file = null
  uploadForm.course = ''
  uploadForm.customCourse = ''
  uploadDialogVisible.value = true
  // 弹窗打开后再触发文件选择
  window.setTimeout(() => fileInputRef.value?.click(), 200)
}

function openCourseManager() {
  courseDrafts.value = Object.fromEntries(subjects.value.map((subject) => [subject.id, subject.name]))
  courseDialogVisible.value = true
}

function showUploadDialog() {
  // 文件已通过拖拽/input选好时调用，不清 file
  uploadDialogVisible.value = true
}

function openFilePicker() {
  fileInputRef.value?.click()
}

function handleFilePicked(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    uploadForm.file = file
    showUploadDialog()
  }
}

function handleDrop(event: DragEvent) {
  const file = event.dataTransfer?.files?.[0]
  if (file) {
    uploadForm.file = file
    showUploadDialog()
  }
}

async function confirmUpload() {
  if (!uploadForm.file) {
    ElMessage.warning('请先选择文件')
    return
  }
  if (!effectiveCourse.value) {
    ElMessage.warning('请选择或新建课程')
    return
  }
  uploading.value = true
  try {
    const subject = await findOrCreateSubject(effectiveCourse.value)
    const material = await uploadMaterial(uploadForm.file, subject.id)
    subjects.value = await listSubjects()
    materials.value = [material, ...materials.value]
    uploadDialogVisible.value = false
    ElMessage.success('上传成功，资料正在处理，状态变为已入库后即可问答')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传失败')
  } finally {
    uploading.value = false
    if (fileInputRef.value) fileInputRef.value.value = ''
  }
}

async function refreshMaterials() {
  loading.value = true
  try {
    subjects.value = await listSubjects()
    materials.value = await listMaterials()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资料列表加载失败')
  } finally {
    loading.value = false
  }
}

async function renameSubject(subject: Subject) {
  const name = courseDrafts.value[subject.id]?.trim() || ''
  if (!name) {
    ElMessage.warning('课程名称不能为空')
    return
  }
  if (name === subject.name) {
    ElMessage.info('课程名称没有变化')
    return
  }

  courseSavingId.value = subject.id
  try {
    const updated = await updateSubject(subject.id, { name })
    subjects.value = subjects.value.map((item) => item.id === updated.id ? updated : item)
    materials.value = materials.value.map((item) =>
      item.subjectId === updated.id ? { ...item, course: updated.name } : item
    )
    courseDrafts.value[updated.id] = updated.name
    ElMessage.success('课程已重命名')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重命名失败')
  } finally {
    courseSavingId.value = null
  }
}

async function removeSubject(subject: Subject, materialCount: number) {
  if (materialCount > 0) {
    ElMessage.warning('请先删除该课程下的资料，再删除课程')
    return
  }

  try {
    await ElMessageBox.confirm(`确定删除课程「${subject.name}」吗？`, '删除课程', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    courseDeletingId.value = subject.id
    await deleteSubject(subject.id)
    subjects.value = subjects.value.filter((item) => item.id !== subject.id)
    const { [subject.id]: _removed, ...rest } = courseDrafts.value
    courseDrafts.value = rest
    ElMessage.success('课程已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '删除课程失败')
    }
  } finally {
    courseDeletingId.value = null
  }
}

async function removeMaterial(material: Material) {
  try {
    await ElMessageBox.confirm(`确定删除「${material.filename}」吗？`, '删除资料', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteMaterial(material.id)
    materials.value = materials.value.filter((item) => item.id !== material.id)
    ElMessage.success('已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '删除失败')
    }
  }
}

function previewMaterial(material: Material) {
  router.push(`/materials/${material.id}`)
}

onMounted(refreshMaterials)
</script>

<template>
  <div class="materials-page">
    <div class="page-header">
      <div>
        <h1>我的资料</h1>
        <p>上传复习材料，按课程分类管理。上传后可以在线预览并选中文字提问。</p>
      </div>
      <div class="header-actions">
        <el-button @click="openCourseManager">
          <PhFolderOpen :size="16" weight="duotone" />
          管理课程
        </el-button>
        <el-button type="primary" :loading="uploading" @click="resetAndOpenDialog">
          <PhUploadSimple :size="16" weight="bold" />
          上传资料
        </el-button>
      </div>
    </div>

    <!-- 上传区 -->
    <section class="upload-card" @click="openFilePicker" @dragover.prevent @drop.prevent="handleDrop">
      <input
        ref="fileInputRef"
        class="file-input"
        type="file"
        accept=".pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.txt,.md,.png,.jpg,.jpeg,.webp,.bmp,.mp3,.wav,.m4a"
        @change="handleFilePicked"
      />
      <div class="upload-icon">
        <PhUploadSimple :size="34" weight="duotone" />
      </div>
      <div class="upload-title">拖拽文件到此处或点击上传</div>
      <div class="upload-desc">支持 PDF / Word / PPT / TXT / 图片 / 音频，Office 上传后自动转 PDF 预览</div>
    </section>

    <!-- 课程分组 -->
    <section v-loading="loading" class="materials-section">
      <div v-if="hasMaterials" class="group-list">
        <section v-for="group in groupedMaterials" :key="group.course" class="course-group">
          <div class="group-header" @click="toggleGroup(group.course)">
            <button class="fold-btn" :class="{ folded: isFolded(group.course) }">
              <PhCaretDown :size="18" weight="bold" />
            </button>
            <PhFolderOpen v-if="!group.isUncategorized" :size="20" weight="duotone" class="folder-icon" />
            <h2 class="course-name">{{ group.course }}</h2>
            <span class="course-count">{{ group.list.length }} 份</span>
          </div>
          <div v-show="!isFolded(group.course)" class="material-grid">
            <article
              v-for="material in group.list"
              :key="material.id"
              class="material-card"
              @click="previewMaterial(material)"
            >
              <div class="card-top">
                <div class="file-type" :class="typeMeta[material.type].className">
                  <component :is="typeMeta[material.type].icon" :size="24" weight="duotone" />
                </div>
                <button
                  class="delete-btn"
                  type="button"
                  @click.stop="removeMaterial(material)"
                >
                  <PhTrash :size="16" />
                </button>
              </div>

              <h3 class="filename" :title="material.filename">{{ material.filename }}</h3>
              <div class="meta-row">
                <span class="type-label">{{ typeMeta[material.type].label }}</span>
                <span>{{ formatDate(material.createdAt) }}</span>
              </div>
              <div class="card-footer">
                <div class="status-pill" :class="statusMeta[material.status].className">
                  <span class="status-dot" />
                  {{ statusMeta[material.status].label }}
                </div>
                <button class="preview-btn" type="button" @click.stop="previewMaterial(material)">
                  <PhEye :size="14" />
                  预览
                </button>
              </div>
            </article>
          </div>
        </section>
      </div>

      <el-empty v-else description="还没有资料，上传后我就能基于它们回答问题" />
    </section>

    <!-- 上传弹窗：选课程 -->
    <el-dialog
      v-model="uploadDialogVisible"
      title="上传资料"
      width="440px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div v-if="uploadForm.file" class="file-preview">
        <div class="file-name">{{ uploadForm.file.name }}</div>
        <div class="file-size">{{ (uploadForm.file.size / 1024 / 1024).toFixed(2) }} MB</div>
      </div>
      <el-form label-position="top" style="margin-top: var(--space-lg)">
        <el-form-item label="所属课程">
          <el-select
            v-model="uploadForm.course"
            placeholder="选择已有课程，或在下方输入新的课程名"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="opt in courseOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="新建课程（可选）">
          <el-input
            v-model="uploadForm.customCourse"
            placeholder="输入新课程名称，如「数据结构」"
            clearable
          >
            <template #prefix>
              <PhPlus :size="16" />
            </template>
          </el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="confirmUpload">
          开始上传
        </el-button>
      </template>
    </el-dialog>

    <!-- 课程管理弹窗 -->
    <el-dialog
      v-model="courseDialogVisible"
      title="管理课程"
      width="560px"
      destroy-on-close
    >
      <div v-if="courseRows.length > 0" class="course-manager-list">
        <div v-for="{ subject, materialCount } in courseRows" :key="subject.id" class="course-manager-row">
          <div class="course-manager-main">
            <el-input
              v-model="courseDrafts[subject.id]"
              maxlength="100"
              show-word-limit
              placeholder="课程名称"
            />
            <span class="course-manager-count">{{ materialCount }} 份资料</span>
          </div>
          <div class="course-manager-actions">
            <el-button
              size="small"
              :loading="courseSavingId === subject.id"
              @click="renameSubject(subject)"
            >
              保存
            </el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :disabled="materialCount > 0"
              :loading="courseDeletingId === subject.id"
              :title="materialCount > 0 ? '请先删除该课程下的资料' : '删除课程'"
              @click="removeSubject(subject, materialCount)"
            >
              删除
            </el-button>
          </div>
        </div>
      </div>
      <el-empty v-else description="还没有课程，上传资料时可以新建课程" />
      <template #footer>
        <el-button @click="courseDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.materials-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-xl);
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-lg);
}

.page-header h1 {
  font-size: var(--font-size-h1);
  margin-bottom: var(--space-xs);
}

.page-header p {
  color: var(--color-text-assist);
  font-size: var(--font-size-body);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.header-actions :deep(.el-button) {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
}

.header-actions :deep(.el-button--primary) {
  box-shadow: var(--shadow-button-primary);
}

.upload-card {
  position: relative;
  padding: var(--space-3xl);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--gradient-upload-area);
  text-align: center;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-default);
}

.upload-card:hover {
  border-color: var(--color-primary);
  background: linear-gradient(135deg, #F8FAFF, #E8EDFF);
  box-shadow: var(--shadow-inset-primary-soft);
}

.file-input {
  display: none;
}

.upload-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto var(--space-md);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.72);
  color: var(--color-primary);
  display: grid;
  place-items: center;
  box-shadow: var(--shadow-sm);
}

.upload-title {
  color: var(--color-text-title);
  font-size: var(--font-size-h3);
  font-weight: 600;
  margin-bottom: var(--space-xs);
}

.upload-desc {
  color: var(--color-text-assist);
  font-size: var(--font-size-small);
}

/* ---------- 课程分组 ---------- */
.materials-section {
  min-height: 240px;
}

.group-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2xl);
}

.course-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

.group-header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  cursor: pointer;
  user-select: none;
}

.fold-btn {
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-assist);
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: all var(--duration-base) var(--ease-default);
  flex-shrink: 0;
}

.fold-btn:hover {
  background: var(--color-page-bg);
  color: var(--color-text-title);
}

.fold-btn.folded {
  transform: rotate(-90deg);
}

.folder-icon {
  color: var(--color-primary);
}

.course-name {
  font-size: var(--font-size-h2);
  font-weight: 600;
  color: var(--color-text-title);
}

.course-count {
  padding: 2px 10px;
  border-radius: var(--radius-full);
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: var(--font-size-small);
  font-weight: 500;
}

.material-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--space-lg);
}

.material-card {
  padding: var(--space-xl);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-card-bg);
  box-shadow: var(--shadow-card-soft);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-default);
  display: flex;
  flex-direction: column;
}

.material-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-card-hover);
  border-color: rgba(91, 127, 255, 0.18);
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-lg);
}

.file-type {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  display: grid;
  place-items: center;
}

.type-pdf {
  color: #FF4D4F;
  background: #FFF1F0;
}

.type-word {
  color: #2F80ED;
  background: #EEF5FF;
}

.type-txt {
  color: #52C41A;
  background: #F0FFF0;
}

.type-image {
  color: #FAAD14;
  background: #FFF7E6;
}

.type-audio {
  color: #8A63D2;
  background: #F5F0FF;
}

.delete-btn {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-assist);
  display: grid;
  place-items: center;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-default);
  opacity: 0;
}

.material-card:hover .delete-btn {
  opacity: 1;
}

.delete-btn:hover {
  color: var(--color-danger);
  background: #FFF1F0;
}

.filename {
  color: var(--color-text-title);
  font-size: var(--font-size-h3);
  font-weight: 600;
  line-height: 1.5;
  margin-bottom: var(--space-sm);
  min-height: 48px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  color: var(--color-text-assist);
  font-size: var(--font-size-small);
  margin-bottom: var(--space-md);
}

.type-label {
  font-weight: 600;
}

.card-footer {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  padding: 4px 10px;
  border-radius: var(--radius-full);
  font-size: var(--font-size-small);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: var(--radius-full);
  background: currentColor;
}

.status-ready {
  color: var(--color-success);
  background: #F0FFF0;
}

.status-processing {
  color: var(--color-warning);
  background: #FFFBE6;
}

.status-failed {
  color: var(--color-danger);
  background: #FFF1F0;
}

.preview-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 0;
  border-radius: var(--radius-sm);
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: var(--font-size-small);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-default);
}

.preview-btn:hover {
  background: var(--color-primary);
  color: #fff;
}

/* ---------- 上传弹窗 ---------- */
.file-preview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-lg);
  border-radius: var(--radius-md);
  background: var(--color-page-bg);
}

.file-name {
  color: var(--color-text-title);
  font-weight: 500;
  font-size: var(--font-size-body);
}

.file-size {
  color: var(--color-text-assist);
  font-size: var(--font-size-small);
}

/* ---------- 课程管理 ---------- */
.course-manager-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.course-manager-row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-card-bg);
}

.course-manager-main {
  flex: 1;
  min-width: 0;
}

.course-manager-count {
  display: inline-block;
  margin-top: var(--space-xs);
  color: var(--color-text-assist);
  font-size: var(--font-size-small);
}

.course-manager-actions {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  flex-shrink: 0;
}

@media (max-width: 767px) {
  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
    flex-direction: column;
  }

  .header-actions :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }

  .upload-card {
    padding: var(--space-2xl) var(--space-lg);
  }

  .material-grid {
    grid-template-columns: 1fr;
  }

  .delete-btn {
    opacity: 1;
  }

  .course-manager-row,
  .course-manager-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
