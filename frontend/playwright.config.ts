import { defineConfig } from '@playwright/test';

// E2E_BASE_URL targets an already running deployment (e.g. the Docker stack on :3000); otherwise the
// backend and Vite are started here. E2E_BROWSER_CHANNEL=chromium uses Playwright's bundled Chromium (CI).
const externalTarget = process.env.E2E_BASE_URL;
const channel = process.env.E2E_BROWSER_CHANNEL ?? 'msedge';
const mvnw = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';

/**
 * End-to-end tests against the real stack: Vite dev server + Spring Boot (LocalDevApplication with an
 * embedded PostgreSQL and the demo catalog). Both are started if they are not already running.
 * Uses the installed Microsoft Edge (or set E2E_BROWSER_CHANNEL=chrome), so no browser download is needed.
 *
 *   npm run e2e
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  timeout: 120_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: externalTarget ?? 'http://localhost:5173',
    channel: channel === 'chromium' ? undefined : channel,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: externalTarget ? undefined : [
    {
      command: `${mvnw} -q spring-boot:test-run -Dspring-boot.run.main-class=com.buildmybundle.LocalDevApplication`,
      cwd: '../backend',
      url: 'http://localhost:8080/actuator/health',
      reuseExistingServer: true,
      timeout: 240_000,
    },
    {
      command: 'npm run dev -- --port 5173 --strictPort',
      url: 'http://localhost:5173',
      reuseExistingServer: true,
      timeout: 60_000,
    },
  ],
});
