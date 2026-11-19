/**
 * CURRENCY · Which money the workbook is kept in.
 *
 * The workbook was first built for a salary in Qatari riyals sent home in
 * Indian rupees, so "QAR" and "INR" were written into headers, chart titles
 * and a few formulas. Two settings now decide them instead:
 *
 *   Main currency — what you earn and spend in (every amount you type).
 *   Home currency — what you send money home in (the Exchange Rate column).
 *
 * Changing either relabels the workbook in place — no rebuild, no data lost.
 * Amounts already typed are NOT converted: switch at the start of a period,
 * or when the money really is in that currency.
 */

const CURRENCY_DEFAULTS = { main: 'QAR', home: 'INR' };
const CURRENCY_LABELS_PROPERTY = 'CURRENCY_LABELS';
const CURRENCY_FORMULAS_PROPERTY = 'CURRENCY_FORMULAS_DYNAMIC';
/** Settings rows for the currency block (below the footnote, clear of the derived section). */
const CURRENCY_SECTION_ROW = 26;

/** The two codes as the Settings sheet has them right now. */
function currencyCodes_(ss) {
  const sheet = (ss || SpreadsheetApp.getActiveSpreadsheet()).getSheetByName(SHEETS.SETTINGS);
  const read = key => String(sheet.getRange(CFG[key]).getValue() || '').trim().toUpperCase();
  return {
    main: read('MAIN_CURRENCY') || CURRENCY_DEFAULTS.main,
    home: read('HOME_CURRENCY') || CURRENCY_DEFAULTS.home
  };
}

/**
 * Adds the Currency block to Settings when it isn't there yet — on sheets
 * built before currencies were configurable — and makes the formulas that
 * spell out "QAR"/"INR" read the setting instead. Cheap when already done.
 */
function ensureCurrencySettings_(ss) {
  ss = ss || SpreadsheetApp.getActiveSpreadsheet();
  const sheet = ss.getSheetByName(SHEETS.SETTINGS);
  if (!sheet) return;
  const r = CURRENCY_SECTION_ROW;
  if (sheet.getRange(r + 2, 1).getValue() !== 'Main Currency') {
    sectionLabel(sheet, r, 1, 3, 'CURRENCY');
    headerRow(sheet, r + 1, 1, ['Parameter', 'Value', 'What it drives']);
    sheet.getRange(r + 2, 1, 2, 3).setValues([
      ['Main Currency', CURRENCY_DEFAULTS.main, 'What you earn and spend in — every amount you type'],
      ['Home Currency', CURRENCY_DEFAULTS.home, 'What money sent home is shown in (see Exchange Rate)']
    ]);
    const values = sheet.getRange(r + 2, 2, 2, 1);
    markInput(values);
    values.setFontWeight('bold').setHorizontalAlignment('center');
    values.setDataValidation(SpreadsheetApp.newDataValidation()
      .requireFormulaSatisfied('=REGEXMATCH(UPPER(B' + (r + 2) + '),"^[A-Z]{3}$")')
      .setHelpText('A three-letter currency code, e.g. QAR, AED, SAR, INR, PKR, PHP.')
      .setAllowInvalid(false)
      .build());
    sheet.getRange(r + 2, 1, 2, 1).setFontWeight('bold');
    sheet.getRange(r + 2, 3, 2, 1).setFontSize(9).setFontColor(THEME.MUTED);
    styleBlock(sheet, r + 1, 1, 3, 3);
    footnote(sheet, r + 4, 1, 9,
      'Changing a currency relabels every sheet. Amounts already entered are not converted, so ' +
      'switch at the start of a period. Update the Exchange Rate (1 main = ? home) to match.');
  }
  makeFormulasCurrencyAware_(ss);
}

/**
 * One-time: formulas that build text such as "… 20.00 QAR" get the literal
 * code replaced by a reference to the setting, so they follow it from then on.
 */
function makeFormulasCurrencyAware_(ss) {
  const props = PropertiesService.getDocumentProperties();
  if (props.getProperty(CURRENCY_FORMULAS_PROPERTY) === '1') return;
  [SHEETS.DASHBOARD, SHEETS.FINANCE, SHEETS.SALARY, SHEETS.WEEKLY, SHEETS.MONTHLY].forEach(name => {
    const sheet = ss.getSheetByName(name);
    if (!sheet || sheet.getLastRow() === 0) return;
    const range = sheet.getRange(1, 1, sheet.getLastRow(), sheet.getLastColumn());
    const formulas = range.getFormulas();
    formulas.forEach((row, i) => row.forEach((f, j) => {
      if (!f || !/"[^"]*\b(QAR|INR)\b[^"]*"/.test(f)) return;
      sheet.getRange(i + 1, j + 1).setFormula(currencyAwareFormula_(f));
    }));
  });
  props.setProperty(CURRENCY_FORMULAS_PROPERTY, '1');
}

/** `"Rate: 1 QAR = "` → `"Rate: 1 "&Settings!B28&" = "` — only inside string literals. */
function currencyAwareFormula_(formula) {
  return formula.replace(/"([^"]*)"/g, (literal, inner) => {
    if (!/\b(QAR|INR)\b/.test(inner)) return literal;
    return inner.split(/\b(QAR|INR)\b/)
      .map((part, i) => i % 2 ? cfg(part === 'QAR' ? 'MAIN_CURRENCY' : 'HOME_CURRENCY') : '"' + part + '"')
      .filter(part => part !== '""')
      .join('&');
  });
}

/**
 * Rewrites the currency codes in headers, labels and chart titles to match
 * Settings. Remembers what the workbook currently says, so running it again
 * with nothing changed does nothing. Menu: Life & Finance ▸ Apply currency.
 */
function applyCurrencyLabels() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureCurrencySettings_(ss);
  const props = PropertiesService.getDocumentProperties();
  const from = JSON.parse(props.getProperty(CURRENCY_LABELS_PROPERTY) || JSON.stringify(CURRENCY_DEFAULTS));
  const to = currencyCodes_(ss);
  if (from.main === to.main && from.home === to.home) return 'Currency labels already match.';

  relabelSheets_(ss, Object.keys(SHEETS).map(key => SHEETS[key]), from, to);

  props.setProperty(CURRENCY_LABELS_PROPERTY, JSON.stringify(to));
  return 'Workbook now in ' + to.main + ' (home: ' + to.home + ').';
}

/** Relabels the named sheets' text cells and chart titles from one pair of codes to another. */
function relabelSheets_(ss, names, from, to) {
  if (from.main === to.main && from.home === to.home) return;
  names.forEach(name => {
    const sheet = ss.getSheetByName(name);
    if (!sheet || sheet.getLastRow() === 0) return;
    const range = sheet.getRange(1, 1, sheet.getLastRow(), sheet.getLastColumn());
    const values = range.getValues();
    const formulas = range.getFormulas();
    values.forEach((row, i) => row.forEach((v, j) => {
      if (typeof v !== 'string' || !v || formulas[i][j]) return;
      // Never touch what people typed: the ledger's input rows, daily notes, and Settings values.
      if (name === SHEETS.DAILY) return;
      if (name === SHEETS.FINANCE && i + 1 >= FT.FIRST_ROW && i + 1 < FT.FIRST_ROW + FT.MAX_ROWS && j + 1 <= FT.NOTES) return;
      if (name === SHEETS.SETTINGS && j + 1 === 2) return;
      if (name === SHEETS.RECURRING && i + 1 >= RECURRING.FIRST_ROW) return;
      if ([SHEETS.FOOD_LOG, SHEETS.MEAL_PLAN, SHEETS.MY_FOODS, SHEETS.PEOPLE].indexOf(name) !== -1 && i + 1 >= 4) return;
      const next = swapCurrencyText_(v, from, to);
      if (next !== v) sheet.getRange(i + 1, j + 1).setValue(next);
    }));
    sheet.getCharts().forEach(chart => {
      const title = chart.getOptions().get('title');
      if (typeof title !== 'string') return;
      const next = swapCurrencyText_(title, from, to);
      if (next !== title) sheet.updateChart(chart.modify().setOption('title', next).build());
    });
  });
}

/**
 * Swaps codes only where they are labels: a cell that is just the code,
 * codes in parentheses ("Spend (QAR)", "1 QAR = INR"), and a few fixed phrases.
 * A placeholder keeps a main/home swap (QAR⇄INR) from colliding.
 */
function swapCurrencyText_(text, from, to) {
  if (text === from.main) return to.main;
  if (text === from.home) return to.home;
  const swap = s => s
    .replace(new RegExp('\\b' + from.main + '\\b', 'g'), '\u0001')
    .replace(new RegExp('\\b' + from.home + '\\b', 'g'), '\u0002')
    .replace(/\u0001/g, to.main)
    .replace(/\u0002/g, to.home);
  let out = text.replace(/\(([^()]*)\)/g, (m, inner) => '(' + swap(inner) + ')');
  out = out.replace(new RegExp('^' + from.main + ' per '), to.main + ' per ');
  out = out.replace(new RegExp('\\b' + from.main + '-to-' + from.home + '\\b'), to.main + '-to-' + to.home);
  out = out.replace(/Remittance to\s*India/, 'Sent\nHome');
  return out;
}

/** API: set both codes (and optionally the exchange rate), then relabel. */
function saveCurrency_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureCurrencySettings_(ss);
  const code = v => {
    const c = String(v || '').trim().toUpperCase();
    if (!/^[A-Z]{3}$/.test(c)) throw new Error('Currency must be a three-letter code, e.g. AED.');
    return c;
  };
  const sheet = ss.getSheetByName(SHEETS.SETTINGS);
  if (p.main !== undefined) sheet.getRange(CFG.MAIN_CURRENCY).setValue(code(p.main));
  if (p.home !== undefined) sheet.getRange(CFG.HOME_CURRENCY).setValue(code(p.home));
  const codes = currencyCodes_(ss);
  // One currency only (e.g. everything in INR): nothing to convert.
  const rate = codes.main === codes.home ? 1 : (p.rate !== undefined && p.rate !== null && p.rate !== '' ? num(p.rate) : null);
  if (rate !== null) {
    if (!(rate > 0)) throw new Error('Enter an exchange rate above zero.');
    sheet.getRange(CFG.RATE).setValue(rate);
    setRateFor_(codes);
  }
  SpreadsheetApp.flush();
  applyCurrencyLabels();
}

/** Simple trigger: typing a new code on Settings relabels the workbook. */
function onEdit(e) {
  if (!e || !e.range) return;
  const sheet = e.range.getSheet();
  if (sheet.getName() !== SHEETS.SETTINGS || e.range.getColumn() !== 2) return;
  const row = e.range.getRow();
  // Typing a new exchange rate makes it the rate for the current pair.
  if ('B' + row === CFG.RATE) { setRateFor_(currencyCodes_(e.source)); return; }
  if (row !== CURRENCY_SECTION_ROW + 2 && row !== CURRENCY_SECTION_ROW + 3) return;
  const value = String(e.range.getValue() || '').trim().toUpperCase();
  if (value && value !== e.range.getValue()) e.range.setValue(value);
  applyCurrencyLabels();
}

// ---- Which pair the exchange rate belongs to --------------------------------
// The Exchange Rate is "1 main = x home". When the currencies change but the
// rate isn't updated, it's stale — the app then uses today's rate instead and
// asks you to update it. RATE_FOR remembers the pair the rate was set for.

const RATE_FOR_PROPERTY = 'RATE_FOR';

function rateFor_() {
  return PropertiesService.getDocumentProperties().getProperty(RATE_FOR_PROPERTY) ||
    (CURRENCY_DEFAULTS.main + '/' + CURRENCY_DEFAULTS.home);
}

function setRateFor_(codes) {
  PropertiesService.getDocumentProperties().setProperty(RATE_FOR_PROPERTY, codes.main + '/' + codes.home);
}
