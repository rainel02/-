import { onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { beadApi } from '@/api/beads';
import BeadWorkbench from '@/components/BeadWorkbench.vue';
const route = useRoute();
const tab = ref('overview');
const project = ref(null);
const saving = ref(false);
const isEditingOverview = ref(false);
const existingTags = ref([]);
const selectedTags = ref([]);
const newTag = ref('');
const confirmDialog = reactive({
    visible: false,
    type: '',
    payload: '',
    message: ''
});
const form = reactive({
    name: '',
    sourceUrl: '',
    patternImage: '',
    workImage: ''
});
function fileToBase64(file) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result || ''));
        reader.onerror = reject;
        reader.readAsDataURL(file);
    });
}
function applyProjectToForm(detail) {
    form.name = detail.name || '';
    form.sourceUrl = detail.sourceUrl || '';
    form.patternImage = detail.patternImage || '';
    form.workImage = detail.workImage || '';
    selectedTags.value = [...(detail.tags || [])];
}
async function loadProject() {
    const id = Number(route.params.id);
    const [detail, tags] = await Promise.all([beadApi.detail(id), beadApi.tags()]);
    project.value = detail;
    existingTags.value = tags;
    applyProjectToForm(detail);
}
async function onPattern(e) {
    const file = e.target.files?.[0];
    if (!file)
        return;
    form.patternImage = await fileToBase64(file);
}
async function onWork(e) {
    const file = e.target.files?.[0];
    if (!file)
        return;
    form.workImage = await fileToBase64(file);
}
function clearPatternImage() {
    form.patternImage = '';
}
function requestClearPatternImage() {
    if (!form.patternImage)
        return;
    confirmDialog.visible = true;
    confirmDialog.type = 'clear-pattern';
    confirmDialog.payload = '';
    confirmDialog.message = '确认删除拼豆图纸吗？删除后仅清空图纸字段，其他内容不变。';
}
function clearWorkImage() {
    form.workImage = '';
}
function addCustomTag() {
    const tag = newTag.value.trim();
    if (!tag)
        return;
    if (!existingTags.value.includes(tag)) {
        existingTags.value.push(tag);
    }
    if (!selectedTags.value.includes(tag)) {
        selectedTags.value.push(tag);
    }
    newTag.value = '';
}
function startEditOverview() {
    if (!project.value)
        return;
    applyProjectToForm(project.value);
    isEditingOverview.value = true;
}
function cancelEditOverview() {
    if (!project.value)
        return;
    applyProjectToForm(project.value);
    isEditingOverview.value = false;
}
async function removeTag(tag) {
    const t = tag.trim();
    if (!t)
        return;
    confirmDialog.visible = true;
    confirmDialog.type = 'remove-tag';
    confirmDialog.payload = t;
    confirmDialog.message = `确认删除 tag「${t}」吗？删除后，所有含该 tag 的拼豆都会移除这个 tag（其他内容不变）。`;
}
function closeConfirmDialog() {
    confirmDialog.visible = false;
    confirmDialog.type = '';
    confirmDialog.payload = '';
    confirmDialog.message = '';
}
async function submitConfirmDialog() {
    if (confirmDialog.type === 'clear-pattern') {
        clearPatternImage();
        closeConfirmDialog();
        return;
    }
    if (confirmDialog.type !== 'remove-tag') {
        closeConfirmDialog();
        return;
    }
    const t = confirmDialog.payload.trim();
    if (!t) {
        closeConfirmDialog();
        return;
    }
    try {
        await beadApi.deleteTag(t);
        existingTags.value = existingTags.value.filter(item => item !== t);
        selectedTags.value = selectedTags.value.filter(item => item !== t);
        if (project.value) {
            project.value = {
                ...project.value,
                tags: (project.value.tags || []).filter(item => item !== t)
            };
        }
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
async function saveProject() {
    if (!project.value)
        return;
    if (!form.name.trim()) {
        alert('拼豆名称不能为空');
        return;
    }
    saving.value = true;
    try {
        const updated = await beadApi.update(project.value.id, {
            name: form.name,
            tags: selectedTags.value,
            sourceUrl: form.sourceUrl,
            patternImage: form.patternImage,
            workImage: form.workImage
        });
        project.value = updated;
        applyProjectToForm(updated);
        isEditingOverview.value = false;
        alert('详情已保存');
    }
    catch (error) {
        console.error(error);
        alert('保存失败，请稍后重试');
    }
    finally {
        saving.value = false;
    }
}
onMounted(async () => {
    try {
        await loadProject();
    }
    catch (error) {
        console.error(error);
        alert('加载详情失败，请稍后重试');
    }
});
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
/** @type {__VLS_StyleScopedClasses['page-tab']} */ ;
/** @type {__VLS_StyleScopedClasses['modal-input']} */ ;
/** @type {__VLS_StyleScopedClasses['tag-remove-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['subtle-action-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['tabs-header']} */ ;
/** @type {__VLS_StyleScopedClasses['page-tab']} */ ;
/** @type {__VLS_StyleScopedClasses['overview-inline-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['file-action-row']} */ ;
/** @type {__VLS_StyleScopedClasses['add-tag-row']} */ ;
/** @type {__VLS_StyleScopedClasses['detail-image']} */ ;
// CSS variable injection 
// CSS variable injection end 
if (__VLS_ctx.project) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
        ...{ class: "card detail-page" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "row-between detail-toolbar" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.h2, __VLS_intrinsicElements.h2)({
        ...{ class: "section-title" },
    });
    if (__VLS_ctx.project.sourceUrl) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.a, __VLS_intrinsicElements.a)({
            ...{ class: "title-link" },
            href: (__VLS_ctx.project.sourceUrl),
            target: "_blank",
            rel: "noopener noreferrer",
        });
        (__VLS_ctx.project.name);
    }
    else {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
        (__VLS_ctx.project.name);
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "row-between tabs-header" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "page-tabs" },
        role: "tablist",
        'aria-label': "详情分页",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.project))
                    return;
                __VLS_ctx.tab = 'overview';
            } },
        ...{ class: "page-tab" },
        ...{ class: ({ active: __VLS_ctx.tab === 'overview' }) },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.project))
                    return;
                __VLS_ctx.tab = 'work';
            } },
        ...{ class: "page-tab" },
        ...{ class: ({ active: __VLS_ctx.tab === 'work' }) },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.startEditOverview) },
        ...{ class: "btn secondary edit-icon-btn" },
        title: "编辑概况",
    });
    if (__VLS_ctx.tab === 'overview') {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "detail-overview" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "overview-state-bar" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "overview-inline-grid" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "field-label" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "field-label" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "field-value" },
        });
        (__VLS_ctx.project.name || '未填写');
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "row detail-tag-list inline-tag-list" },
        });
        for (const [tag] of __VLS_getVForSourceType((__VLS_ctx.project.tags))) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
                key: (tag),
                ...{ class: "badge" },
            });
            (tag);
        }
        if (!__VLS_ctx.project.tags || __VLS_ctx.project.tags.length === 0) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
                ...{ class: "field-value" },
            });
        }
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "detail-image-block" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "field-label" },
        });
        if (__VLS_ctx.project.patternImage) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.img)({
                src: (__VLS_ctx.project.patternImage),
                ...{ class: "detail-image" },
            });
        }
        else {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
        }
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "detail-image-block" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "field-label" },
        });
        if (__VLS_ctx.project.workImage) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.img)({
                src: (__VLS_ctx.project.workImage),
                ...{ class: "detail-image" },
            });
        }
        else {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
        }
    }
    else {
        /** @type {[typeof BeadWorkbench, ]} */ ;
        // @ts-ignore
        const __VLS_0 = __VLS_asFunctionalComponent(BeadWorkbench, new BeadWorkbench({
            project: (__VLS_ctx.project),
        }));
        const __VLS_1 = __VLS_0({
            project: (__VLS_ctx.project),
        }, ...__VLS_functionalComponentArgsRest(__VLS_0));
    }
}
else {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
        ...{ class: "card" },
    });
}
if (__VLS_ctx.isEditingOverview) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ onClick: (__VLS_ctx.cancelEditOverview) },
        ...{ class: "modal-mask" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ onClick: () => { } },
        ...{ class: "modal-panel edit-modal-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.h3, __VLS_intrinsicElements.h3)({
        ...{ class: "edit-modal-title" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "overview-edit" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({
        ...{ class: "edit-label" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        ...{ class: "input edit-input modal-input" },
    });
    (__VLS_ctx.form.name);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({
        ...{ class: "edit-label top-gap" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "row detail-tag-list" },
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
                    if (!(__VLS_ctx.isEditingOverview))
                        return;
                    __VLS_ctx.removeTag(tag);
                } },
            ...{ class: "tag-remove-btn" },
        });
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "row add-tag-row" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        ...{ class: "input fill-input modal-input" },
        placeholder: "新增 tag",
    });
    (__VLS_ctx.newTag);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.addCustomTag) },
        ...{ class: "btn secondary subtle-action-btn" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({
        ...{ class: "edit-label top-gap" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        ...{ class: "input edit-input modal-input" },
        placeholder: "https://...",
    });
    (__VLS_ctx.form.sourceUrl);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "detail-image-block" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({
        ...{ class: "edit-label top-gap" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "row file-action-row" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        ...{ onChange: (__VLS_ctx.onPattern) },
        ...{ class: "input fill-input modal-input" },
        type: "file",
        accept: "image/*",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.requestClearPatternImage) },
        ...{ class: "btn secondary subtle-action-btn" },
    });
    if (__VLS_ctx.form.patternImage) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.img)({
            src: (__VLS_ctx.form.patternImage),
            ...{ class: "detail-image" },
        });
    }
    else {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "detail-image-block" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({
        ...{ class: "edit-label top-gap" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "row file-action-row" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        ...{ onChange: (__VLS_ctx.onWork) },
        ...{ class: "input fill-input modal-input" },
        type: "file",
        accept: "image/*",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.clearWorkImage) },
        ...{ class: "btn secondary subtle-action-btn" },
    });
    if (__VLS_ctx.form.workImage) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.img)({
            src: (__VLS_ctx.form.workImage),
            ...{ class: "detail-image" },
        });
    }
    else {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "row edit-modal-actions" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.cancelEditOverview) },
        ...{ class: "btn secondary" },
        disabled: (__VLS_ctx.saving),
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.saveProject) },
        ...{ class: "btn success" },
        disabled: (__VLS_ctx.saving),
    });
    (__VLS_ctx.saving ? '保存中...' : '保存详情');
}
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
/** @type {__VLS_StyleScopedClasses['detail-page']} */ ;
/** @type {__VLS_StyleScopedClasses['row-between']} */ ;
/** @type {__VLS_StyleScopedClasses['detail-toolbar']} */ ;
/** @type {__VLS_StyleScopedClasses['section-title']} */ ;
/** @type {__VLS_StyleScopedClasses['title-link']} */ ;
/** @type {__VLS_StyleScopedClasses['row-between']} */ ;
/** @type {__VLS_StyleScopedClasses['tabs-header']} */ ;
/** @type {__VLS_StyleScopedClasses['page-tabs']} */ ;
/** @type {__VLS_StyleScopedClasses['page-tab']} */ ;
/** @type {__VLS_StyleScopedClasses['page-tab']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['edit-icon-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['detail-overview']} */ ;
/** @type {__VLS_StyleScopedClasses['overview-state-bar']} */ ;
/** @type {__VLS_StyleScopedClasses['overview-inline-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['field-label']} */ ;
/** @type {__VLS_StyleScopedClasses['field-label']} */ ;
/** @type {__VLS_StyleScopedClasses['field-value']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['detail-tag-list']} */ ;
/** @type {__VLS_StyleScopedClasses['inline-tag-list']} */ ;
/** @type {__VLS_StyleScopedClasses['badge']} */ ;
/** @type {__VLS_StyleScopedClasses['field-value']} */ ;
/** @type {__VLS_StyleScopedClasses['detail-image-block']} */ ;
/** @type {__VLS_StyleScopedClasses['field-label']} */ ;
/** @type {__VLS_StyleScopedClasses['detail-image']} */ ;
/** @type {__VLS_StyleScopedClasses['detail-image-block']} */ ;
/** @type {__VLS_StyleScopedClasses['field-label']} */ ;
/** @type {__VLS_StyleScopedClasses['detail-image']} */ ;
/** @type {__VLS_StyleScopedClasses['card']} */ ;
/** @type {__VLS_StyleScopedClasses['modal-mask']} */ ;
/** @type {__VLS_StyleScopedClasses['modal-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['edit-modal-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['edit-modal-title']} */ ;
/** @type {__VLS_StyleScopedClasses['overview-edit']} */ ;
/** @type {__VLS_StyleScopedClasses['edit-label']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['edit-input']} */ ;
/** @type {__VLS_StyleScopedClasses['modal-input']} */ ;
/** @type {__VLS_StyleScopedClasses['edit-label']} */ ;
/** @type {__VLS_StyleScopedClasses['top-gap']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['detail-tag-list']} */ ;
/** @type {__VLS_StyleScopedClasses['tag-item']} */ ;
/** @type {__VLS_StyleScopedClasses['tag-item-label']} */ ;
/** @type {__VLS_StyleScopedClasses['tag-remove-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['add-tag-row']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['fill-input']} */ ;
/** @type {__VLS_StyleScopedClasses['modal-input']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['subtle-action-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['edit-label']} */ ;
/** @type {__VLS_StyleScopedClasses['top-gap']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['edit-input']} */ ;
/** @type {__VLS_StyleScopedClasses['modal-input']} */ ;
/** @type {__VLS_StyleScopedClasses['detail-image-block']} */ ;
/** @type {__VLS_StyleScopedClasses['edit-label']} */ ;
/** @type {__VLS_StyleScopedClasses['top-gap']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['file-action-row']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['fill-input']} */ ;
/** @type {__VLS_StyleScopedClasses['modal-input']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['subtle-action-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['detail-image']} */ ;
/** @type {__VLS_StyleScopedClasses['detail-image-block']} */ ;
/** @type {__VLS_StyleScopedClasses['edit-label']} */ ;
/** @type {__VLS_StyleScopedClasses['top-gap']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['file-action-row']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['fill-input']} */ ;
/** @type {__VLS_StyleScopedClasses['modal-input']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['subtle-action-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['detail-image']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['edit-modal-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['success']} */ ;
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
            BeadWorkbench: BeadWorkbench,
            tab: tab,
            project: project,
            saving: saving,
            isEditingOverview: isEditingOverview,
            existingTags: existingTags,
            selectedTags: selectedTags,
            newTag: newTag,
            confirmDialog: confirmDialog,
            form: form,
            onPattern: onPattern,
            onWork: onWork,
            requestClearPatternImage: requestClearPatternImage,
            clearWorkImage: clearWorkImage,
            addCustomTag: addCustomTag,
            startEditOverview: startEditOverview,
            cancelEditOverview: cancelEditOverview,
            removeTag: removeTag,
            closeConfirmDialog: closeConfirmDialog,
            submitConfirmDialog: submitConfirmDialog,
            saveProject: saveProject,
        };
    },
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
});
; /* PartiallyEnd: #4569/main.vue */
