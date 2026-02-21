<template>
  <section class="card new-page">
    <h2 class="section-title">新建拼豆</h2>
    <div class="row new-layout">
      <div class="new-col">
        <label>拼豆名称*<input class="input" v-model="form.name" /></label>
        <label>拼豆 tag</label>
        <div class="card tag-card">
          <div class="row tag-list">
            <div v-for="tag in existingTags" :key="tag" class="tag-item">
              <label class="tag-item-label">
                <input type="checkbox" :value="tag" v-model="selectedTags" /> {{ tag }}
              </label>
              <button class="tag-remove-btn" @click="removeTag(tag)">x</button>
            </div>
          </div>
          <div class="row">
            <input class="input" v-model="newTag" placeholder="新增 tag" />
            <button class="btn secondary" @click="addCustomTag">添加</button>
          </div>
          <div class="selected-tags">已选：{{ selectedTags.join(', ') || '无' }}</div>
        </div>
        <label>拼豆状态*
          <select class="select" v-model="form.status">
            <option value="DONE">已拼完</option>
            <option value="IN_PROGRESS">正在拼</option>
            <option value="TODO">将要拼</option>
          </select>
        </label>
        <label>图纸来源链接<input class="input" v-model="form.sourceUrl" placeholder="https://..." /></label>
        <label>拼豆图纸*<input class="input" type="file" accept="image/*" @change="onPattern" /></label>
        <label>我的作品<input class="input" type="file" accept="image/*" @change="onWork" /></label>
      </div>

      <div class="new-col">
        <h3 class="extract-title">提取色号与数量*</h3>
        <div v-if="form.patternImage" class="card crop-card">
          <div class="helper-text">可拖动四个角控制点定位“色号+数量”区域，再点击提取</div>
          <div
            ref="cropStageRef"
            class="crop-stage"
            @mousemove="onCropMouseMove"
            @mouseup="onCropMouseUp"
            @mouseleave="onCropMouseUp"
          >
            <img ref="patternImgRef" :src="form.patternImage" class="crop-image" @load="onPatternImageLoad" />
            <div v-if="hasSelection" class="crop-mask" :style="cropRectStyle"></div>
            <div v-if="hasSelection" class="crop-handle tl" :style="topLeftHandleStyle" @mousedown="onHandleMouseDown('TL', $event)"></div>
            <div v-if="hasSelection" class="crop-handle tr" :style="topRightHandleStyle" @mousedown="onHandleMouseDown('TR', $event)"></div>
            <div v-if="hasSelection" class="crop-handle bl" :style="bottomLeftHandleStyle" @mousedown="onHandleMouseDown('BL', $event)"></div>
            <div v-if="hasSelection" class="crop-handle br" :style="bottomRightHandleStyle" @mousedown="onHandleMouseDown('BR', $event)"></div>
            <div v-show="magnifierVisible" class="magnifier" :style="magnifierStyle">
              <canvas ref="magnifierCanvasRef" width="150" height="150"></canvas>
            </div>
          </div>
          <div class="row selection-row">
            <div class="helper-text">{{ selectionHint }}</div>
            <button class="btn secondary" @click="resetSelection">重置选区</button>
          </div>
          <div class="nudge-panel">
            <div class="row nudge-toolbar">
              <label class="helper-text">微调角点</label>
              <select class="select nudge-select" v-model="selectedCorner">
                <option value="TL">左上角</option>
                <option value="TR">右上角</option>
                <option value="BL">左下角</option>
                <option value="BR">右下角</option>
              </select>
              <label class="helper-text">步长</label>
              <input class="input nudge-input" type="number" min="1" max="20" v-model.number="nudgeStep" />
            </div>
            <div class="nudge-grid">
              <button class="btn secondary" @click="nudgeSelection(0, -nudgeStep)">↑</button>
              <button class="btn secondary" @click="nudgeSelection(-nudgeStep, 0)">←</button>
              <button class="btn secondary" @click="nudgeSelection(nudgeStep, 0)">→</button>
              <button class="btn secondary" @click="nudgeSelection(0, nudgeStep)">↓</button>
            </div>
          </div>
        </div>
        <button class="btn secondary" @click="extract" :disabled="!form.patternImage || extracting">{{ extracting ? '识别中...' : '从图纸提取色号' }}</button>
        <div class="table-wrap color-table-wrap">
          <table class="table">
            <thead><tr><th>色号</th><th>数量</th><th></th></tr></thead>
            <tbody>
              <tr v-for="(row, idx) in form.requiredColors" :key="idx">
                <td><input class="input" v-model="row.code" /></td>
                <td><input class="input" type="number" min="1" v-model.number="row.quantity" /></td>
                <td class="color-action-col">
                  <button class="icon-action-btn add" @click="insertColorRowAfter(idx)">+</button>
                  <button class="icon-action-btn remove" @click="removeColor(idx)">×</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="row page-actions">
      <button class="btn success" @click="save">保存</button>
      <RouterLink class="btn secondary" to="/overview">返回总览</RouterLink>
    </div>
  </section>

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
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { beadApi } from '@/api/beads'
import type { BeadProject } from '@/types'

const router = useRouter()
const existingTags = ref<string[]>([])
const selectedTags = ref<string[]>([])
const newTag = ref('')
const confirmDialog = reactive({
  visible: false,
  tag: '',
  message: ''
})
const extracting = ref(false)
const cropStageRef = ref<HTMLDivElement | null>(null)
const patternImgRef = ref<HTMLImageElement | null>(null)
const magnifierCanvasRef = ref<HTMLCanvasElement | null>(null)
const activeHandle = ref<'TL' | 'TR' | 'BL' | 'BR' | null>(null)
const selectedCorner = ref<'TL' | 'TR' | 'BL' | 'BR'>('BR')
const nudgeStep = ref(1)
const cropRect = reactive({ x: 0, y: 0, w: 0, h: 0 })
const magnifierVisible = ref(false)
const magnifierPos = reactive({ x: 0, y: 0 })
const MIN_CROP_SIZE = 8

const form = reactive<Partial<BeadProject>>({
  name: '',
  tags: [],
  status: 'TODO',
  sourceUrl: '',
  patternImage: '',
  workImage: '',
  requiredColors: []
})

function fileToBase64(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

function initSelectionByCorners() {
  const bounds = getImageBoundsInStage()
  if (!bounds) return
  const marginX = bounds.width * 0.1
  const marginY = bounds.height * 0.1
  cropRect.x = bounds.left + marginX
  cropRect.y = bounds.top + marginY
  cropRect.w = Math.max(MIN_CROP_SIZE, bounds.width - marginX * 2)
  cropRect.h = Math.max(MIN_CROP_SIZE, bounds.height - marginY * 2)
  activeHandle.value = null
}

function onHandleMouseDown(handle: 'TL' | 'TR' | 'BL' | 'BR', event: MouseEvent) {
  event.preventDefault()
  event.stopPropagation()
  activeHandle.value = handle
  selectedCorner.value = handle
  magnifierVisible.value = true
  const point = toStagePoint(event)
  if (point) {
    updateMagnifier(point)
    updateMagnifierPosition(point)
  }
}

function onCropMouseMove(event: MouseEvent) {
  if (!activeHandle.value) return
  const point = toStagePoint(event)
  const bounds = getImageBoundsInStage()
  if (!point || !bounds) return

  updateMagnifier(point)
  updateMagnifierPosition(point)

  const right = cropRect.x + cropRect.w
  const bottom = cropRect.y + cropRect.h

  if (activeHandle.value === 'TL') {
    const newX = Math.min(Math.max(point.x, bounds.left), right - MIN_CROP_SIZE)
    const newY = Math.min(Math.max(point.y, bounds.top), bottom - MIN_CROP_SIZE)
    cropRect.w = right - newX
    cropRect.h = bottom - newY
    cropRect.x = newX
    cropRect.y = newY
    return
  }

  if (activeHandle.value === 'TR') {
    const maxRight = bounds.left + bounds.width
    const newRight = Math.max(Math.min(point.x, maxRight), cropRect.x + MIN_CROP_SIZE)
    const newY = Math.min(Math.max(point.y, bounds.top), bottom - MIN_CROP_SIZE)
    cropRect.w = newRight - cropRect.x
    cropRect.h = bottom - newY
    cropRect.y = newY
    return
  }

  if (activeHandle.value === 'BL') {
    const maxBottom = bounds.top + bounds.height
    const newX = Math.min(Math.max(point.x, bounds.left), right - MIN_CROP_SIZE)
    const newBottom = Math.max(Math.min(point.y, maxBottom), cropRect.y + MIN_CROP_SIZE)
    cropRect.w = right - newX
    cropRect.h = newBottom - cropRect.y
    cropRect.x = newX
    return
  }

  const maxRight = bounds.left + bounds.width
  const maxBottom = bounds.top + bounds.height
  const newRight = Math.max(Math.min(point.x, maxRight), cropRect.x + MIN_CROP_SIZE)
  const newBottom = Math.max(Math.min(point.y, maxBottom), cropRect.y + MIN_CROP_SIZE)
  cropRect.w = newRight - cropRect.x
  cropRect.h = newBottom - cropRect.y
}

function onCropMouseUp() {
  activeHandle.value = null
  magnifierVisible.value = false
  if (cropRect.w < MIN_CROP_SIZE || cropRect.h < MIN_CROP_SIZE) {
    initSelectionByCorners()
  }
}

function refreshNudgeMagnifier() {
  const point = selectedCorner.value === 'TL'
    ? { x: cropRect.x, y: cropRect.y }
    : selectedCorner.value === 'TR'
      ? { x: cropRect.x + cropRect.w, y: cropRect.y }
      : selectedCorner.value === 'BL'
        ? { x: cropRect.x, y: cropRect.y + cropRect.h }
        : { x: cropRect.x + cropRect.w, y: cropRect.y + cropRect.h }

  magnifierVisible.value = true
  updateMagnifier(point)
  updateMagnifierPosition(point)
}

function nudgeSelection(dx: number, dy: number) {
  const bounds = getImageBoundsInStage()
  if (!bounds) return

  const stepX = Number.isFinite(dx) ? dx : 0
  const stepY = Number.isFinite(dy) ? dy : 0
  if (!stepX && !stepY) return

  const right = cropRect.x + cropRect.w
  const bottom = cropRect.y + cropRect.h
  const maxRight = bounds.left + bounds.width
  const maxBottom = bounds.top + bounds.height

  if (selectedCorner.value === 'TL') {
    const nextX = Math.min(Math.max(cropRect.x + stepX, bounds.left), right - MIN_CROP_SIZE)
    const nextY = Math.min(Math.max(cropRect.y + stepY, bounds.top), bottom - MIN_CROP_SIZE)
    cropRect.w = right - nextX
    cropRect.h = bottom - nextY
    cropRect.x = nextX
    cropRect.y = nextY
    refreshNudgeMagnifier()
    return
  }

  if (selectedCorner.value === 'TR') {
    const nextRight = Math.max(Math.min(right + stepX, maxRight), cropRect.x + MIN_CROP_SIZE)
    const nextY = Math.min(Math.max(cropRect.y + stepY, bounds.top), bottom - MIN_CROP_SIZE)
    cropRect.w = nextRight - cropRect.x
    cropRect.h = bottom - nextY
    cropRect.y = nextY
    refreshNudgeMagnifier()
    return
  }

  if (selectedCorner.value === 'BL') {
    const nextX = Math.min(Math.max(cropRect.x + stepX, bounds.left), right - MIN_CROP_SIZE)
    const nextBottom = Math.max(Math.min(bottom + stepY, maxBottom), cropRect.y + MIN_CROP_SIZE)
    cropRect.w = right - nextX
    cropRect.h = nextBottom - cropRect.y
    cropRect.x = nextX
    refreshNudgeMagnifier()
    return
  }

  const nextRight = Math.max(Math.min(right + stepX, maxRight), cropRect.x + MIN_CROP_SIZE)
  const nextBottom = Math.max(Math.min(bottom + stepY, maxBottom), cropRect.y + MIN_CROP_SIZE)
  cropRect.w = nextRight - cropRect.x
  cropRect.h = nextBottom - cropRect.y
  refreshNudgeMagnifier()
}

function updateMagnifierPosition(point: { x: number; y: number }) {
  if (!cropStageRef.value) return
  const size = 150
  const margin = 12
  const stageWidth = cropStageRef.value.clientWidth
  const stageHeight = cropStageRef.value.clientHeight

  let x = point.x + margin
  let y = point.y + margin

  if (activeHandle.value === 'BR' || activeHandle.value === 'BL') {
    y = point.y - size - margin
  }

  if (x + size > stageWidth) x = stageWidth - size
  if (y + size > stageHeight) y = stageHeight - size
  if (x < 0) x = 0
  if (y < 0) y = 0

  magnifierPos.x = x
  magnifierPos.y = y
}

function updateMagnifier(point: { x: number; y: number }) {
  if (!patternImgRef.value || !magnifierCanvasRef.value) return
  const bounds = getImageBoundsInStage()
  if (!bounds) return

  const image = patternImgRef.value
  const canvas = magnifierCanvasRef.value
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const scaleX = image.naturalWidth / bounds.width
  const scaleY = image.naturalHeight / bounds.height
  const localX = point.x - bounds.left
  const localY = point.y - bounds.top
  const naturalX = localX * scaleX
  const naturalY = localY * scaleY

  const size = 150
  const zoom = 4
  const sourceSize = size / zoom
  const sx = naturalX - sourceSize / 2
  const sy = naturalY - sourceSize / 2

  ctx.clearRect(0, 0, size, size)
  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, size, size)
  ctx.drawImage(image, sx, sy, sourceSize, sourceSize, 0, 0, size, size)

  ctx.strokeStyle = 'rgba(255, 0, 0, 0.85)'
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.moveTo(size / 2, 0)
  ctx.lineTo(size / 2, size)
  ctx.moveTo(0, size / 2)
  ctx.lineTo(size, size / 2)
  ctx.stroke()
}

function getImageBoundsInStage() {
  if (!cropStageRef.value || !patternImgRef.value) return null
  const stageRect = cropStageRef.value.getBoundingClientRect()
  const imageRect = patternImgRef.value.getBoundingClientRect()

  const naturalWidth = patternImgRef.value.naturalWidth
  const naturalHeight = patternImgRef.value.naturalHeight
  if (!naturalWidth || !naturalHeight) {
    return {
      left: imageRect.left - stageRect.left,
      top: imageRect.top - stageRect.top,
      width: imageRect.width,
      height: imageRect.height
    }
  }

  const boxWidth = imageRect.width
  const boxHeight = imageRect.height
  const naturalRatio = naturalWidth / naturalHeight
  const boxRatio = boxWidth / boxHeight

  let renderWidth = boxWidth
  let renderHeight = boxHeight
  let offsetX = 0
  let offsetY = 0

  if (naturalRatio > boxRatio) {
    renderWidth = boxWidth
    renderHeight = boxWidth / naturalRatio
    offsetY = (boxHeight - renderHeight) / 2
  } else {
    renderHeight = boxHeight
    renderWidth = boxHeight * naturalRatio
    offsetX = (boxWidth - renderWidth) / 2
  }

  return {
    left: imageRect.left - stageRect.left + offsetX,
    top: imageRect.top - stageRect.top + offsetY,
    width: renderWidth,
    height: renderHeight
  }
}

function toStagePoint(event: MouseEvent) {
  const bounds = getImageBoundsInStage()
  if (!cropStageRef.value || !bounds) return null
  const stageRect = cropStageRef.value.getBoundingClientRect()
  const right = bounds.left + bounds.width
  const bottom = bounds.top + bounds.height
  const x = Math.max(bounds.left, Math.min(event.clientX - stageRect.left, right))
  const y = Math.max(bounds.top, Math.min(event.clientY - stageRect.top, bottom))
  return { x, y }
}

function onPatternImageLoad() {
  initSelectionByCorners()
}

function resetSelection() {
  initSelectionByCorners()
}

async function onPattern(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  form.patternImage = await fileToBase64(file)
  await nextTick()
  initSelectionByCorners()
}

async function onWork(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  form.workImage = await fileToBase64(file)
}

async function extract() {
  if (!form.patternImage) return
  if (!hasSelection.value) {
    alert('请先在图纸上框选色号说明区域')
    return
  }
  extracting.value = true
  try {
    const selectedImage = cropSelectedImageBase64()
    const rows = await beadApi.extractColors({ imageBase64: selectedImage })
    form.requiredColors = rows
    if (rows.length === 0) {
      alert('未识别到有效色号，请调整图纸清晰度后重试，或手动添加。')
    }
  } catch (error) {
    console.error(error)
    alert('图纸识别失败，请重试或手动添加色号。')
  } finally {
    extracting.value = false
  }
}

function removeColor(index: number) {
  form.requiredColors?.splice(index, 1)
}

function insertColorRowAfter(index: number) {
  if (!form.requiredColors) {
    form.requiredColors = []
  }
  const target = Math.max(0, Math.min(index + 1, form.requiredColors.length))
  form.requiredColors.splice(target, 0, { code: '', quantity: 1 })
}

async function save() {
  form.tags = selectedTags.value
  if (!form.name?.trim()) return alert('拼豆名称必填')
  if (!form.status) return alert('拼豆状态必填')
  if (!form.patternImage) return alert('拼豆图纸必填')
  if (!form.requiredColors || form.requiredColors.length === 0) return alert('提取色号与数量必填')

  const created = await beadApi.create({
    ...form,
    quantityDone: 0,
    quantityPlan: 1
  })
  router.push(`/detail/${created.id}`)
}

async function addCustomTag() {
  const t = newTag.value.trim()
  if (!t) return
  try {
    await beadApi.addTag(t)
    if (!existingTags.value.includes(t)) {
      existingTags.value.push(t)
    }
    if (!selectedTags.value.includes(t)) {
      selectedTags.value.push(t)
    }
    newTag.value = ''
  } catch (error) {
    console.error(error)
    alert('新增 tag 失败，请稍后重试')
  }
}

async function removeTag(tag: string) {
  const t = tag.trim()
  if (!t) return
  confirmDialog.visible = true
  confirmDialog.tag = t
  confirmDialog.message = `确认删除 tag「${t}」吗？删除后，所有含该 tag 的拼豆都会移除这个 tag（其他内容不变）。`
}

function closeConfirmDialog() {
  confirmDialog.visible = false
  confirmDialog.tag = ''
  confirmDialog.message = ''
}

async function submitConfirmDialog() {
  const t = confirmDialog.tag.trim()
  if (!t) {
    closeConfirmDialog()
    return
  }

  try {
    await beadApi.deleteTag(t)
    existingTags.value = existingTags.value.filter(item => item !== t)
    selectedTags.value = selectedTags.value.filter(item => item !== t)
    alert(`已删除 tag：${t}`)
  } catch (error) {
    console.error(error)
    alert('删除 tag 失败，请稍后重试')
  } finally {
    closeConfirmDialog()
  }
}

function cropSelectedImageBase64() {
  if (!patternImgRef.value || !hasSelection.value) {
    throw new Error('未选择裁剪区域')
  }
  const bounds = getImageBoundsInStage()
  if (!bounds) {
    throw new Error('图像位置无效')
  }
  const imageEl = patternImgRef.value
  const displayWidth = bounds.width
  const displayHeight = bounds.height
  if (!displayWidth || !displayHeight) {
    throw new Error('图像尺寸无效')
  }
  const scaleX = imageEl.naturalWidth / displayWidth
  const scaleY = imageEl.naturalHeight / displayHeight

  const paddingDisplayPx = 8
  const paddingX = paddingDisplayPx * scaleX
  const paddingY = paddingDisplayPx * scaleY

  const localX = cropRect.x - bounds.left
  const localY = cropRect.y - bounds.top

  const naturalX = localX * scaleX
  const naturalY = localY * scaleY
  const naturalW = cropRect.w * scaleX
  const naturalH = cropRect.h * scaleY

  const sx = Math.max(0, Math.floor(naturalX - paddingX))
  const sy = Math.max(0, Math.floor(naturalY - paddingY))
  const ex = Math.min(imageEl.naturalWidth, Math.ceil(naturalX + naturalW + paddingX))
  const ey = Math.min(imageEl.naturalHeight, Math.ceil(naturalY + naturalH + paddingY))
  const sw = Math.max(1, ex - sx)
  const sh = Math.max(1, ey - sy)

  const canvas = document.createElement('canvas')
  canvas.width = sw
  canvas.height = sh
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    throw new Error('无法创建图像上下文')
  }
  ctx.imageSmoothingEnabled = true
  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(imageEl, sx, sy, sw, sh, 0, 0, sw, sh)
  return canvas.toDataURL('image/jpeg', 0.95)
}

const hasSelection = computed(() => cropRect.w > 0 && cropRect.h > 0)

const cropRectStyle = computed(() => ({
  left: `${cropRect.x}px`,
  top: `${cropRect.y}px`,
  width: `${cropRect.w}px`,
  height: `${cropRect.h}px`
}))

const topLeftHandleStyle = computed(() => ({
  left: `${cropRect.x}px`,
  top: `${cropRect.y}px`
}))

const topRightHandleStyle = computed(() => ({
  left: `${cropRect.x + cropRect.w}px`,
  top: `${cropRect.y}px`
}))

const bottomLeftHandleStyle = computed(() => ({
  left: `${cropRect.x}px`,
  top: `${cropRect.y + cropRect.h}px`
}))

const bottomRightHandleStyle = computed(() => ({
  left: `${cropRect.x + cropRect.w}px`,
  top: `${cropRect.y + cropRect.h}px`
}))

const selectionHint = computed(() => {
  if (!hasSelection.value) return '未框选'
  const left = Math.round(cropRect.x)
  const top = Math.round(cropRect.y)
  const right = Math.round(cropRect.x + cropRect.w)
  const bottom = Math.round(cropRect.y + cropRect.h)
  return `左上(${left}, ${top}) 右下(${right}, ${bottom})`
})

const magnifierStyle = computed(() => ({
  left: `${magnifierPos.x}px`,
  top: `${magnifierPos.y}px`
}))

onMounted(async () => {
  try {
    existingTags.value = await beadApi.tags()
  } catch (e) {
    console.error(e)
  }
})
</script>

<style scoped>
.new-page {
  display: grid;
  gap: 14px;
}

.new-layout {
  align-items: flex-start;
  gap: 16px;
}

.new-col {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 10px;
}

.tag-card,
.crop-card {
  padding: 12px;
}

.tag-list {
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.tag-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 8px;
  border: 1px solid #c4d8ad;
  border-radius: 999px;
  background: #f9fbf1;
}

.tag-item-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.color-action-col {
  display: flex;
  align-items: center;
  gap: 6px;
}

.icon-action-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid #c4d8ad;
  background: #fff;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.15s ease, box-shadow 0.2s ease, background-color 0.2s ease, border-color 0.2s ease;
}

.icon-action-btn:hover {
  transform: translateY(-1px) scale(1.04);
  box-shadow: 0 8px 18px rgba(64, 81, 59, 0.2);
  background: #f9fbf1;
  border-color: #9dc08b;
}

.icon-action-btn:active {
  transform: translateY(0) scale(0.96);
}

.icon-action-btn:focus-visible {
  outline: 2px solid #609966;
  outline-offset: 2px;
}

.icon-action-btn.add {
  color: #166534;
}

.icon-action-btn.remove {
  color: #b91c1c;
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

.selected-tags,
.helper-text {
  margin-top: 6px;
  font-size: 12px;
  color: #609966;
}

.extract-title {
  margin: 0;
  color: #40513b;
}

.selection-row {
  margin-top: 8px;
  justify-content: space-between;
}

.nudge-toolbar {
  flex-wrap: wrap;
  gap: 6px;
}

.nudge-select {
  max-width: 120px;
}

.nudge-input {
  max-width: 80px;
}

.color-table-wrap {
  max-height: 300px;
}

.page-actions {
  margin-top: 2px;
}

.crop-stage {
  position: relative;
  border: 1px solid #c4d8ad;
  border-radius: 12px;
  overflow: hidden;
  cursor: default;
  background: #f9fbf1;
}

.crop-image {
  display: block;
  width: 100%;
  max-height: 320px;
  object-fit: contain;
  user-select: none;
}

.crop-mask {
  position: absolute;
  border: 2px solid #609966;
  background: rgba(157, 192, 139, 0.26);
  pointer-events: none;
  box-sizing: border-box;
}

.crop-handle {
  position: absolute;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #40513b;
  border: 2px solid #edf1d6;
  box-shadow: 0 0 0 1px #40513b;
  transform: translate(-50%, -50%);
  z-index: 2;
}

.crop-handle.tl,
.crop-handle.tr,
.crop-handle.bl,
.crop-handle.br {
  cursor: crosshair;
}

.magnifier {
  position: absolute;
  width: 150px;
  height: 150px;
  border: 2px solid #609966;
  border-radius: 50%;
  overflow: hidden;
  background: #ffffff;
  box-shadow: 0 8px 20px rgba(64, 81, 59, 0.22);
  pointer-events: none;
  z-index: 5;
}

.magnifier canvas {
  width: 100%;
  height: 100%;
  display: block;
}

.nudge-panel {
  margin-top: 8px;
  border: 1px dashed #9dc08b;
  border-radius: 10px;
  padding: 8px;
  background: #f9fbf1;
}

.nudge-grid {
  margin-top: 6px;
  display: grid;
  grid-template-columns: repeat(3, 42px);
  gap: 6px;
  justify-content: start;
}

.nudge-grid button:nth-child(1) { grid-column: 2; }
.nudge-grid button:nth-child(2) { grid-column: 1; }
.nudge-grid button:nth-child(3) { grid-column: 3; }
.nudge-grid button:nth-child(4) { grid-column: 2; }

@media (max-width: 900px) {
  .new-layout {
    flex-direction: column;
  }
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
</style>
