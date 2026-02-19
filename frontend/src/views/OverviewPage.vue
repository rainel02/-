<template>
  <section class="overview-page">
    <div class="row-between overview-toolbar">
      <h2 class="section-title">拼豆总览</h2>
      <div class="row overview-filter-row">
        <select class="select filter-select" v-model="selectedTag">
          <option value="">全部</option>
          <option v-for="tag in allTags" :key="tag" :value="tag">{{ tag }}</option>
        </select>
        <button class="btn refresh-btn" @click="load">刷新</button>
      </div>
    </div>

    <div class="grid-3">
      <div class="kanban-col" @dragover.prevent @drop="onDrop('DONE')">
        <h3>已拼完</h3>
        <div v-for="item in byStatus.DONE" :key="item.id" class="kanban-item" draggable="true" @dragstart="onDragStart(item.id)">
          <a v-if="item.sourceUrl" :href="item.sourceUrl" target="_blank" rel="noopener noreferrer" class="project-name">{{ item.name }}</a>
          <strong v-else class="project-name" @click="toggleCollapsed(item.id)">{{ item.name }}</strong>
          <template v-if="!isCollapsed(item.id)">
            <div class="row-between item-meta">
              <span class="badge">{{ item.tags.join(', ') || '无tag' }}</span>
            </div>
            <img v-if="item.patternImage" :src="item.patternImage" class="project-image" />
            <div class="row item-stats">
              <label>已拼</label>
              <input class="input" type="number" min="0" :value="item.quantityDone" @change="changeQty(item, 'done', $event)" />
              <label>计划</label>
              <input class="input" type="number" min="0" :value="item.quantityPlan" @change="changeQty(item, 'plan', $event)" />
            </div>
            <div class="row-between item-actions">
              <RouterLink :to="`/detail/${item.id}`">查看详情</RouterLink>
              <button class="btn warn" @click="removeProject(item.id)">删除</button>
            </div>
          </template>
        </div>
      </div>

      <div class="kanban-col" @dragover.prevent @drop="onDrop('IN_PROGRESS')">
        <h3>正在拼</h3>
        <div v-for="item in byStatus.IN_PROGRESS" :key="item.id" class="kanban-item" draggable="true" @dragstart="onDragStart(item.id)">
          <a v-if="item.sourceUrl" :href="item.sourceUrl" target="_blank" rel="noopener noreferrer" class="project-name">{{ item.name }}</a>
          <strong v-else class="project-name" @click="toggleCollapsed(item.id)">{{ item.name }}</strong>
          <template v-if="!isCollapsed(item.id)">
            <div class="row-between item-meta">
              <span class="badge">{{ item.tags.join(', ') || '无tag' }}</span>
            </div>
            <img v-if="item.patternImage" :src="item.patternImage" class="project-image" />
            <div class="row item-stats">
              <label>已拼</label>
              <input class="input" type="number" min="0" :value="item.quantityDone" @change="changeQty(item, 'done', $event)" />
              <label>计划</label>
              <input class="input" type="number" min="0" :value="item.quantityPlan" @change="changeQty(item, 'plan', $event)" />
            </div>
            <div class="row-between item-actions">
              <RouterLink :to="`/detail/${item.id}`">查看详情</RouterLink>
              <button class="btn warn" @click="removeProject(item.id)">删除</button>
            </div>
          </template>
        </div>
      </div>

      <div class="kanban-col" @dragover.prevent @drop="onDrop('TODO')">
        <h3>将要拼</h3>
        <div v-for="item in byStatus.TODO" :key="item.id" class="kanban-item" draggable="true" @dragstart="onDragStart(item.id)">
          <a v-if="item.sourceUrl" :href="item.sourceUrl" target="_blank" rel="noopener noreferrer" class="project-name">{{ item.name }}</a>
          <strong v-else class="project-name" @click="toggleCollapsed(item.id)">{{ item.name }}</strong>
          <template v-if="!isCollapsed(item.id)">
            <div class="row-between item-meta">
              <span class="badge">{{ item.tags.join(', ') || '无tag' }}</span>
            </div>
            <img v-if="item.patternImage" :src="item.patternImage" class="project-image" />
            <div class="row item-stats">
              <label>已拼</label>
              <input class="input" type="number" min="0" :value="item.quantityDone" @change="changeQty(item, 'done', $event)" />
              <label>计划</label>
              <input class="input" type="number" min="0" :value="item.quantityPlan" @change="changeQty(item, 'plan', $event)" />
            </div>
            <div class="row-between item-actions">
              <RouterLink :to="`/detail/${item.id}`">查看详情</RouterLink>
              <button class="btn warn" @click="removeProject(item.id)">删除</button>
            </div>
          </template>
        </div>
      </div>
    </div>

    <button class="scroll-top-btn" @click="scrollToTop">回到顶部</button>
  </section>

  <div v-if="confirmDialog.visible" class="modal-mask" @click.self="closeConfirmDialog">
    <div class="modal-panel confirm-panel" @click.stop>
      <h3 class="confirm-title">确认删除</h3>
      <p class="confirm-text">确认删除这个拼豆项目吗？删除后不可恢复。</p>
      <div class="row confirm-actions">
        <button class="btn secondary" @click="closeConfirmDialog">取消</button>
        <button class="btn warn" @click="submitRemoveProject">确认删除</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { beadApi } from '@/api/beads'
import type { BeadProjectSummary, BeadStatus } from '@/types'

const list = ref<BeadProjectSummary[]>([])
const draggingId = ref<number | null>(null)
const collapsedIds = ref<number[]>([])
const allTags = ref<string[]>([])
const selectedTag = ref('')
const confirmDialog = ref({ visible: false, projectId: 0 })

const filteredList = computed(() => {
  if (!selectedTag.value) {
    return list.value
  }
  return list.value.filter(v => (v.tags || []).includes(selectedTag.value))
})

const byStatus = computed(() => ({
  DONE: filteredList.value.filter(v => v.status === 'DONE'),
  IN_PROGRESS: filteredList.value.filter(v => v.status === 'IN_PROGRESS'),
  TODO: filteredList.value.filter(v => v.status === 'TODO')
}))

async function load() {
  try {
    const [projects, tags] = await Promise.all([beadApi.list(), beadApi.tags()])
    list.value = projects
    allTags.value = tags
  } catch (e) {
    console.error(e)
    alert('加载总览失败，请确认后端服务在 8080 端口运行')
  }
}

function onDragStart(id: number) {
  draggingId.value = id
}

async function onDrop(status: BeadStatus) {
  if (!draggingId.value) return
  const item = list.value.find(v => v.id === draggingId.value)
  if (!item) return
  try {
    const quantityDone = status === 'DONE' ? item.quantityPlan : item.quantityDone
    const updated = await beadApi.updateStatusAndQty(item.id, {
      status,
      quantityDone,
      quantityPlan: item.quantityPlan
    })
    const idx = list.value.findIndex(v => v.id === updated.id)
    if (idx >= 0) list.value[idx] = updated
  } catch (e) {
    console.error(e)
    alert('拖拽更新失败：请检查后端是否启动，或刷新后重试')
  } finally {
    draggingId.value = null
  }
}

async function changeQty(item: BeadProjectSummary, kind: 'done' | 'plan', e: Event) {
  const value = Number((e.target as HTMLInputElement).value || 0)
  try {
    const updated = await beadApi.updateStatusAndQty(item.id, {
      status: item.status,
      quantityDone: kind === 'done' ? value : item.quantityDone,
      quantityPlan: kind === 'plan' ? value : item.quantityPlan
    })
    const idx = list.value.findIndex(v => v.id === updated.id)
    if (idx >= 0) list.value[idx] = updated
  } catch (e) {
    console.error(e)
    alert('数量更新失败，请稍后重试')
  }
}

async function removeProject(id: number) {
  confirmDialog.value = { visible: true, projectId: id }
}

function closeConfirmDialog() {
  confirmDialog.value = { visible: false, projectId: 0 }
}

async function submitRemoveProject() {
  const id = confirmDialog.value.projectId
  if (!id) {
    closeConfirmDialog()
    return
  }
  try {
    await beadApi.remove(id)
    list.value = list.value.filter(v => v.id !== id)
  } catch (e) {
    console.error(e)
    alert('删除失败，请稍后重试')
  } finally {
    closeConfirmDialog()
  }
}

function isCollapsed(id: number) {
  return collapsedIds.value.includes(id)
}

function toggleCollapsed(id: number) {
  if (isCollapsed(id)) {
    collapsedIds.value = collapsedIds.value.filter(v => v !== id)
  } else {
    collapsedIds.value = [...collapsedIds.value, id]
  }
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(load)
</script>

<style scoped>
.overview-page {
  display: grid;
  gap: 16px;
}

.overview-toolbar {
  margin-bottom: 2px;
}

.overview-filter-row {
  flex-wrap: nowrap;
}

.filter-select {
  min-width: 180px;
}

.refresh-btn {
  white-space: nowrap;
}

.project-name {
  cursor: pointer;
  display: block;
  color: #40513b;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.item-meta,
.item-stats,
.item-actions {
  margin-top: 10px;
}

.project-image {
  width: 100%;
  max-height: 132px;
  object-fit: cover;
  border-radius: 10px;
  margin-top: 10px;
}

.scroll-top-btn {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 50;
  border: 1px solid #9dc08b;
  border-radius: 999px;
  background: #edf1d6;
  color: #40513b;
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 600;
  box-shadow: 0 8px 18px rgba(64, 81, 59, 0.16);
  cursor: pointer;
}

.confirm-panel {
  width: min(420px, 92vw);
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

@media (max-width: 768px) {
  .overview-filter-row {
    flex-wrap: nowrap;
  }
}
</style>
