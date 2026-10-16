/*
 * МЕСПЛАН в настоящем браузере — test-stand/full-e2e.js
 *
 * Стенд: test-stand/full.sh up. Запуск (Playwright в Docker):
 *   W=$(mktemp -d); cp test-stand/full-e2e.js $W/e2e.js; cp ~/.cache/mesplan-stand/password $W/
 *   cp <файл заявки ok.txt> <файл с ошибкой bad.txt> $W/
 *   docker run --rm --network host -u $(id -u) -e HOME=/tmp -v $W:/w -w /w \
 *     mcr.microsoft.com/playwright:v1.48.0-jammy bash -c "npm i -s playwright@1.48.0; node e2e.js"
 *
 * Проверяет: загрузку файла (зелёный итог сервера), файл с ошибкой (поле и
 * значение в тосте, без выхода из системы), открытие загруженной заявки и ответ
 * администрации, новую заявку (подсветка пустых обязательных, затем сохранение),
 * «Просмотр» с удалением, отказ пользователю без роли МЕСПЛАН.
 */
const { chromium } = require('playwright');
const fs = require('fs');
const PASS = fs.readFileSync('/w/password', 'utf8').trim();
const FRONT = 'http://localhost:18081';
const MP = 'http://localhost:18687';
const out = [];
const ok = (name, cond, extra) => out.push(`${cond ? '✓' : '✗ FAIL'} ${name}${extra ? ' — ' + extra : ''}`);
const snack = async page => (await page.locator('.mat-mdc-snack-bar-label, simple-snack-bar, .mat-mdc-snack-bar-container').allInnerTexts().catch(() => [])).join(' | ');
async function waitSnack(page, re, ms = 10000) {
  let t = '';
  for (let i = 0; i < ms / 250 && !re.test(t); i++) { await page.waitForTimeout(250); t = await snack(page); }
  return t;
}

async function login(browser, user) {
  const ctx = await browser.newContext({ viewport: { width: 1500, height: 1000 } });
  const page = await ctx.newPage();
  const statuses = [];
  page.on('response', r => { if (r.url().startsWith(MP)) statuses.push(r.status()); });
  await page.goto(FRONT + '/oper/login');
  await page.fill('#login-username', user);
  await page.fill('#login-password', PASS);
  await page.click('button[type=submit]');
  await page.waitForURL(u => !u.toString().includes('/login'), { timeout: 20000 });
  return { ctx, page, statuses };
}

(async () => {
  const browser = await chromium.launch();
  const P = await login(browser, 'planner');
  const page = P.page;

  // 1. Загрузка правильного файла
  await page.goto(FRONT + '/oper/mesplan?mesplanWorkOption=1');
  await page.setInputFiles('input[type=file]', '/w/ok.txt');
  await page.click('button:has-text("Загрузить")');
  let t = await waitSnack(page, /загружена/);
  ok('загрузка: зелёный итог сервера', /Заявка № 0000012345 \(строка 3\) загружена, администраций: 2/.test(t), t);
  await page.screenshot({ path: '/w/1-upload.png' });

  // 2. Открыть загруженную заявку и ответить за администрацию 2
  await page.click('button:has-text("Открыть заявку")');
  await page.waitForSelector('.mp-mode--edit');
  const head = await page.locator('.mp-mode').innerText();
  ok('«Ввод» открыл загруженную заявку', /Заявка № 0000012345/.test(head) && /запись \d+/.test(head), head.replace(/\s+/g, ' '));
  const rows = page.locator('.md-row');
  ok('две администрации в форме', (await rows.count()) === 2);
  ok('дата погрузки в поле', (await page.inputValue('input[name=datepogr]')) === '2026-11-15');
  ok('отправитель — кириллица цела', (await page.inputValue('input[name=otprname]')) === 'ГУП ТАДЖИКСКИЙ АЛЮМИНИЙ');
  const r2 = rows.nth(1).locator('input');
  await r2.nth(6).fill('2');
  await r2.nth(7).fill('Отказ: стык закрыт на ремонт');
  await page.click('button:has-text("Сохранить изменения")');
  t = await waitSnack(page, /Изменения сохранены/);
  ok('правка заявки сохранена', /Изменения сохранены \(запись \d+\)/.test(t), t);
  await page.screenshot({ path: '/w/2-edit.png', fullPage: true });

  // 3. Файл с ошибкой формата
  await page.click('[data-tab="1"]');
  await page.setInputFiles('input[type=file]', '/w/bad.txt');
  await page.click('button:has-text("Загрузить")');
  t = await waitSnack(page, /Неверный формат/);
  ok('ошибка формата названа полем и значением', /поле 4 «Дата погрузки»: ожидалась дата ДДММГГГГ, получено «2026-11-15»/.test(t), t);
  ok('после 400 оператор не выкинут', !page.url().includes('/login'));

  // 4. Новая заявка: сначала пустая — подсветка, затем заполненная
  await page.click('[data-tab="2"]');
  await page.waitForSelector('.mp-mode:not(.mp-mode--edit)');
  ok('вкладка «Ввод» открыла пустую форму', /Новая заявка/.test(await page.locator('.mp-mode').innerText()));
  await page.waitForTimeout(1500); // справочники формы (3 запроса) загрузились
  const sentBefore = P.statuses.length;
  await page.click('button:has-text("Сохранить заявку")');
  await page.waitForSelector('.mp-problems');
  const problems = await page.locator('.mp-problems li').allInnerTexts();
  ok('пустая форма: перечислены причины', problems.includes('не заполнено «№ заявки»') && problems.includes('нет ни одной ж/д администрации'), problems.length + ' причин');
  ok('пустые обязательные подсвечены', (await page.locator('.field__input--invalid').count()) >= 15);
  ok('при этом на сервер ничего не ушло', P.statuses.length === sentBefore, `${P.statuses.length - sentBefore} запросов`);
  const fill = async (n, v) => page.fill(`input[name=${n}]`, v);
  await fill('ftype', '01'); await page.selectOption('select[name=strview]', '1');
  await fill('datepogr', '2026-11-20'); await page.selectOption('select[name=transid]', { label: '1' });
  await fill('orderno', '555'); await fill('strorderno', '1'); await fill('codengr', '112');
  await fill('otprname', 'ОТПРАВИТЕЛЬ ТЕСТ'); await fill('poluchname', 'ПОЛУЧАТЕЛЬ ТЕСТ');
  await fill('stotpr', '745807'); await fill('stnazn', '938608'); await fill('etsng', '41107');
  await fill('gng', '76011000'); await fill('strnazn', '643');
  await page.selectOption('select[name=prinadl]', { label: 'Инвентарный' });
  await page.selectOption('select[name=rodvag]', { index: 1 });
  await fill('netto', '68'); await fill('vags', '1');
  await page.selectOption('select[name=rodotpr]', { label: 'Повагонная' });
  await page.click('button:has-text("Добавить")');
  const nr = page.locator('.md-row').nth(0).locator('input');
  await nr.nth(0).fill('66'); await nr.nth(1).fill('746000'); await nr.nth(2).fill('ЭКСПЕДИТОР ТЕСТ'); await nr.nth(3).fill('99');
  await page.click('button:has-text("Сохранить заявку")');
  t = await waitSnack(page, /Заявка сохранена/);
  ok('новая заявка сохранена', /Заявка сохранена \(запись \d+\)/.test(t), t);
  ok('после сохранения форма — правка этой записи', /orderId=\d+/.test(page.url()) && /Заявка № 555/.test(await page.locator('.mp-mode').innerText()));
  ok('кол-во администраций посчитано', (await page.locator('.field--readonly input').inputValue()) === '1');
  ok('род вагона сохранён и показан', (await page.locator('select[name=rodvag]').inputValue()) !== '');
  await page.screenshot({ path: '/w/3-new.png', fullPage: true });

  // 5. Просмотр: обе заявки, отказ помечен, удаление в два шага
  await page.click('[data-tab="3"]');
  await page.waitForSelector('.ve-table tbody tr');
  let n = await page.locator('.ve-table tbody tr').count();
  ok('«Просмотр»: две заявки за неделю', n === 2, String(n));
  ok('отказ помечен в строке', (await page.locator('.ve-table tr.is-deny').count()) === 1);
  await page.screenshot({ path: '/w/4-list.png', fullPage: true });
  await page.locator('.ve-table tbody tr').first().locator('button[aria-label="Удалить заявку"]').click();
  await page.click('.ve-confirm__yes');
  t = await waitSnack(page, /удалена/);
  ok('удаление подтверждено и выполнено', /Заявка № 555 \(запись \d+\) удалена/.test(t), t);
  n = await page.locator('.ve-table tbody tr').count();
  ok('строка ушла из списка', n === 1, String(n));

  // 6. Оператор без роли МЕСПЛАН
  const R = await login(browser, 'Regar');
  await R.page.goto(FRONT + '/oper/mesplan?mesplanWorkOption=3');
  await R.page.waitForTimeout(2000);
  ok('Regar (ROLE_D) не пускается на страницу', !R.page.url().includes('/oper/mesplan'), R.page.url());
  const st = await R.page.evaluate(async mp => {
    const r = await fetch(mp + '/api/order/list?from=2026-10-01&to=2026-10-31',
      { headers: { Authorization: 'Bearer ' + localStorage.getItem('auth-token') } });
    return r.status;
  }, MP);
  ok('и сервер отвечает ему 403', st === 403, String(st));

  await browser.close();
  console.log(out.join('\n'));
  console.log(out.filter(x => x.startsWith('✓')).length + '/' + out.length);
})().catch(e => { console.log(out.join('\n')); console.error(e); process.exit(1); });
