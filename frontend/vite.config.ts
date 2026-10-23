import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// Where the Spring Boot API runs. Override with API_TARGET, e.g. to point a second dev
// server at a backend on another port: API_TARGET=http://localhost:8081 npm run dev
const apiTarget = process.env.API_TARGET ?? 'http://localhost:8080'
const backend = {
  '/api': apiTarget,
  '/actuator': apiTarget,
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    // Makes the app installable on a phone's home screen (web app manifest + service worker)
    VitePWA({
      registerType: 'autoUpdate', // a new version takes over on the next app open
      includeAssets: ['icons/apple-touch-icon.png', 'icons/favicon-32.png'],
      manifest: {
        name: 'ServiceLog',
        short_name: 'ServiceLog',
        description: 'HVAC service history and tune-up reminders for one-person shops.',
        start_url: '/',
        scope: '/',
        display: 'standalone', // full screen, no browser bar
        orientation: 'portrait',
        background_color: '#f4f7f7',
        theme_color: '#f4f7f7',
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: '/icons/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // Cache only the app's own files. API responses (customer data) are never cached;
        // working offline is a separate story (S21).
        // Only the Latin font file: the other alphabets load on demand if ever needed
        globPatterns: ['**/*.{js,css,html,png,webp,svg,ico,webmanifest}', 'assets/atkinson-hyperlegible-next-latin-wght-normal-*.woff2', 'assets/atkinson-hyperlegible-mono-latin-wght-normal-*.woff2'],
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api\//, /^\/actuator\//],
        runtimeCaching: [],
      },
    }),
  ],
  server: {
    // Forward API calls to Spring Boot during development
    proxy: backend,
  },
  preview: {
    // Same for `npm run preview`, which serves the production build (where the service worker runs)
    proxy: backend,
  },
})
