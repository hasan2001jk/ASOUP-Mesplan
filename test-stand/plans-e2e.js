/*
 * «Планы» (0170/0304/0305/0306) в настоящем браузере — test-stand/plans-e2e.js
 *
 * Стенд: test-stand/full.sh up. Запуск (Playwright в Docker):
 *   W=$(mktemp -d); cp test-stand/plans-e2e.js $W/e2e.js; cp ~/.cache/mesplan-stand/password $W/
 *   cp docs/mesplan_messages/2018/07/0170 $W/0170; cp docs/mesplan_messages/2018/07/0305 $W/0305
 *   cp docs/mesplan_messages/2018/07/305.0 $W/305.0
 *   docker run --rm --network host -u $(id -u) -e HOME=/tmp -v $W:/w -w /w \
 *     mcr.microsoft.com/playwright:v1.48.0-jammy bash -c "npm i -s playwright@1.48.0; node e2e.js"
 *
 * Проверяет: 0170, набранная руками, совпадает с переданной в июле 2018 байт в
 * байт (и скачанный файл тоже); открытие переданной 0305 и её пересборка;
 * сверка 0305 с 0170 того же месяца; квитанция 8997; «Скачать» гаснет при
 * ошибке. Сервер МЕСПЛАН не нужен — вкладка работает в браузере.
 */
const { chromium } = require('playwright');
const fs = require('fs');
const PASS = fs.readFileSync('/w/password', 'utf8').trim();
const FRONT = 'http://localhost:18081';
const out = [];
const ok = (name, cond, extra) => out.push(`${cond ? '✓' : '✗ FAIL'} ${name}${extra ? ' — ' + extra : ''}`);

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1500, height: 1000 }, acceptDownloads: true });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto(FRONT + '/oper/login');
  await page.fill('#login-username', 'planner');
  await page.fill('#login-password', PASS);
  await page.click('button[type=submit]');
  await page.waitForURL(u => !u.toString().includes('/login'), { timeout: 20000 });
  await page.evaluate(() => Object.keys(localStorage).filter(k => k.startsWith('oper-mesplan-plan')).forEach(k => localStorage.removeItem(k)));

  await page.goto(FRONT + '/oper/mesplan?mesplanWorkOption=4');
  await page.waitForSelector('app-plan-messages');
  ok('вкладка «Планы» открыта', await page.locator('.tab.is-active').innerText().then(t => t.includes('Планы')));

  // --- 0170 руками: июль 2018 ---
  await page.click('.pm-kind:has-text("0170")');
  await page.selectOption('.pm-head select', { label: '08 · август' });
  const rows = [['08', '15', '896'], ['19', '11', '660'], ['23', '16', '1088'], ['30', '2', '136'], ['32', '1', '50'], ['28', '1', '60']];
  for (let i = 0; i < rows.length; i++) {
    if (i > 0) { await page.click('button:has-text("Добавить группу")'); }
    const tr = page.locator('.pm-table tbody tr').nth(i + 1);
    const inputs = tr.locator('input');
    for (let j = 0; j < 3; j++) { await inputs.nth(j).fill(rows[i][j]); }
  }
  const sent0170 = fs.readFileSync('/w/0170', 'latin1');
  const pre = await page.locator('.pm-pre').first().innerText();
  ok('0170 на экране = переданная 2018/07', pre.replace(/\r?\n/g, '\r\n') === sent0170, JSON.stringify(pre));
  ok('строка «99 всего» считает сама', (await page.locator('.pm-total').innerText()).includes('46'));
  ok('название группы 19 видно', (await page.locator('.pm-hint').allInnerTexts()).includes('Строительные грузы'));
  const [dl] = await Promise.all([page.waitForEvent('download'), page.click('button:has-text("Скачать")')]);
  const file = await dl.path();
  ok('скачан файл «0170»', dl.suggestedFilename() === '0170', dl.suggestedFilename());
  ok('скачанный файл = переданный байт в байт (CRLF)', fs.readFileSync(file, 'latin1') === sent0170);
  await page.screenshot({ path: '/w/0170.png', fullPage: true });

  // --- ошибка гасит «Скачать» ---
  await page.locator('.pm-table tbody tr').nth(1).locator('input').nth(0).fill('44');
  ok('код 44 — ошибка и «Скачать» выключена',
    (await page.locator('.pm-msgs--err').innerText()).includes('01–43') && await page.locator('button:has-text("Скачать")').isDisabled());
  await page.locator('.pm-table tbody tr').nth(1).locator('input').nth(0).fill('08');

  // --- открыть переданную 0305 ---
  await page.setInputFiles('.pm-file input', '/w/0305');
  await page.waitForTimeout(500);
  ok('файл переключил на 0305', await page.locator('.pm-kind.is-active').innerText().then(t => t.includes('0305')));
  const pre305 = await page.locator('.pm-pre').first().innerText();
  ok('0305 собрана заново = файл', pre305.replace(/\r?\n/g, '\r\n') === fs.readFileSync('/w/0305', 'latin1'), JSON.stringify(pre305));
  ok('0305 и 0170 того же месяца сошлись (46 вагонов)', await page.locator('.pm-ok').isVisible());
  ok('графа «Всего» в строке 73 = 17', (await page.locator('.pm-table tbody tr').nth(2).innerText()).includes('17'));
  await page.screenshot({ path: '/w/0305.png', fullPage: true });

  // --- расхождение: правим 0305 ---
  await page.locator('.pm-table tbody tr').nth(1).locator('input').nth(1).fill('2');
  ok('после правки — расхождение с 0170', (await page.locator('.pm-msgs--warn').innerText()).includes('0170'));

  // --- квитанция ---
  await page.setInputFiles('.pm-file input', '/w/305.0');
  await page.waitForTimeout(500);
  ok('квитанция 8997: принято', (await page.locator('.pm-receipt').innerText()).includes('принято без ошибок'));
  ok('квитанция не сбила форму 0305', await page.locator('.pm-kind.is-active').innerText().then(t => t.includes('0305')));

  // --- черновики живут после перезагрузки ---
  await page.reload();
  await page.waitForSelector('app-plan-messages');
  ok('после перезагрузки — та же 0305', await page.locator('.pm-kind.is-active').innerText().then(t => t.includes('0305')));
  await page.click('.pm-kind:has-text("0170")');
  ok('и та же 0170', (await page.locator('.pm-pre').first().innerText()).includes('99 00460 0028900'));

  await page.click('.pm-kind:has-text("0306")');
  await page.screenshot({ path: '/w/0306.png', fullPage: true });
  ok('ошибок JS нет', errors.length === 0, errors.join(' | '));

  await browser.close();
  console.log(out.join('\n'));
  const failed = out.filter(l => l.startsWith('✗')).length;
  console.log(`\n${out.length - failed} passed, ${failed} failed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
