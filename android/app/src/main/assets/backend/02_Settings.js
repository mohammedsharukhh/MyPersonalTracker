/**
 * SETTINGS · Targets, limits and reference lists.
 *
 * Every other sheet reads its rules from here. The monthly spend limit is the
 * one deliberate departure from the source workbook: it was a typed 100 QAR
 * sitting beside a 20 QAR daily limit, which cannot both hold (20 x 30 = 600),
 * and the sheet reported spend at 2015% of budget as a result. It is now
 * derived from the daily limit, so the two can never drift apart again.
 */
/** What the Monthly Salary row on Settings is for. */
const SALARY_NOTE = 'Your usual salary — the app offers it when you enter a month; no month is filled in for you';

function buildSettings(ss) {
  const sheet = resetSheet(ss, SHEETS.SETTINGS);
  ensureGrid(sheet, 40, 10);

  titleBar(sheet, 'SETTINGS  ·  Targets, Limits & Reference Lists',
           'Edit only the yellow cells. Every other sheet reads its rules from here.', 9);

  sectionLabel(sheet, 4, 1, 3, 'CORE PARAMETERS');
  headerRow(sheet, 5, 1, ['Parameter', 'Value', 'What it drives']);

  const params = [
    ['Start Date',                    periodStart_(), 'First day of every tracker sheet'],
    ['End Date',                      periodEnd_(), 'Last day of the tracked period'],
    ['Exchange Rate (1 QAR = INR)',   26.10,   'Every main-to-home currency conversion'],
    ['Daily Spend Limit (QAR)',       20.00,   'Turns a day red when exceeded'],
    ['Monthly Spend Limit (QAR)',     100.00,  'Month-to-date budget remaining'],
    ['Water Target (Litres/day)',     4.0,     'Water habit scoring'],
    ['Sleep Target (hours/night)',    7.5,     'Sleep habit scoring'],
    ['Steps Target (per day)',        8000,    'Steps habit scoring'],
    ['Screen Time Limit (hours/day)', 3.0,     'Screen-time success rate'],
    ['Monthly Salary (QAR)',          '',      SALARY_NOTE],
    ['Savings Goal (% of income)',    0.95,    'Goal line on Salary & Savings'],
    ['Great Day Threshold (score)',   0.85,    "Streak counter + 'Excellent' rating"],
    // N matches the source workbook: Net Savings = Income - Expenses -
    // Remittance. Set to Y to treat money sent home as savings that merely
    // changed location, which raises the savings rate accordingly.
    ['Remittance counts as savings',  'N',     'Y = money sent home is savings, not spend']
  ];
  sheet.getRange(6, 1, params.length, 3).setValues(params);

  const values = sheet.getRange(6, 2, params.length, 1);
  markInput(values);
  values.setFontWeight('bold').setHorizontalAlignment('center');

  sheet.getRange(6, 1, params.length, 1).setFontWeight('bold');
  sheet.getRange(6, 3, params.length, 1).setFontSize(9).setFontColor(THEME.MUTED);

  sheet.getRange(6, 2).setNumberFormat('dd-mmm-yy');   // Start Date
  sheet.getRange(7, 2).setNumberFormat('dd-mmm-yy');   // End Date
  sheet.getRange(8, 2).setNumberFormat('0.00');        // Exchange Rate
  sheet.getRange(9, 2).setNumberFormat('#,##0.00');    // Daily Spend Limit
  sheet.getRange(10, 2).setNumberFormat('#,##0.00');   // Monthly Spend Limit
  sheet.getRange(11, 2).setNumberFormat('0.0');        // Water Target
  sheet.getRange(12, 2).setNumberFormat('0.0');        // Sleep Target
  sheet.getRange(13, 2).setNumberFormat('#,##0');      // Steps Target
  sheet.getRange(14, 2).setNumberFormat('0.0');        // Screen Time Limit
  sheet.getRange(15, 2).setNumberFormat('#,##0.00');   // Monthly Salary
  sheet.getRange(16, 2).setNumberFormat('0.0%');       // Savings Goal
  sheet.getRange(17, 2).setNumberFormat('0.0%');       // Great Day Threshold

  ynValidation(sheet, 18, 2, 1);                       // Remittance switch
  styleBlock(sheet, 5, 1, params.length + 1, 3);

  sectionLabel(sheet, 20, 1, 3, 'DERIVED (calculated — do not edit)');
  sheet.getRange(20 + 1, 1).setValue('Days In Period').setFontWeight('bold');
  sheet.getRange(20 + 1, 2)
    .setFormula('=' + cfg('END_DATE') + '-' + cfg('START_DATE') + '+1')
    .setNumberFormat('#,##0')
    .setFontWeight('bold')
    .setHorizontalAlignment('center');
  markCalculated(sheet.getRange(20 + 1, 2));
  sheet.getRange(20 + 1, 3)
    .setValue('How many rows the tracker sheets generate')
    .setFontSize(9).setFontColor(THEME.MUTED);

  styleBlock(sheet, 19, 1, 3, 3);

  // Reference lists, read by the dropdowns on the ledger.
  sectionLabel(sheet, 4, 5, 1, 'EXPENSE CATEGORIES');
  sheet.getRange(5, 5, CATEGORIES.length, 1)
    .setValues(CATEGORIES.map(c => [c]))
    .setFontSize(9);
  styleBlock(sheet, 5, 5, CATEGORIES.length, 1);

  sectionLabel(sheet, 4, 7, 1, 'PAYMENT METHODS');
  sheet.getRange(5, 7, PAYMENT_METHODS.length, 1)
    .setValues(PAYMENT_METHODS.map(m => [m]))
    .setFontSize(9);
  styleBlock(sheet, 5, 7, PAYMENT_METHODS.length, 1);

  sectionLabel(sheet, 4, 9, 1, 'Y / N');
  sheet.getRange(5, 9, 2, 1).setValues([['Y'], ['N']])
    .setFontSize(9).setHorizontalAlignment('center');
  styleBlock(sheet, 5, 9, 2, 1);

  footnote(sheet, 23, 1, 9,
    'Changing Start Date or End Date changes how many rows the tracker sheets need — run ' +
    '"Life & Finance ▸ Rebuild tracker rows" afterwards so the Daily and Finance sheets match. ' +
    'The monthly spend limit is derived from the daily limit rather than typed, so the two cannot ' +
    'contradict each other. Set "Remittance counts as savings" to N if you would rather treat money ' +
    'sent home as an expense.');

  sheet.setColumnWidth(1, 220);
  sheet.setColumnWidth(2, 110);
  sheet.setColumnWidth(3, 260);
  sheet.setColumnWidth(4, 20);
  sheet.setColumnWidth(5, 170);
  sheet.setColumnWidth(6, 20);
  sheet.setColumnWidth(7, 170);
  sheet.setColumnWidth(8, 20);
  sheet.setColumnWidth(9, 60);

  sheet.setHiddenGridlines(true);
  sheet.setFrozenRows(3);
  return sheet;
}

/** A fresh workbook's period: today, for 30 days. "Start next period" moves it on later. */
function periodStart_() {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
}

function periodEnd_() {
  const start = periodStart_();
  return new Date(start.getFullYear(), start.getMonth(), start.getDate() + 29);
}
