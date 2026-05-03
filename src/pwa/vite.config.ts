import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      manifest: {
        name: 'Pitwall',
        short_name: 'Pitwall',
        description: 'AI Racing Coach',
        theme_color: '#0d0d12',
        background_color: '#0d0d12',
        display: 'fullscreen',
        orientation: 'portrait',
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,png,woff2,mp3,json}'],
        runtimeCaching: [
          {
            urlPattern: /\/audio\/coaches\/.+\.mp3$/,
            handler: 'CacheFirst',
            options: { cacheName: 'coach-voices', expiration: { maxEntries: 300 } },
          },
          {
            urlPattern: /127\.0\.0\.1:8765\/(session|driver|track|coach|markers)/,
            handler: 'NetworkFirst',
            options: { cacheName: 'bridge', networkTimeoutSeconds: 3 },
          },
        ],
      },
    }),
  ],
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://127.0.0.1:8765', rewrite: p => p.replace(/^\/api/, '') },
    },
  },
})
