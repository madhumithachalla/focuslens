// Serves docs/ on a local port and captures phone-size screenshots of each route.
// Usage: node capture.js [out-dir] [route ...]
const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');
const os = require('os');
const { chromium } = require('playwright');

const repo = path.resolve(__dirname, '..', '..', '..', '..');
const docs = path.join(repo, 'docs');
const outDir = process.argv[2] || path.join(process.env.SCRATCH || '/tmp', 'focuslens-screens');
const routes = process.argv.slice(3).length ? process.argv.slice(3) : ['index.html'];
const port = 8765 + Math.floor(Math.random() * 500);

(async () => {
  fs.mkdirSync(outDir, { recursive: true });
  const server = spawn('python3', ['-m', 'http.server', String(port), '--bind', '127.0.0.1'], { cwd: docs, stdio: 'ignore' });
  await new Promise((r) => setTimeout(r, 800));
  const browser = await chromium.launch({ args: ['--no-sandbox'] });
  try {
    const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
    const page = await ctx.newPage();
    const errors = [];
    page.on('pageerror', (e) => errors.push(String(e)));
    for (const route of routes) {
      const name = route.replace(/[^a-z0-9]+/gi, '_').replace(/^_|_$/g, '') || 'page';
      await page.goto(`http://127.0.0.1:${port}/${route}`, { waitUntil: 'networkidle', timeout: 30000 }).catch(() => {});
      await page.waitForTimeout(800);
      // Clear the first-run "Before you start" notice so the real screen is captured.
      const cont = page.getByRole('button', { name: 'Continue' });
      if (await cont.count()) {
        const box = page.getByRole('checkbox').first();
        if (await box.count()) await box.check().catch(() => {});
        await cont.first().click().catch(() => {});
        await page.waitForTimeout(500);
      }
      const file = path.join(outDir, `${name}.png`);
      await page.screenshot({ path: file });
      await page.screenshot({ path: path.join(outDir, `${name}_full.png`), fullPage: true });
      console.log('saved', file);
    }
    if (errors.length) console.log('page errors:', errors.slice(0, 5).join(' | '));
  } finally {
    await browser.close();
    server.kill();
  }
})().catch((e) => { console.error(e); process.exit(1); });
