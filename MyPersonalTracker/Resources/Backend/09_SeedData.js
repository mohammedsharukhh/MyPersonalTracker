/**
 * One-time import of the data from the original PDF tracker.
 *
 * Run `seedOriginalData` once, from the Apps Script editor or the menu. It is
 * safe to re-run: every write is addressed by date, so a second run overwrites
 * the same rows rather than appending duplicates.
 *
 * Values are exactly as the source workbook recorded them. The corrected
 * formulas then recompute the summaries from these same inputs — which is what
 * produces the different headline figures discussed in the README.
 */

/** Daily Tracker rows, 15-18 Aug 2026, from page 2 of the PDF. */
const SEED_DAILY = [
  // date,        office, breakfast, lunch, protein, workout, water, sleep, steps, weight, screen, notes
  ['2026-08-15', 'Y', 'Y', 'Y', 'Y', 'N', 3.0, 8.0, 5000, 61.0, 1.5, 'missing workout due to time fluctuation'],
  ['2026-08-16', 'Y', 'Y', 'Y', 'Y', 'N', 4.0, 8.0, 4000, 61.0, 1.8, 'missing workout due to time fluctuation'],
  ['2026-08-17', 'Y', 'Y', 'Y', 'Y', 'N', 3.0, 7.0, 4000, 61.0, 1.4, 'missing workout due to time fluctuation'],
  ['2026-08-18', 'Y', 'Y', 'Y', 'Y', 'N', 4.0, 8.0, 4000, 61.0, 2.0, 'missing workout due to time fluctuation']
];

/** Finance Tracker ledger, from page 5 of the PDF. */
const SEED_EXPENSES = [
  // date,        description,        category,            method, amount, notes
  ['2026-08-15', 'Hotel',            'Rent & Utilities',  'Cash', 500.00, '2 Days Spent'],
  ['2026-08-16', 'Mobile Purchased', 'Gifts & Family',    'Cash', 750.00, 'Bought new phone — OnePlus'],
  ['2026-08-17', 'Uber Trip',        'Transport',         'Cash', 400.00, 'Uber trip'],
  ['2026-08-18', 'Outside Food',     'Food & Dining',     'Cash', 300.00, 'Went to restaurant'],
  ['2026-08-19', 'Recharge',         'Mobile & Internet', 'Cash',  65.00, 'Vodafone recharge']
];

/** Salary & Savings, August 2026, from page 12 of the PDF. */
const SEED_SALARY = {
  month: '2026-08-01',
  salary: 2500.00,
  otherIncome: 1000.00,
  remittance: 1000.00
};

/**
 * Writes the original data into the rebuilt workbook.
 *
 * Assumes the period in Settings still covers August 2026; if the dates have
 * been moved past it, the daily rows have nowhere to land and the run reports
 * how many it could not place rather than failing silently.
 */
function seedOriginalData() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const dayCount = getDayCount(ss);

  const placedDaily = seedDailyRows(ss, dayCount);
  const placedExpenses = seedExpenseRows(ss);
  const placedSalary = seedSalaryRow(ss);

  SpreadsheetApp.flush();
  buildDashboard(ss, dayCount);

  const missed = SEED_DAILY.length - placedDaily;
  const message =
    placedDaily + ' of ' + SEED_DAILY.length + ' daily rows imported\n' +
    placedExpenses + ' of ' + SEED_EXPENSES.length + ' expenses imported\n' +
    (placedSalary ? 'August salary and remittance imported' : 'Salary row not found for Aug 2026') +
    (missed > 0
      ? '\n\nThe missing days fall outside the period set in Settings. Set the Start Date to ' +
        '15-Aug-2026 (or earlier) and run this again.'
      : '');

  notify('Original data imported', message);
}

/** Matches each seed row to its date row on the Daily Tracker. */
function seedDailyRows(ss, dayCount) {
  const sheet = ss.getSheetByName(SHEETS.DAILY);
  const dates = sheet.getRange(DT.FIRST_ROW, DT.DATE, dayCount, 1).getValues();

  // Index the sheet's dates once rather than scanning per seed row.
  const rowByDate = {};
  dates.forEach((d, i) => {
    if (d[0] instanceof Date) rowByDate[toKey(d[0])] = DT.FIRST_ROW + i;
  });

  let placed = 0;
  SEED_DAILY.forEach(entry => {
    const row = rowByDate[entry[0]];
    if (!row) return;
    // Columns C..M in one write: the five Y/N habits, five numbers, notes.
    sheet.getRange(row, DT.OFFICE, 1, DT.NOTES - DT.OFFICE + 1)
      .setValues([entry.slice(1)]);
    placed++;
  });
  return placed;
}

/** Appends the expenses, clearing any previous seed of the same rows first. */
function seedExpenseRows(ss) {
  const sheet = ss.getSheetByName(SHEETS.FINANCE);

  // Re-running must not duplicate: wipe the block this seed owns, then rewrite.
  sheet.getRange(FT.FIRST_ROW, FT.DATE, SEED_EXPENSES.length, 4).clearContent();
  sheet.getRange(FT.FIRST_ROW, FT.QAR, SEED_EXPENSES.length, 1).clearContent();
  sheet.getRange(FT.FIRST_ROW, FT.NOTES, SEED_EXPENSES.length, 1).clearContent();

  const head = SEED_EXPENSES.map(e => [parseDate(e[0]), e[1], e[2], e[3]]);
  const amounts = SEED_EXPENSES.map(e => [e[4]]);
  const notes = SEED_EXPENSES.map(e => [e[5]]);

  sheet.getRange(FT.FIRST_ROW, FT.DATE, head.length, 4).setValues(head);
  sheet.getRange(FT.FIRST_ROW, FT.QAR, amounts.length, 1).setValues(amounts);
  sheet.getRange(FT.FIRST_ROW, FT.NOTES, notes.length, 1).setValues(notes);

  return SEED_EXPENSES.length;
}

/** Fills August's salary, other income and remittance. */
function seedSalaryRow(ss) {
  const sheet = ss.getSheetByName(SHEETS.SALARY);
  const months = sheet.getRange(SALARY_COLS.FIRST_ROW, SALARY_COLS.MONTH, SALARY_MONTHS, 1).getValues();
  const target = SEED_SALARY.month;

  for (let i = 0; i < months.length; i++) {
    const value = months[i][0];
    if (!(value instanceof Date)) continue;
    // Match on year-month; the sheet stores the 1st of each month.
    if (toKey(value).slice(0, 7) === target.slice(0, 7)) {
      const row = SALARY_COLS.FIRST_ROW + i;
      sheet.getRange(row, SALARY_COLS.SALARY).setValue(SEED_SALARY.salary);
      sheet.getRange(row, SALARY_COLS.OTHER).setValue(SEED_SALARY.otherIncome);
      sheet.getRange(row, SALARY_COLS.REMIT_QAR).setValue(SEED_SALARY.remittance);
      return true;
    }
  }
  return false;
}

/** Date -> 'yyyy-MM-dd', in the script's timezone. */
function toKey(date) {
  return Utilities.formatDate(date, Session.getScriptTimeZone(), 'yyyy-MM-dd');
}

/** 'yyyy-MM-dd' -> a local Date at midnight, avoiding UTC drift. */
function parseDate(key) {
  const parts = key.split('-');
  return new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
}
