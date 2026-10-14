import { chromium } from 'playwright';
import { spawnSync } from 'node:child_process';
const result = spawnSync(process.execPath, ['node_modules/@angular/cli/bin/ng.js', 'test', '--watch=false',
  '--browsers=ChromeHeadless', '--progress=false'], {
  stdio: 'inherit',
  env: { ...process.env, CHROME_BIN: process.env.CHROME_BIN || chromium.executablePath(),
    NG_BUILD_MAX_WORKERS: process.env.NG_BUILD_MAX_WORKERS || '2' }
});
process.exit(result.status ?? 1);
