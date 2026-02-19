import { computed, nextTick, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { beadApi } from '@/api/beads';
const router = useRouter();
const existingTags = ref([]);
const selectedTags = ref([]);
const newTag = ref('');
const confirmDialog = reactive({
    visible: false,
    tag: '',
    message: ''
});
const extracting = ref(false);
const cropStageRef = ref(null);
const patternImgRef = ref(null);
const magnifierCanvasRef = ref(null);
const activeHandle = ref(null);
const selectedCorner = ref('BR');
const nudgeStep = ref(1);
const cropRect = reactive({ x: 0, y: 0, w: 0, h: 0 });
const magnifierVisible = ref(false);
const magnifierPos = reactive({ x: 0, y: 0 });
const MIN_CROP_SIZE = 8;
const form = reactive({
    name: '',
    tags: [],
    status: 'TODO',
    sourceUrl: '',
    patternImage: '',
    workImage: '',
    requiredColors: []
});
function fileToBase64(file) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result || ''));
        reader.onerror = reject;
        reader.readAsDataURL(file);
    });
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
function onHandleMouseDown(handle, event) {
    event.preventDefault();
    event.stopPropagation();
    activeHandle.value = handle;
    selectedCorner.value = handle;
    magnifierVisible.value = true;
    const point = toStagePoint(event);
    if (point) {
        updateMagnifier(point);
        updateMagnifierPosition(point);
    }
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
    if (activeHandle.value === 'TR') {
        const maxRight = bounds.left + bounds.width;
        const newRight = Math.max(Math.min(point.x, maxRight), cropRect.x + MIN_CROP_SIZE);
        const newY = Math.min(Math.max(point.y, bounds.top), bottom - MIN_CROP_SIZE);
        cropRect.w = newRight - cropRect.x;
        cropRect.h = bottom - newY;
        cropRect.y = newY;
        return;
    }
    if (activeHandle.value === 'BL') {
        const maxBottom = bounds.top + bounds.height;
        const newX = Math.min(Math.max(point.x, bounds.left), right - MIN_CROP_SIZE);
        const newBottom = Math.max(Math.min(point.y, maxBottom), cropRect.y + MIN_CROP_SIZE);
        cropRect.w = right - newX;
        cropRect.h = newBottom - cropRect.y;
        cropRect.x = newX;
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
    if (cropRect.w < MIN_CROP_SIZE || cropRect.h < MIN_CROP_SIZE) {
        initSelectionByCorners();
    }
}
function refreshNudgeMagnifier() {
    const point = selectedCorner.value === 'TL'
        ? { x: cropRect.x, y: cropRect.y }
        : selectedCorner.value === 'TR'
            ? { x: cropRect.x + cropRect.w, y: cropRect.y }
            : selectedCorner.value === 'BL'
                ? { x: cropRect.x, y: cropRect.y + cropRect.h }
                : { x: cropRect.x + cropRect.w, y: cropRect.y + cropRect.h };
    magnifierVisible.value = true;
    updateMagnifier(point);
    updateMagnifierPosition(point);
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
    const maxRight = bounds.left + bounds.width;
    const maxBottom = bounds.top + bounds.height;
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
    if (selectedCorner.value === 'TR') {
        const nextRight = Math.max(Math.min(right + stepX, maxRight), cropRect.x + MIN_CROP_SIZE);
        const nextY = Math.min(Math.max(cropRect.y + stepY, bounds.top), bottom - MIN_CROP_SIZE);
        cropRect.w = nextRight - cropRect.x;
        cropRect.h = bottom - nextY;
        cropRect.y = nextY;
        refreshNudgeMagnifier();
        return;
    }
    if (selectedCorner.value === 'BL') {
        const nextX = Math.min(Math.max(cropRect.x + stepX, bounds.left), right - MIN_CROP_SIZE);
        const nextBottom = Math.max(Math.min(bottom + stepY, maxBottom), cropRect.y + MIN_CROP_SIZE);
        cropRect.w = right - nextX;
        cropRect.h = nextBottom - cropRect.y;
        cropRect.x = nextX;
        refreshNudgeMagnifier();
        return;
    }
    const nextRight = Math.max(Math.min(right + stepX, maxRight), cropRect.x + MIN_CROP_SIZE);
    const nextBottom = Math.max(Math.min(bottom + stepY, maxBottom), cropRect.y + MIN_CROP_SIZE);
    cropRect.w = nextRight - cropRect.x;
    cropRect.h = nextBottom - cropRect.y;
    refreshNudgeMagnifier();
}
function updateMagnifierPosition(point) {
    if (!cropStageRef.value)
        return;
    const size = 150;
    const margin = 12;
    const stageWidth = cropStageRef.value.clientWidth;
    const stageHeight = cropStageRef.value.clientHeight;
    let x = point.x + margin;
    let y = point.y + margin;
    if (activeHandle.value === 'BR' || activeHandle.value === 'BL') {
        y = point.y - size - margin;
    }
    if (x + size > stageWidth)
        x = stageWidth - size;
    if (y + size > stageHeight)
        y = stageHeight - size;
    if (x < 0)
        x = 0;
    if (y < 0)
        y = 0;
    magnifierPos.x = x;
    magnifierPos.y = y;
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
        renderWidth = boxWidth;
        renderHeight = boxWidth / naturalRatio;
        offsetY = (boxHeight - renderHeight) / 2;
    }
    else {
        renderHeight = boxHeight;
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
function onPatternImageLoad() {
    initSelectionByCorners();
}
function resetSelection() {
    initSelectionByCorners();
}
async function onPattern(e) {
    const file = e.target.files?.[0];
    if (!file)
        return;
    form.patternImage = await fileToBase64(file);
    await nextTick();
    initSelectionByCorners();
}
async function onWork(e) {
    const file = e.target.files?.[0];
    if (!file)
        return;
    form.workImage = await fileToBase64(file);
}
async function extract() {
    if (!form.patternImage)
        return;
    if (!hasSelection.value) {
        alert('请先在图纸上框选色号说明区域');
        return;
    }
    extracting.value = true;
    try {
        const selectedImage = cropSelectedImageBase64();
        const rows = await beadApi.extractColors(selectedImage);
        form.requiredColors = rows;
        if (rows.length === 0) {
            alert('未识别到有效色号，请调整图纸清晰度后重试，或手动添加。');
        }
    }
    catch (error) {
        console.error(error);
        alert('图纸识别失败，请重试或手动添加色号。');
    }
    finally {
        extracting.value = false;
    }
}
function removeColor(index) {
    form.requiredColors?.splice(index, 1);
}
function addColorRow() {
    if (!form.requiredColors) {
        form.requiredColors = [];
    }
    form.requiredColors.push({ code: '', quantity: 1 });
}
async function save() {
    form.tags = selectedTags.value;
    if (!form.name?.trim())
        return alert('拼豆名称必填');
    if (!form.status)
        return alert('拼豆状态必填');
    if (!form.patternImage)
        return alert('拼豆图纸必填');
    if (!form.requiredColors || form.requiredColors.length === 0)
        return alert('提取色号与数量必填');
    const created = await beadApi.create({
        ...form,
        quantityDone: 0,
        quantityPlan: 1
    });
    router.push(`/detail/${created.id}`);
}
function addCustomTag() {
    const t = newTag.value.trim();
    if (!t)
        return;
    if (!existingTags.value.includes(t)) {
        existingTags.value.push(t);
    }
    if (!selectedTags.value.includes(t)) {
        selectedTags.value.push(t);
    }
    newTag.value = '';
}
async function removeTag(tag) {
    const t = tag.trim();
    if (!t)
        return;
    confirmDialog.visible = true;
    confirmDialog.tag = t;
    confirmDialog.message = `确认删除 tag「${t}」吗？删除后，所有含该 tag 的拼豆都会移除这个 tag（其他内容不变）。`;
}
function closeConfirmDialog() {
    confirmDialog.visible = false;
    confirmDialog.tag = '';
    confirmDialog.message = '';
}
async function submitConfirmDialog() {
    const t = confirmDialog.tag.trim();
    if (!t) {
        closeConfirmDialog();
        return;
    }
    try {
        await beadApi.deleteTag(t);
        existingTags.value = existingTags.value.filter(item => item !== t);
        selectedTags.value = selectedTags.value.filter(item => item !== t);
        alert(`已删除 tag：${t}`);
    }
    catch (error) {
        console.error(error);
        alert('删除 tag 失败，请稍后重试');
    }
    finally {
        closeConfirmDialog();
    }
}
function cropSelectedImageBase64() {
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
    const paddingDisplayPx = 8;
    const paddingX = paddingDisplayPx * scaleX;
    const paddingY = paddingDisplayPx * scaleY;
    const localX = cropRect.x - bounds.left;
    const localY = cropRect.y - bounds.top;
    const naturalX = localX * scaleX;
    const naturalY = localY * scaleY;
    const naturalW = cropRect.w * scaleX;
    const naturalH = cropRect.h * scaleY;
    const sx = Math.max(0, Math.floor(naturalX - paddingX));
    const sy = Math.max(0, Math.floor(naturalY - paddingY));
    const ex = Math.min(imageEl.naturalWidth, Math.ceil(naturalX + naturalW + paddingX));
    const ey = Math.min(imageEl.naturalHeight, Math.ceil(naturalY + naturalH + paddingY));
    const sw = Math.max(1, ex - sx);
    const sh = Math.max(1, ey - sy);
    const canvas = document.createElement('canvas');
    canvas.width = sw;
    canvas.height = sh;
    const ctx = canvas.getContext('2d');
    if (!ctx) {
        throw new Error('无法创建图像上下文');
    }
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(imageEl, sx, sy, sw, sh, 0, 0, sw, sh);
    return canvas.toDataURL('image/jpeg', 0.95);
}
const hasSelection = computed(() => cropRect.w > 0 && cropRect.h > 0);
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
const topRightHandleStyle = computed(() => ({
    left: `${cropRect.x + cropRect.w}px`,
    top: `${cropRect.y}px`
}));
const bottomLeftHandleStyle = computed(() => ({
    left: `${cropRect.x}px`,
    top: `${cropRect.y + cropRect.h}px`
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
onMounted(async () => {
    try {
        existingTags.value = await beadApi.tags();
    }
    catch (e) {
        console.error(e);
    }
});
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
/** @type {__VLS_StyleScopedClasses['tag-remove-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['crop-handle']} */ ;
/** @type {__VLS_StyleScopedClasses['crop-handle']} */ ;
/** @type {__VLS_StyleScopedClasses['crop-handle']} */ ;
/** @type {__VLS_StyleScopedClasses['crop-handle']} */ ;
/** @type {__VLS_StyleScopedClasses['magnifier']} */ ;
/** @type {__VLS_StyleScopedClasses['nudge-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['nudge-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['nudge-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['nudge-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['new-layout']} */ ;
// CSS variable injection 
// CSS variable injection end 
__VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
    ...{ class: "card new-page" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.h2, __VLS_intrinsicElements.h2)({
    ...{ class: "section-title" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "row new-layout" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "new-col" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
    ...{ class: "input" },
});
(__VLS_ctx.form.name);
__VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "card tag-card" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "row tag-list" },
});
for (const [tag] of __VLS_getVForSourceType((__VLS_ctx.existingTags))) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        key: (tag),
        ...{ class: "tag-item" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({
        ...{ class: "tag-item-label" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        type: "checkbox",
        value: (tag),
    });
    (__VLS_ctx.selectedTags);
    (tag);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                __VLS_ctx.removeTag(tag);
            } },
        ...{ class: "tag-remove-btn" },
    });
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "row" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
    ...{ class: "input" },
    placeholder: "新增 tag",
});
(__VLS_ctx.newTag);
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.addCustomTag) },
    ...{ class: "btn secondary" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "selected-tags" },
});
(__VLS_ctx.selectedTags.join(', ') || '无');
__VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
    ...{ class: "select" },
    value: (__VLS_ctx.form.status),
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
    value: "DONE",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
    value: "IN_PROGRESS",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
    value: "TODO",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
    ...{ class: "input" },
    placeholder: "https://...",
});
(__VLS_ctx.form.sourceUrl);
__VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
    ...{ onChange: (__VLS_ctx.onPattern) },
    ...{ class: "input" },
    type: "file",
    accept: "image/*",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
    ...{ onChange: (__VLS_ctx.onWork) },
    ...{ class: "input" },
    type: "file",
    accept: "image/*",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "new-col" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.h3, __VLS_intrinsicElements.h3)({
    ...{ class: "extract-title" },
});
if (__VLS_ctx.form.patternImage) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "card crop-card" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "helper-text" },
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
        src: (__VLS_ctx.form.patternImage),
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
                    if (!(__VLS_ctx.form.patternImage))
                        return;
                    if (!(__VLS_ctx.hasSelection))
                        return;
                    __VLS_ctx.onHandleMouseDown('TL', $event);
                } },
            ...{ class: "crop-handle tl" },
            ...{ style: (__VLS_ctx.topLeftHandleStyle) },
        });
    }
    if (__VLS_ctx.hasSelection) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ onMousedown: (...[$event]) => {
                    if (!(__VLS_ctx.form.patternImage))
                        return;
                    if (!(__VLS_ctx.hasSelection))
                        return;
                    __VLS_ctx.onHandleMouseDown('TR', $event);
                } },
            ...{ class: "crop-handle tr" },
            ...{ style: (__VLS_ctx.topRightHandleStyle) },
        });
    }
    if (__VLS_ctx.hasSelection) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ onMousedown: (...[$event]) => {
                    if (!(__VLS_ctx.form.patternImage))
                        return;
                    if (!(__VLS_ctx.hasSelection))
                        return;
                    __VLS_ctx.onHandleMouseDown('BL', $event);
                } },
            ...{ class: "crop-handle bl" },
            ...{ style: (__VLS_ctx.bottomLeftHandleStyle) },
        });
    }
    if (__VLS_ctx.hasSelection) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ onMousedown: (...[$event]) => {
                    if (!(__VLS_ctx.form.patternImage))
                        return;
                    if (!(__VLS_ctx.hasSelection))
                        return;
                    __VLS_ctx.onHandleMouseDown('BR', $event);
                } },
            ...{ class: "crop-handle br" },
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
        ...{ class: "row selection-row" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "helper-text" },
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
        ...{ class: "row nudge-toolbar" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({
        ...{ class: "helper-text" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
        ...{ class: "select nudge-select" },
        value: (__VLS_ctx.selectedCorner),
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "TL",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "TR",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "BL",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "BR",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({
        ...{ class: "helper-text" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        ...{ class: "input nudge-input" },
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
                if (!(__VLS_ctx.form.patternImage))
                    return;
                __VLS_ctx.nudgeSelection(0, -__VLS_ctx.nudgeStep);
            } },
        ...{ class: "btn secondary" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.form.patternImage))
                    return;
                __VLS_ctx.nudgeSelection(-__VLS_ctx.nudgeStep, 0);
            } },
        ...{ class: "btn secondary" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.form.patternImage))
                    return;
                __VLS_ctx.nudgeSelection(__VLS_ctx.nudgeStep, 0);
            } },
        ...{ class: "btn secondary" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.form.patternImage))
                    return;
                __VLS_ctx.nudgeSelection(0, __VLS_ctx.nudgeStep);
            } },
        ...{ class: "btn secondary" },
    });
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.extract) },
    ...{ class: "btn secondary" },
    disabled: (!__VLS_ctx.form.patternImage || __VLS_ctx.extracting),
});
(__VLS_ctx.extracting ? '识别中...' : '从图纸提取色号');
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "table-wrap color-table-wrap" },
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
for (const [row, idx] of __VLS_getVForSourceType((__VLS_ctx.form.requiredColors))) {
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
                __VLS_ctx.removeColor(idx);
            } },
        ...{ class: "btn warn" },
    });
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.addColorRow) },
    ...{ class: "btn secondary" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "row page-actions" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.save) },
    ...{ class: "btn success" },
});
const __VLS_0 = {}.RouterLink;
/** @type {[typeof __VLS_components.RouterLink, typeof __VLS_components.RouterLink, ]} */ ;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent(__VLS_0, new __VLS_0({
    ...{ class: "btn secondary" },
    to: "/overview",
}));
const __VLS_2 = __VLS_1({
    ...{ class: "btn secondary" },
    to: "/overview",
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
__VLS_3.slots.default;
var __VLS_3;
if (__VLS_ctx.confirmDialog.visible) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ onClick: (__VLS_ctx.closeConfirmDialog) },
        ...{ class: "modal-mask" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ onClick: () => { } },
        ...{ class: "modal-panel confirm-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.h3, __VLS_intrinsicElements.h3)({
        ...{ class: "confirm-title" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({
        ...{ class: "confirm-text" },
    });
    (__VLS_ctx.confirmDialog.message);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "row confirm-actions" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.closeConfirmDialog) },
        ...{ class: "btn secondary" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.submitConfirmDialog) },
        ...{ class: "btn warn" },
    });
}
/** @type {__VLS_StyleScopedClasses['card']} */ ;
/** @type {__VLS_StyleScopedClasses['new-page']} */ ;
/** @type {__VLS_StyleScopedClasses['section-title']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['new-layout']} */ ;
/** @type {__VLS_StyleScopedClasses['new-col']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['card']} */ ;
/** @type {__VLS_StyleScopedClasses['tag-card']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['tag-list']} */ ;
/** @type {__VLS_StyleScopedClasses['tag-item']} */ ;
/** @type {__VLS_StyleScopedClasses['tag-item-label']} */ ;
/** @type {__VLS_StyleScopedClasses['tag-remove-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['selected-tags']} */ ;
/** @type {__VLS_StyleScopedClasses['select']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['new-col']} */ ;
/** @type {__VLS_StyleScopedClasses['extract-title']} */ ;
/** @type {__VLS_StyleScopedClasses['card']} */ ;
/** @type {__VLS_StyleScopedClasses['crop-card']} */ ;
/** @type {__VLS_StyleScopedClasses['helper-text']} */ ;
/** @type {__VLS_StyleScopedClasses['crop-stage']} */ ;
/** @type {__VLS_StyleScopedClasses['crop-image']} */ ;
/** @type {__VLS_StyleScopedClasses['crop-mask']} */ ;
/** @type {__VLS_StyleScopedClasses['crop-handle']} */ ;
/** @type {__VLS_StyleScopedClasses['tl']} */ ;
/** @type {__VLS_StyleScopedClasses['crop-handle']} */ ;
/** @type {__VLS_StyleScopedClasses['tr']} */ ;
/** @type {__VLS_StyleScopedClasses['crop-handle']} */ ;
/** @type {__VLS_StyleScopedClasses['bl']} */ ;
/** @type {__VLS_StyleScopedClasses['crop-handle']} */ ;
/** @type {__VLS_StyleScopedClasses['br']} */ ;
/** @type {__VLS_StyleScopedClasses['magnifier']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['selection-row']} */ ;
/** @type {__VLS_StyleScopedClasses['helper-text']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['nudge-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['nudge-toolbar']} */ ;
/** @type {__VLS_StyleScopedClasses['helper-text']} */ ;
/** @type {__VLS_StyleScopedClasses['select']} */ ;
/** @type {__VLS_StyleScopedClasses['nudge-select']} */ ;
/** @type {__VLS_StyleScopedClasses['helper-text']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['nudge-input']} */ ;
/** @type {__VLS_StyleScopedClasses['nudge-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['table-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['color-table-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['table']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['warn']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['page-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['success']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['modal-mask']} */ ;
/** @type {__VLS_StyleScopedClasses['modal-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['confirm-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['confirm-title']} */ ;
/** @type {__VLS_StyleScopedClasses['confirm-text']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['confirm-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['warn']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            existingTags: existingTags,
            selectedTags: selectedTags,
            newTag: newTag,
            confirmDialog: confirmDialog,
            extracting: extracting,
            cropStageRef: cropStageRef,
            patternImgRef: patternImgRef,
            magnifierCanvasRef: magnifierCanvasRef,
            selectedCorner: selectedCorner,
            nudgeStep: nudgeStep,
            magnifierVisible: magnifierVisible,
            form: form,
            onHandleMouseDown: onHandleMouseDown,
            onCropMouseMove: onCropMouseMove,
            onCropMouseUp: onCropMouseUp,
            nudgeSelection: nudgeSelection,
            onPatternImageLoad: onPatternImageLoad,
            resetSelection: resetSelection,
            onPattern: onPattern,
            onWork: onWork,
            extract: extract,
            removeColor: removeColor,
            addColorRow: addColorRow,
            save: save,
            addCustomTag: addCustomTag,
            removeTag: removeTag,
            closeConfirmDialog: closeConfirmDialog,
            submitConfirmDialog: submitConfirmDialog,
            hasSelection: hasSelection,
            cropRectStyle: cropRectStyle,
            topLeftHandleStyle: topLeftHandleStyle,
            topRightHandleStyle: topRightHandleStyle,
            bottomLeftHandleStyle: bottomLeftHandleStyle,
            bottomRightHandleStyle: bottomRightHandleStyle,
            selectionHint: selectionHint,
            magnifierStyle: magnifierStyle,
        };
    },
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
});
; /* PartiallyEnd: #4569/main.vue */
