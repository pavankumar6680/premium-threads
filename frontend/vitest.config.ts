import { configDefaults, defineConfig, mergeConfig } from 'vitest/config';
import viteConfig from './vite.config.ts';

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      css: false,
      restoreMocks: true,
      // user-event journeys through lazily loaded pages are slow on busy CI runners and laptops
      testTimeout: 20_000,
      exclude: [...configDefaults.exclude, 'e2e/**'],
    },
  }),
);
