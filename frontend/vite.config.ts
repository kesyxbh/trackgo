import react from '@vitejs/plugin-react'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // The backend has no CORS configuration, so the browser must not call it directly. The page is
  // served from :5175 and every /api/... request is forwarded to Spring Boot by this proxy, which
  // makes the two look like one origin. Override the target with VITE_API_PROXY_TARGET
  // (for example in .env.local).
  const env = loadEnv(mode, process.cwd(), '')
  const proxy = {
    '/api': { target: env.VITE_API_PROXY_TARGET || 'http://localhost:8082', changeOrigin: true },
  }

  return {
    plugins: [react()],
    server: { port: 5175, proxy },
    preview: { port: 5175, proxy },
    test: { include: ['src/**/*.test.ts'] },
  }
})
