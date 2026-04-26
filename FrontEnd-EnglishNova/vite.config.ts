import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.VITE_GATEWAY_PROXY_TARGET || 'http://localhost:8087'
  const apiProxy = Object.fromEntries(
    [
      '^/auth/(login|register|me|profile|profile/avatar)$',
      '^/upload/images/',
      '^/system/',
      '^/study/',
      '^/search/',
      '^/public-wordbooks',
      '^/imports/(presets|files)$',
      '^/wordbooks',
      '^/quiz/sessions',
    ].map((path) => [path, { target: apiTarget, changeOrigin: true }]),
  )

  return {
    plugins: [
      react(), 
      tailwindcss()],
    server: {
      host: '0.0.0.0',
      port: Number(env.VITE_PORT || env.FRONTEND_PORT || 3000),
      strictPort: true,
      proxy: apiProxy,
    },
  }
})
