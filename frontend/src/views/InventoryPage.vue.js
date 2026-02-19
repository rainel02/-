import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { beadApi } from '@/api/beads';
import { getCodeHex, normalizeColorCode } from '@/utils/color-map';
const tab = ref('stock');
const codeGroups = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'M'];
const stock = ref([]);
const usage = ref([]);
const demand = ref([]);
const todoProjects = ref([]);
const stockSortField = ref('code');
const stockDesc = ref(false);
const thresholdDraft = ref({});
const thresholdSaving = ref({});
const inTotalDraft = ref({});
const inTotalSaving = ref({});
const showRestockDialog = ref(false);
const restockDraft = ref({});
const uniformFillValue = ref(1000);
const inTotalSaveTimers = new Map();
const thresholdSaveTimers = new Map();
const stockTableWrapRef = ref(null);
const usageTableWrapRef = ref(null);
const demandTableWrapRef = ref(null);
const usageProjects = computed(() => collectProjects(usage.value.flatMap(row => row.projects)));
const demandProjects = computed(() => collectProjects(demand.value.flatMap(row => row.projects)));
const sortedStock = computed(() => {
    const arr = [...stock.value];
    arr.sort((a, b) => {
        if (stockSortField.value === 'code') {
            return compareColorCode(a.code, b.code);
        }
        return String(a.warning).localeCompare(String(b.warning), 'zh-CN');
    });
    if (stockDesc.value)
        arr.reverse();
    return arr;
});
function normalizedCode(code) {
    return normalizeColorCode(code);
}
function compareColorCode(leftCode, rightCode) {
    const left = normalizeColorCode(leftCode || '');
    const right = normalizeColorCode(rightCode || '');
    const regex = /^([A-Z]+)(\d+)$/;
    const lm = left.match(regex);
    const rm = right.match(regex);
    if (lm && rm) {
        const letterDiff = lm[1].localeCompare(rm[1], 'zh-CN');
        if (letterDiff !== 0) {
            return letterDiff;
        }
        return Number(lm[2]) - Number(rm[2]);
    }
    return left.localeCompare(right, 'zh-CN');
}
function syncDrafts(rows) {
    const thresholdMap = {};
    const inTotalMap = {};
    const restockMap = {};
    for (const row of rows) {
        const code = normalizeColorCode(row.code);
        thresholdMap[code] = row.alertThreshold ?? 0;
        inTotalMap[code] = row.inTotal ?? 0;
        restockMap[code] = restockDraft.value[code] ?? 0;
    }
    thresholdDraft.value = thresholdMap;
    inTotalDraft.value = inTotalMap;
    restockDraft.value = restockMap;
}
async function reloadAll() {
    const [stockResp, usageResp, demandResp, todoResp] = await Promise.allSettled([
        beadApi.inventory.stock(),
        beadApi.inventory.usage(),
        beadApi.inventory.demand(),
        beadApi.inventory.todoProjects()
    ]);
    stock.value = stockResp.status === 'fulfilled' ? stockResp.value.map(row => ({ ...row, code: normalizeColorCode(row.code) })) : [];
    usage.value = usageResp.status === 'fulfilled' ? usageResp.value.map(row => ({ ...row, code: normalizeColorCode(row.code) })) : [];
    demand.value = demandResp.status === 'fulfilled' ? demandResp.value.map(row => ({ ...row, code: normalizeColorCode(row.code) })) : [];
    todoProjects.value = todoResp.status === 'fulfilled' ? todoResp.value : [];
    syncDrafts(stock.value);
    if (stockResp.status === 'rejected' || usageResp.status === 'rejected' || demandResp.status === 'rejected') {
        console.error('库存页关键接口存在失败', {
            stock: stockResp,
            usage: usageResp,
            demand: demandResp
        });
        alert('部分库存接口请求失败，请检查后端是否为最新版本并已重启');
    }
}
function collectProjects(items) {
    const map = new Map();
    for (const item of items) {
        if (!map.has(item.projectId)) {
            map.set(item.projectId, item);
        }
    }
    return [...map.values()].sort((a, b) => String(a.projectName || '').localeCompare(String(b.projectName || ''), 'zh-CN'));
}
function findProjectUsed(items, projectId) {
    const found = items.find(item => item.projectId === projectId);
    return found?.used ?? 0;
}
function totalRowProjectUsed(items) {
    return items.reduce((sum, item) => sum + Number(item.used || 0), 0);
}
async function saveThreshold(code) {
    const normalized = normalizeColorCode(code);
    const value = Math.max(0, Number(thresholdDraft.value[normalized] ?? 0));
    const current = stock.value.find(row => row.code === normalized)?.alertThreshold ?? 0;
    if (value === current) {
        return;
    }
    thresholdSaving.value = { ...thresholdSaving.value, [normalized]: true };
    try {
        stock.value = await beadApi.inventory.updateThresholds({ [normalized]: value });
        stock.value = stock.value.map(row => ({ ...row, code: normalizeColorCode(row.code) }));
        syncDrafts(stock.value);
    }
    catch (error) {
        console.error(error);
        alert(`色号 ${normalized} 阈值保存失败`);
        await reloadAll();
    }
    finally {
        thresholdSaving.value = { ...thresholdSaving.value, [normalized]: false };
    }
}
function queueSaveThreshold(code) {
    const normalized = normalizeColorCode(code);
    const existing = thresholdSaveTimers.get(normalized);
    if (existing) {
        window.clearTimeout(existing);
    }
    const timer = window.setTimeout(() => {
        thresholdSaveTimers.delete(normalized);
        void saveThreshold(normalized);
    }, 450);
    thresholdSaveTimers.set(normalized, timer);
}
async function saveInTotal(code) {
    const normalized = normalizeColorCode(code);
    const value = Math.max(0, Number(inTotalDraft.value[normalized] ?? 0));
    const current = stock.value.find(row => row.code === normalized)?.inTotal ?? 0;
    if (value === current) {
        return;
    }
    inTotalSaving.value = { ...inTotalSaving.value, [normalized]: true };
    try {
        stock.value = await beadApi.inventory.updateInTotals({ [normalized]: value });
        stock.value = stock.value.map(row => ({ ...row, code: normalizeColorCode(row.code) }));
        syncDrafts(stock.value);
    }
    catch (error) {
        console.error(error);
        alert(`色号 ${normalized} 入库总量保存失败`);
        await reloadAll();
    }
    finally {
        inTotalSaving.value = { ...inTotalSaving.value, [normalized]: false };
    }
}
function queueSaveInTotal(code) {
    const normalized = normalizeColorCode(code);
    const existing = inTotalSaveTimers.get(normalized);
    if (existing) {
        window.clearTimeout(existing);
    }
    const timer = window.setTimeout(() => {
        inTotalSaveTimers.delete(normalized);
        void saveInTotal(normalized);
    }, 450);
    inTotalSaveTimers.set(normalized, timer);
}
function openRestockDialog() {
    showRestockDialog.value = true;
}
function fillRestock(value) {
    const next = {};
    const safeValue = Math.max(0, Number(value || 0));
    for (const row of stock.value) {
        next[row.code] = safeValue;
    }
    restockDraft.value = next;
}
function jumpToCodeGroup(group) {
    const wrap = tab.value === 'stock'
        ? stockTableWrapRef.value
        : tab.value === 'usage'
            ? usageTableWrapRef.value
            : demandTableWrapRef.value;
    if (!wrap) {
        return;
    }
    const target = wrap.querySelector(`tr[data-code^="${group}"]`);
    if (target) {
        target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
}
function onTableWheel(event, tableType) {
    if (!event.shiftKey) {
        return;
    }
    const wrap = tableType === 'stock'
        ? stockTableWrapRef.value
        : tableType === 'usage'
            ? usageTableWrapRef.value
            : demandTableWrapRef.value;
    if (!wrap) {
        return;
    }
    const canScrollX = wrap.scrollWidth > wrap.clientWidth;
    if (!canScrollX) {
        return;
    }
    const horizontalDelta = event.deltaY === 0 ? event.deltaX : event.deltaY;
    if (horizontalDelta === 0) {
        return;
    }
    event.preventDefault();
    wrap.scrollLeft += horizontalDelta;
}
function warningClass(warning) {
    return String(warning || '').includes('急需补货') ? 'warning-low' : 'warning-ok';
}
async function submitRestock() {
    const payload = {};
    for (const [code, quantity] of Object.entries(restockDraft.value)) {
        const normalized = normalizeColorCode(code);
        const value = Math.max(0, Number(quantity || 0));
        if (value > 0) {
            payload[normalized] = (payload[normalized] || 0) + value;
        }
    }
    if (Object.keys(payload).length === 0) {
        alert('请先填写至少一个色号的入库数量');
        return;
    }
    await beadApi.inventory.restock(payload);
    await reloadAll();
    showRestockDialog.value = false;
    alert('入库完成');
}
onBeforeUnmount(() => {
    for (const timer of inTotalSaveTimers.values()) {
        window.clearTimeout(timer);
    }
    inTotalSaveTimers.clear();
    for (const timer of thresholdSaveTimers.values()) {
        window.clearTimeout(timer);
    }
    thresholdSaveTimers.clear();
});
onMounted(async () => {
    try {
        await reloadAll();
    }
    catch (error) {
        console.error(error);
        alert('库存数据加载失败，请确认后端服务已启动');
    }
});
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
/** @type {__VLS_StyleScopedClasses['table-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['table-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['table']} */ ;
/** @type {__VLS_StyleScopedClasses['table-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['table']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-usage']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-demand']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-usage']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-demand']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-usage']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-demand']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-usage']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-demand']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-usage']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-demand']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['stock-select']} */ ;
/** @type {__VLS_StyleScopedClasses['uniform-input']} */ ;
/** @type {__VLS_StyleScopedClasses['usage-col-1']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-col-1']} */ ;
// CSS variable injection 
// CSS variable injection end 
__VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
    ...{ class: "card inventory-page" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.h2, __VLS_intrinsicElements.h2)({
    ...{ class: "section-title" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "tabs" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (...[$event]) => {
            __VLS_ctx.tab = 'stock';
        } },
    ...{ class: "tab" },
    ...{ class: ({ active: __VLS_ctx.tab === 'stock' }) },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (...[$event]) => {
            __VLS_ctx.tab = 'usage';
        } },
    ...{ class: "tab" },
    ...{ class: ({ active: __VLS_ctx.tab === 'usage' }) },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (...[$event]) => {
            __VLS_ctx.tab = 'demand';
        } },
    ...{ class: "tab" },
    ...{ class: ({ active: __VLS_ctx.tab === 'demand' }) },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "quick-jump" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
    ...{ class: "quick-jump-label" },
});
for (const [group] of __VLS_getVForSourceType((__VLS_ctx.codeGroups))) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                __VLS_ctx.jumpToCodeGroup(group);
            } },
        key: (group),
        ...{ class: "btn secondary jump-btn" },
    });
    (group);
}
if (__VLS_ctx.tab === 'stock') {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "row stock-toolbar" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
        ...{ class: "select stock-select" },
        value: (__VLS_ctx.stockSortField),
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "code",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "warning",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.tab === 'stock'))
                    return;
                __VLS_ctx.stockDesc = !__VLS_ctx.stockDesc;
            } },
        ...{ class: "btn secondary" },
    });
    (__VLS_ctx.stockDesc ? '降序' : '升序');
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.openRestockDialog) },
        ...{ class: "btn" },
    });
}
if (__VLS_ctx.tab === 'stock') {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ onWheel: (...[$event]) => {
                if (!(__VLS_ctx.tab === 'stock'))
                    return;
                __VLS_ctx.onTableWheel($event, 'stock');
            } },
        ...{ class: "table-wrap" },
        ref: "stockTableWrapRef",
    });
    /** @type {typeof __VLS_ctx.stockTableWrapRef} */ ;
    __VLS_asFunctionalElement(__VLS_intrinsicElements.table, __VLS_intrinsicElements.table)({
        ...{ class: "table" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.thead, __VLS_intrinsicElements.thead)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.tbody, __VLS_intrinsicElements.tbody)({});
    for (const [row] of __VLS_getVForSourceType((__VLS_ctx.sortedStock))) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({
            key: (row.code),
            'data-code': (__VLS_ctx.normalizedCode(row.code)),
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "code-cell" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "color-dot" },
            ...{ style: ({ background: __VLS_ctx.getCodeHex(row.code) || '#fff' }) },
        });
        (__VLS_ctx.normalizedCode(row.code));
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({
            ...{ class: "narrow-col" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "saving-cell" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            ...{ onInput: (...[$event]) => {
                    if (!(__VLS_ctx.tab === 'stock'))
                        return;
                    __VLS_ctx.queueSaveInTotal(row.code);
                } },
            ...{ onBlur: (...[$event]) => {
                    if (!(__VLS_ctx.tab === 'stock'))
                        return;
                    __VLS_ctx.saveInTotal(row.code);
                } },
            ...{ onKeydown: (...[$event]) => {
                    if (!(__VLS_ctx.tab === 'stock'))
                        return;
                    __VLS_ctx.saveInTotal(row.code);
                } },
            ...{ class: "input compact-input" },
            type: "number",
            min: "0",
        });
        (__VLS_ctx.inTotalDraft[row.code]);
        if (__VLS_ctx.inTotalSaving[row.code]) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
                ...{ class: "saving-text" },
            });
        }
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
        (row.remain);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
        (row.used);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({
            ...{ class: "narrow-col" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "saving-cell" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            ...{ onInput: (...[$event]) => {
                    if (!(__VLS_ctx.tab === 'stock'))
                        return;
                    __VLS_ctx.queueSaveThreshold(row.code);
                } },
            ...{ onBlur: (...[$event]) => {
                    if (!(__VLS_ctx.tab === 'stock'))
                        return;
                    __VLS_ctx.saveThreshold(row.code);
                } },
            ...{ onKeydown: (...[$event]) => {
                    if (!(__VLS_ctx.tab === 'stock'))
                        return;
                    __VLS_ctx.saveThreshold(row.code);
                } },
            ...{ class: "input compact-input" },
            type: "number",
            min: "0",
        });
        (__VLS_ctx.thresholdDraft[row.code]);
        if (__VLS_ctx.thresholdSaving[row.code]) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
                ...{ class: "saving-text" },
            });
        }
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "warning-badge" },
            ...{ class: (__VLS_ctx.warningClass(row.warning)) },
        });
        (row.warning);
    }
}
if (__VLS_ctx.tab === 'usage') {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ onWheel: (...[$event]) => {
                if (!(__VLS_ctx.tab === 'usage'))
                    return;
                __VLS_ctx.onTableWheel($event, 'usage');
            } },
        ...{ class: "table-wrap" },
        ref: "usageTableWrapRef",
    });
    /** @type {typeof __VLS_ctx.usageTableWrapRef} */ ;
    __VLS_asFunctionalElement(__VLS_intrinsicElements.table, __VLS_intrinsicElements.table)({
        ...{ class: "table sticky-usage" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.thead, __VLS_intrinsicElements.thead)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({
        ...{ class: "sticky-col usage-col-1" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({
        ...{ class: "sticky-col usage-col-2" },
    });
    for (const [project] of __VLS_getVForSourceType((__VLS_ctx.usageProjects))) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({
            key: (`usage-head-${project.projectId}`),
            ...{ class: "usage-head-row1" },
        });
        if (project.projectUrl) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.a, __VLS_intrinsicElements.a)({
                href: (project.projectUrl),
                target: "_blank",
            });
            (project.projectName);
        }
        else {
            const __VLS_0 = {}.RouterLink;
            /** @type {[typeof __VLS_components.RouterLink, typeof __VLS_components.RouterLink, ]} */ ;
            // @ts-ignore
            const __VLS_1 = __VLS_asFunctionalComponent(__VLS_0, new __VLS_0({
                to: (`/detail/${project.projectId}`),
            }));
            const __VLS_2 = __VLS_1({
                to: (`/detail/${project.projectId}`),
            }, ...__VLS_functionalComponentArgsRest(__VLS_1));
            __VLS_3.slots.default;
            (project.projectName);
            var __VLS_3;
        }
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({
        ...{ class: "sticky-col usage-col-1 usage-head-row2" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({
        ...{ class: "sticky-col usage-col-2 usage-head-row2" },
    });
    for (const [project] of __VLS_getVForSourceType((__VLS_ctx.usageProjects))) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({
            key: (`usage-image-${project.projectId}`),
            ...{ class: "usage-head-row2" },
        });
        if (project.projectImage) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.img)({
                src: (project.projectImage),
                ...{ class: "project-thumb" },
            });
        }
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.tbody, __VLS_intrinsicElements.tbody)({});
    if (__VLS_ctx.usage.length === 0) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({
            colspan: (2 + __VLS_ctx.usageProjects.length),
        });
    }
    for (const [row] of __VLS_getVForSourceType((__VLS_ctx.usage))) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({
            key: (row.code),
            'data-code': (__VLS_ctx.normalizedCode(row.code)),
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({
            ...{ class: "sticky-col usage-col-1 usage-body-sticky" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "code-cell" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "color-dot" },
            ...{ style: ({ background: __VLS_ctx.getCodeHex(row.code) || '#fff' }) },
        });
        (__VLS_ctx.normalizedCode(row.code));
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({
            ...{ class: "sticky-col usage-col-2 usage-body-sticky" },
        });
        (row.used);
        for (const [project] of __VLS_getVForSourceType((__VLS_ctx.usageProjects))) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({
                key: (`usage-body-${row.code}-${project.projectId}`),
            });
            (__VLS_ctx.findProjectUsed(row.projects, project.projectId));
        }
    }
}
if (__VLS_ctx.tab === 'demand') {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ onWheel: (...[$event]) => {
                if (!(__VLS_ctx.tab === 'demand'))
                    return;
                __VLS_ctx.onTableWheel($event, 'demand');
            } },
        ...{ class: "table-wrap" },
        ref: "demandTableWrapRef",
    });
    /** @type {typeof __VLS_ctx.demandTableWrapRef} */ ;
    __VLS_asFunctionalElement(__VLS_intrinsicElements.table, __VLS_intrinsicElements.table)({
        ...{ class: "table sticky-demand" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.thead, __VLS_intrinsicElements.thead)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({
        ...{ class: "sticky-col demand-col-1" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({
        ...{ class: "sticky-col demand-col-2" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({
        ...{ class: "sticky-col demand-col-3" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({
        ...{ class: "sticky-col demand-col-4" },
    });
    for (const [project] of __VLS_getVForSourceType((__VLS_ctx.demandProjects))) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({
            key: (`demand-head-${project.projectId}`),
            ...{ class: "demand-head-row1" },
        });
        if (project.projectUrl) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.a, __VLS_intrinsicElements.a)({
                href: (project.projectUrl),
                target: "_blank",
            });
            (project.projectName);
        }
        else {
            const __VLS_4 = {}.RouterLink;
            /** @type {[typeof __VLS_components.RouterLink, typeof __VLS_components.RouterLink, ]} */ ;
            // @ts-ignore
            const __VLS_5 = __VLS_asFunctionalComponent(__VLS_4, new __VLS_4({
                to: (`/detail/${project.projectId}`),
            }));
            const __VLS_6 = __VLS_5({
                to: (`/detail/${project.projectId}`),
            }, ...__VLS_functionalComponentArgsRest(__VLS_5));
            __VLS_7.slots.default;
            (project.projectName);
            var __VLS_7;
        }
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({
        ...{ class: "sticky-col demand-col-1 demand-head-row2" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({
        ...{ class: "sticky-col demand-col-2 demand-head-row2" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({
        ...{ class: "sticky-col demand-col-3 demand-head-row2" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({
        ...{ class: "sticky-col demand-col-4 demand-head-row2" },
    });
    for (const [project] of __VLS_getVForSourceType((__VLS_ctx.demandProjects))) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({
            key: (`demand-image-${project.projectId}`),
            ...{ class: "demand-head-row2" },
        });
        if (project.projectImage) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.img)({
                src: (project.projectImage),
                ...{ class: "project-thumb" },
            });
        }
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.tbody, __VLS_intrinsicElements.tbody)({});
    if (__VLS_ctx.demand.length === 0) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({
            colspan: (4 + __VLS_ctx.demandProjects.length),
        });
    }
    for (const [row] of __VLS_getVForSourceType((__VLS_ctx.demand))) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({
            key: (row.code),
            'data-code': (__VLS_ctx.normalizedCode(row.code)),
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({
            ...{ class: "sticky-col demand-col-1 demand-body-sticky" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "code-cell" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "color-dot" },
            ...{ style: ({ background: __VLS_ctx.getCodeHex(row.code) || '#fff' }) },
        });
        (__VLS_ctx.normalizedCode(row.code));
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({
            ...{ class: "sticky-col demand-col-2 demand-body-sticky" },
        });
        (row.remain);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({
            ...{ class: "sticky-col demand-col-3 demand-body-sticky" },
        });
        (__VLS_ctx.totalRowProjectUsed(row.projects));
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({
            ...{ class: "sticky-col demand-col-4 demand-body-sticky" },
        });
        (row.need);
        for (const [project] of __VLS_getVForSourceType((__VLS_ctx.demandProjects))) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({
                key: (`demand-body-${row.code}-${project.projectId}`),
            });
            (__VLS_ctx.findProjectUsed(row.projects, project.projectId));
        }
    }
}
if (__VLS_ctx.showRestockDialog) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "modal-mask" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "modal-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "row-between dialog-toolbar" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.h3, __VLS_intrinsicElements.h3)({
        ...{ class: "dialog-title" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.showRestockDialog))
                    return;
                __VLS_ctx.showRestockDialog = false;
            } },
        ...{ class: "btn secondary" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "row dialog-actions" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        ...{ class: "input uniform-input" },
        type: "number",
        min: "0",
    });
    (__VLS_ctx.uniformFillValue);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.showRestockDialog))
                    return;
                __VLS_ctx.fillRestock(__VLS_ctx.uniformFillValue);
            } },
        ...{ class: "btn warn" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.submitRestock) },
        ...{ class: "btn success" },
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
    __VLS_asFunctionalElement(__VLS_intrinsicElements.tbody, __VLS_intrinsicElements.tbody)({});
    for (const [row] of __VLS_getVForSourceType((__VLS_ctx.sortedStock))) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({
            key: (`restock-${row.code}`),
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "code-cell" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "color-dot" },
            ...{ style: ({ background: __VLS_ctx.getCodeHex(row.code) || '#fff' }) },
        });
        (__VLS_ctx.normalizedCode(row.code));
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({
            ...{ class: "restock-col" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            ...{ class: "input" },
            type: "number",
            min: "0",
        });
        (__VLS_ctx.restockDraft[row.code]);
    }
}
/** @type {__VLS_StyleScopedClasses['card']} */ ;
/** @type {__VLS_StyleScopedClasses['inventory-page']} */ ;
/** @type {__VLS_StyleScopedClasses['section-title']} */ ;
/** @type {__VLS_StyleScopedClasses['tabs']} */ ;
/** @type {__VLS_StyleScopedClasses['tab']} */ ;
/** @type {__VLS_StyleScopedClasses['tab']} */ ;
/** @type {__VLS_StyleScopedClasses['tab']} */ ;
/** @type {__VLS_StyleScopedClasses['quick-jump']} */ ;
/** @type {__VLS_StyleScopedClasses['quick-jump-label']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['jump-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['stock-toolbar']} */ ;
/** @type {__VLS_StyleScopedClasses['select']} */ ;
/** @type {__VLS_StyleScopedClasses['stock-select']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['table-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['table']} */ ;
/** @type {__VLS_StyleScopedClasses['code-cell']} */ ;
/** @type {__VLS_StyleScopedClasses['color-dot']} */ ;
/** @type {__VLS_StyleScopedClasses['narrow-col']} */ ;
/** @type {__VLS_StyleScopedClasses['saving-cell']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['compact-input']} */ ;
/** @type {__VLS_StyleScopedClasses['saving-text']} */ ;
/** @type {__VLS_StyleScopedClasses['narrow-col']} */ ;
/** @type {__VLS_StyleScopedClasses['saving-cell']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['compact-input']} */ ;
/** @type {__VLS_StyleScopedClasses['saving-text']} */ ;
/** @type {__VLS_StyleScopedClasses['warning-badge']} */ ;
/** @type {__VLS_StyleScopedClasses['table-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['table']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-usage']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['usage-col-1']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['usage-col-2']} */ ;
/** @type {__VLS_StyleScopedClasses['usage-head-row1']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['usage-col-1']} */ ;
/** @type {__VLS_StyleScopedClasses['usage-head-row2']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['usage-col-2']} */ ;
/** @type {__VLS_StyleScopedClasses['usage-head-row2']} */ ;
/** @type {__VLS_StyleScopedClasses['usage-head-row2']} */ ;
/** @type {__VLS_StyleScopedClasses['project-thumb']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['usage-col-1']} */ ;
/** @type {__VLS_StyleScopedClasses['usage-body-sticky']} */ ;
/** @type {__VLS_StyleScopedClasses['code-cell']} */ ;
/** @type {__VLS_StyleScopedClasses['color-dot']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['usage-col-2']} */ ;
/** @type {__VLS_StyleScopedClasses['usage-body-sticky']} */ ;
/** @type {__VLS_StyleScopedClasses['table-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['table']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-demand']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-col-1']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-col-2']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-col-3']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-col-4']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-head-row1']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-col-1']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-head-row2']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-col-2']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-head-row2']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-col-3']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-head-row2']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-col-4']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-head-row2']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-head-row2']} */ ;
/** @type {__VLS_StyleScopedClasses['project-thumb']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-col-1']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-body-sticky']} */ ;
/** @type {__VLS_StyleScopedClasses['code-cell']} */ ;
/** @type {__VLS_StyleScopedClasses['color-dot']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-col-2']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-body-sticky']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-col-3']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-body-sticky']} */ ;
/** @type {__VLS_StyleScopedClasses['sticky-col']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-col-4']} */ ;
/** @type {__VLS_StyleScopedClasses['demand-body-sticky']} */ ;
/** @type {__VLS_StyleScopedClasses['modal-mask']} */ ;
/** @type {__VLS_StyleScopedClasses['modal-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['row-between']} */ ;
/** @type {__VLS_StyleScopedClasses['dialog-toolbar']} */ ;
/** @type {__VLS_StyleScopedClasses['dialog-title']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['dialog-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['uniform-input']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['warn']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['success']} */ ;
/** @type {__VLS_StyleScopedClasses['table-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['table']} */ ;
/** @type {__VLS_StyleScopedClasses['code-cell']} */ ;
/** @type {__VLS_StyleScopedClasses['color-dot']} */ ;
/** @type {__VLS_StyleScopedClasses['restock-col']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            getCodeHex: getCodeHex,
            tab: tab,
            codeGroups: codeGroups,
            usage: usage,
            demand: demand,
            stockSortField: stockSortField,
            stockDesc: stockDesc,
            thresholdDraft: thresholdDraft,
            thresholdSaving: thresholdSaving,
            inTotalDraft: inTotalDraft,
            inTotalSaving: inTotalSaving,
            showRestockDialog: showRestockDialog,
            restockDraft: restockDraft,
            uniformFillValue: uniformFillValue,
            stockTableWrapRef: stockTableWrapRef,
            usageTableWrapRef: usageTableWrapRef,
            demandTableWrapRef: demandTableWrapRef,
            usageProjects: usageProjects,
            demandProjects: demandProjects,
            sortedStock: sortedStock,
            normalizedCode: normalizedCode,
            findProjectUsed: findProjectUsed,
            totalRowProjectUsed: totalRowProjectUsed,
            saveThreshold: saveThreshold,
            queueSaveThreshold: queueSaveThreshold,
            saveInTotal: saveInTotal,
            queueSaveInTotal: queueSaveInTotal,
            openRestockDialog: openRestockDialog,
            fillRestock: fillRestock,
            jumpToCodeGroup: jumpToCodeGroup,
            onTableWheel: onTableWheel,
            warningClass: warningClass,
            submitRestock: submitRestock,
        };
    },
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
});
; /* PartiallyEnd: #4569/main.vue */
