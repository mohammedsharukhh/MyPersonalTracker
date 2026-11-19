/**
 * RECURRING · Expenses that repeat every month (rent, phone, gym).
 *
 * One row per rule on the "Recurring" tab. Whenever the app syncs, every rule
 * that has fallen due since it was last added is written to the ledger like a
 * normal expense — so rent appears on the 1st even if nobody opened the app
 * that day. "Last added" (yyyy-MM) records the newest month already posted.
 */

const RECURRING = {
  DESC: 1, CATEGORY: 2, METHOD: 3, AMOUNT: 4, DAY: 5, ACTIVE: 6, LAST: 7, NOTES: 8,
  HEADER_ROW: 3,
  FIRST_ROW: 4
};

/** Creates the Recurring tab the first time it's needed. Never clears it. */
function ensureRecurringSheet_(ss) {
  let sheet = ss.getSheetByName(SHEETS.RECURRING);
  if (sheet) return sheet;
  sheet = ss.insertSheet(SHEETS.RECURRING, ss.getNumSheets());
  ensureGrid(sheet, 60, 8);
  titleBar(sheet, 'RECURRING EXPENSES  ·  Added to the ledger automatically each month',
    'One row per bill. Set Active to N to pause one. Last Added is filled in for you.', 8);
  const main = currencyCodes_(ss).main;
  headerRow(sheet, RECURRING.HEADER_ROW, 1,
    ['Description', 'Category', 'Payment Method', 'Amount\n(' + main + ')', 'Day of\nMonth', 'Active', 'Last Added', 'Notes']);
  const rows = 50;
  const r = RECURRING.FIRST_ROW;
  markInput(sheet.getRange(r, 1, rows, 6));
  markInput(sheet.getRange(r, RECURRING.NOTES, rows, 1));
  markCalculated(sheet.getRange(r, RECURRING.LAST, rows, 1));
  sheet.getRange(r, RECURRING.AMOUNT, rows, 1).setNumberFormat('#,##0.00');
  sheet.getRange(r, RECURRING.LAST, rows, 1).setNumberFormat('@');
  sheet.getRange(r, RECURRING.CATEGORY, rows, 1).setDataValidation(
    SpreadsheetApp.newDataValidation().requireValueInList(CATEGORIES, true).build());
  sheet.getRange(r, RECURRING.METHOD, rows, 1).setDataValidation(
    SpreadsheetApp.newDataValidation().requireValueInList(PAYMENT_METHODS, true).build());
  sheet.getRange(r, RECURRING.DAY, rows, 1).setDataValidation(
    SpreadsheetApp.newDataValidation().requireNumberBetween(1, 31).setAllowInvalid(false).build());
  ynValidation(sheet, r, RECURRING.ACTIVE, rows);
  styleBlock(sheet, RECURRING.HEADER_ROW, 1, rows + 1, 8);
  [200, 150, 160, 90, 70, 60, 90, 220].forEach((w, i) => sheet.setColumnWidth(i + 1, w));
  sheet.setHiddenGridlines(true);
  sheet.setFrozenRows(RECURRING.HEADER_ROW);
  return sheet;
}

function readRecurring_(ss) {
  const sheet = ss.getSheetByName(SHEETS.RECURRING);
  if (!sheet || sheet.getLastRow() < RECURRING.FIRST_ROW) return [];
  const count = sheet.getLastRow() - RECURRING.FIRST_ROW + 1;
  return sheet.getRange(RECURRING.FIRST_ROW, 1, count, RECURRING.NOTES).getValues()
    .map((r, i) => ({
      row: RECURRING.FIRST_ROW + i,
      description: value_(r[RECURRING.DESC - 1]),
      category: value_(r[RECURRING.CATEGORY - 1]),
      method: value_(r[RECURRING.METHOD - 1]),
      amount: value_(r[RECURRING.AMOUNT - 1]),
      day: value_(r[RECURRING.DAY - 1]),
      active: r[RECURRING.ACTIVE - 1] === 'Y',
      lastAdded: r[RECURRING.LAST - 1] instanceof Date
        ? Utilities.formatDate(r[RECURRING.LAST - 1], tz_(), 'yyyy-MM')
        : (value_(r[RECURRING.LAST - 1]) === null ? null : String(r[RECURRING.LAST - 1])),
      notes: value_(r[RECURRING.NOTES - 1])
    }))
    .filter(rule => rule.description || rule.amount);
}

// ---- Months as "yyyy-MM" ----------------------------------------------------

function monthOf_(ymd) { return ymd.slice(0, 7); }
function shiftMonth_(month, by) {
  const [y, m] = month.split('-').map(Number);
  const d = new Date(y, m - 1 + by, 1);
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0');
}
function daysInMonth_(month) {
  const [y, m] = month.split('-').map(Number);
  return new Date(y, m, 0).getDate();
}

/** The month a new rule counts as already posted for: this month if its day has passed. */
function initialLastAdded_(day, addThisMonth) {
  const today = ymd_(new Date());
  const thisMonth = monthOf_(today);
  if (addThisMonth) return shiftMonth_(thisMonth, -1);
  return Number(day) <= Number(today.slice(8, 10)) ? thisMonth : shiftMonth_(thisMonth, -1);
}

/**
 * Posts every due occurrence to the ledger. Only dates inside the period are
 * posted (the ledger belongs to the period); later ones wait their turn.
 * Returns how many expenses were added.
 */
function processRecurring_(ss) {
  const sheet = ss.getSheetByName(SHEETS.RECURRING);
  if (!sheet || sheet.getLastRow() < RECURRING.FIRST_ROW) return 0;
  endFinishedLoanBills_(ss);
  const settings = ss.getSheetByName(SHEETS.SETTINGS);
  const start = ymd_(settings.getRange(CFG.START_DATE).getValue());
  const end = ymd_(settings.getRange(CFG.END_DATE).getValue());
  const today = ymd_(new Date());
  let added = 0;

  readRecurring_(ss).forEach(rule => {
    const amount = num(rule.amount);
    const day = Number(rule.day);
    if (!rule.active || !(amount > 0) || !(day >= 1 && day <= 31)) return;
    let last = /^\d{4}-\d{2}$/.test(rule.lastAdded || '') ? rule.lastAdded : initialLastAdded_(day, false);
    for (let month = shiftMonth_(last, 1); month <= monthOf_(today); month = shiftMonth_(month, 1)) {
      const date = month + '-' + String(Math.min(day, daysInMonth_(month))).padStart(2, '0');
      if (date > today) break;
      if (start && end && date >= start && date <= end) {
        addExpense_({
          date: date, description: rule.description || '', category: rule.category || '',
          method: rule.method || '', qar: amount,
          notes: (rule.notes ? rule.notes + ' · ' : '') + 'Recurring'
        });
        added++;
      }
      last = month;
    }
    if (last !== rule.lastAdded) sheet.getRange(rule.row, RECURRING.LAST).setValue(last);
  });
  return added;
}

/** API: add (no row) or edit (row) a rule. `addThisMonth` posts this month's straight away. */
function saveRecurring_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheet = ensureRecurringSheet_(ss);
  const amount = num(p.amount);
  if (!(amount > 0)) throw new Error('Enter an amount above zero.');
  const day = Number(p.day);
  if (!(day >= 1 && day <= 31)) throw new Error('Day of month must be 1–31.');
  if (p.category && CATEGORIES.indexOf(p.category) === -1) throw new Error('Unknown category: ' + p.category);
  if (p.method && PAYMENT_METHODS.indexOf(p.method) === -1) throw new Error('Unknown payment method: ' + p.method);

  let row = p.row ? Number(p.row) : null;
  let last;
  if (row) {
    if (!(row >= RECURRING.FIRST_ROW && row <= sheet.getLastRow())) throw new Error('Not a recurring row: ' + p.row);
    last = String(sheet.getRange(row, RECURRING.LAST).getValue() || '') || initialLastAdded_(day, false);
  } else {
    const descs = sheet.getRange(RECURRING.FIRST_ROW, 1, Math.max(1, sheet.getMaxRows() - RECURRING.FIRST_ROW + 1), RECURRING.AMOUNT).getValues();
    const free = descs.findIndex(r => r[0] === '' && r[RECURRING.AMOUNT - 1] === '');
    row = free === -1 ? sheet.getLastRow() + 1 : RECURRING.FIRST_ROW + free;
    // The app passes the month of an expense it just logged, so it isn't posted twice.
    last = /^\d{4}-\d{2}$/.test(String(p.lastAdded || '')) ? String(p.lastAdded) : initialLastAdded_(day, !!p.addThisMonth);
  }
  sheet.getRange(row, 1, 1, RECURRING.NOTES).setValues([[
    p.description || '', p.category || '', p.method || '', amount, day,
    p.active === false ? 'N' : 'Y', last, p.notes || ''
  ]]);
  SpreadsheetApp.flush();
  processRecurring_(ss);
}

function deleteRecurring_(p) {
  const sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SHEETS.RECURRING);
  const row = Number(p.row);
  if (!sheet || !(row >= RECURRING.FIRST_ROW && row <= sheet.getLastRow())) throw new Error('Not a recurring row: ' + p.row);
  sheet.getRange(row, 1, 1, RECURRING.NOTES).clearContent();
}
