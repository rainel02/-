import { computed, nextTick, reactive, ref, watch } from 'vue';
import { beadApi } from '@/api/beads';
const props = defineProps();
const rows = ref(20);
const cols = ref(20);
const localColors = ref([]);
const cells = ref([]);
const selectedCode = ref(null);
const analyzing = ref(false);
const extractingColors = ref(false);
const debugMode = ref(false);
const debugResult = ref(null);
const analyzeStatus = ref('尚未分析');
const cellSize = ref(30);
const MIN_CELL_SIZE = 16;
const MAX_CELL_SIZE = 64;
const cropStageRef = ref(null);
const patternImgRef = ref(null);
const magnifierCanvasRef = ref(null);
const gridViewportRef = ref(null);
const activeHandle = ref(null);
const selectedCorner = ref('BR');
const nudgeStep = ref(1);
const cropRect = reactive({ x: 0, y: 0, w: 0, h: 0 });
const MIN_CROP_SIZE = 8;
const magnifierVisible = ref(false);
const magnifierPos = reactive({ x: 0, y: 0 });
let magnifierHideTimer = null;
const isGridPanning = ref(false);
const gridPanStart = reactive({ x: 0, y: 0, scrollLeft: 0, scrollTop: 0 });
const RAW_COLOR_HEX = {
    A1: '#FAF5CD', A2: '#FCFED6', A3: '#FCFF92', A4: '#F7EC5C', A5: '#FFE44B', A6: '#FDA951', A7: '#FA8C4F', A8: '#F9E045', A9: '#F99C5F', A10: '#F47E36', A11: '#FEDB99', A12: '#FDA276', A13: '#FEC667', A14: '#F85842', A15: '#FBF65E', A16: '#FEFF97', A17: '#FDE173', A18: '#FCBF80', A19: '#FD7E77', A20: '#F9D66E', A21: '#FAE393', A22: '#EDF878', A23: '#E1C9BD', A24: '#F3F6A9', A25: '#FFD785', A26: '#FEC832',
    B1: '#DFF139', B2: '#64F343', B3: '#9FF685', B4: '#5FDF34', B5: '#39E158', B6: '#64B0A4', B7: '#3FAE7C', B8: '#1D9E54', B9: '#2A5037', B10: '#9AD1BA', B11: '#627032', B12: '#1A6E3D', B13: '#C8E87D', B14: '#ACE84C', B15: '#305335', B16: '#C0ED9C', B17: '#9FB33E', B18: '#E6ED4F', B19: '#26B78E', B20: '#CAEDCF', B21: '#176268', B22: '#0A4241', B23: '#343B1A', B24: '#E8FAA6', B25: '#4E846D', B26: '#907C35', B27: '#D0E0AF', B28: '#9EE5BB', B29: '#C6DF5F', B30: '#E3FBB1', B31: '#B2F694', B32: '#92AD60',
    C1: '#FFFEE4', C2: '#ABF8FE', C3: '#9EE0F8', C4: '#44CDFB', C5: '#06ABE3', C6: '#54A7E9', C7: '#3977CC', C8: '#0F52BD', C9: '#3349C3', C10: '#3DBBE3', C11: '#2ADED3', C12: '#1E334E', C13: '#CDE7FE', C14: '#D6FDFC', C15: '#21C5C4', C16: '#1858A2', C17: '#02D1F3', C18: '#213244', C19: '#188690', C20: '#1A70A9', C21: '#BEDDFC', C22: '#6BB1BB', C23: '#C8E2F9', C24: '#7EC5F9', C25: '#A9E8E0', C26: '#42ADD1', C27: '#D0DEEF', C28: '#BDCEED', C29: '#364A89',
    D1: '#ACB7EF', D2: '#868DD3', D3: '#3653AF', D4: '#162C7E', D5: '#B34EC6', D6: '#B37BDC', D7: '#8758A9', D8: '#E3D2FE', D9: '#D6BAF5', D10: '#301A49', D11: '#BCBAE2', D12: '#DC99CE', D13: '#B5038F', D14: '#882893', D15: '#2F1E8E', D16: '#E2E4F0', D17: '#C7D3F9', D18: '#9A64B8', D19: '#D8C2D9', D20: '#9C34AD', D21: '#940595', D22: '#383995', D23: '#FADBF8', D24: '#768AE1', D25: '#4950C2', D26: '#D6C6EB',
    E1: '#F6D4CB', E2: '#FCC1DD', E3: '#F6BDE8', E4: '#E9639E', E5: '#F1559F', E6: '#BC4072', E7: '#C63674', E8: '#FDDBE9', E9: '#E575C7', E10: '#D33997', E11: '#F7DAD4', E12: '#F893BF', E13: '#B5026A', E14: '#FAD4BF', E15: '#F5C9CA', E16: '#FBF4EC', E17: '#F7E3EC', E18: '#FBCBDB', E19: '#F6BBD1', E20: '#D7C6CE', E21: '#C09DA4', E22: '#B58B9F', E23: '#937D8A', E24: '#DEBEE5',
    F1: '#FF9280', F2: '#F73D48', F3: '#EF4D3E', F4: '#F92B40', F5: '#E30328', F6: '#913635', F7: '#911932', F8: '#BB0126', F9: '#B0677A', F10: '#874628', F11: '#6F321D', F12: '#F8516D', F13: '#F45C45', F14: '#FCADB2', F15: '#D50527', F16: '#F8C0A9', F17: '#E89B7D', F18: '#D07E4A', F19: '#BE454A', F20: '#C69495', F21: '#F2BBC6', F22: '#F7C3D0', F23: '#EC806D', F24: '#E09DAF', F25: '#E84854',
    G1: '#FFEAD3', G2: '#FCC6AC', G3: '#F1C4A5', G4: '#DCB387', G5: '#E7B34E', G6: '#F3A014', G7: '#98503A', G8: '#4B2B1C', G9: '#E4B685', G10: '#DA8C42', G11: '#DAC898', G12: '#FEC993', G13: '#B2714B', G14: '#8B684C', G15: '#F6F8E3', G16: '#F2D8C1', G17: '#79544E', G18: '#FFEAD6', G19: '#DD7D41', G20: '#A5452F', G21: '#B38561',
    H1: '#FBFBFB', H2: '#FFFFFF', H3: '#B4B4B4', H4: '#878787', H5: '#464648', H6: '#2C2C2C', H7: '#010101', H8: '#E7D6DC', H9: '#EFEDEE', H10: '#ECEAEB', H11: '#CDCDCD', H12: '#FDF6EE', H13: '#F4EFD1', H14: '#CED7D4', H15: '#98A6A6', H16: '#1B1213', H17: '#F0EEEF', H18: '#FCFFF8', H19: '#F2EEE5', H20: '#96A09F', H21: '#F8FBE6', H22: '#CACADA', H23: '#9B9C94',
    M1: '#BBC6B6', M2: '#909994', M3: '#697E30', M4: '#E0D4BC', M5: '#D0CBAE', M6: '#B0AA86', M7: '#B0A796', M8: '#AE8082', M9: '#A88764', M10: '#C6B2BB', M11: '#9D7693', M12: '#644B51', M13: '#C79266', M14: '#C37463', M15: '#747D7A'
};
function normalizeColorCode(code) {
    return code.toUpperCase().trim().replace(/^([A-Z]+)0+(?=[1-9])/, '$1');
}
function getCodeHex(code) {
    const normalized = normalizeColorCode(code || '');
    return RAW_COLOR_HEX[normalized] || '';
}
function getContrastColor(hex) {
    const cleaned = (hex || '').replace('#', '');
    if (!/^[0-9a-fA-F]{6}$/.test(cleaned)) {
        return '#111827';
    }
    const r = Number.parseInt(cleaned.slice(0, 2), 16);
    const g = Number.parseInt(cleaned.slice(2, 4), 16);
    const b = Number.parseInt(cleaned.slice(4, 6), 16);
    const brightness = (r * 299 + g * 587 + b * 114) / 1000;
    return brightness > 125 ? '#111827' : '#ffffff';
}
watch(() => props.project, async (v) => {
    localColors.value = (v.requiredColors || []).map(c => ({ ...c }));
    rows.value = v.gridRows && v.gridRows > 0 ? v.gridRows : rows.value;
    cols.value = v.gridCols && v.gridCols > 0 ? v.gridCols : cols.value;
    cells.value = parseGridCells(v.gridCellsJson, rows.value * cols.value);
    await nextTick();
    initSelectionByCorners();
}, { immediate: true });
const gridStyle = computed(() => ({
    display: 'grid',
    gridTemplateColumns: `repeat(${cols.value}, ${cellSize.value}px)`,
    gap: '1px',
    background: '#d1d5db'
}));
function cellStyle(code) {
    const showAll = selectedCode.value === null;
    const isMatch = code === selectedCode.value;
    const hex = code ? getCodeHex(code) : '';
    const textColor = hex ? getContrastColor(hex) : '#111827';
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
        border: isMatch ? '1px solid #111827' : '1px solid transparent',
        boxSizing: 'border-box',
        transform: isMatch ? 'scale(1.04)' : 'scale(1)',
        zIndex: isMatch ? 1 : 0
    };
}
async function analyzeGrid() {
    if (!props.project.patternImage)
        return;
    if (rows.value <= 0 || cols.value <= 0) {
        alert('行列数必须大于 0');
        return;
    }
    if (!hasSelection.value) {
        alert('请先框选图案主体区域');
        return;
    }
    analyzing.value = true;
    analyzeStatus.value = '正在调用百度 OCR 分析网格...';
    try {
        const cropped = cropSelectedImage();
        const candidateCodes = localColors.value
            .map(item => (item.code || '').trim().toUpperCase())
            .filter(Boolean);
        const result = await beadApi.analyzeGrid({
            imageBase64: cropped.dataUrl,
            rows: rows.value,
            cols: cols.value,
            imageWidth: cropped.width,
            imageHeight: cropped.height,
            candidateCodes
        });
        const total = rows.value * cols.value;
        const nextCells = new Array(total).fill('');
        for (const cell of result.cells || []) {
            if (cell.row < 0 || cell.row >= rows.value || cell.col < 0 || cell.col >= cols.value) {
                continue;
            }
            nextCells[cell.row * cols.value + cell.col] = cell.code || '';
        }
        cells.value = nextCells;
        await beadApi.saveGridResult(props.project.id, {
            rows: rows.value,
            cols: cols.value,
            cells: nextCells
        });
        analyzeStatus.value = `分析完成：OCR标记 ${result.ocrCount}，填充格子 ${result.filledCount}/${total}`;
    }
    catch (error) {
        console.error(error);
        analyzeStatus.value = '分析失败，请调整选区后重试';
        alert('网格分析失败，请重试');
    }
    finally {
        analyzing.value = false;
    }
}
async function extractRequiredColorsBySelection() {
    if (!props.project.patternImage) {
        alert('暂无图纸，请先上传图纸');
        return;
    }
    if (!hasSelection.value) {
        alert('请先框选色号区域');
        return;
    }
    extractingColors.value = true;
    try {
        const cropped = cropSelectedImage();
        if (debugMode.value) {
            const result = await beadApi.extractColorsDebug(cropped.dataUrl);
            debugResult.value = result;
            localColors.value = result.colors || [];
        }
        else {
            const rows = await beadApi.extractColors(cropped.dataUrl);
            debugResult.value = null;
            localColors.value = rows;
        }
        if (localColors.value.length === 0) {
            alert('未识别到有效色号，请调整选区后重试');
        }
    }
    catch (error) {
        console.error(error);
        alert('识别失败，请稍后重试');
    }
    finally {
        extractingColors.value = false;
    }
}
async function saveColors() {
    const clean = localColors.value.filter(v => v.code && v.quantity > 0);
    await beadApi.saveColors(props.project.id, clean);
    alert('已保存色号数量');
}
function getImageBoundsInStage() {
    if (!cropStageRef.value || !patternImgRef.value)
        return null;
    const stageRect = cropStageRef.value.getBoundingClientRect();
    const imageRect = patternImgRef.value.getBoundingClientRect();
    const naturalWidth = patternImgRef.value.naturalWidth;
    const naturalHeight = patternImgRef.value.naturalHeight;
    if (!naturalWidth || !naturalHeight) {
        return {
            left: imageRect.left - stageRect.left,
            top: imageRect.top - stageRect.top,
            width: imageRect.width,
            height: imageRect.height
        };
    }
    const boxWidth = imageRect.width;
    const boxHeight = imageRect.height;
    const naturalRatio = naturalWidth / naturalHeight;
    const boxRatio = boxWidth / boxHeight;
    let renderWidth = boxWidth;
    let renderHeight = boxHeight;
    let offsetX = 0;
    let offsetY = 0;
    if (naturalRatio > boxRatio) {
        renderHeight = boxWidth / naturalRatio;
        offsetY = (boxHeight - renderHeight) / 2;
    }
    else {
        renderWidth = boxHeight * naturalRatio;
        offsetX = (boxWidth - renderWidth) / 2;
    }
    return {
        left: imageRect.left - stageRect.left + offsetX,
        top: imageRect.top - stageRect.top + offsetY,
        width: renderWidth,
        height: renderHeight
    };
}
function initSelectionByCorners() {
    const bounds = getImageBoundsInStage();
    if (!bounds)
        return;
    const marginX = bounds.width * 0.1;
    const marginY = bounds.height * 0.1;
    cropRect.x = bounds.left + marginX;
    cropRect.y = bounds.top + marginY;
    cropRect.w = Math.max(MIN_CROP_SIZE, bounds.width - marginX * 2);
    cropRect.h = Math.max(MIN_CROP_SIZE, bounds.height - marginY * 2);
    activeHandle.value = null;
}
function resetSelection() {
    initSelectionByCorners();
}
function onPatternImageLoad() {
    initSelectionByCorners();
}
function onHandleMouseDown(handle, event) {
    event.preventDefault();
    event.stopPropagation();
    activeHandle.value = handle;
    selectedCorner.value = handle;
    magnifierVisible.value = true;
}
function onCropMouseMove(event) {
    if (!activeHandle.value)
        return;
    const point = toStagePoint(event);
    const bounds = getImageBoundsInStage();
    if (!point || !bounds)
        return;
    updateMagnifier(point);
    updateMagnifierPosition(point);
    const right = cropRect.x + cropRect.w;
    const bottom = cropRect.y + cropRect.h;
    if (activeHandle.value === 'TL') {
        const newX = Math.min(Math.max(point.x, bounds.left), right - MIN_CROP_SIZE);
        const newY = Math.min(Math.max(point.y, bounds.top), bottom - MIN_CROP_SIZE);
        cropRect.w = right - newX;
        cropRect.h = bottom - newY;
        cropRect.x = newX;
        cropRect.y = newY;
        return;
    }
    const maxRight = bounds.left + bounds.width;
    const maxBottom = bounds.top + bounds.height;
    const newRight = Math.max(Math.min(point.x, maxRight), cropRect.x + MIN_CROP_SIZE);
    const newBottom = Math.max(Math.min(point.y, maxBottom), cropRect.y + MIN_CROP_SIZE);
    cropRect.w = newRight - cropRect.x;
    cropRect.h = newBottom - cropRect.y;
}
function onCropMouseUp() {
    activeHandle.value = null;
    magnifierVisible.value = false;
}
function updateMagnifierPosition(point) {
    if (!cropStageRef.value)
        return;
    const size = 150;
    const margin = 12;
    const stageWidth = cropStageRef.value.clientWidth;
    const stageHeight = cropStageRef.value.clientHeight;
    const handleForPosition = activeHandle.value ?? selectedCorner.value;
    let x = point.x + margin;
    let y = point.y + margin;
    if (handleForPosition === 'BR') {
        y = point.y - size - margin;
    }
    if (x + size > stageWidth) {
        x = stageWidth - size;
    }
    if (y + size > stageHeight) {
        y = stageHeight - size;
    }
    if (x < 0) {
        x = 0;
    }
    if (y < 0) {
        y = 0;
    }
    magnifierPos.x = x;
    magnifierPos.y = y;
}
function refreshNudgeMagnifier() {
    const bounds = getImageBoundsInStage();
    if (!bounds)
        return;
    const point = selectedCorner.value === 'TL'
        ? { x: cropRect.x, y: cropRect.y }
        : { x: cropRect.x + cropRect.w, y: cropRect.y + cropRect.h };
    magnifierVisible.value = true;
    updateMagnifier(point);
    updateMagnifierPosition(point);
    if (magnifierHideTimer !== null) {
        window.clearTimeout(magnifierHideTimer);
    }
    magnifierHideTimer = window.setTimeout(() => {
        if (!activeHandle.value) {
            magnifierVisible.value = false;
        }
    }, 900);
}
function nudgeSelection(dx, dy) {
    const bounds = getImageBoundsInStage();
    if (!bounds)
        return;
    const stepX = Number.isFinite(dx) ? dx : 0;
    const stepY = Number.isFinite(dy) ? dy : 0;
    if (!stepX && !stepY)
        return;
    const right = cropRect.x + cropRect.w;
    const bottom = cropRect.y + cropRect.h;
    if (selectedCorner.value === 'TL') {
        const nextX = Math.min(Math.max(cropRect.x + stepX, bounds.left), right - MIN_CROP_SIZE);
        const nextY = Math.min(Math.max(cropRect.y + stepY, bounds.top), bottom - MIN_CROP_SIZE);
        cropRect.w = right - nextX;
        cropRect.h = bottom - nextY;
        cropRect.x = nextX;
        cropRect.y = nextY;
        refreshNudgeMagnifier();
        return;
    }
    const maxRight = bounds.left + bounds.width;
    const maxBottom = bounds.top + bounds.height;
    const nextRight = Math.max(Math.min(right + stepX, maxRight), cropRect.x + MIN_CROP_SIZE);
    const nextBottom = Math.max(Math.min(bottom + stepY, maxBottom), cropRect.y + MIN_CROP_SIZE);
    cropRect.w = nextRight - cropRect.x;
    cropRect.h = nextBottom - cropRect.y;
    refreshNudgeMagnifier();
}
function updateMagnifier(point) {
    if (!patternImgRef.value || !magnifierCanvasRef.value)
        return;
    const bounds = getImageBoundsInStage();
    if (!bounds)
        return;
    const image = patternImgRef.value;
    const canvas = magnifierCanvasRef.value;
    const ctx = canvas.getContext('2d');
    if (!ctx)
        return;
    const scaleX = image.naturalWidth / bounds.width;
    const scaleY = image.naturalHeight / bounds.height;
    const localX = point.x - bounds.left;
    const localY = point.y - bounds.top;
    const naturalX = localX * scaleX;
    const naturalY = localY * scaleY;
    const size = 150;
    const zoom = 4;
    const sourceSize = size / zoom;
    const sx = naturalX - sourceSize / 2;
    const sy = naturalY - sourceSize / 2;
    ctx.clearRect(0, 0, size, size);
    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, size, size);
    ctx.drawImage(image, sx, sy, sourceSize, sourceSize, 0, 0, size, size);
    ctx.strokeStyle = 'rgba(255, 0, 0, 0.85)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(size / 2, 0);
    ctx.lineTo(size / 2, size);
    ctx.moveTo(0, size / 2);
    ctx.lineTo(size, size / 2);
    ctx.stroke();
}
function toStagePoint(event) {
    const bounds = getImageBoundsInStage();
    if (!cropStageRef.value || !bounds)
        return null;
    const stageRect = cropStageRef.value.getBoundingClientRect();
    const right = bounds.left + bounds.width;
    const bottom = bounds.top + bounds.height;
    const x = Math.max(bounds.left, Math.min(event.clientX - stageRect.left, right));
    const y = Math.max(bounds.top, Math.min(event.clientY - stageRect.top, bottom));
    return { x, y };
}
function cropSelectedImage() {
    if (!patternImgRef.value || !hasSelection.value) {
        throw new Error('未选择裁剪区域');
    }
    const bounds = getImageBoundsInStage();
    if (!bounds) {
        throw new Error('图像位置无效');
    }
    const imageEl = patternImgRef.value;
    const displayWidth = bounds.width;
    const displayHeight = bounds.height;
    if (!displayWidth || !displayHeight) {
        throw new Error('图像尺寸无效');
    }
    const scaleX = imageEl.naturalWidth / displayWidth;
    const scaleY = imageEl.naturalHeight / displayHeight;
    const localX = cropRect.x - bounds.left;
    const localY = cropRect.y - bounds.top;
    const sx = Math.max(0, Math.floor(localX * scaleX));
    const sy = Math.max(0, Math.floor(localY * scaleY));
    const sw = Math.max(1, Math.floor(cropRect.w * scaleX));
    const sh = Math.max(1, Math.floor(cropRect.h * scaleY));
    const canvas = document.createElement('canvas');
    canvas.width = sw;
    canvas.height = sh;
    const context = canvas.getContext('2d');
    if (!context) {
        throw new Error('无法创建图像上下文');
    }
    context.drawImage(imageEl, sx, sy, sw, sh, 0, 0, sw, sh);
    return {
        dataUrl: canvas.toDataURL('image/jpeg', 0.95),
        width: sw,
        height: sh
    };
}
function onGridWheel(event) {
    const oldSize = cellSize.value;
    const delta = event.deltaY < 0 ? 2 : -2;
    const nextSize = Math.max(MIN_CELL_SIZE, Math.min(MAX_CELL_SIZE, oldSize + delta));
    if (nextSize === oldSize) {
        return;
    }
    const viewport = gridViewportRef.value;
    if (!viewport) {
        cellSize.value = nextSize;
        return;
    }
    const rect = viewport.getBoundingClientRect();
    const mouseX = event.clientX - rect.left + viewport.scrollLeft;
    const mouseY = event.clientY - rect.top + viewport.scrollTop;
    const ratio = nextSize / oldSize;
    cellSize.value = nextSize;
    viewport.scrollLeft = mouseX * ratio - (event.clientX - rect.left);
    viewport.scrollTop = mouseY * ratio - (event.clientY - rect.top);
}
function onGridMouseDown(event) {
    if (event.button !== 0) {
        return;
    }
    const viewport = gridViewportRef.value;
    if (!viewport) {
        return;
    }
    isGridPanning.value = true;
    gridPanStart.x = event.clientX;
    gridPanStart.y = event.clientY;
    gridPanStart.scrollLeft = viewport.scrollLeft;
    gridPanStart.scrollTop = viewport.scrollTop;
}
function onGridMouseMove(event) {
    if (!isGridPanning.value) {
        return;
    }
    const viewport = gridViewportRef.value;
    if (!viewport) {
        return;
    }
    const dx = event.clientX - gridPanStart.x;
    const dy = event.clientY - gridPanStart.y;
    viewport.scrollLeft = gridPanStart.scrollLeft - dx;
    viewport.scrollTop = gridPanStart.scrollTop - dy;
}
function onGridMouseUp() {
    isGridPanning.value = false;
}
function parseGridCells(gridCellsJson, expectedLength) {
    if (!gridCellsJson) {
        return new Array(expectedLength).fill('');
    }
    try {
        const parsed = JSON.parse(gridCellsJson);
        if (!Array.isArray(parsed)) {
            return new Array(expectedLength).fill('');
        }
        const normalized = parsed.map(item => (item == null ? '' : String(item)));
        if (normalized.length < expectedLength) {
            return [...normalized, ...new Array(expectedLength - normalized.length).fill('')];
        }
        return normalized.slice(0, expectedLength);
    }
    catch {
        return new Array(expectedLength).fill('');
    }
}
const hasSelection = computed(() => cropRect.w > 0 && cropRect.h > 0);
const paletteCodes = computed(() => {
    const fromGrid = cells.value.filter(Boolean);
    const fromRequired = localColors.value
        .map(item => (item.code || '').trim().toUpperCase())
        .filter(Boolean);
    return Array.from(new Set([...fromGrid, ...fromRequired])).sort((left, right) => left.localeCompare(right, 'en'));
});
const cropRectStyle = computed(() => ({
    left: `${cropRect.x}px`,
    top: `${cropRect.y}px`,
    width: `${cropRect.w}px`,
    height: `${cropRect.h}px`
}));
const topLeftHandleStyle = computed(() => ({
    left: `${cropRect.x}px`,
    top: `${cropRect.y}px`
}));
const bottomRightHandleStyle = computed(() => ({
    left: `${cropRect.x + cropRect.w}px`,
    top: `${cropRect.y + cropRect.h}px`
}));
const selectionHint = computed(() => {
    if (!hasSelection.value)
        return '未框选';
    const left = Math.round(cropRect.x);
    const top = Math.round(cropRect.y);
    const right = Math.round(cropRect.x + cropRect.w);
    const bottom = Math.round(cropRect.y + cropRect.h);
    return `左上(${left}, ${top}) 右下(${right}, ${bottom})`;
});
const magnifierStyle = computed(() => ({
    left: `${magnifierPos.x}px`,
    top: `${magnifierPos.y}px`
}));
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
/** @type {__VLS_StyleScopedClasses['magnifier']} */ ;
/** @type {__VLS_StyleScopedClasses['bead-grid-scroll']} */ ;
/** @type {__VLS_StyleScopedClasses['palette-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['nudge-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['nudge-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['nudge-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['nudge-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['debug-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['debug-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['debug-panel']} */ ;
// CSS variable injection 
// CSS variable injection end 
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "row-between" },
    ...{ style: {} },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.h3, __VLS_intrinsicElements.h3)({
    ...{ style: {} },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.saveColors) },
    ...{ class: "btn success" },
});
if (!__VLS_ctx.project.patternImage) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "card" },
    });
    const __VLS_0 = {}.RouterLink;
    /** @type {[typeof __VLS_components.RouterLink, typeof __VLS_components.RouterLink, ]} */ ;
    // @ts-ignore
    const __VLS_1 = __VLS_asFunctionalComponent(__VLS_0, new __VLS_0({
        to: (`/detail/${__VLS_ctx.project.id}`),
    }));
    const __VLS_2 = __VLS_1({
        to: (`/detail/${__VLS_ctx.project.id}`),
    }, ...__VLS_functionalComponentArgsRest(__VLS_1));
    __VLS_3.slots.default;
    var __VLS_3;
}
else {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "card" },
        ...{ style: {} },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "row-between" },
        ...{ style: {} },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.h4, __VLS_intrinsicElements.h4)({
        ...{ style: {} },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.extractRequiredColorsBySelection) },
        ...{ class: "btn secondary" },
        disabled: (__VLS_ctx.extractingColors || !__VLS_ctx.project.patternImage || !__VLS_ctx.hasSelection),
    });
    (__VLS_ctx.extractingColors ? '识别中...' : '按当前选区识别');
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "row" },
        ...{ style: {} },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({
        ...{ style: {} },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        type: "checkbox",
    });
    (__VLS_ctx.debugMode);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({
        ...{ style: {} },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "table-wrap" },
        ...{ style: {} },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.table, __VLS_intrinsicElements.table)({
        ...{ class: "table" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.thead, __VLS_intrinsicElements.thead)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.tbody, __VLS_intrinsicElements.tbody)({});
    for (const [row, idx] of __VLS_getVForSourceType((__VLS_ctx.localColors))) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({
            key: (idx),
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            ...{ class: "input" },
        });
        (row.code);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            ...{ class: "input" },
            type: "number",
            min: "1",
        });
        (row.quantity);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
            ...{ onClick: (...[$event]) => {
                    if (!!(!__VLS_ctx.project.patternImage))
                        return;
                    __VLS_ctx.localColors.splice(idx, 1);
                } },
            ...{ class: "btn warn" },
        });
    }
    if (__VLS_ctx.debugMode && __VLS_ctx.debugResult) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "debug-panel" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ style: {} },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.strong, __VLS_intrinsicElements.strong)({});
        (__VLS_ctx.debugResult.strategy || 'unknown');
        __VLS_asFunctionalElement(__VLS_intrinsicElements.details, __VLS_intrinsicElements.details)({
            open: true,
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.summary, __VLS_intrinsicElements.summary)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.pre, __VLS_intrinsicElements.pre)({});
        ((__VLS_ctx.debugResult.locationLines || []).join('\n') || '（空）');
        __VLS_asFunctionalElement(__VLS_intrinsicElements.details, __VLS_intrinsicElements.details)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.summary, __VLS_intrinsicElements.summary)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.pre, __VLS_intrinsicElements.pre)({});
        ((__VLS_ctx.debugResult.pairLogs || []).join('\n') || '（空）');
        __VLS_asFunctionalElement(__VLS_intrinsicElements.details, __VLS_intrinsicElements.details)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.summary, __VLS_intrinsicElements.summary)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.pre, __VLS_intrinsicElements.pre)({});
        ((__VLS_ctx.debugResult.fallbackLogs || []).join('\n') || '（空）');
        __VLS_asFunctionalElement(__VLS_intrinsicElements.details, __VLS_intrinsicElements.details)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.summary, __VLS_intrinsicElements.summary)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.pre, __VLS_intrinsicElements.pre)({});
        (__VLS_ctx.debugResult.rawText || '（空）');
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "card" },
        ...{ style: {} },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.h4, __VLS_intrinsicElements.h4)({
        ...{ style: {} },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "row" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        ...{ class: "input" },
        ...{ style: {} },
        type: "number",
    });
    (__VLS_ctx.rows);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        ...{ class: "input" },
        ...{ style: {} },
        type: "number",
    });
    (__VLS_ctx.cols);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.analyzeGrid) },
        ...{ class: "btn secondary" },
        disabled: (__VLS_ctx.analyzing || !__VLS_ctx.project.patternImage),
    });
    (__VLS_ctx.analyzing ? '分析中...' : '分析网格');
    __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({
        ...{ style: {} },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "card" },
        ...{ style: {} },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ onMousemove: (__VLS_ctx.onCropMouseMove) },
        ...{ onMouseup: (__VLS_ctx.onCropMouseUp) },
        ...{ onMouseleave: (__VLS_ctx.onCropMouseUp) },
        ref: "cropStageRef",
        ...{ class: "crop-stage" },
    });
    /** @type {typeof __VLS_ctx.cropStageRef} */ ;
    __VLS_asFunctionalElement(__VLS_intrinsicElements.img)({
        ...{ onLoad: (__VLS_ctx.onPatternImageLoad) },
        ref: "patternImgRef",
        src: (__VLS_ctx.project.patternImage),
        ...{ class: "crop-image" },
    });
    /** @type {typeof __VLS_ctx.patternImgRef} */ ;
    if (__VLS_ctx.hasSelection) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "crop-mask" },
            ...{ style: (__VLS_ctx.cropRectStyle) },
        });
    }
    if (__VLS_ctx.hasSelection) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ onMousedown: (...[$event]) => {
                    if (!!(!__VLS_ctx.project.patternImage))
                        return;
                    if (!(__VLS_ctx.hasSelection))
                        return;
                    __VLS_ctx.onHandleMouseDown('TL', $event);
                } },
            ...{ class: "crop-handle" },
            ...{ style: (__VLS_ctx.topLeftHandleStyle) },
        });
    }
    if (__VLS_ctx.hasSelection) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ onMousedown: (...[$event]) => {
                    if (!!(!__VLS_ctx.project.patternImage))
                        return;
                    if (!(__VLS_ctx.hasSelection))
                        return;
                    __VLS_ctx.onHandleMouseDown('BR', $event);
                } },
            ...{ class: "crop-handle" },
            ...{ style: (__VLS_ctx.bottomRightHandleStyle) },
        });
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "magnifier" },
        ...{ style: (__VLS_ctx.magnifierStyle) },
    });
    __VLS_asFunctionalDirective(__VLS_directives.vShow)(null, { ...__VLS_directiveBindingRestFields, value: (__VLS_ctx.magnifierVisible) }, null, null);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.canvas, __VLS_intrinsicElements.canvas)({
        ref: "magnifierCanvasRef",
        width: "150",
        height: "150",
    });
    /** @type {typeof __VLS_ctx.magnifierCanvasRef} */ ;
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "row" },
        ...{ style: {} },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ style: {} },
    });
    (__VLS_ctx.selectionHint);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.resetSelection) },
        ...{ class: "btn secondary" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "nudge-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "row" },
        ...{ style: {} },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({
        ...{ style: {} },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
        ...{ class: "select" },
        ...{ style: {} },
        value: (__VLS_ctx.selectedCorner),
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "TL",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "BR",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({
        ...{ style: {} },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        ...{ class: "input" },
        ...{ style: {} },
        type: "number",
        min: "1",
        max: "20",
    });
    (__VLS_ctx.nudgeStep);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "nudge-grid" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!!(!__VLS_ctx.project.patternImage))
                    return;
                __VLS_ctx.nudgeSelection(0, -__VLS_ctx.nudgeStep);
            } },
        ...{ class: "btn secondary" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!!(!__VLS_ctx.project.patternImage))
                    return;
                __VLS_ctx.nudgeSelection(-__VLS_ctx.nudgeStep, 0);
            } },
        ...{ class: "btn secondary" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!!(!__VLS_ctx.project.patternImage))
                    return;
                __VLS_ctx.nudgeSelection(__VLS_ctx.nudgeStep, 0);
            } },
        ...{ class: "btn secondary" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!!(!__VLS_ctx.project.patternImage))
                    return;
                __VLS_ctx.nudgeSelection(0, __VLS_ctx.nudgeStep);
            } },
        ...{ class: "btn secondary" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ style: {} },
    });
    (__VLS_ctx.analyzeStatus);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "card" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.h4, __VLS_intrinsicElements.h4)({
        ...{ style: {} },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "palette-wrap" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!!(!__VLS_ctx.project.patternImage))
                    return;
                __VLS_ctx.selectedCode = null;
            } },
        ...{ class: "palette-btn" },
        ...{ class: ({ active: __VLS_ctx.selectedCode === null }) },
    });
    for (const [code] of __VLS_getVForSourceType((__VLS_ctx.paletteCodes))) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
            ...{ onClick: (...[$event]) => {
                    if (!!(!__VLS_ctx.project.patternImage))
                        return;
                    __VLS_ctx.selectedCode = code;
                } },
            key: (code),
            ...{ class: "palette-btn" },
            ...{ class: ({ active: __VLS_ctx.selectedCode === code }) },
        });
        (code);
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ onWheel: (__VLS_ctx.onGridWheel) },
        ...{ onMousedown: (__VLS_ctx.onGridMouseDown) },
        ...{ onMousemove: (__VLS_ctx.onGridMouseMove) },
        ...{ onMouseup: (__VLS_ctx.onGridMouseUp) },
        ...{ onMouseleave: (__VLS_ctx.onGridMouseUp) },
        ref: "gridViewportRef",
        ...{ class: "bead-grid-scroll" },
        ...{ class: ({ panning: __VLS_ctx.isGridPanning }) },
    });
    /** @type {typeof __VLS_ctx.gridViewportRef} */ ;
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "bead-grid-inner" },
        ...{ style: (__VLS_ctx.gridStyle) },
    });
    for (const [cell, idx] of __VLS_getVForSourceType((__VLS_ctx.cells))) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            key: (idx),
            ...{ style: (__VLS_ctx.cellStyle(cell)) },
        });
        (cell || '');
    }
}
/** @type {__VLS_StyleScopedClasses['row-between']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['success']} */ ;
/** @type {__VLS_StyleScopedClasses['card']} */ ;
/** @type {__VLS_StyleScopedClasses['card']} */ ;
/** @type {__VLS_StyleScopedClasses['row-between']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['table-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['table']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['warn']} */ ;
/** @type {__VLS_StyleScopedClasses['debug-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['card']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['card']} */ ;
/** @type {__VLS_StyleScopedClasses['crop-stage']} */ ;
/** @type {__VLS_StyleScopedClasses['crop-image']} */ ;
/** @type {__VLS_StyleScopedClasses['crop-mask']} */ ;
/** @type {__VLS_StyleScopedClasses['crop-handle']} */ ;
/** @type {__VLS_StyleScopedClasses['crop-handle']} */ ;
/** @type {__VLS_StyleScopedClasses['magnifier']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['nudge-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['select']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['nudge-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['card']} */ ;
/** @type {__VLS_StyleScopedClasses['palette-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['palette-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['palette-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['bead-grid-scroll']} */ ;
/** @type {__VLS_StyleScopedClasses['bead-grid-inner']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            rows: rows,
            cols: cols,
            localColors: localColors,
            cells: cells,
            selectedCode: selectedCode,
            analyzing: analyzing,
            extractingColors: extractingColors,
            debugMode: debugMode,
            debugResult: debugResult,
            analyzeStatus: analyzeStatus,
            cropStageRef: cropStageRef,
            patternImgRef: patternImgRef,
            magnifierCanvasRef: magnifierCanvasRef,
            gridViewportRef: gridViewportRef,
            selectedCorner: selectedCorner,
            nudgeStep: nudgeStep,
            magnifierVisible: magnifierVisible,
            isGridPanning: isGridPanning,
            gridStyle: gridStyle,
            cellStyle: cellStyle,
            analyzeGrid: analyzeGrid,
            extractRequiredColorsBySelection: extractRequiredColorsBySelection,
            saveColors: saveColors,
            resetSelection: resetSelection,
            onPatternImageLoad: onPatternImageLoad,
            onHandleMouseDown: onHandleMouseDown,
            onCropMouseMove: onCropMouseMove,
            onCropMouseUp: onCropMouseUp,
            nudgeSelection: nudgeSelection,
            onGridWheel: onGridWheel,
            onGridMouseDown: onGridMouseDown,
            onGridMouseMove: onGridMouseMove,
            onGridMouseUp: onGridMouseUp,
            hasSelection: hasSelection,
            paletteCodes: paletteCodes,
            cropRectStyle: cropRectStyle,
            topLeftHandleStyle: topLeftHandleStyle,
            bottomRightHandleStyle: bottomRightHandleStyle,
            selectionHint: selectionHint,
            magnifierStyle: magnifierStyle,
        };
    },
    __typeProps: {},
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
    __typeProps: {},
});
; /* PartiallyEnd: #4569/main.vue */
