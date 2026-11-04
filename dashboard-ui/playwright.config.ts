import { defineConfig, devices } from '@playwright/test';

/**
 * Browser E2E against the real stack: Spring Boot (dev profile, PostgreSQL) + the Vite dev server that proxies /api.
 *
 * Ports are deliberately unusual so a normal dev stack on 8080/5173 is never touched. The dev profile rebuilds the
 * schema on every start (ddl-auto: create), so E2E_DB_URL must point at a database used for nothing else
 * (default: readybell_e2e) - never at the database of your dev stack.
 *
 *   createdb readybell_e2e            # once; see docs/DEVELOPMENT.md
 *   npm run e2e                       # starts both servers if they are not already up
 */
const BACKEND_PORT = Number(process.env.E2E_BACKEND_PORT ?? 18080);
const UI_PORT = Number(process.env.E2E_UI_PORT ?? 5273);
const CI = !!process.env.CI;

export default defineConfig({
  testDir: './e2e',
  // One shared database and one dev restaurant: run the scenarios one after another
  workers: 1,
  fullyParallel: false,
  retries: CI ? 1 : 0,
  timeout: 45_000,
  expect: { timeout: 10_000 },
  reporter: CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: `http://localhost:${UI_PORT}`,
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: `./gradlew bootRun --args='--server.port=${BACKEND_PORT}'`,
      cwd: '..',
      url: `http://localhost:${BACKEND_PORT}/actuator/health`,
      reuseExistingServer: !CI,
      timeout: 240_000,
      stdout: 'pipe',
      env: {
        SPRING_PROFILES_ACTIVE: 'dev',
        // The dev profile needs no Redis (rate limits and tickets stay in memory), but its health check would
        // report DOWN without one and Playwright would wait for a healthy backend forever
        MANAGEMENT_HEALTH_REDIS_ENABLED: 'false',
        DB_URL: process.env.E2E_DB_URL ?? 'jdbc:postgresql://localhost:3254/readybell_e2e',
        DB_USERNAME: process.env.E2E_DB_USERNAME ?? 'postgres',
        DB_PASSWORD: process.env.E2E_DB_PASSWORD ?? 'qwer1234',
      },
    },
    {
      command: `npx vite --port ${UI_PORT} --strictPort`,
      url: `http://localhost:${UI_PORT}`,
      reuseExistingServer: !CI,
      timeout: 60_000,
      env: { BACKEND_URL: `http://localhost:${BACKEND_PORT}` },
    },
  ],
});
