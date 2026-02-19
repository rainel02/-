<template>
  <div class="workbench">
    <div v-if="!project.patternImage" class="card">暂无图纸，请先<RouterLink :to="`/detail/${project.id}`">上传图纸</RouterLink>再开始拼豆</div>

    <template v-else>
      <div class="card section-card">
        <div class="row-between section-head">
          <h4 class="section-subtitle">需要色号与数量</h4>
          <div class="row workbench-actions">
            <button class="btn secondary" :disabled="extractingColors || !project.patternImage || !hasSelection" @click="extractRequiredColorsBySelection">
              {{ extractingColors ? '识别中...' : '识别' }}
            </button>
            <button class="btn success" @click="saveColors">保存</button>
          </div>
        </div>
        <!-- <div class="row" style="margin:0 0 8px 0;">
          <label style="display:flex; align-items:center; gap:6px; font-size:12px; color:#6b7280;">
            <input type="checkbox" v-model="debugMode" /> 调试输出模式
          </label>
        </div> -->
        <p class="hint-text">先在下方图纸中框选“色号+数量”区域，再点击“识别”。</p>
        <div class="row add-color-row">
          <input
            class="input manual-code-input"
            v-model="manualCode"
            placeholder="手动新增色号（如 A1）"
            @keyup.enter="addManualColor"
          />
          <input
            class="input manual-qty-input"
            type="number"
            min="1"
            v-model.number="manualQuantity"
            @keyup.enter="addManualColor"
          />
          <button class="btn secondary" @click="addManualColor">添加色号</button>
        </div>
        <div class="table-wrap" style="max-height:260px;">
          <table class="table">
            <thead><tr><th>色号</th><th>数量</th><th></th></tr></thead>
            <tbody>
              <tr v-for="(row, idx) in localColors" :key="idx">
                <td><input class="input" v-model="row.code" /></td>
                <td><input class="input" type="number" min="1" v-model.number="row.quantity" /></td>
                <td><button class="btn warn" @click="localColors.splice(idx,1)">删</button></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="debugMode && debugResult" class="debug-panel">
          <div style="font-size:12px; color:#111827; margin-bottom:6px;"><strong>调试策略：</strong>{{ debugResult.strategy || 'unknown' }}</div>
          <details open>
            <summary>坐标分行结果</summary>
            <pre>{{ (debugResult.locationLines || []).join('\n') || '（空）' }}</pre>
          </details>
          <details>
            <summary>双行/兜底配对日志</summary>
            <pre>{{ (debugResult.pairLogs || []).join('\n') || '（空）' }}</pre>
          </details>
          <details>
            <summary>文本兜底日志</summary>
            <pre>{{ (debugResult.fallbackLogs || []).join('\n') || '（空）' }}</pre>
          </details>
          <details>
            <summary>OCR 原始文本</summary>
            <pre>{{ debugResult.rawText || '（空）' }}</pre>
          </details>
        </div>
      </div>

      <div class="card section-card">
        <h4 class="section-subtitle">网格设置与分析</h4>
        <div class="row grid-settings-row">
          <label>行数</label><input class="input compact-control" type="number" v-model.number="rows" />
          <label>列数</label><input class="input compact-control" type="number" v-model.number="cols" />
          <button class="btn secondary" @click="analyzeGrid" :disabled="analyzing || !project.patternImage">{{ analyzing ? '分析中...' : '分析网格' }}</button>
        </div>
        <p class="hint-text">先拖动四个角控制点框选图案主体，再点击“分析网格”。</p>

        <div
          ref="cropStageRef"
          class="crop-stage"
          @mousemove="onCropMouseMove"
          @mouseup="onCropMouseUp"
          @mouseleave="onCropMouseUp"
        >
          <img ref="patternImgRef" :src="project.patternImage" class="crop-image" @load="onPatternImageLoad" />
          <div v-if="hasSelection" class="crop-mask" :style="cropRectStyle"></div>
          <div v-if="hasSelection" class="crop-handle" :style="topLeftHandleStyle" @mousedown="onHandleMouseDown('TL', $event)"></div>
          <div v-if="hasSelection" class="crop-handle" :style="topRightHandleStyle" @mousedown="onHandleMouseDown('TR', $event)"></div>
          <div v-if="hasSelection" class="crop-handle" :style="bottomLeftHandleStyle" @mousedown="onHandleMouseDown('BL', $event)"></div>
          <div v-if="hasSelection" class="crop-handle" :style="bottomRightHandleStyle" @mousedown="onHandleMouseDown('BR', $event)"></div>
          <div v-show="magnifierVisible" class="magnifier" :style="magnifierStyle">
            <canvas ref="magnifierCanvasRef" width="150" height="150"></canvas>
          </div>
        </div>
        <div class="row selection-row">
          <div class="row nudge-toolbar">
            <label class="hint-text">微调角点</label>
            <select class="select compact-control" v-model="selectedCorner">
              <option value="TL">左上角</option>
              <option value="TR">右上角</option>
              <option value="BL">左下角</option>
              <option value="BR">右下角</option>
            </select>
            <label class="hint-text">步长</label>
            <input class="input compact-step" type="number" min="1" max="20" v-model.number="nudgeStep" />
          </div>
          <div class="hint-text selection-coords">{{ selectionHint }}</div>
          <button class="btn secondary compact-btn selection-reset" @click="resetSelection">重置选区</button>
        </div>
        <div class="nudge-panel">
          <div class="nudge-grid">
            <button class="btn secondary" @click="nudgeSelection(0, -nudgeStep)">↑</button>
            <button class="btn secondary" @click="nudgeSelection(-nudgeStep, 0)">←</button>
            <button class="btn secondary" @click="nudgeSelection(nudgeStep, 0)">→</button>
            <button class="btn secondary" @click="nudgeSelection(0, nudgeStep)">↓</button>
          </div>
        </div>

        <div class="hint-text status-text">{{ analyzeStatus }}</div>
      </div>

      <div ref="fullscreenHostRef" class="card fullscreen-host" :class="{ 'is-fullscreen': isGridFullscreen }">
        <div class="row-between section-head">
          <h4 class="section-subtitle">开始拼豆</h4>
          <button class="btn secondary" @click="toggleGridFullscreen">{{ isGridFullscreen ? '退出全屏' : '全屏查看图纸' }}</button>
        </div>
        <div class="hint-text">
          图纸网格：{{ rows }} 行 × {{ cols }} 列（每 5 行/列加深边框）
        </div>
        <div class="palette-wrap">
          <button class="palette-btn" :class="{ active: selectedCode === null }" @click="selectedCode = null">全部显示</button>
          <button
            v-for="code in paletteCodes"
            :key="code"
            class="palette-btn"
            :class="{ active: selectedCode === code }"
            @click="selectedCode = code"
          >{{ code }}</button>
        </div>
        <div
          ref="gridViewportRef"
          class="bead-grid-scroll"
          :class="{ panning: isGridPanning }"
          @wheel.prevent="onGridWheel"
          @mousedown="onGridMouseDown"
          @mousemove="onGridMouseMove"
          @mouseup="onGridMouseUp"
          @mouseleave="onGridMouseUp"
        >
          <div class="indexed-grid" :style="indexedGridStyle">
            <div class="grid-corner" :style="indexCornerStyle">#</div>

            <div class="grid-col-header" :style="colHeaderStyle">
              <div
                v-for="colNumber in colNumbers"
                :key="`col-${colNumber}`"
                class="grid-index-cell"
                :style="indexCellStyle"
              >{{ colNumber }}</div>
            </div>

            <div class="grid-row-header" :style="rowHeaderStyle">
              <div
                v-for="rowNumber in rowNumbers"
                :key="`row-${rowNumber}`"
                class="grid-index-cell"
                :style="indexCellStyle"
              >{{ rowNumber }}</div>
            </div>

            <div class="bead-grid-inner" :style="gridStyle">
              <div
                v-for="(cell, idx) in cells"
                :key="idx"
                :style="cellStyle(cell, idx)"
              >{{ cell || '' }}</div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch, type CSSProperties } from 'vue'
import { beadApi } from '@/api/beads'
import type { BeadProject, ColorExtractionDebugResult, ColorRequirement } from '@/types'

const props = defineProps<{ project: BeadProject }>()

const rows = ref(20)
const cols = ref(20)
const localColors = ref<ColorRequirement[]>([])
const manualCode = ref('')
const manualQuantity = ref<number>(1)
const cells = ref<string[]>([])
const selectedCode = ref<string | null>(null)
const analyzing = ref(false)
const extractingColors = ref(false)
const debugMode = ref(false)
const debugResult = ref<ColorExtractionDebugResult | null>(null)
const analyzeStatus = ref('尚未分析')
const cellSize = ref(30)
const MIN_CELL_SIZE = 16
const MAX_CELL_SIZE = 64

const cropStageRef = ref<HTMLDivElement | null>(null)
const patternImgRef = ref<HTMLImageElement | null>(null)
const magnifierCanvasRef = ref<HTMLCanvasElement | null>(null)
const gridViewportRef = ref<HTMLDivElement | null>(null)
const fullscreenHostRef = ref<HTMLDivElement | null>(null)
const activeHandle = ref<'TL' | 'TR' | 'BL' | 'BR' | null>(null)
const selectedCorner = ref<'TL' | 'TR' | 'BL' | 'BR'>('BR')
const nudgeStep = ref(1)
const cropRect = reactive({ x: 0, y: 0, w: 0, h: 0 })
const MIN_CROP_SIZE = 8
const magnifierVisible = ref(false)
const magnifierPos = reactive({ x: 0, y: 0 })
let magnifierHideTimer: number | null = null
const isGridPanning = ref(false)
const isGridFullscreen = ref(false)
const gridPanStart = reactive({ x: 0, y: 0, scrollLeft: 0, scrollTop: 0 })

const RAW_COLOR_HEX: Record<string, string> = {
  A1: '#FAF5CD', A2: '#FCFED6', A3: '#FCFF92', A4: '#F7EC5C', A5: '#FFE44B', A6: '#FDA951', A7: '#FA8C4F', A8: '#F9E045', A9: '#F99C5F', A10: '#F47E36', A11: '#FEDB99', A12: '#FDA276', A13: '#FEC667', A14: '#F85842', A15: '#FBF65E', A16: '#FEFF97', A17: '#FDE173', A18: '#FCBF80', A19: '#FD7E77', A20: '#F9D66E', A21: '#FAE393', A22: '#EDF878', A23: '#E1C9BD', A24: '#F3F6A9', A25: '#FFD785', A26: '#FEC832',
  B1: '#DFF139', B2: '#64F343', B3: '#9FF685', B4: '#5FDF34', B5: '#39E158', B6: '#64B0A4', B7: '#3FAE7C', B8: '#1D9E54', B9: '#2A5037', B10: '#9AD1BA', B11: '#627032', B12: '#1A6E3D', B13: '#C8E87D', B14: '#ACE84C', B15: '#305335', B16: '#C0ED9C', B17: '#9FB33E', B18: '#E6ED4F', B19: '#26B78E', B20: '#CAEDCF', B21: '#176268', B22: '#0A4241', B23: '#343B1A', B24: '#E8FAA6', B25: '#4E846D', B26: '#907C35', B27: '#D0E0AF', B28: '#9EE5BB', B29: '#C6DF5F', B30: '#E3FBB1', B31: '#B2F694', B32: '#92AD60',
  C1: '#FFFEE4', C2: '#ABF8FE', C3: '#9EE0F8', C4: '#44CDFB', C5: '#06ABE3', C6: '#54A7E9', C7: '#3977CC', C8: '#0F52BD', C9: '#3349C3', C10: '#3DBBE3', C11: '#2ADED3', C12: '#1E334E', C13: '#CDE7FE', C14: '#D6FDFC', C15: '#21C5C4', C16: '#1858A2', C17: '#02D1F3', C18: '#213244', C19: '#188690', C20: '#1A70A9', C21: '#BEDDFC', C22: '#6BB1BB', C23: '#C8E2F9', C24: '#7EC5F9', C25: '#A9E8E0', C26: '#42ADD1', C27: '#D0DEEF', C28: '#BDCEED', C29: '#364A89',
  D1: '#ACB7EF', D2: '#868DD3', D3: '#3653AF', D4: '#162C7E', D5: '#B34EC6', D6: '#B37BDC', D7: '#8758A9', D8: '#E3D2FE', D9: '#D6BAF5', D10: '#301A49', D11: '#BCBAE2', D12: '#DC99CE', D13: '#B5038F', D14: '#882893', D15: '#2F1E8E', D16: '#E2E4F0', D17: '#C7D3F9', D18: '#9A64B8', D19: '#D8C2D9', D20: '#9C34AD', D21: '#940595', D22: '#383995', D23: '#FADBF8', D24: '#768AE1', D25: '#4950C2', D26: '#D6C6EB',
  E1: '#F6D4CB', E2: '#FCC1DD', E3: '#F6BDE8', E4: '#E9639E', E5: '#F1559F', E6: '#BC4072', E7: '#C63674', E8: '#FDDBE9', E9: '#E575C7', E10: '#D33997', E11: '#F7DAD4', E12: '#F893BF', E13: '#B5026A', E14: '#FAD4BF', E15: '#F5C9CA', E16: '#FBF4EC', E17: '#F7E3EC', E18: '#FBCBDB', E19: '#F6BBD1', E20: '#D7C6CE', E21: '#C09DA4', E22: '#B58B9F', E23: '#937D8A', E24: '#DEBEE5',
  F1: '#FF9280', F2: '#F73D48', F3: '#EF4D3E', F4: '#F92B40', F5: '#E30328', F6: '#913635', F7: '#911932', F8: '#BB0126', F9: '#B0677A', F10: '#874628', F11: '#6F321D', F12: '#F8516D', F13: '#F45C45', F14: '#FCADB2', F15: '#D50527', F16: '#F8C0A9', F17: '#E89B7D', F18: '#D07E4A', F19: '#BE454A', F20: '#C69495', F21: '#F2BBC6', F22: '#F7C3D0', F23: '#EC806D', F24: '#E09DAF', F25: '#E84854',
  G1: '#FFEAD3', G2: '#FCC6AC', G3: '#F1C4A5', G4: '#DCB387', G5: '#E7B34E', G6: '#F3A014', G7: '#98503A', G8: '#4B2B1C', G9: '#E4B685', G10: '#DA8C42', G11: '#DAC898', G12: '#FEC993', G13: '#B2714B', G14: '#8B684C', G15: '#F6F8E3', G16: '#F2D8C1', G17: '#79544E', G18: '#FFEAD6', G19: '#DD7D41', G20: '#A5452F', G21: '#B38561',
  H1: '#FBFBFB', H2: '#FFFFFF', H3: '#B4B4B4', H4: '#878787', H5: '#464648', H6: '#2C2C2C', H7: '#010101', H8: '#E7D6DC', H9: '#EFEDEE', H10: '#ECEAEB', H11: '#CDCDCD', H12: '#FDF6EE', H13: '#F4EFD1', H14: '#CED7D4', H15: '#98A6A6', H16: '#1B1213', H17: '#F0EEEF', H18: '#FCFFF8', H19: '#F2EEE5', H20: '#96A09F', H21: '#F8FBE6', H22: '#CACADA', H23: '#9B9C94',
  M1: '#BBC6B6', M2: '#909994', M3: '#697E30', M4: '#E0D4BC', M5: '#D0CBAE', M6: '#B0AA86', M7: '#B0A796', M8: '#AE8082', M9: '#A88764', M10: '#C6B2BB', M11: '#9D7693', M12: '#644B51', M13: '#C79266', M14: '#C37463', M15: '#747D7A'
}

function normalizeColorCode(code: string) {
  return code.toUpperCase().trim().replace(/^([A-Z]+)0+(?=[1-9])/, '$1')
}

function getCodeHex(code: string) {
  const normalized = normalizeColorCode(code || '')
  return RAW_COLOR_HEX[normalized] || ''
}

function getContrastColor(hex: string) {
  const cleaned = (hex || '').replace('#', '')
  if (!/^[0-9a-fA-F]{6}$/.test(cleaned)) {
    return '#111827'
  }
  const r = Number.parseInt(cleaned.slice(0, 2), 16)
  const g = Number.parseInt(cleaned.slice(2, 4), 16)
  const b = Number.parseInt(cleaned.slice(4, 6), 16)
  const brightness = (r * 299 + g * 587 + b * 114) / 1000
  return brightness > 125 ? '#111827' : '#ffffff'
}

function addManualColor() {
  const code = normalizeColorCode(manualCode.value || '')
  const quantity = Number(manualQuantity.value)
  if (!code) {
    alert('请输入色号')
    return
  }
  if (!Number.isFinite(quantity) || quantity <= 0) {
    alert('数量必须大于 0')
    return
  }

  const existing = localColors.value.find(item => normalizeColorCode(item.code || '') === code)
  if (existing) {
    existing.code = code
    existing.quantity = Number(existing.quantity || 0) + quantity
  } else {
    localColors.value.push({ code, quantity })
  }

  manualCode.value = ''
  manualQuantity.value = 1
}

watch(
  () => props.project,
  async (v) => {
    localColors.value = (v.requiredColors || []).map(c => ({ ...c }))
    rows.value = v.gridRows && v.gridRows > 0 ? v.gridRows : rows.value
    cols.value = v.gridCols && v.gridCols > 0 ? v.gridCols : cols.value
    cells.value = parseGridCells(v.gridCellsJson, rows.value * cols.value)
    await nextTick()
    initSelectionByCorners()
  },
  { immediate: true }
)

const gridStyle = computed(() => ({
  display: 'grid',
  gridTemplateColumns: `repeat(${cols.value}, ${cellSize.value}px)`,
  gap: '0',
  background: '#d1d5db'
}))

const indexSize = computed(() => cellSize.value)

const rowNumbers = computed(() => Array.from({ length: rows.value }, (_, index) => index + 1))
const colNumbers = computed(() => Array.from({ length: cols.value }, (_, index) => index + 1))

const indexedGridStyle = computed(() => ({
  display: 'grid',
  gridTemplateColumns: `${indexSize.value}px auto`,
  gridTemplateRows: `${indexSize.value}px auto`,
  width: 'max-content',
  minWidth: '100%'
}))

const colHeaderStyle = computed(() => ({
  gridColumn: '2',
  gridRow: '1',
  display: 'grid',
  gridTemplateColumns: `repeat(${cols.value}, ${cellSize.value}px)`,
  width: `${cols.value * cellSize.value}px`
}))

const rowHeaderStyle = computed(() => ({
  gridColumn: '1',
  gridRow: '2',
  display: 'grid',
  gridTemplateRows: `repeat(${rows.value}, ${cellSize.value}px)`,
  height: `${rows.value * cellSize.value}px`
}))

const indexCellStyle = computed(() => ({
  width: `${indexSize.value}px`,
  height: `${indexSize.value}px`,
  fontSize: `${Math.max(10, Math.floor(indexSize.value * 0.32))}px`
}))

const indexCornerStyle = computed(() => ({
  width: `${indexSize.value}px`,
  height: `${indexSize.value}px`,
  fontSize: `${Math.max(10, Math.floor(indexSize.value * 0.32))}px`
}))

function cellStyle(code: string, index: number): CSSProperties {
  const showAll = selectedCode.value === null
  const isMatch = code === selectedCode.value
  const hex = code ? getCodeHex(code) : ''
  const textColor = hex ? getContrastColor(hex) : '#111827'
  const row = Math.floor(index / cols.value)
  const col = index % cols.value
  const majorTop = row % 5 === 0
  const majorLeft = col % 5 === 0
  const isLastRow = row === rows.value - 1
  const isLastCol = col === cols.value - 1
  return {
    width: `${cellSize.value}px`,
    height: `${cellSize.value}px`,
    background: hex || '#fff',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: `${Math.max(9, Math.floor(cellSize.value * 0.32))}px`,
    color: textColor,
    opacity: showAll || !code || isMatch ? '1' : '0.12',
    borderTop: majorTop ? '2px solid #9ca3af' : '1px solid #e5e7eb',
    borderLeft: majorLeft ? '2px solid #9ca3af' : '1px solid #e5e7eb',
    borderRight: isLastCol ? '2px solid #9ca3af' : '1px solid #e5e7eb',
    borderBottom: isLastRow ? '2px solid #9ca3af' : '1px solid #e5e7eb',
    boxSizing: 'border-box',
    transform: isMatch ? 'scale(1.04)' : 'scale(1)',
    zIndex: isMatch ? 1 : 0
  }
}

async function analyzeGrid() {
  if (!props.project.patternImage) return
  if (rows.value <= 0 || cols.value <= 0) {
    alert('行列数必须大于 0')
    return
  }
  if (!hasSelection.value) {
    alert('请先框选图案主体区域')
    return
  }

  analyzing.value = true
  analyzeStatus.value = '正在调用百度 OCR 分析网格...'

  try {
    const cropped = cropSelectedImage()
    const candidateCodes = localColors.value
      .map(item => (item.code || '').trim().toUpperCase())
      .filter(Boolean)

    const result = await beadApi.analyzeGrid({
      imageBase64: cropped.dataUrl,
      rows: rows.value,
      cols: cols.value,
      imageWidth: cropped.width,
      imageHeight: cropped.height,
      candidateCodes
    })

    const total = rows.value * cols.value
    const nextCells = new Array<string>(total).fill('')
    for (const cell of result.cells || []) {
      if (cell.row < 0 || cell.row >= rows.value || cell.col < 0 || cell.col >= cols.value) {
        continue
      }
      nextCells[cell.row * cols.value + cell.col] = cell.code || ''
    }
    cells.value = nextCells
    await beadApi.saveGridResult(props.project.id, {
      rows: rows.value,
      cols: cols.value,
      cells: nextCells
    })
    analyzeStatus.value = `分析完成：OCR标记 ${result.ocrCount}，填充格子 ${result.filledCount}/${total}`
  } catch (error) {
    console.error(error)
    analyzeStatus.value = '分析失败，请调整选区后重试'
    alert('网格分析失败，请重试')
  } finally {
    analyzing.value = false
  }
}

async function extractRequiredColorsBySelection() {
  if (!props.project.patternImage) {
    alert('暂无图纸，请先上传图纸')
    return
  }
  if (!hasSelection.value) {
    alert('请先框选色号区域')
    return
  }

  extractingColors.value = true
  try {
    const cropped = cropSelectedImage()
    if (debugMode.value) {
      const result = await beadApi.extractColorsDebug(cropped.dataUrl)
      debugResult.value = result
      localColors.value = result.colors || []
    } else {
      const rows = await beadApi.extractColors(cropped.dataUrl)
      debugResult.value = null
      localColors.value = rows
    }
    if (localColors.value.length === 0) {
      alert('未识别到有效色号，请调整选区后重试')
    }
  } catch (error) {
    console.error(error)
    alert('识别失败，请稍后重试')
  } finally {
    extractingColors.value = false
  }
}

async function saveColors() {
  const clean = localColors.value.filter(v => v.code && v.quantity > 0)
  await beadApi.saveColors(props.project.id, clean)
  alert('保存成功')
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
    renderHeight = boxWidth / naturalRatio
    offsetY = (boxHeight - renderHeight) / 2
  } else {
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

function resetSelection() {
  initSelectionByCorners()
}

function onPatternImageLoad() {
  initSelectionByCorners()
}

function onHandleMouseDown(handle: 'TL' | 'TR' | 'BL' | 'BR', event: MouseEvent) {
  event.preventDefault()
  event.stopPropagation()
  activeHandle.value = handle
  selectedCorner.value = handle
  magnifierVisible.value = true
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
}

function updateMagnifierPosition(point: { x: number; y: number }) {
  if (!cropStageRef.value) return
  const size = 150
  const margin = 12
  const stageWidth = cropStageRef.value.clientWidth
  const stageHeight = cropStageRef.value.clientHeight
  const handleForPosition = activeHandle.value ?? selectedCorner.value

  let x = point.x + margin
  let y = point.y + margin

  if (handleForPosition === 'BR' || handleForPosition === 'BL') {
    y = point.y - size - margin
  }

  if (x + size > stageWidth) {
    x = stageWidth - size
  }
  if (y + size > stageHeight) {
    y = stageHeight - size
  }
  if (x < 0) {
    x = 0
  }
  if (y < 0) {
    y = 0
  }

  magnifierPos.x = x
  magnifierPos.y = y
}

function refreshNudgeMagnifier() {
  const bounds = getImageBoundsInStage()
  if (!bounds) return

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

  if (magnifierHideTimer !== null) {
    window.clearTimeout(magnifierHideTimer)
  }
  magnifierHideTimer = window.setTimeout(() => {
    if (!activeHandle.value) {
      magnifierVisible.value = false
    }
  }, 900)
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

function cropSelectedImage() {
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
  const localX = cropRect.x - bounds.left
  const localY = cropRect.y - bounds.top
  const sx = Math.max(0, Math.floor(localX * scaleX))
  const sy = Math.max(0, Math.floor(localY * scaleY))
  const sw = Math.max(1, Math.floor(cropRect.w * scaleX))
  const sh = Math.max(1, Math.floor(cropRect.h * scaleY))

  const canvas = document.createElement('canvas')
  canvas.width = sw
  canvas.height = sh
  const context = canvas.getContext('2d')
  if (!context) {
    throw new Error('无法创建图像上下文')
  }
  context.drawImage(imageEl, sx, sy, sw, sh, 0, 0, sw, sh)
  return {
    dataUrl: canvas.toDataURL('image/jpeg', 0.95),
    width: sw,
    height: sh
  }
}

function onGridWheel(event: WheelEvent) {
  const oldSize = cellSize.value
  const delta = event.deltaY < 0 ? 2 : -2
  const nextSize = Math.max(MIN_CELL_SIZE, Math.min(MAX_CELL_SIZE, oldSize + delta))
  if (nextSize === oldSize) {
    return
  }

  const viewport = gridViewportRef.value
  if (!viewport) {
    cellSize.value = nextSize
    return
  }

  const rect = viewport.getBoundingClientRect()
  const mouseX = event.clientX - rect.left + viewport.scrollLeft
  const mouseY = event.clientY - rect.top + viewport.scrollTop
  const ratio = nextSize / oldSize

  cellSize.value = nextSize

  viewport.scrollLeft = mouseX * ratio - (event.clientX - rect.left)
  viewport.scrollTop = mouseY * ratio - (event.clientY - rect.top)
}

function onGridMouseDown(event: MouseEvent) {
  if (event.button !== 0) {
    return
  }
  const viewport = gridViewportRef.value
  if (!viewport) {
    return
  }
  isGridPanning.value = true
  gridPanStart.x = event.clientX
  gridPanStart.y = event.clientY
  gridPanStart.scrollLeft = viewport.scrollLeft
  gridPanStart.scrollTop = viewport.scrollTop
}

function onGridMouseMove(event: MouseEvent) {
  if (!isGridPanning.value) {
    return
  }
  const viewport = gridViewportRef.value
  if (!viewport) {
    return
  }
  const dx = event.clientX - gridPanStart.x
  const dy = event.clientY - gridPanStart.y
  viewport.scrollLeft = gridPanStart.scrollLeft - dx
  viewport.scrollTop = gridPanStart.scrollTop - dy
}

function onGridMouseUp() {
  isGridPanning.value = false
}

async function toggleGridFullscreen() {
  const host = fullscreenHostRef.value
  if (!host) {
    return
  }

  try {
    if (document.fullscreenElement === host) {
      await document.exitFullscreen()
      return
    }
    if (!document.fullscreenElement) {
      await host.requestFullscreen()
      return
    }
    await host.requestFullscreen()
  } catch (error) {
    console.error(error)
    alert('当前浏览器不支持该全屏操作')
  }
}

function onFullscreenChange() {
  isGridFullscreen.value = document.fullscreenElement === fullscreenHostRef.value
}

function parseGridCells(gridCellsJson: string | undefined, expectedLength: number) {
  if (!gridCellsJson) {
    return new Array<string>(expectedLength).fill('')
  }
  try {
    const parsed = JSON.parse(gridCellsJson)
    if (!Array.isArray(parsed)) {
      return new Array<string>(expectedLength).fill('')
    }
    const normalized = parsed.map(item => (item == null ? '' : String(item)))
    if (normalized.length < expectedLength) {
      return [...normalized, ...new Array<string>(expectedLength - normalized.length).fill('')]
    }
    return normalized.slice(0, expectedLength)
  } catch {
    return new Array<string>(expectedLength).fill('')
  }
}

const hasSelection = computed(() => cropRect.w > 0 && cropRect.h > 0)

const paletteCodes = computed(() => {
  const fromGrid = cells.value.filter(Boolean)
  const fromRequired = localColors.value
    .map(item => (item.code || '').trim().toUpperCase())
    .filter(Boolean)
  return Array.from(new Set([...fromGrid, ...fromRequired])).sort((left, right) => left.localeCompare(right, 'en'))
})

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
  return `左上(${left}, ${top}) 右上(${right}, ${top}) 左下(${left}, ${bottom}) 右下(${right}, ${bottom})`
})

const magnifierStyle = computed(() => ({
  left: `${magnifierPos.x}px`,
  top: `${magnifierPos.y}px`
}))

onMounted(() => {
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})
</script>

<style scoped>
.workbench {
  display: grid;
  gap: 10px;
}

.workbench-actions {
  justify-content: flex-end;
}

.workbench-title {
  margin: 0;
  color: #40513b;
}

.section-card {
  margin-bottom: 2px;
}

.section-head {
  margin-bottom: 8px;
}

.section-subtitle {
  margin: 0;
  color: #40513b;
}

.hint-text {
  font-size: 12px;
  color: #609966;
  margin: 0 0 8px 0;
}

.add-color-row {
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: nowrap;
}

.manual-code-input {
  flex: 1;
  min-width: 0;
}

.manual-qty-input {
  width: 96px;
}

.grid-settings-row {
  flex-wrap: wrap;
}

.compact-control {
  max-width: 90px;
  height: 34px;
  padding: 6px 8px;
  font-size: 13px;
}

.compact-step {
  max-width: 66px;
  height: 34px;
  padding: 6px 8px;
  font-size: 13px;
}

.crop-panel {
  padding: 10px;
  margin-top: 8px;
}

.selection-row {
  margin-top: 8px;
  align-items: center;
  gap: 8px;
}

.nudge-toolbar {
  display: inline-flex;
  flex-wrap: nowrap !important;
  white-space: nowrap;
  gap: 6px;
  align-items: center;
}

.nudge-toolbar > * {
  flex: 0 0 auto;
}

.nudge-toolbar .hint-text {
  margin: 0;
  display: inline-flex;
  align-items: center;
}

.selection-coords {
  margin: 0;
  flex: 1;
  text-align: center;
}

.selection-reset {
  margin-left: auto;
}

.compact-btn {
  height: 34px;
  padding: 6px 10px;
  font-size: 12px;
}

.status-text {
  margin-top: 8px;
}

.crop-stage {
  position: relative;
  border: 1px solid #c4d8ad;
  border-radius: 12px;
  overflow: hidden;
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
  cursor: crosshair;
}

.magnifier {
  position: absolute;
  width: 150px;
  height: 150px;
  border: 2px solid #609966;
  border-radius: 50%;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 8px 20px rgba(64, 81, 59, 0.22);
  pointer-events: none;
  z-index: 5;
}

.magnifier canvas {
  width: 100%;
  height: 100%;
  display: block;
}

.bead-grid-scroll {
  max-height: 420px;
  max-width: 100%;
  overflow: auto;
  border: 1px solid #c4d8ad;
  border-radius: 12px;
  padding: 8px;
  cursor: grab;
  user-select: none;
  background: #ffffff;
}

.bead-grid-scroll.panning {
  cursor: grabbing;
}

.bead-grid-inner {
  width: max-content;
  min-width: 100%;
}

.indexed-grid {
  position: relative;
}

.grid-corner {
  grid-column: 1;
  grid-row: 1;
  position: sticky;
  top: 0;
  left: 0;
  z-index: 14;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #edf1d6;
  border: 1px solid #c4d8ad;
  box-sizing: border-box;
  color: #40513b;
  font-weight: 600;
}

.grid-col-header {
  position: sticky;
  top: 0;
  z-index: 13;
  background: #edf1d6;
}

.grid-row-header {
  position: sticky;
  left: 0;
  z-index: 13;
  background: #edf1d6;
}

.grid-index-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #edf1d6;
  border: 1px solid #c4d8ad;
  box-sizing: border-box;
  color: #40513b;
  font-weight: 600;
  line-height: 1;
}

.fullscreen-host.is-fullscreen {
  width: 100vw;
  height: 100vh;
  border-radius: 0;
  border: none;
  margin: 0;
  padding: 12px;
  background: #f9fbf1;
  display: flex;
  flex-direction: column;
}

.fullscreen-host.is-fullscreen .bead-grid-scroll {
  flex: 1;
  max-height: none;
}

.palette-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.palette-btn {
  border: 1px solid #c4d8ad;
  background: #fff;
  border-radius: 999px;
  padding: 6px 10px;
  cursor: pointer;
  font-size: 12px;
}

.palette-btn.active {
  border-color: #40513b;
  background: #40513b;
  color: #edf1d6;
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

.debug-panel {
  margin-top: 10px;
  padding: 10px;
  border: 1px dashed #9dc08b;
  border-radius: 10px;
  background: #f9fbf1;
}

.debug-panel details {
  margin-top: 6px;
}

.debug-panel summary {
  cursor: pointer;
  font-size: 12px;
  color: #40513b;
}

.debug-panel pre {
  margin: 6px 0 0 0;
  padding: 8px;
  white-space: pre-wrap;
  word-break: break-word;
  background: #ffffff;
  border: 1px solid #c4d8ad;
  border-radius: 6px;
  max-height: 180px;
  overflow: auto;
  font-size: 12px;
  color: #40513b;
}

@media (max-width: 860px) {
  .section-head {
    gap: 8px;
    align-items: center;
  }

  .workbench-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .add-color-row {
    flex-wrap: wrap;
  }

  .manual-qty-input {
    width: 120px;
  }

  .selection-row {
    width: 100%;
    flex-wrap: wrap;
  }

  .selection-coords {
    text-align: left;
    flex: 0 0 100%;
  }

  .nudge-toolbar {
    flex-wrap: nowrap;
    white-space: nowrap;
  }

  .palette-wrap {
    gap: 6px;
  }
}
</style>
