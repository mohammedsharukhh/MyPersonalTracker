/**
 * Menu, entry forms and the build entry point.
 *
 * The forms exist because typing into a 17-column grid on a phone is painful:
 * they write to the correct row for you, so daily logging is a few taps.
 */

function onOpen() {
  SpreadsheetApp.getUi()
    .createMenu('Life & Finance')
    .addItem("Log today's habits", 'showDailyForm')
    .addItem('Add expense', 'showExpenseForm')
    .addSeparator()
    .addItem('Go to today', 'goToToday')
    .addItem('Refresh dashboard', 'refreshDashboard')
    .addSeparator()
    .addItem('Rebuild tracker rows (after changing dates)', 'rebuildTrackerRows')
    .addItem('Apply currency (after changing it on Settings)', 'applyCurrencyFromMenu')
    .addSeparator()
    .addItem('Show app key', 'showAppKey')
    .addItem('Continue setup (if it stopped)', 'continueSetup')
    .addItem('Reset all data…', 'resetAllDataFromMenu')
    .addItem('Rebuild ENTIRE workbook', 'confirmFullRebuild')
    .addToUi();
}

/**
 * Builds every sheet from scratch. Safe to re-run; it replaces, not appends.
 *
 * Runs both from the spreadsheet menu and from the Apps Script editor's Run
 * button — the editor has no spreadsheet UI attached, so `getUi()` throws
 * there. The closing alert is a courtesy, not part of the build, so it is
 * skipped rather than allowed to fail a run that otherwise succeeded.
 */
function buildWorkbook() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();

  buildSettings(ss);
  SpreadsheetApp.flush();

  const dayCount = getDayCount(ss);

  buildDailyTracker(ss, dayCount);
  buildFinanceTracker(ss, dayCount);
  buildSalarySavings(ss);
  buildWeeklySummary(ss, dayCount);
  buildMonthlySummary(ss, dayCount);
  SpreadsheetApp.flush();

  // Dashboard last: its charts reference ranges on the sheets above.
  buildDashboard(ss, dayCount);

  // Tab order, left to right as the workbook reads.
  [SHEETS.DASHBOARD, SHEETS.DAILY, SHEETS.FINANCE, SHEETS.SALARY,
   SHEETS.WEEKLY, SHEETS.MONTHLY, SHEETS.SETTINGS].forEach((name, i) => {
    const sheet = ss.getSheetByName(name);
    if (sheet) { ss.setActiveSheet(sheet); ss.moveActiveSheet(i + 1); }
  });

  // A default "Sheet1" from a brand-new spreadsheet is noise.
  const leftover = ss.getSheetByName('Sheet1');
  if (leftover && ss.getSheets().length > 1) ss.deleteSheet(leftover);

  // A fresh build is in the default currencies again; make its text formulas
  // follow the Currency setting from now on.
  const props = PropertiesService.getDocumentProperties();
  props.deleteProperty(CURRENCY_LABELS_PROPERTY);
  props.deleteProperty(CURRENCY_FORMULAS_PROPERTY);
  ensureCurrencySettings_(ss);

  ss.setActiveSheet(ss.getSheetByName(SHEETS.DASHBOARD));
  SpreadsheetApp.flush();

  notify('Life & Finance Command Center',
    'Workbook built.\n\nStart on Settings to set your dates, targets and salary, then log on ' +
    'Daily Tracker and Finance Tracker. The Dashboard updates itself.');
}

/**
 * Shows an alert when a spreadsheet UI is attached, and logs otherwise.
 *
 * `SpreadsheetApp.getUi()` throws when the script is run from the Apps Script
 * editor or a trigger rather than from the sheet itself. Every caller here
 * uses it for confirmation only, so losing the dialog must not lose the work.
 */
function notify(title, message) {
  try {
    SpreadsheetApp.getUi().alert(title, message, SpreadsheetApp.getUi().ButtonSet.OK);
  } catch (e) {
    console.log(title + ' — ' + message);
  }
}

/** Days in the configured period, floored at 1. */
function getDayCount(ss) {
  const settings = ss.getSheetByName(SHEETS.SETTINGS);
  const start = settings.getRange(CFG.START_DATE).getValue();
  const end   = settings.getRange(CFG.END_DATE).getValue();
  if (!(start instanceof Date) || !(end instanceof Date)) return 47;
  const days = Math.round((end - start) / 86400000) + 1;
  return Math.max(1, Math.min(days, 400));
}

function confirmFullRebuild() {
  const ui = SpreadsheetApp.getUi();
  const answer = ui.alert('Rebuild entire workbook?',
    'This regenerates every sheet.\n\nYour logged data in the Daily Tracker and Finance Tracker ' +
    'WILL BE ERASED. Use "Rebuild tracker rows" instead if you only changed the dates.',
    ui.ButtonSet.YES_NO);
  if (answer === ui.Button.YES) buildWorkbook();
}

function goToToday() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const daily = ss.getSheetByName(SHEETS.DAILY);
  const dayCount = getDayCount(ss);
  const dates = daily.getRange(DT.FIRST_ROW, DT.DATE, dayCount, 1).getValues();
  const today = new Date().toDateString();

  for (let i = 0; i < dates.length; i++) {
    if (dates[i][0] instanceof Date && dates[i][0].toDateString() === today) {
      ss.setActiveSheet(daily);
      daily.setActiveRange(daily.getRange(DT.FIRST_ROW + i, DT.OFFICE));
      return;
    }
  }
  notify('Not in range', 'Today is outside the period set in Settings.');
}

function refreshDashboard() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  buildDashboard(ss, getDayCount(ss));
  ss.setActiveSheet(ss.getSheetByName(SHEETS.DASHBOARD));
}

// ---- Entry forms ---------------------------------------------------------

function showDailyForm() {
  const html = HtmlService.createHtmlOutputFromFile('DailyForm')
    .setWidth(420).setHeight(560);
  SpreadsheetApp.getUi().showModalDialog(html, "Log today's habits");
}

function showExpenseForm() {
  const html = HtmlService.createHtmlOutputFromFile('ExpenseForm')
    .setWidth(420).setHeight(520);
  SpreadsheetApp.getUi().showModalDialog(html, 'Add expense');
}

/** Values already logged for today, so the form opens pre-filled. */
function getTodayEntry() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const daily = ss.getSheetByName(SHEETS.DAILY);
  const dayCount = getDayCount(ss);
  const dates = daily.getRange(DT.FIRST_ROW, DT.DATE, dayCount, 1).getValues();
  const today = new Date().toDateString();

  for (let i = 0; i < dates.length; i++) {
    if (dates[i][0] instanceof Date && dates[i][0].toDateString() === today) {
      const row = DT.FIRST_ROW + i;
      const v = daily.getRange(row, DT.OFFICE, 1, DT.NOTES - DT.OFFICE + 1).getValues()[0];
      return {
        found: true,
        date: Utilities.formatDate(dates[i][0], Session.getScriptTimeZone(), 'EEE, d MMM yyyy'),
        office: v[0], breakfast: v[1], lunch: v[2], protein: v[3], workout: v[4],
        water: v[5], sleep: v[6], steps: v[7], weight: v[8], screen: v[9], notes: v[10]
      };
    }
  }
  return { found: false };
}

/** Writes the daily form back to today's row. */
function saveTodayEntry(entry) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const daily = ss.getSheetByName(SHEETS.DAILY);
  const dayCount = getDayCount(ss);
  const dates = daily.getRange(DT.FIRST_ROW, DT.DATE, dayCount, 1).getValues();
  const today = new Date().toDateString();

  for (let i = 0; i < dates.length; i++) {
    if (dates[i][0] instanceof Date && dates[i][0].toDateString() === today) {
      const row = DT.FIRST_ROW + i;
      daily.getRange(row, DT.OFFICE, 1, DT.NOTES - DT.OFFICE + 1).setValues([[
        entry.office, entry.breakfast, entry.lunch, entry.protein, entry.workout,
        num(entry.water), num(entry.sleep), num(entry.steps),
        num(entry.weight), num(entry.screen), entry.notes || ''
      ]]);
      return 'Saved for ' + Utilities.formatDate(dates[i][0], Session.getScriptTimeZone(), 'd MMM');
    }
  }
  throw new Error('Today is outside the period set in Settings.');
}

/** Appends one expense to the first free ledger row. */
function saveExpense(expense) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const finance = ss.getSheetByName(SHEETS.FINANCE);

  const dates = finance.getRange(FT.FIRST_ROW, FT.DATE, FT.MAX_ROWS, 1).getValues();
  let target = -1;
  for (let i = 0; i < dates.length; i++) {
    if (dates[i][0] === '' || dates[i][0] === null) { target = FT.FIRST_ROW + i; break; }
  }
  if (target === -1) throw new Error('The ledger is full — no empty rows left.');

  const amount = num(expense.amount);
  if (!amount) throw new Error('Enter an amount.');

  finance.getRange(target, FT.DATE, 1, 4).setValues([[
    expense.date ? new Date(expense.date) : new Date(),
    expense.description || '',
    expense.category || '',
    expense.method || ''
  ]]);
  finance.getRange(target, FT.QAR).setValue(amount);
  finance.getRange(target, FT.NOTES).setValue(expense.notes || '');

  return 'Added ' + amount.toFixed(2) + ' ' + currencyCodes_(ss).main;
}

/** Category and payment lists, for the form dropdowns. */
function getFormLists() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const codes = currencyCodes_(ss);
  const rate = ss.getSheetByName(SHEETS.SETTINGS).getRange(CFG.RATE).getValue();
  return { categories: CATEGORIES, methods: PAYMENT_METHODS, main: codes.main, home: codes.home, rate: Number(rate) || 0 };
}

function applyCurrencyFromMenu() {
  notify('Currency', applyCurrencyLabels());
}

function num(value) {
  if (value === '' || value === null || value === undefined) return '';
  const parsed = Number(value);
  return isFinite(parsed) ? parsed : '';
}
