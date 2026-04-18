import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { createApiProxy } from './src/utools/proxy.mjs'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [react(), tailwindcss()],
    server: {
      host: '0.0.0.0',
      port: Number(env.VITE_PORT || env.FRONTEND_PORT || 3000),
      strictPort: true,
      proxy: createApiProxy(env),
    },
  }
})
