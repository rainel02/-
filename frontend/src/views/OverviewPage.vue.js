import { computed, onMounted, ref } from 'vue';
import { beadApi } from '@/api/beads';
const list = ref([]);
const draggingId = ref(null);
const collapsedIds = ref([]);
const allTags = ref([]);
const selectedTag = ref('');
const confirmDialog = ref({ visible: false, projectId: 0 });
const filteredList = computed(() => {
    if (!selectedTag.value) {
        return list.value;
    }
    return list.value.filter(v => (v.tags || []).includes(selectedTag.value));
});
const byStatus = computed(() => ({
    DONE: filteredList.value.filter(v => v.status === 'DONE'),
    IN_PROGRESS: filteredList.value.filter(v => v.status === 'IN_PROGRESS'),
    TODO: filteredList.value.filter(v => v.status === 'TODO')
}));
async function load() {
    try {
        const [projects, tags] = await Promise.all([beadApi.list(), beadApi.tags()]);
        list.value = projects;
        allTags.value = tags;
    }
    catch (e) {
        console.error(e);
        alert('加载总览失败，请确认桌面版后端服务已启动（或重启应用后重试）');
    }
}
function onDragStart(id) {
    draggingId.value = id;
}
async function onDrop(status) {
    if (!draggingId.value)
        return;
    const item = list.value.find(v => v.id === draggingId.value);
    if (!item)
        return;
    try {
        const quantityDone = status === 'DONE' ? item.quantityPlan : item.quantityDone;
        const updated = await beadApi.updateStatusAndQty(item.id, {
            status,
            quantityDone,
            quantityPlan: item.quantityPlan
        });
        const idx = list.value.findIndex(v => v.id === updated.id);
        if (idx >= 0)
            list.value[idx] = updated;
    }
    catch (e) {
        console.error(e);
        alert('拖拽更新失败：请检查后端是否启动，或刷新后重试');
    }
    finally {
        draggingId.value = null;
    }
}
async function changeQty(item, kind, e) {
    const value = Number(e.target.value || 0);
    try {
        const updated = await beadApi.updateStatusAndQty(item.id, {
            status: item.status,
            quantityDone: kind === 'done' ? value : item.quantityDone,
            quantityPlan: kind === 'plan' ? value : item.quantityPlan
        });
        const idx = list.value.findIndex(v => v.id === updated.id);
        if (idx >= 0)
            list.value[idx] = updated;
    }
    catch (e) {
        console.error(e);
        alert('数量更新失败，请稍后重试');
    }
}
async function removeProject(id) {
    confirmDialog.value = { visible: true, projectId: id };
}
function closeConfirmDialog() {
    confirmDialog.value = { visible: false, projectId: 0 };
}
async function submitRemoveProject() {
    const id = confirmDialog.value.projectId;
    if (!id) {
        closeConfirmDialog();
        return;
    }
    try {
        await beadApi.remove(id);
        list.value = list.value.filter(v => v.id !== id);
    }
    catch (e) {
        console.error(e);
        alert('删除失败，请稍后重试');
    }
    finally {
        closeConfirmDialog();
    }
}
function isCollapsed(id) {
    return collapsedIds.value.includes(id);
}
function toggleCollapsed(id) {
    if (isCollapsed(id)) {
        collapsedIds.value = collapsedIds.value.filter(v => v !== id);
    }
    else {
        collapsedIds.value = [...collapsedIds.value, id];
    }
}
function scrollToTop() {
    window.scrollTo({ top: 0, behavior: 'smooth' });
}
onMounted(load);
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
/** @type {__VLS_StyleScopedClasses['overview-filter-row']} */ ;
// CSS variable injection 
// CSS variable injection end 
__VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
    ...{ class: "overview-page" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "row-between overview-toolbar" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.h2, __VLS_intrinsicElements.h2)({
    ...{ class: "section-title" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "row overview-filter-row" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
    ...{ class: "select filter-select" },
    value: (__VLS_ctx.selectedTag),
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
    value: "",
});
for (const [tag] of __VLS_getVForSourceType((__VLS_ctx.allTags))) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        key: (tag),
        value: (tag),
    });
    (tag);
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.load) },
    ...{ class: "btn refresh-btn" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "grid-3" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ onDragover: () => { } },
    ...{ onDrop: (...[$event]) => {
            __VLS_ctx.onDrop('DONE');
        } },
    ...{ class: "kanban-col" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.h3, __VLS_intrinsicElements.h3)({});
for (const [item] of __VLS_getVForSourceType((__VLS_ctx.byStatus.DONE))) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ onDragstart: (...[$event]) => {
                __VLS_ctx.onDragStart(item.id);
            } },
        key: (item.id),
        ...{ class: "kanban-item" },
        draggable: "true",
    });
    if (item.sourceUrl) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.a, __VLS_intrinsicElements.a)({
            href: (item.sourceUrl),
            target: "_blank",
            rel: "noopener noreferrer",
            ...{ class: "project-name" },
        });
        (item.name);
    }
    else {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.strong, __VLS_intrinsicElements.strong)({
            ...{ onClick: (...[$event]) => {
                    if (!!(item.sourceUrl))
                        return;
                    __VLS_ctx.toggleCollapsed(item.id);
                } },
            ...{ class: "project-name" },
        });
        (item.name);
    }
    if (!__VLS_ctx.isCollapsed(item.id)) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "row-between item-meta" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "badge" },
        });
        (item.tags.join(', ') || '无tag');
        if (item.patternImage) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.img)({
                src: (item.patternImage),
                ...{ class: "project-image" },
            });
        }
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "row item-stats" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            ...{ onChange: (...[$event]) => {
                    if (!(!__VLS_ctx.isCollapsed(item.id)))
                        return;
                    __VLS_ctx.changeQty(item, 'done', $event);
                } },
            ...{ class: "input" },
            type: "number",
            min: "0",
            value: (item.quantityDone),
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            ...{ onChange: (...[$event]) => {
                    if (!(!__VLS_ctx.isCollapsed(item.id)))
                        return;
                    __VLS_ctx.changeQty(item, 'plan', $event);
                } },
            ...{ class: "input" },
            type: "number",
            min: "0",
            value: (item.quantityPlan),
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "row-between item-actions" },
        });
        const __VLS_0 = {}.RouterLink;
        /** @type {[typeof __VLS_components.RouterLink, typeof __VLS_components.RouterLink, ]} */ ;
        // @ts-ignore
        const __VLS_1 = __VLS_asFunctionalComponent(__VLS_0, new __VLS_0({
            to: (`/detail/${item.id}`),
        }));
        const __VLS_2 = __VLS_1({
            to: (`/detail/${item.id}`),
        }, ...__VLS_functionalComponentArgsRest(__VLS_1));
        __VLS_3.slots.default;
        var __VLS_3;
        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
            ...{ onClick: (...[$event]) => {
                    if (!(!__VLS_ctx.isCollapsed(item.id)))
                        return;
                    __VLS_ctx.removeProject(item.id);
                } },
            ...{ class: "btn warn" },
        });
    }
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ onDragover: () => { } },
    ...{ onDrop: (...[$event]) => {
            __VLS_ctx.onDrop('IN_PROGRESS');
        } },
    ...{ class: "kanban-col" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.h3, __VLS_intrinsicElements.h3)({});
for (const [item] of __VLS_getVForSourceType((__VLS_ctx.byStatus.IN_PROGRESS))) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ onDragstart: (...[$event]) => {
                __VLS_ctx.onDragStart(item.id);
            } },
        key: (item.id),
        ...{ class: "kanban-item" },
        draggable: "true",
    });
    if (item.sourceUrl) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.a, __VLS_intrinsicElements.a)({
            href: (item.sourceUrl),
            target: "_blank",
            rel: "noopener noreferrer",
            ...{ class: "project-name" },
        });
        (item.name);
    }
    else {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.strong, __VLS_intrinsicElements.strong)({
            ...{ onClick: (...[$event]) => {
                    if (!!(item.sourceUrl))
                        return;
                    __VLS_ctx.toggleCollapsed(item.id);
                } },
            ...{ class: "project-name" },
        });
        (item.name);
    }
    if (!__VLS_ctx.isCollapsed(item.id)) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "row-between item-meta" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "badge" },
        });
        (item.tags.join(', ') || '无tag');
        if (item.patternImage) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.img)({
                src: (item.patternImage),
                ...{ class: "project-image" },
            });
        }
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "row item-stats" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            ...{ onChange: (...[$event]) => {
                    if (!(!__VLS_ctx.isCollapsed(item.id)))
                        return;
                    __VLS_ctx.changeQty(item, 'done', $event);
                } },
            ...{ class: "input" },
            type: "number",
            min: "0",
            value: (item.quantityDone),
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            ...{ onChange: (...[$event]) => {
                    if (!(!__VLS_ctx.isCollapsed(item.id)))
                        return;
                    __VLS_ctx.changeQty(item, 'plan', $event);
                } },
            ...{ class: "input" },
            type: "number",
            min: "0",
            value: (item.quantityPlan),
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "row-between item-actions" },
        });
        const __VLS_4 = {}.RouterLink;
        /** @type {[typeof __VLS_components.RouterLink, typeof __VLS_components.RouterLink, ]} */ ;
        // @ts-ignore
        const __VLS_5 = __VLS_asFunctionalComponent(__VLS_4, new __VLS_4({
            to: (`/detail/${item.id}`),
        }));
        const __VLS_6 = __VLS_5({
            to: (`/detail/${item.id}`),
        }, ...__VLS_functionalComponentArgsRest(__VLS_5));
        __VLS_7.slots.default;
        var __VLS_7;
        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
            ...{ onClick: (...[$event]) => {
                    if (!(!__VLS_ctx.isCollapsed(item.id)))
                        return;
                    __VLS_ctx.removeProject(item.id);
                } },
            ...{ class: "btn warn" },
        });
    }
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ onDragover: () => { } },
    ...{ onDrop: (...[$event]) => {
            __VLS_ctx.onDrop('TODO');
        } },
    ...{ class: "kanban-col" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.h3, __VLS_intrinsicElements.h3)({});
for (const [item] of __VLS_getVForSourceType((__VLS_ctx.byStatus.TODO))) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ onDragstart: (...[$event]) => {
                __VLS_ctx.onDragStart(item.id);
            } },
        key: (item.id),
        ...{ class: "kanban-item" },
        draggable: "true",
    });
    if (item.sourceUrl) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.a, __VLS_intrinsicElements.a)({
            href: (item.sourceUrl),
            target: "_blank",
            rel: "noopener noreferrer",
            ...{ class: "project-name" },
        });
        (item.name);
    }
    else {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.strong, __VLS_intrinsicElements.strong)({
            ...{ onClick: (...[$event]) => {
                    if (!!(item.sourceUrl))
                        return;
                    __VLS_ctx.toggleCollapsed(item.id);
                } },
            ...{ class: "project-name" },
        });
        (item.name);
    }
    if (!__VLS_ctx.isCollapsed(item.id)) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "row-between item-meta" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "badge" },
        });
        (item.tags.join(', ') || '无tag');
        if (item.patternImage) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.img)({
                src: (item.patternImage),
                ...{ class: "project-image" },
            });
        }
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "row item-stats" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            ...{ onChange: (...[$event]) => {
                    if (!(!__VLS_ctx.isCollapsed(item.id)))
                        return;
                    __VLS_ctx.changeQty(item, 'done', $event);
                } },
            ...{ class: "input" },
            type: "number",
            min: "0",
            value: (item.quantityDone),
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            ...{ onChange: (...[$event]) => {
                    if (!(!__VLS_ctx.isCollapsed(item.id)))
                        return;
                    __VLS_ctx.changeQty(item, 'plan', $event);
                } },
            ...{ class: "input" },
            type: "number",
            min: "0",
            value: (item.quantityPlan),
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "row-between item-actions" },
        });
        const __VLS_8 = {}.RouterLink;
        /** @type {[typeof __VLS_components.RouterLink, typeof __VLS_components.RouterLink, ]} */ ;
        // @ts-ignore
        const __VLS_9 = __VLS_asFunctionalComponent(__VLS_8, new __VLS_8({
            to: (`/detail/${item.id}`),
        }));
        const __VLS_10 = __VLS_9({
            to: (`/detail/${item.id}`),
        }, ...__VLS_functionalComponentArgsRest(__VLS_9));
        __VLS_11.slots.default;
        var __VLS_11;
        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
            ...{ onClick: (...[$event]) => {
                    if (!(!__VLS_ctx.isCollapsed(item.id)))
                        return;
                    __VLS_ctx.removeProject(item.id);
                } },
            ...{ class: "btn warn" },
        });
    }
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.scrollToTop) },
    ...{ class: "scroll-top-btn" },
});
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
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "row confirm-actions" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.closeConfirmDialog) },
        ...{ class: "btn secondary" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.submitRemoveProject) },
        ...{ class: "btn warn" },
    });
}
/** @type {__VLS_StyleScopedClasses['overview-page']} */ ;
/** @type {__VLS_StyleScopedClasses['row-between']} */ ;
/** @type {__VLS_StyleScopedClasses['overview-toolbar']} */ ;
/** @type {__VLS_StyleScopedClasses['section-title']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['overview-filter-row']} */ ;
/** @type {__VLS_StyleScopedClasses['select']} */ ;
/** @type {__VLS_StyleScopedClasses['filter-select']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['refresh-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['grid-3']} */ ;
/** @type {__VLS_StyleScopedClasses['kanban-col']} */ ;
/** @type {__VLS_StyleScopedClasses['kanban-item']} */ ;
/** @type {__VLS_StyleScopedClasses['project-name']} */ ;
/** @type {__VLS_StyleScopedClasses['project-name']} */ ;
/** @type {__VLS_StyleScopedClasses['row-between']} */ ;
/** @type {__VLS_StyleScopedClasses['item-meta']} */ ;
/** @type {__VLS_StyleScopedClasses['badge']} */ ;
/** @type {__VLS_StyleScopedClasses['project-image']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['item-stats']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['row-between']} */ ;
/** @type {__VLS_StyleScopedClasses['item-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['warn']} */ ;
/** @type {__VLS_StyleScopedClasses['kanban-col']} */ ;
/** @type {__VLS_StyleScopedClasses['kanban-item']} */ ;
/** @type {__VLS_StyleScopedClasses['project-name']} */ ;
/** @type {__VLS_StyleScopedClasses['project-name']} */ ;
/** @type {__VLS_StyleScopedClasses['row-between']} */ ;
/** @type {__VLS_StyleScopedClasses['item-meta']} */ ;
/** @type {__VLS_StyleScopedClasses['badge']} */ ;
/** @type {__VLS_StyleScopedClasses['project-image']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['item-stats']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['row-between']} */ ;
/** @type {__VLS_StyleScopedClasses['item-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['warn']} */ ;
/** @type {__VLS_StyleScopedClasses['kanban-col']} */ ;
/** @type {__VLS_StyleScopedClasses['kanban-item']} */ ;
/** @type {__VLS_StyleScopedClasses['project-name']} */ ;
/** @type {__VLS_StyleScopedClasses['project-name']} */ ;
/** @type {__VLS_StyleScopedClasses['row-between']} */ ;
/** @type {__VLS_StyleScopedClasses['item-meta']} */ ;
/** @type {__VLS_StyleScopedClasses['badge']} */ ;
/** @type {__VLS_StyleScopedClasses['project-image']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['item-stats']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['row-between']} */ ;
/** @type {__VLS_StyleScopedClasses['item-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['warn']} */ ;
/** @type {__VLS_StyleScopedClasses['scroll-top-btn']} */ ;
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
            allTags: allTags,
            selectedTag: selectedTag,
            confirmDialog: confirmDialog,
            byStatus: byStatus,
            load: load,
            onDragStart: onDragStart,
            onDrop: onDrop,
            changeQty: changeQty,
            removeProject: removeProject,
            closeConfirmDialog: closeConfirmDialog,
            submitRemoveProject: submitRemoveProject,
            isCollapsed: isCollapsed,
            toggleCollapsed: toggleCollapsed,
            scrollToTop: scrollToTop,
        };
    },
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
});
; /* PartiallyEnd: #4569/main.vue */
