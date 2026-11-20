import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/api/http'
import { useUserStore } from '@/stores/user'

/**
 * 路由表。
 *
 * 视图全部懒加载：登录页不该把编辑器和 mermaid 的体积一起带进首屏。
 */
const routes = [
  { path: '/', redirect: '/home' },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '回到小窝' },
  },
  {
    path: '/home',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
    meta: { title: '小窝看板' },
  },
  {
    path: '/notes',
    name: 'notes',
    component: () => import('@/views/NotesView.vue'),
    meta: { title: '书写页' },
  },
  {
    path: '/market',
    name: 'market',
    component: () => import('@/views/MarketView.vue'),
    meta: { title: '插件市场' },
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('@/views/SettingsView.vue'),
    meta: { title: '个人设置' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'notfound',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { public: true, title: '走错了房间' },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

/**
 * 登录守卫。
 *
 * 令牌有效性不由前端判断——过期令牌照样能通过这里的检查，
 * 真正的 401 由 http 拦截器统一跳转，这里只拦"完全没登录"的情况，
 * 避免两种跳转逻辑互相打断。
 */
router.beforeEach((to) => {
  if (to.meta.public) {
    // 已登录还去登录页，直接送回看板
    if (getToken() && to.name === 'login') return { path: '/home' }
    return true
  }
  if (!getToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  const user = useUserStore()
  if (!user.profile) {
    // 刷新页面后 store 是空的，补一次 profile 才能拿到昵称与主题设置
    void user.fetchProfile()
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 小窝笔记` : '小窝笔记'
})

export default router
