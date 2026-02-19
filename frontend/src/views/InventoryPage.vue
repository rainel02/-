<template>
  <section class="card inventory-page">
    <h2 class="section-title">库存总览</h2>

    <div class="tabs">
      <button class="tab" :class="{active: tab==='stock'}" @click="tab='stock'">库存表</button>
      <button class="tab" :class="{active: tab==='usage'}" @click="tab='usage'">使用表</button>
      <button class="tab" :class="{active: tab==='demand'}" @click="tab='demand'">需求表</button>
    </div>

    <div class="quick-jump">
      <span class="quick-jump-label">快速跳转：</span>
      <button v-for="group in codeGroups" :key="group" class="btn secondary jump-btn" @click="jumpToCodeGroup(group)">{{ group }}</button>
    </div>

    <div class="row stock-toolbar" v-if="tab==='stock'">
      <label>排序字段</label>
      <select class="select stock-select" v-model="stockSortField">
        <option value="code">色号</option>
        <option value="warning">库存预警</option>
      </select>
      <button class="btn secondary" @click="stockDesc = !stockDesc">{{ stockDesc ? '降序' : '升序' }}</button>
      <button class="btn" @click="openRestockDialog">入库</button>
    </div>

    <div class="table-wrap" v-if="tab==='stock'" ref="stockTableWrapRef" @wheel="onTableWheel($event, 'stock')">
      <table class="table">
        <thead><tr><th>色号</th><th>入库总量</th><th>库存余量</th><th>使用量</th><th>提醒阈值</th><th>库存预警</th></tr></thead>
        <tbody>
          <tr v-for="row in sortedStock" :key="row.code" :data-code="normalizedCode(row.code)">
            <td>
              <span class="code-cell">
                <span class="color-dot" :style="{ background: getCodeHex(row.code) || '#fff' }"></span>
                {{ normalizedCode(row.code) }}
              </span>
            </td>
            <td class="narrow-col">
              <div class="saving-cell">
                <input
                  class="input compact-input"
                  type="number"
                  min="0"
                  v-model.number="inTotalDraft[row.code]"
                  @input="queueSaveInTotal(row.code)"
                  @blur="saveInTotal(row.code)"
                  @keydown.enter.prevent="saveInTotal(row.code)"
                />
                <span v-if="inTotalSaving[row.code]" class="saving-text">保存中...</span>
              </div>
            </td>
            <td>{{ row.remain }}</td>
            <td>{{ row.used }}</td>
            <td class="narrow-col">
              <div class="saving-cell">
                <input
                  class="input compact-input"
                  type="number"
                  min="0"
                  v-model.number="thresholdDraft[row.code]"
                  @input="queueSaveThreshold(row.code)"
                  @blur="saveThreshold(row.code)"
                  @keydown.enter.prevent="saveThreshold(row.code)"
                />
                <span v-if="thresholdSaving[row.code]" class="saving-text">保存中...</span>
              </div>
            </td>
            <td>
              <span class="warning-badge" :class="warningClass(row.warning)">{{ row.warning }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="table-wrap" v-if="tab==='usage'" ref="usageTableWrapRef" @wheel="onTableWheel($event, 'usage')">
      <table class="table sticky-usage">
        <thead>
          <tr>
            <th class="sticky-col usage-col-1">色号</th>
            <th class="sticky-col usage-col-2">总使用</th>
            <th v-for="project in usageProjects" :key="`usage-head-${project.projectId}`" class="usage-head-row1">
              <a v-if="project.projectUrl" :href="project.projectUrl" target="_blank">{{ project.projectName }}</a>
              <RouterLink v-else :to="`/detail/${project.projectId}`">{{ project.projectName }}</RouterLink>
            </th>
          </tr>
          <tr>
            <th class="sticky-col usage-col-1 usage-head-row2"></th>
            <th class="sticky-col usage-col-2 usage-head-row2"></th>
            <th v-for="project in usageProjects" :key="`usage-image-${project.projectId}`" class="usage-head-row2">
              <img v-if="project.projectImage" :src="project.projectImage" class="project-thumb" />
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="usage.length === 0">
            <td :colspan="2 + usageProjects.length">暂无使用数据</td>
          </tr>
          <tr v-for="row in usage" :key="row.code" :data-code="normalizedCode(row.code)">
            <td class="sticky-col usage-col-1 usage-body-sticky">
              <span class="code-cell">
                <span class="color-dot" :style="{ background: getCodeHex(row.code) || '#fff' }"></span>
                {{ normalizedCode(row.code) }}
              </span>
            </td>
            <td class="sticky-col usage-col-2 usage-body-sticky">{{ row.used }}</td>
            <td v-for="project in usageProjects" :key="`usage-body-${row.code}-${project.projectId}`">{{ findProjectUsed(row.projects, project.projectId) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="table-wrap" v-if="tab==='demand'" ref="demandTableWrapRef" @wheel="onTableWheel($event, 'demand')">
      <table class="table sticky-demand">
        <thead>
          <tr>
            <th class="sticky-col demand-col-1">色号</th>
            <th class="sticky-col demand-col-2">库存余量</th>
            <th class="sticky-col demand-col-3">总需求</th>
            <th class="sticky-col demand-col-4">需补库存量</th>
            <th v-for="project in demandProjects" :key="`demand-head-${project.projectId}`" class="demand-head-row1">
              <a v-if="project.projectUrl" :href="project.projectUrl" target="_blank">{{ project.projectName }}</a>
              <RouterLink v-else :to="`/detail/${project.projectId}`">{{ project.projectName }}</RouterLink>
            </th>
          </tr>
          <tr>
            <th class="sticky-col demand-col-1 demand-head-row2"></th>
            <th class="sticky-col demand-col-2 demand-head-row2"></th>
            <th class="sticky-col demand-col-3 demand-head-row2"></th>
            <th class="sticky-col demand-col-4 demand-head-row2"></th>
            <th v-for="project in demandProjects" :key="`demand-image-${project.projectId}`" class="demand-head-row2">
              <img v-if="project.projectImage" :src="project.projectImage" class="project-thumb" />
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="demand.length === 0">
            <td :colspan="4 + demandProjects.length">暂无需求数据</td>
          </tr>
          <tr v-for="row in demand" :key="row.code" :data-code="normalizedCode(row.code)">
            <td class="sticky-col demand-col-1 demand-body-sticky">
              <span class="code-cell">
                <span class="color-dot" :style="{ background: getCodeHex(row.code) || '#fff' }"></span>
                {{ normalizedCode(row.code) }}
              </span>
            </td>
            <td class="sticky-col demand-col-2 demand-body-sticky">{{ row.remain }}</td>
            <td class="sticky-col demand-col-3 demand-body-sticky">{{ totalRowProjectUsed(row.projects) }}</td>
            <td class="sticky-col demand-col-4 demand-body-sticky">{{ row.need }}</td>
            <td v-for="project in demandProjects" :key="`demand-body-${row.code}-${project.projectId}`">{{ findProjectUsed(row.projects, project.projectId) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>

  <div v-if="showRestockDialog" class="modal-mask">
    <div class="modal-panel">
      <div class="row-between dialog-toolbar">
        <h3 class="dialog-title">批量入库</h3>
        <button class="btn secondary" @click="showRestockDialog = false">关闭</button>
      </div>
      <div class="row dialog-actions">
        <label>统一填充</label>
        <input class="input uniform-input" type="number" min="0" v-model.number="uniformFillValue" />
        <button class="btn warn" @click="fillRestock(uniformFillValue)">应用到全部</button>
        <button class="btn success" @click="submitRestock">确认入库</button>
      </div>
      <div class="table-wrap" style="max-height:50vh;">
        <table class="table">
          <thead><tr><th>色号</th><th>入库数量</th></tr></thead>
          <tbody>
            <tr v-for="row in sortedStock" :key="`restock-${row.code}`">
              <td>
                <span class="code-cell">
                  <span class="color-dot" :style="{ background: getCodeHex(row.code) || '#fff' }"></span>
                  {{ normalizedCode(row.code) }}
                </span>
              </td>
              <td class="restock-col">
                <input class="input" type="number" min="0" v-model.number="restockDraft[row.code]" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { beadApi } from '@/api/beads'
import { getCodeHex, normalizeColorCode } from '@/utils/color-map'
import type { InventoryRow, UsageRow, DemandRow, TodoProjectRow, UsageProjectItem } from '@/types'

const tab = ref<'stock' | 'usage' | 'demand'>('stock')
const codeGroups = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'M']

const stock = ref<InventoryRow[]>([])
const usage = ref<UsageRow[]>([])
const demand = ref<DemandRow[]>([])
const todoProjects = ref<TodoProjectRow[]>([])

const stockSortField = ref<'code' | 'warning'>('code')
const stockDesc = ref(false)

const thresholdDraft = ref<Record<string, number>>({})
const thresholdSaving = ref<Record<string, boolean>>({})
const inTotalDraft = ref<Record<string, number>>({})
const inTotalSaving = ref<Record<string, boolean>>({})
const showRestockDialog = ref(false)
const restockDraft = ref<Record<string, number>>({})
const uniformFillValue = ref(1000)
const inTotalSaveTimers = new Map<string, number>()
const thresholdSaveTimers = new Map<string, number>()

const stockTableWrapRef = ref<HTMLElement | null>(null)
const usageTableWrapRef = ref<HTMLElement | null>(null)
const demandTableWrapRef = ref<HTMLElement | null>(null)

const usageProjects = computed(() => collectProjects(usage.value.flatMap(row => row.projects)))
const demandProjects = computed(() => collectProjects(demand.value.flatMap(row => row.projects)))

const sortedStock = computed(() => {
  const arr = [...stock.value]
  arr.sort((a, b) => {
    if (stockSortField.value === 'code') {
      return compareColorCode(a.code, b.code)
    }
    return String(a.warning).localeCompare(String(b.warning), 'zh-CN')
  })
  if (stockDesc.value) arr.reverse()
  return arr
})

function normalizedCode(code: string) {
  return normalizeColorCode(code)
}

function compareColorCode(leftCode: string, rightCode: string) {
  const left = normalizeColorCode(leftCode || '')
  const right = normalizeColorCode(rightCode || '')
  const regex = /^([A-Z]+)(\d+)$/
  const lm = left.match(regex)
  const rm = right.match(regex)
  if (lm && rm) {
    const letterDiff = lm[1].localeCompare(rm[1], 'zh-CN')
    if (letterDiff !== 0) {
      return letterDiff
    }
    return Number(lm[2]) - Number(rm[2])
  }
  return left.localeCompare(right, 'zh-CN')
}

function syncDrafts(rows: InventoryRow[]) {
  const thresholdMap: Record<string, number> = {}
  const inTotalMap: Record<string, number> = {}
  const restockMap: Record<string, number> = {}
  for (const row of rows) {
    const code = normalizeColorCode(row.code)
    thresholdMap[code] = row.alertThreshold ?? 0
    inTotalMap[code] = row.inTotal ?? 0
    restockMap[code] = restockDraft.value[code] ?? 0
  }
  thresholdDraft.value = thresholdMap
  inTotalDraft.value = inTotalMap
  restockDraft.value = restockMap
}

async function reloadAll() {
  const [stockResp, usageResp, demandResp, todoResp] = await Promise.allSettled([
    beadApi.inventory.stock(),
    beadApi.inventory.usage(),
    beadApi.inventory.demand(),
    beadApi.inventory.todoProjects()
  ])

  stock.value = stockResp.status === 'fulfilled' ? stockResp.value.map(row => ({ ...row, code: normalizeColorCode(row.code) })) : []
  usage.value = usageResp.status === 'fulfilled' ? usageResp.value.map(row => ({ ...row, code: normalizeColorCode(row.code) })) : []
  demand.value = demandResp.status === 'fulfilled' ? demandResp.value.map(row => ({ ...row, code: normalizeColorCode(row.code) })) : []
  todoProjects.value = todoResp.status === 'fulfilled' ? todoResp.value : []
  syncDrafts(stock.value)

  if (stockResp.status === 'rejected' || usageResp.status === 'rejected' || demandResp.status === 'rejected') {
    console.error('库存页关键接口存在失败', {
      stock: stockResp,
      usage: usageResp,
      demand: demandResp
    })
    alert('部分库存接口请求失败，请检查后端是否为最新版本并已重启')
  }
}

function collectProjects(items: UsageProjectItem[]) {
  const map = new Map<number, UsageProjectItem>()
  for (const item of items) {
    if (!map.has(item.projectId)) {
      map.set(item.projectId, item)
    }
  }
  return [...map.values()].sort((a, b) => String(a.projectName || '').localeCompare(String(b.projectName || ''), 'zh-CN'))
}

function findProjectUsed(items: UsageProjectItem[], projectId: number) {
  const found = items.find(item => item.projectId === projectId)
  return found?.used ?? 0
}

function totalRowProjectUsed(items: UsageProjectItem[]) {
  return items.reduce((sum, item) => sum + Number(item.used || 0), 0)
}

async function saveThreshold(code: string) {
  const normalized = normalizeColorCode(code)
  const value = Math.max(0, Number(thresholdDraft.value[normalized] ?? 0))
  const current = stock.value.find(row => row.code === normalized)?.alertThreshold ?? 0
  if (value === current) {
    return
  }

  thresholdSaving.value = { ...thresholdSaving.value, [normalized]: true }
  try {
    stock.value = await beadApi.inventory.updateThresholds({ [normalized]: value })
    stock.value = stock.value.map(row => ({ ...row, code: normalizeColorCode(row.code) }))
    syncDrafts(stock.value)
  } catch (error) {
    console.error(error)
    alert(`色号 ${normalized} 阈值保存失败`)
    await reloadAll()
  } finally {
    thresholdSaving.value = { ...thresholdSaving.value, [normalized]: false }
  }
}

function queueSaveThreshold(code: string) {
  const normalized = normalizeColorCode(code)
  const existing = thresholdSaveTimers.get(normalized)
  if (existing) {
    window.clearTimeout(existing)
  }
  const timer = window.setTimeout(() => {
    thresholdSaveTimers.delete(normalized)
    void saveThreshold(normalized)
  }, 450)
  thresholdSaveTimers.set(normalized, timer)
}

async function saveInTotal(code: string) {
  const normalized = normalizeColorCode(code)
  const value = Math.max(0, Number(inTotalDraft.value[normalized] ?? 0))
  const current = stock.value.find(row => row.code === normalized)?.inTotal ?? 0
  if (value === current) {
    return
  }

  inTotalSaving.value = { ...inTotalSaving.value, [normalized]: true }
  try {
    stock.value = await beadApi.inventory.updateInTotals({ [normalized]: value })
    stock.value = stock.value.map(row => ({ ...row, code: normalizeColorCode(row.code) }))
    syncDrafts(stock.value)
  } catch (error) {
    console.error(error)
    alert(`色号 ${normalized} 入库总量保存失败`) 
    await reloadAll()
  } finally {
    inTotalSaving.value = { ...inTotalSaving.value, [normalized]: false }
  }
}

function queueSaveInTotal(code: string) {
  const normalized = normalizeColorCode(code)
  const existing = inTotalSaveTimers.get(normalized)
  if (existing) {
    window.clearTimeout(existing)
  }
  const timer = window.setTimeout(() => {
    inTotalSaveTimers.delete(normalized)
    void saveInTotal(normalized)
  }, 450)
  inTotalSaveTimers.set(normalized, timer)
}

function openRestockDialog() {
  showRestockDialog.value = true
}

function fillRestock(value: number) {
  const next: Record<string, number> = {}
  const safeValue = Math.max(0, Number(value || 0))
  for (const row of stock.value) {
    next[row.code] = safeValue
  }
  restockDraft.value = next
}

function jumpToCodeGroup(group: string) {
  const wrap = tab.value === 'stock'
    ? stockTableWrapRef.value
    : tab.value === 'usage'
      ? usageTableWrapRef.value
      : demandTableWrapRef.value
  if (!wrap) {
    return
  }
  const target = wrap.querySelector(`tr[data-code^="${group}"]`) as HTMLElement | null
  if (target) {
    target.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
}

function onTableWheel(event: WheelEvent, tableType: 'stock' | 'usage' | 'demand') {
  if (!event.shiftKey) {
    return
  }

  const wrap = tableType === 'stock'
    ? stockTableWrapRef.value
    : tableType === 'usage'
      ? usageTableWrapRef.value
      : demandTableWrapRef.value

  if (!wrap) {
    return
  }

  const canScrollX = wrap.scrollWidth > wrap.clientWidth
  if (!canScrollX) {
    return
  }

  const horizontalDelta = event.deltaY === 0 ? event.deltaX : event.deltaY

  if (horizontalDelta === 0) {
    return
  }

  event.preventDefault()
  wrap.scrollLeft += horizontalDelta
}

function warningClass(warning: string) {
  return String(warning || '').includes('急需补货') ? 'warning-low' : 'warning-ok'
}

async function submitRestock() {
  const payload: Record<string, number> = {}
  for (const [code, quantity] of Object.entries(restockDraft.value)) {
    const normalized = normalizeColorCode(code)
    const value = Math.max(0, Number(quantity || 0))
    if (value > 0) {
      payload[normalized] = (payload[normalized] || 0) + value
    }
  }
  if (Object.keys(payload).length === 0) {
    alert('请先填写至少一个色号的入库数量')
    return
  }
  await beadApi.inventory.restock(payload)
  await reloadAll()
  showRestockDialog.value = false
  alert('入库完成')
}

onBeforeUnmount(() => {
  for (const timer of inTotalSaveTimers.values()) {
    window.clearTimeout(timer)
  }
  inTotalSaveTimers.clear()

  for (const timer of thresholdSaveTimers.values()) {
    window.clearTimeout(timer)
  }
  thresholdSaveTimers.clear()
})

onMounted(async () => {
  try {
    await reloadAll()
  } catch (error) {
    console.error(error)
    alert('库存数据加载失败，请确认后端服务已启动')
  }
})
</script>

<style scoped>
.inventory-page {
  display: grid;
  gap: 12px;
}

.quick-jump {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.quick-jump-label,
.saving-text {
  font-size: 12px;
  color: #609966;
}

.stock-toolbar {
  margin-bottom: 10px;
}

.stock-select {
  max-width: 220px;
}

.jump-btn {
  min-width: 36px;
  padding: 4px 8px;
}

.narrow-col {
  min-width: 70px;
}

.saving-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.code-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.color-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 1px solid #9dc08b;
  flex-shrink: 0;
}

.project-thumb {
  max-width: 90px;
  max-height: 90px;
  border-radius: 10px;
  object-fit: cover;
  border: 1px solid #c4d8ad;
}

.compact-input {
  width: 70px;
  min-width: 70px;
  padding: 4px 6px;
}

.warning-badge {
  display: inline-block;
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
}

.warning-ok {
  color: #40513b;
  background: #dce8c6;
}

.warning-low {
  color: #edf1d6;
  background: #40513b;
}

.table-wrap {
  overflow-x: auto;
  overflow-y: auto;
}

.table-wrap .table {
  width: max-content;
  min-width: 100%;
}

.table-wrap .table th,
.table-wrap .table td {
  white-space: nowrap;
}

.sticky-usage,
.sticky-demand {
  border-collapse: separate;
  border-spacing: 0;
}

.sticky-usage thead tr:nth-child(1) th,
.sticky-demand thead tr:nth-child(1) th {
  position: sticky;
  top: 0;
  z-index: 30;
  background: #edf1d6;
  height: 40px;
}

.sticky-usage thead tr:nth-child(2) th,
.sticky-demand thead tr:nth-child(2) th {
  position: sticky;
  top: 40px;
  z-index: 29;
  background: #edf1d6;
}

.sticky-col {
  position: sticky;
  background: #fff;
}

.sticky-usage thead tr:nth-child(1) .sticky-col,
.sticky-demand thead tr:nth-child(1) .sticky-col {
  z-index: 52;
  background: #edf1d6;
}

.sticky-usage thead tr:nth-child(2) .sticky-col,
.sticky-demand thead tr:nth-child(2) .sticky-col {
  z-index: 51;
  background: #edf1d6;
}

.sticky-usage tbody .sticky-col,
.sticky-demand tbody .sticky-col {
  z-index: 18;
}

.usage-col-1 { left: 0; min-width: 90px; z-index: 18; }
.usage-col-2 { left: 90px; min-width: 90px; z-index: 18; }

.demand-col-1 { left: 0; min-width: 90px; z-index: 18; }
.demand-col-2 { left: 90px; min-width: 100px; z-index: 18; }
.demand-col-3 { left: 190px; min-width: 90px; z-index: 18; }
.demand-col-4 { left: 280px; min-width: 100px; z-index: 18; }

.usage-head-row2,
.demand-head-row2 {
  background: #edf1d6;
}

.usage-body-sticky,
.demand-body-sticky {
  background: #fff;
}

.dialog-toolbar {
  margin-bottom: 10px;
}

.dialog-title {
  margin: 0;
  color: #40513b;
}

.dialog-actions {
  margin-bottom: 10px;
  align-items: center;
}

.uniform-input {
  max-width: 120px;
}

.restock-col {
  min-width: 130px;
}

@media (max-width: 900px) {
  .stock-select,
  .uniform-input {
    max-width: 100%;
  }

  .usage-col-1,
  .demand-col-1 {
    min-width: 80px;
  }
}
</style>
