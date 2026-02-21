import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/overview' },
    { path: '/overview', component: () => import('@/views/OverviewPage.vue') },
    { path: '/new', component: () => import('@/views/NewBeadPage.vue') },
    { path: '/detail/:id', component: () => import('@/views/BeadDetailPage.vue') },
    { path: '/inventory', component: () => import('@/views/InventoryPage.vue') },
    { path: '/settings', component: () => import('@/views/SettingsPage.vue') }
  ]
})

export default router
