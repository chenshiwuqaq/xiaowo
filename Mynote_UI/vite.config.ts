import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 说明：这里刻意不挂 vue-devtools 插件。它在 Node 22 + Vite 8 组合下会注入
// @unocss/vite 不兼容的 hook（defer），导致 dev 服务器启动失败；与产品功能无关，先移除。
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    host: '127.0.0.1',
    proxy: {
      // 后端接口与上传的静态资源都走同源代理，前端代码里不必硬编码 8080
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
      '/upload': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 1500,
  },
})
