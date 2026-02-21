<template>
  <div class="layout">
    <aside class="sidebar">
      <h1>拼豆管理</h1>
      <RouterLink class="nav-link" to="/overview">拼豆总览</RouterLink>
      <RouterLink class="nav-link" to="/new">新建拼豆</RouterLink>
      <RouterLink class="nav-link" to="/inventory">库存总览</RouterLink>
      <RouterLink class="nav-link" to="/settings">设置</RouterLink>
    </aside>
    <main class="main">
      <RouterView />
    </main>
  </div>
</template>

<script setup lang="ts">
</script>
