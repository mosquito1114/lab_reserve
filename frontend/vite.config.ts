import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * Vite 配置
 *
 * 开发环境（npm run dev）：
 *   - 端口 6173（可用 VITE_DEV_PORT 覆盖）
 *   - /api 代理到后端 8080，避免跨域
 *
 * 生产环境（npm run build）：
 *   - 产物输出到 dist/
 *   - 由 nginx（docker/nginx/default.conf）托管并把 /api 反代到后端容器
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [vue()],

    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },

    server: {
      port: Number(env.VITE_DEV_PORT || 6173),
      host: true,
      open: false,
      proxy: {
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true
        }
      }
    },

    build: {
      outDir: 'dist',
      assetsDir: 'assets',
      sourcemap: false,
      chunkSizeWarningLimit: 1500,
      rollupOptions: {
        output: {
          // 把体积大的第三方库拆出来，避免单个 chunk 过大
          manualChunks: {
            vue: ['vue', 'vue-router', 'pinia'],
            elementPlus: ['element-plus', '@element-plus/icons-vue'],
          }
        }
      }
    }
  }
})
