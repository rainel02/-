import { onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { beadApi } from '@/api/beads';
import BeadWorkbench from '@/components/BeadWorkbench.vue';
const route = useRoute();
const tab = ref('overview');
const project = ref(null);
const saving = ref(false);
const existingTags = ref([]);
const selectedTags = ref([]);
const newTag = ref('');
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
if (__VLS_ctx.project) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
        ...{ class: "card" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "row-between" },
        ...{ style: {} },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.h2, __VLS_intrinsicElements.h2)({
        ...{ class: "section-title" },
    });
    (__VLS_ctx.form.name || __VLS_ctx.project.name);
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
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "tabs" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.project))
                    return;
                __VLS_ctx.tab = 'overview';
            } },
        ...{ class: "tab" },
        ...{ class: ({ active: __VLS_ctx.tab === 'overview' }) },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.project))
                    return;
                __VLS_ctx.tab = 'work';
            } },
        ...{ class: "tab" },
        ...{ class: ({ active: __VLS_ctx.tab === 'work' }) },
    });
    if (__VLS_ctx.tab === 'overview') {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ style: {} },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            ...{ class: "input" },
        });
        (__VLS_ctx.form.name);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "card" },
            ...{ style: {} },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "row" },
            ...{ style: {} },
        });
        for (const [tag] of __VLS_getVForSourceType((__VLS_ctx.existingTags))) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({
                key: (tag),
                ...{ style: {} },
            });
            __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
                type: "checkbox",
                value: (tag),
            });
            (__VLS_ctx.selectedTags);
            (tag);
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
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            ...{ class: "input" },
            placeholder: "https://...",
        });
        (__VLS_ctx.form.sourceUrl);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ style: {} },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            ...{ onChange: (__VLS_ctx.onPattern) },
            ...{ class: "input" },
            type: "file",
            accept: "image/*",
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "row" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
            ...{ onClick: (__VLS_ctx.clearPatternImage) },
            ...{ class: "btn secondary" },
        });
        if (__VLS_ctx.form.patternImage) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.img)({
                src: (__VLS_ctx.form.patternImage),
                ...{ style: {} },
            });
        }
        else {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
        }
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ style: {} },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            ...{ onChange: (__VLS_ctx.onWork) },
            ...{ class: "input" },
            type: "file",
            accept: "image/*",
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "row" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
            ...{ onClick: (__VLS_ctx.clearWorkImage) },
            ...{ class: "btn secondary" },
        });
        if (__VLS_ctx.form.workImage) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.img)({
                src: (__VLS_ctx.form.workImage),
                ...{ style: {} },
            });
        }
        else {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
        }
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "row" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
            ...{ onClick: (__VLS_ctx.saveProject) },
            ...{ class: "btn success" },
            disabled: (__VLS_ctx.saving),
        });
        (__VLS_ctx.saving ? '保存中...' : '保存详情');
    }
    else {
        /** @type {[typeof BeadWorkbench, ]} */ ;
        // @ts-ignore
        const __VLS_4 = __VLS_asFunctionalComponent(BeadWorkbench, new BeadWorkbench({
            project: (__VLS_ctx.project),
        }));
        const __VLS_5 = __VLS_4({
            project: (__VLS_ctx.project),
        }, ...__VLS_functionalComponentArgsRest(__VLS_4));
    }
}
else {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
        ...{ class: "card" },
    });
}
/** @type {__VLS_StyleScopedClasses['card']} */ ;
/** @type {__VLS_StyleScopedClasses['row-between']} */ ;
/** @type {__VLS_StyleScopedClasses['section-title']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['tabs']} */ ;
/** @type {__VLS_StyleScopedClasses['tab']} */ ;
/** @type {__VLS_StyleScopedClasses['tab']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['card']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['input']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['secondary']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['btn']} */ ;
/** @type {__VLS_StyleScopedClasses['success']} */ ;
/** @type {__VLS_StyleScopedClasses['card']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            BeadWorkbench: BeadWorkbench,
            tab: tab,
            project: project,
            saving: saving,
            existingTags: existingTags,
            selectedTags: selectedTags,
            newTag: newTag,
            form: form,
            onPattern: onPattern,
            onWork: onWork,
            clearPatternImage: clearPatternImage,
            clearWorkImage: clearWorkImage,
            addCustomTag: addCustomTag,
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
