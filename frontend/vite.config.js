import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { seedDesignPlugin } from '@seed-design/vite-plugin'

export default defineConfig({
  plugins: [react(), seedDesignPlugin({ colorMode: 'light-only' })],
  resolve: {
    tsconfigPaths: true,
  },
  server: {
    proxy: { '/api/v1': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    server: {
      deps: {
        inline: ['@seed-design/react', '@seed-design/css'],
      },
    },
    setupFiles: './src/test/setup.ts',
  },
})
