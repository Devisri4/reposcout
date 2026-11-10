import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the page calls /api on its own address and Vite forwards it to the
// Spring Boot backend on port 8080, so no CORS setup is needed locally or in Codespaces.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Codespaces opens the page on https://<name>-5173.app.github.dev
    allowedHosts: ['.app.github.dev'],
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.js',
  },
});
