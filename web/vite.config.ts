import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// dist собирается прямо в ресурсы мода и коммитится —
// ./gradlew build не требует Node.
export default defineConfig({
  plugins: [react()],
  base: './',
  build: {
    outDir: '../src/main/resources/web',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8776',
      '/ws': { target: 'ws://localhost:8776', ws: true },
    },
  },
})
