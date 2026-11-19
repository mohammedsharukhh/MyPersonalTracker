/**
 * PERIOD · Moving the workbook to new dates without losing anything.
 *
 * Two entry points:
 *   rebuildTrackerRows()  — menu, after typing new dates on Settings.
 *   startNextPeriod_()    — the app's "Start next period": archives the old
 *                            period as values, sets the new dates, rebuilds.
 *
 * Both keep every logged day (matched by date), every ledger entry, and every
 * month's salary/other income/remittance (matched by month). The earlier
 * rebuild rebuilt the Finance Tracker from scratch, which erased the ledger,
 * and left Salary & Savings inputs in place while the month column moved.
 */

/** Menu: Life & Finance ▸ Rebuild tracker rows (after changing dates). */
function rebuildTrackerRows() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  rebuildPeriod_(ss, null);
  notify('Rebuilt', 'Tracker rows rebuilt. Logged days, expenses and salary entries were matched by date and kept.');
}

/**
 * API: { start: 'yyyy-MM-dd', end: 'yyyy-MM-dd', archive: true }.
 * With archive (the default) the old period is copied to "Archive …" tabs as
 * plain values, and ledger entries before the new start are moved out of the
 * live ledger (they stay in the archive).
 */
function startNextPeriod_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const settings = ss.getSheetByName(SHEETS.SETTINGS);
  const start = parseYmd_(p.start);
  const end = parseYmd_(p.end);
  const days = Math.round((end - start) / 86400000) + 1;
  if (days < 1) throw new Error('The end date must be on or after the start date.');
  if (days > 370) throw new Error('A period can be at most about a year long.');

  const archive = p.archive !== false;
  if (archive) {
    archivePeriod_(ss, settings.getRange(CFG.START_DATE).getValue(), settings.getRange(CFG.END_DATE).getValue());
  }
  settings.getRange(CFG.START_DATE).setValue(start);
  settings.getRange(CFG.END_DATE).setValue(end);
  SpreadsheetApp.flush();
  rebuildPeriod_(ss, archive ? start : null);
}

/**
 * Rebuilds the date-driven sheets for the dates now on Settings, carrying the
 * typed inputs across. `dropBefore` (a Date) leaves out older ledger entries.
 */
function rebuildPeriod_(ss, dropBefore) {
  carryWeight_(ss);
  // The Daily and Salary rows still hold what was typed for the dates they were
  // built with — their date cells follow Settings by formula, so once the start
  // date changes they already show the new dates. Capture by the old ones.
  const rowsStart = rowsStart_(ss);
  const days = captureDays_(ss, rowsStart);
  const ledger = captureLedger_(ss).filter(e => !dropBefore || !(e.date instanceof Date) || e.date >= dropBefore);
  const salary = captureSalary_(ss, rowsStart);

  const dayCount = getDayCount(ss);
  buildDailyTracker(ss, dayCount);
  buildFinanceTracker(ss, dayCount);
  buildWeeklySummary(ss, dayCount);
  buildMonthlySummary(ss, dayCount);
  SpreadsheetApp.flush();

  restoreDays_(ss, days, dayCount);
  restoreLedger_(ss, ledger);
  restoreSalary_(ss, salary);
  buildDashboard(ss, dayCount);
  SpreadsheetApp.flush();
  applyHabitLabels_(ss);
  rememberRowsStart_(ss);

  // The rebuilt sheets come back labelled QAR/INR with literal codes in their
  // text formulas; bring just those sheets in line with the Currency setting.
  const props = PropertiesService.getDocumentProperties();
  props.deleteProperty(CURRENCY_FORMULAS_PROPERTY);
  ensureCurrencySettings_(ss);
  relabelSheets_(ss, [SHEETS.DAILY, SHEETS.FINANCE, SHEETS.WEEKLY, SHEETS.MONTHLY, SHEETS.DASHBOARD],
    CURRENCY_DEFAULTS, currencyCodes_(ss));
}

// ---- Capture / restore -----------------------------------------------------

// ---- Which dates the rows were built for ------------------------------------

const ROWS_START_PROPERTY = 'ROWS_START';

/** The start date ("yyyy-MM-dd") the Daily and Salary rows were last built for. */
function rowsStart_(ss) {
  return PropertiesService.getDocumentProperties().getProperty(ROWS_START_PROPERTY) || settingsStart_(ss);
}

function rememberRowsStart_(ss) {
  const start = settingsStart_(ss);
  if (start) PropertiesService.getDocumentProperties().setProperty(ROWS_START_PROPERTY, start);
}

function settingsStart_(ss) {
  const v = ss.getSheetByName(SHEETS.SETTINGS).getRange(CFG.START_DATE).getValue();
  return v instanceof Date ? ymd_(v) : null;
}

/** "yyyy-MM-dd" plus n days (worked at noon, so a daylight-saving change can't slip a day). */
function addDays_(ymd, n) {
  return ymd_(new Date(parseYmd_(ymd).getTime() + 12 * 3600000 + n * 86400000));
}

/**
 * The start date was changed straight on the Settings tab: the rows still hold
 * what was typed for the old dates, so rebuild — each day and each month's
 * salary moves to its real date. Runs when the app next reads the sheet.
 */
function realignIfStartMoved_(ss) {
  const props = PropertiesService.getDocumentProperties();
  const remembered = props.getProperty(ROWS_START_PROPERTY);
  const start = settingsStart_(ss);
  if (!start) return;
  if (!remembered) { props.setProperty(ROWS_START_PROPERTY, start); return; }
  if (remembered === start) return;
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(20000)) return;
  try {
    if (props.getProperty(ROWS_START_PROPERTY) !== settingsStart_(ss)) rebuildPeriod_(ss, null);
  } finally {
    lock.releaseLock();
  }
}

// ---- Capture / restore -----------------------------------------------------

/** What was typed on each Daily row, keyed by the date the row was built for. */
function captureDays_(ss, rowsStart) {
  const sheet = ss.getSheetByName(SHEETS.DAILY);
  const width = DT.NOTES - DT.OFFICE + 1;
  const out = {};
  const lastRow = sheet.getLastRow();
  if (lastRow < DT.FIRST_ROW || !rowsStart) return out;
  const count = lastRow - DT.FIRST_ROW + 1;
  const data = sheet.getRange(DT.FIRST_ROW, DT.OFFICE, count, width).getValues();
  data.forEach((r, i) => {
    if (r.some(c => c !== '' && c !== null)) out[addDays_(rowsStart, i)] = r;
  });
  return out;
}

function restoreDays_(ss, days, dayCount) {
  const sheet = ss.getSheetByName(SHEETS.DAILY);
  const width = DT.NOTES - DT.OFFICE + 1;
  SpreadsheetApp.flush();
  const dates = sheet.getRange(DT.FIRST_ROW, DT.DATE, dayCount, 1).getValues();
  const rows = dates.map(d => {
    const key = d[0] instanceof Date ? ymd_(d[0]) : null;
    return key && days[key] ? days[key] : new Array(width).fill('');
  });
  sheet.getRange(DT.FIRST_ROW, DT.OFFICE, dayCount, width).setValues(rows);
}

/** Every ledger row with something typed in it, in order. */
function captureLedger_(ss) {
  const sheet = ss.getSheetByName(SHEETS.FINANCE);
  const values = sheet.getRange(FT.FIRST_ROW, FT.DATE, FT.MAX_ROWS, FT.NOTES).getValues();
  return values
    .filter(r => r[FT.DATE - 1] !== '' || r[FT.QAR - 1] !== '')
    .map(r => ({
      date: r[FT.DATE - 1],
      left: [r[FT.DATE - 1], r[FT.DESC - 1], r[FT.CATEGORY - 1], r[FT.METHOD - 1]],
      amount: r[FT.QAR - 1],
      notes: r[FT.NOTES - 1]
    }));
}

function restoreLedger_(ss, entries) {
  if (!entries.length) return;
  const sheet = ss.getSheetByName(SHEETS.FINANCE);
  const n = entries.length;
  sheet.getRange(FT.FIRST_ROW, FT.DATE, n, 4).setValues(entries.map(e => e.left));
  sheet.getRange(FT.FIRST_ROW, FT.QAR, n, 1).setValues(entries.map(e => [e.amount]));
  sheet.getRange(FT.FIRST_ROW, FT.NOTES, n, 1).setValues(entries.map(e => [e.notes]));
}

/** Salary (only when typed over the default), other income and remittance, keyed by the month the row was built for. */
function captureSalary_(ss, rowsStart) {
  const sheet = ss.getSheetByName(SHEETS.SALARY);
  const range = sheet.getRange(SALARY_COLS.FIRST_ROW, 1, SALARY_MONTHS, SALARY_COLS.REMIT_QAR);
  const values = range.getValues();
  const formulas = range.getFormulas();
  const out = {};
  const first = rowsStart ? rowsStart.slice(0, 7) : null;
  values.forEach((r, i) => {
    const month = first ? shiftMonth_(first, i)
      : (r[SALARY_COLS.MONTH - 1] instanceof Date ? Utilities.formatDate(r[SALARY_COLS.MONTH - 1], tz_(), 'yyyy-MM') : null);
    if (!month) return;
    out[month] = {
      salary: formulas[i][SALARY_COLS.SALARY - 1] ? null : r[SALARY_COLS.SALARY - 1],
      other: r[SALARY_COLS.OTHER - 1],
      remit: r[SALARY_COLS.REMIT_QAR - 1]
    };
  });
  return out;
}

/** The month column follows the start date by formula; put each month's inputs back on its new row. */
function restoreSalary_(ss, byMonth) {
  const sheet = ss.getSheetByName(SHEETS.SALARY);
  SpreadsheetApp.flush();
  const months = sheet.getRange(SALARY_COLS.FIRST_ROW, SALARY_COLS.MONTH, SALARY_MONTHS, 1).getValues();
  months.forEach((m, i) => {
    const row = SALARY_COLS.FIRST_ROW + i;
    const key = m[0] instanceof Date ? Utilities.formatDate(m[0], tz_(), 'yyyy-MM') : null;
    const e = key ? byMonth[key] : null;
    sheet.getRange(row, SALARY_COLS.SALARY).setValue(e && e.salary !== null ? e.salary : '');
    sheet.getRange(row, SALARY_COLS.OTHER).setValue(e ? e.other : '');
    sheet.getRange(row, SALARY_COLS.REMIT_QAR).setValue(e ? e.remit : '');
  });
}

// ---- Archive ---------------------------------------------------------------

/** Copies Dashboard, Daily, Finance and Salary as plain values to "Archive …" tabs at the end. */
function archivePeriod_(ss, start, end) {
  const label = (start instanceof Date && end instanceof Date)
    ? Utilities.formatDate(start, tz_(), 'd MMM yy') + '–' + Utilities.formatDate(end, tz_(), 'd MMM yy')
    : Utilities.formatDate(new Date(), tz_(), 'd MMM yy');
  [[SHEETS.DASHBOARD, 'Dashboard'], [SHEETS.DAILY, 'Daily'], [SHEETS.FINANCE, 'Finance'], [SHEETS.SALARY, 'Salary']]
    .forEach(([name, short]) => {
      const source = ss.getSheetByName(name);
      if (!source) return;
      let title = 'Archive ' + label + ' · ' + short;
      for (let n = 2; ss.getSheetByName(title); n++) title = 'Archive ' + label + ' (' + n + ') · ' + short;
      const copy = source.copyTo(ss).setName(title);
      copy.getCharts().forEach(chart => copy.removeChart(chart));
      const range = copy.getDataRange();
      range.copyTo(range, { contentsOnly: true });
      ss.setActiveSheet(copy);
      ss.moveActiveSheet(ss.getNumSheets());
    });
  const dashboard = ss.getSheetByName(SHEETS.DASHBOARD);
  if (dashboard) ss.setActiveSheet(dashboard);
}
