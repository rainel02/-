<template>
  <section class="card settings-page">
    <h2 class="section-title">设置</h2>

    <div class="card settings-card">
      <h3 class="settings-subtitle">Baidu OCR 配置</h3>
      <div class="settings-grid">
        <label>AK
          <input class="input" v-model="ocr.ak" placeholder="请输入 baidu ocr ak" />
        </label>
        <label>SK
          <input class="input" v-model="ocr.sk" placeholder="请输入 baidu ocr sk" />
        </label>
        <label>AK2（可选）
          <input class="input" v-model="ocr.ak2" placeholder="备用 ak" />
        </label>
        <label>SK2（可选）
          <input class="input" v-model="ocr.sk2" placeholder="备用 sk" />
        </label>
      </div>
      <div class="row">
        <button class="btn success" @click="saveOcr">保存 OCR 配置</button>
      </div>
    </div>

    <div class="card settings-card">
      <h3 class="settings-subtitle">Tag 管理</h3>
      <div class="row tag-row">
        <input class="input" v-model="newTag" placeholder="新增 tag" @keyup.enter="addTag" />
        <button class="btn secondary" @click="addTag">新增</button>
      </div>
      <div class="tag-list">
        <span v-for="tag in tags" :key="tag" class="tag-item">
          {{ tag }}
          <button class="tag-delete" @click="deleteTag(tag)">×</button>
        </span>
      </div>
    </div>

    <div class="card settings-card">
      <h3 class="settings-subtitle">库存导出</h3>
      <button class="btn" @click="exportInventory">导出库存 Excel</button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { beadApi } from '@/api/beads'

const ocr = reactive({
  ak: '',
  sk: '',
  ak2: '',
  sk2: ''
})

const tags = ref<string[]>([])
const newTag = ref('')

async function loadAll() {
  try {
    const [ocrSettings, tagList] = await Promise.all([
      beadApi.getOcrSettings(),
      beadApi.tags()
    ])
    ocr.ak = ocrSettings.ak || ''
    ocr.sk = ocrSettings.sk || ''
    ocr.ak2 = ocrSettings.ak2 || ''
    ocr.sk2 = ocrSettings.sk2 || ''
    tags.value = [...(tagList || [])].sort((a, b) => a.localeCompare(b, 'zh-CN'))
  } catch (error) {
    console.error(error)
    alert('加载设置失败，请稍后重试')
  }
}

async function saveOcr() {
  try {
    const saved = await beadApi.saveOcrSettings({ ...ocr })
    ocr.ak = saved.ak || ''
    ocr.sk = saved.sk || ''
    ocr.ak2 = saved.ak2 || ''
    ocr.sk2 = saved.sk2 || ''
    alert('OCR 配置已保存')
  } catch (error) {
    console.error(error)
    alert('保存 OCR 配置失败')
  }
}

async function addTag() {
  const tag = newTag.value.trim()
  if (!tag) return
  try {
    await beadApi.addTag(tag)
    if (!tags.value.includes(tag)) {
      tags.value.push(tag)
      tags.value.sort((a, b) => a.localeCompare(b, 'zh-CN'))
    }
    newTag.value = ''
  } catch (error) {
    console.error(error)
    alert('新增 tag 失败')
  }
}

async function deleteTag(tag: string) {
  if (!confirm(`确认删除 tag「${tag}」吗？`)) {
    return
  }
  try {
    await beadApi.deleteTag(tag)
    tags.value = tags.value.filter(item => item !== tag)
  } catch (error) {
    console.error(error)
    alert('删除 tag 失败')
  }
}

async function exportInventory() {
  try {
    const apiBase = window.pindouDesktop?.apiBase || '/api'
    const resp = await fetch(`${apiBase}/inventory/export`)
    if (!resp.ok) {
      throw new Error(`导出失败: ${resp.status}`)
    }
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = 'inventory-stock.xlsx'
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
  } catch (error) {
    console.error(error)
    alert('导出库存 Excel 失败')
  }
}

onMounted(loadAll)
</script>

<style scoped>
.settings-page {
  display: grid;
  gap: 12px;
}

.settings-card {
  display: grid;
  gap: 10px;
}

.settings-subtitle {
  margin: 0;
  color: #40513b;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(200px, 1fr));
  gap: 10px;
}

.tag-row {
  gap: 8px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid #c4d8ad;
  background: #f9fbf1;
}

.tag-delete {
  border: none;
  background: transparent;
  cursor: pointer;
  color: #b91c1c;
  font-size: 16px;
  line-height: 1;
}

@media (max-width: 900px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }
}
</style>
