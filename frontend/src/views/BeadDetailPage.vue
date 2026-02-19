<template>
  <section v-if="project" class="card detail-page">
    <div class="row-between detail-toolbar">
      <h2 class="section-title">
        拼豆详情：
        <a v-if="project.sourceUrl" class="title-link" :href="project.sourceUrl" target="_blank" rel="noopener noreferrer">{{ project.name }}</a>
        <span v-else>{{ project.name }}</span>
      </h2>
    </div>

    <div class="row-between tabs-header">
      <div class="page-tabs" role="tablist" aria-label="详情分页">
        <button class="page-tab" :class="{active: tab==='overview'}" @click="tab='overview'">概况</button>
        <button class="page-tab" :class="{active: tab==='work'}" @click="tab='work'">开始拼豆</button>
      </div>
      <button class="btn secondary edit-icon-btn" @click="startEditOverview" title="编辑概况">✎</button>
    </div>

    <div v-if="tab==='overview'" class="detail-overview">
      <div class="overview-state-bar">
        <div class="overview-inline-grid">
          <span class="field-label">拼豆名称</span>
          <span class="field-label">拼豆 tag</span>
          <span class="field-value">{{ project.name || '未填写' }}</span>
          <div class="row detail-tag-list inline-tag-list">
            <span v-for="tag in project.tags" :key="tag" class="badge">{{ tag }}</span>
            <span v-if="!project.tags || project.tags.length === 0" class="field-value">无</span>
          </div>
        </div>

      </div>

      <div class="detail-image-block">
        <div class="field-label">拼豆图纸</div>
        <img v-if="project.patternImage" :src="project.patternImage" class="detail-image" />
        <span v-else>暂无</span>
      </div>

      <div class="detail-image-block">
        <div class="field-label">我的作品</div>
        <img v-if="project.workImage" :src="project.workImage" class="detail-image" />
        <span v-else>暂无</span>
      </div>

    </div>

    <BeadWorkbench v-else :project="project" />
  </section>

  <section v-else class="card">加载中...</section>

  <div v-if="isEditingOverview" class="modal-mask" @click.self="cancelEditOverview">
    <div class="modal-panel edit-modal-panel" @click.stop>
      <h3 class="edit-modal-title">编辑概况</h3>
      <div class="overview-edit">
        <label class="edit-label">拼豆名称
          <input class="input edit-input modal-input" v-model="form.name" />
        </label>

        <label class="edit-label top-gap">拼豆 tag（可复用）</label>
        <div>
          <div class="row detail-tag-list">
            <div v-for="tag in existingTags" :key="tag" class="tag-item">
              <label class="tag-item-label">
                <input type="checkbox" :value="tag" v-model="selectedTags" /> {{ tag }}
              </label>
              <button class="tag-remove-btn" @click="removeTag(tag)">x</button>
            </div>
          </div>
          <div class="row add-tag-row">
            <input class="input fill-input modal-input" v-model="newTag" placeholder="新增 tag" />
            <button class="btn secondary subtle-action-btn" @click="addCustomTag">添加</button>
          </div>
        </div>

        <label class="edit-label top-gap">图纸来源链接
          <input class="input edit-input modal-input" v-model="form.sourceUrl" placeholder="https://..." />
        </label>

        <div class="detail-image-block">
          <label class="edit-label top-gap">拼豆图纸</label>
          <div class="row file-action-row">
            <input class="input fill-input modal-input" type="file" accept="image/*" @change="onPattern" />
            <button class="btn secondary subtle-action-btn" @click="requestClearPatternImage">清空拼豆图纸</button>
          </div>
          <img v-if="form.patternImage" :src="form.patternImage" class="detail-image" />
          <span v-else>暂无</span>
        </div>

        <div class="detail-image-block">
          <label class="edit-label top-gap">我的作品</label>
          <div class="row file-action-row">
            <input class="input fill-input modal-input" type="file" accept="image/*" @change="onWork" />
            <button class="btn secondary subtle-action-btn" @click="clearWorkImage">清空我的作品</button>
          </div>
          <img v-if="form.workImage" :src="form.workImage" class="detail-image" />
          <span v-else>暂无</span>
        </div>
      </div>
      <div class="row edit-modal-actions">
        <button class="btn secondary" @click="cancelEditOverview" :disabled="saving">取消</button>
        <button class="btn success" @click="saveProject" :disabled="saving">{{ saving ? '保存中...' : '保存详情' }}</button>
      </div>
    </div>
  </div>

  <div v-if="confirmDialog.visible" class="modal-mask" @click.self="closeConfirmDialog">
    <div class="modal-panel confirm-panel" @click.stop>
      <h3 class="confirm-title">确认操作</h3>
      <p class="confirm-text">{{ confirmDialog.message }}</p>
      <div class="row confirm-actions">
        <button class="btn secondary" @click="closeConfirmDialog">取消</button>
        <button class="btn warn" @click="submitConfirmDialog">确认</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { beadApi } from '@/api/beads'
import type { BeadProject } from '@/types'
import BeadWorkbench from '@/components/BeadWorkbench.vue'

const route = useRoute()
const tab = ref<'overview' | 'work'>('overview')
const project = ref<BeadProject | null>(null)
const saving = ref(false)
const isEditingOverview = ref(false)
const existingTags = ref<string[]>([])
const selectedTags = ref<string[]>([])
const newTag = ref('')
const confirmDialog = reactive({
  visible: false,
  type: '' as '' | 'remove-tag' | 'clear-pattern',
  payload: '',
  message: ''
})
const form = reactive({
  name: '',
  sourceUrl: '',
  patternImage: '',
  workImage: ''
})

function fileToBase64(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

function applyProjectToForm(detail: BeadProject) {
  form.name = detail.name || ''
  form.sourceUrl = detail.sourceUrl || ''
  form.patternImage = detail.patternImage || ''
  form.workImage = detail.workImage || ''
  selectedTags.value = [...(detail.tags || [])]
}

async function loadProject() {
  const id = Number(route.params.id)
  const [detail, tags] = await Promise.all([beadApi.detail(id), beadApi.tags()])
  project.value = detail
  existingTags.value = tags
  applyProjectToForm(detail)
}

async function onPattern(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  form.patternImage = await fileToBase64(file)
}

async function onWork(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  form.workImage = await fileToBase64(file)
}

function clearPatternImage() {
  form.patternImage = ''
}

function requestClearPatternImage() {
  if (!form.patternImage) return
  confirmDialog.visible = true
  confirmDialog.type = 'clear-pattern'
  confirmDialog.payload = ''
  confirmDialog.message = '确认删除拼豆图纸吗？删除后仅清空图纸字段，其他内容不变。'
}

function clearWorkImage() {
  form.workImage = ''
}

function addCustomTag() {
  const tag = newTag.value.trim()
  if (!tag) return
  if (!existingTags.value.includes(tag)) {
    existingTags.value.push(tag)
  }
  if (!selectedTags.value.includes(tag)) {
    selectedTags.value.push(tag)
  }
  newTag.value = ''
}

function startEditOverview() {
  if (!project.value) return
  applyProjectToForm(project.value)
  isEditingOverview.value = true
}

function cancelEditOverview() {
  if (!project.value) return
  applyProjectToForm(project.value)
  isEditingOverview.value = false
}

async function removeTag(tag: string) {
  const t = tag.trim()
  if (!t) return
  confirmDialog.visible = true
  confirmDialog.type = 'remove-tag'
  confirmDialog.payload = t
  confirmDialog.message = `确认删除 tag「${t}」吗？删除后，所有含该 tag 的拼豆都会移除这个 tag（其他内容不变）。`
}

function closeConfirmDialog() {
  confirmDialog.visible = false
  confirmDialog.type = ''
  confirmDialog.payload = ''
  confirmDialog.message = ''
}

async function submitConfirmDialog() {
  if (confirmDialog.type === 'clear-pattern') {
    clearPatternImage()
    closeConfirmDialog()
    return
  }

  if (confirmDialog.type !== 'remove-tag') {
    closeConfirmDialog()
    return
  }

  const t = confirmDialog.payload.trim()
  if (!t) {
    closeConfirmDialog()
    return
  }

  try {
    await beadApi.deleteTag(t)
    existingTags.value = existingTags.value.filter(item => item !== t)
    selectedTags.value = selectedTags.value.filter(item => item !== t)
    if (project.value) {
      project.value = {
        ...project.value,
        tags: (project.value.tags || []).filter(item => item !== t)
      }
    }
    alert(`已删除 tag：${t}`)
  } catch (error) {
    console.error(error)
    alert('删除 tag 失败，请稍后重试')
  } finally {
    closeConfirmDialog()
  }
}

async function saveProject() {
  if (!project.value) return
  if (!form.name.trim()) {
    alert('拼豆名称不能为空')
    return
  }
  saving.value = true
  try {
    const updated = await beadApi.update(project.value.id, {
      name: form.name,
      tags: selectedTags.value,
      sourceUrl: form.sourceUrl,
      patternImage: form.patternImage,
      workImage: form.workImage
    })
    project.value = updated
    applyProjectToForm(updated)
    isEditingOverview.value = false
    alert('详情已保存')
  } catch (error) {
    console.error(error)
    alert('保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    await loadProject()
  } catch (error) {
    console.error(error)
    alert('加载详情失败，请稍后重试')
  }
})
</script>

<style scoped>
.detail-page {
  display: grid;
  gap: 14px;
}

.detail-toolbar {
  margin-bottom: 2px;
}

.tabs-header {
  margin-bottom: 12px;
}

.page-tabs {
  display: inline-flex;
  border: 1px solid #c4d8ad;
  border-radius: 12px;
  overflow: hidden;
  background: #edf1d6;
}

.page-tab {
  border: none;
  background: transparent;
  color: #40513b;
  padding: 8px 14px;
  min-width: 108px;
  cursor: pointer;
  font: inherit;
  font-weight: 600;
}

.page-tab.active {
  background: #609966;
  color: #edf1d6;
}

.edit-icon-btn {
  min-width: 42px;
  padding: 7px 10px;
  font-size: 16px;
  line-height: 1;
}

.detail-overview {
  display: grid;
  gap: 12px;
}

.overview-state-bar {
  margin-bottom: 4px;
}

.overview-inline-grid {
  display: grid;
  grid-template-columns: minmax(84px, auto) 1fr;
  gap: 8px;
}

.inline-tag-list {
  margin-bottom: 0;
}

.overview-mode {
  color: #609966;
  font-size: 13px;
}

.overview-field {
  display: grid;
  gap: 6px;
}

.field-label {
  color: #609966;
  font-size: 12px;
}

.field-value {
  color: #40513b;
  word-break: break-all;
}

.title-link {
  display: inline-flex;
  width: fit-content;
  color: #40513b;
  font-weight: 600;
  text-decoration: underline;
  text-decoration-color: #9dc08b;
  text-underline-offset: 3px;
}

.detail-tag-card {
  padding: 12px;
}

.detail-tag-list {
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.overview-edit {
  font-size: inherit;
}

.edit-label {
  display: block;
  color: #609966;
  font-size: 12px;
}

.top-gap {
  margin-top: 10px;
}

.edit-input {
  margin-top: 10px;
}

.modal-input {
  height: 40px;
  font-size: 14px;
  line-height: 1.4;
}

.modal-input[type='file'] {
  padding-top: 8px;
  padding-bottom: 8px;
}

.add-tag-row,
.file-action-row {
  align-items: center;
  gap: 10px;
  flex-wrap: nowrap !important;
}

.fill-input {
  flex: 1;
  min-width: 0;
}

.tag-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 40px;
  padding: 7px 10px;
  border: 1px solid #c4d8ad;
  border-radius: 999px;
  background: #f9fbf1;
}

.tag-item-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
}

.tag-remove-btn {
  width: 20px;
  height: 20px;
  border: 1px solid #9dc08b;
  border-radius: 999px;
  background: #edf1d6;
  color: #40513b;
  line-height: 1;
  cursor: pointer;
  font-size: 12px;
}

.tag-remove-btn:hover {
  background: #9dc08b;
}

.subtle-action-btn {
  padding: 6px 10px;
  font-size: 12px;
  color: #609966;
  background: #f9fbf1;
  border-color: #c4d8ad;
  opacity: 0.85;
}

.subtle-action-btn:hover {
  opacity: 1;
}

.edit-modal-panel {
  width: min(860px, 96vw);
  max-height: 86vh;
  overflow: auto;
}

.edit-modal-title {
  margin: 0 0 6px 0;
  color: #40513b;
}

.edit-modal-actions {
  justify-content: flex-end;
  margin-top: 16px;
}

.confirm-panel {
  width: min(440px, 92vw);
}

.confirm-title {
  margin: 0 0 8px 0;
  color: #40513b;
}

.confirm-text {
  margin: 0;
  color: #40513b;
  line-height: 1.5;
}

.confirm-actions {
  justify-content: flex-end;
  margin-top: 14px;
}

.detail-image-block {
  display: grid;
  gap: 8px;
}

.detail-image {
  display: block;
  max-width: 380px;
  margin-top: 6px;
  border-radius: 12px;
  border: 1px solid #c4d8ad;
}

@media (max-width: 860px) {
  .tabs-header {
    align-items: flex-start;
  }

  .page-tab {
    min-width: 88px;
  }

  .overview-inline-grid {
    grid-template-columns: 1fr;
  }

  .file-action-row,
  .add-tag-row {
    flex-wrap: wrap !important;
  }

  .detail-image {
    max-width: 100%;
  }
}
</style>
