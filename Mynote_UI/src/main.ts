import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { tooltip } from './directives/tooltip'

import './styles/tokens.css'
import './styles/base.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
// 全局自定义悬浮提示，替代原生 title，贴合纸面视觉
app.directive('tooltip', tooltip)
// 只用它的消息提示与确认框（ElMessage / ElMessageBox），其余界面全部自写。
// tokens.css 里已把 --el-* 变量对齐到本产品的色板，所以不会出现两套观感。
app.use(ElementPlus)

app.mount('#app')

// 首屏占位由 index.html 里的 #boot 提供，挂载完成后摘掉
document.getElementById('boot')?.remove()
