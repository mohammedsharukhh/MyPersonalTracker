/**
 * SALARY SPLIT · Where each month's salary is meant to go.
 *
 *   Settings     — Salary Split block: a percentage for each bucket (they
 *                  should add up to 100%) and an optional emergency fund goal.
 *   Set Aside    — money you move into the emergency fund or savings / SIP.
 *                  Not spending, so it never touches the Finance ledger.
 *   Salary Split — month by month: each bucket's share of that month's income
 *                  and what has gone to it so far, all by formula.
 *
 * Buckets are filled from:
 *   Essentials      ledger categories in SPLIT_BUCKETS.Essentials, and every monthly
 *                   bill (Recurring tab) whatever its category
 *   Lifestyle       ledger categories in SPLIT_BUCKETS.Lifestyle
 *   Family support  money sent home (Salary & Savings) + Remittance and Gifts & Family expenses
 *   Emergency fund  Set Aside entries for it
 *   Savings / SIP   Set Aside entries for it
 */

const SPLIT_SECTION_ROW = 66;
const SPLIT_BUCKETS = [
  { name: 'Essentials', key: 'SPLIT_ESSENTIALS', pct: 0.45,
    categories: ['Groceries', 'Rent & Utilities', 'Transport', 'Mobile & Internet', 'Health & Pharmacy', 'Household', 'Education'] },
  { name: 'Emergency fund', key: 'SPLIT_EMERGENCY', pct: 0.10, setAside: true },
  { name: 'Savings / SIP', key: 'SPLIT_SAVINGS', pct: 0.20, setAside: true },
  { name: 'Family support', key: 'SPLIT_FAMILY', pct: 0.15, categories: ['Remittance', 'Gifts & Family'], sentHome: true },
  { name: 'Lifestyle', key: 'SPLIT_LIFESTYLE', pct: 0.10,
    categories: ['Food & Dining', 'Personal Care', 'Clothing', 'Entertainment', 'Travel', 'Other'] }
];
const SET_ASIDE_BUCKETS = SPLIT_BUCKETS.filter(b => b.setAside).map(b => b.name);
const SET_ASIDE = { ID: 1, DATE: 2, BUCKET: 3, AMOUNT: 4, NOTE: 5, FIRST_ROW: 4 };
const SPLIT_FIRST_ROW = 4;
/** Bumped when the Salary Split formulas change, so existing sheets get the new ones. */
const SPLIT_FORMULAS_VERSION = '2';
const SPLIT_FORMULAS_PROPERTY = 'SPLIT_FORMULAS';

function ensureSplit_(ss) {
  ensureSplitSettings_(ss);
  ensureSpendLimit_(ss);
  const main = currencyCodes_(ss).main;
  ensureTab_(ss, SHEETS.SET_ASIDE, 'SET ASIDE  ·  Money moved to your emergency fund and savings / SIP',
    'Added from the app (Finance ▸ Savings ▸ Put aside). This is saving, not spending, so it is not in the Finance ledger.',
    ['Entry ID', 'Date', 'Bucket', 'Amount\n(' + main + ')', 'Note'],
    [110, 90, 140, 100, 260],
    sheet => {
      sheet.getRange(SET_ASIDE.FIRST_ROW, SET_ASIDE.DATE, 2000, 1).setNumberFormat('dd-mmm-yy');
      sheet.getRange(SET_ASIDE.FIRST_ROW, SET_ASIDE.AMOUNT, 2000, 1).setNumberFormat('#,##0.00');
      sheet.getRange(SET_ASIDE.FIRST_ROW, SET_ASIDE.BUCKET, 2000, 1).setDataValidation(
        SpreadsheetApp.newDataValidation().requireValueInList(SET_ASIDE_BUCKETS, true).build());
    });
  if (!ss.getSheetByName(SHEETS.SPLIT)) buildSplitSheet_(ss, main);
  const props = PropertiesService.getDocumentProperties();
  if (props.getProperty(SPLIT_FORMULAS_PROPERTY) !== SPLIT_FORMULAS_VERSION) {
    writeSplitFormulas_(ss);
    props.setProperty(SPLIT_FORMULAS_PROPERTY, SPLIT_FORMULAS_VERSION);
  }
}

/** Percentages and the emergency fund goal, below the Body block on Settings. */
function ensureSplitSettings_(ss) {
  const sheet = ss.getSheetByName(SHEETS.SETTINGS);
  if (!sheet) return;
  const r = SPLIT_SECTION_ROW;
  if (sheet.getMaxRows() >= r + 8 && sheet.getRange(r + 2, 1).getValue() === 'Essentials (%)') return;
  ensureGrid(sheet, r + 10, 3);
  sectionLabel(sheet, r, 1, 3, 'SALARY SPLIT  ·  where each month\'s income goes');
  headerRow(sheet, r + 1, 1, ['Bucket', 'Share', 'What counts']);
  const rows = SPLIT_BUCKETS.map(b => [b.name + ' (%)', b.pct,
    b.setAside ? 'Money you put aside (Set Aside tab)'
      : (b.sentHome ? 'Sent home + ' : '') + b.categories.join(', ')]);
  rows.push(['Emergency Fund Goal', '', 'Optional: the total you want in the emergency fund']);
  sheet.getRange(r + 2, 1, rows.length, 3).setValues(rows);
  sheet.getRange(r + 2, 2, SPLIT_BUCKETS.length, 1).setNumberFormat('0%');
  sheet.getRange(r + 2 + SPLIT_BUCKETS.length, 2).setNumberFormat('#,##0');
  const inputs = sheet.getRange(r + 2, 2, rows.length, 1);
  markInput(inputs);
  inputs.setFontWeight('bold').setHorizontalAlignment('center');
  // A check that the shares add up.
  const totalRow = r + 2 + rows.length;
  sheet.getRange(totalRow, 1, 1, 3).setValues([['Total', '', '']]);
  sheet.getRange(totalRow, 2).setFormula('=SUM(B' + (r + 2) + ':B' + (r + 1 + SPLIT_BUCKETS.length) + ')').setNumberFormat('0%')
    .setBackground(THEME.CALC_BG).setFontWeight('bold').setHorizontalAlignment('center');
  sheet.getRange(totalRow, 3).setFormula('=IF(ROUND(B' + totalRow + ',4)=1,"✓ Adds up to 100%","Should add up to 100%")');
  sheet.getRange(r + 2, 1, rows.length + 1, 1).setFontWeight('bold');
  sheet.getRange(r + 2, 3, rows.length + 1, 1).setFontSize(9).setFontColor(THEME.MUTED).setWrap(true);
  styleBlock(sheet, r + 1, 1, rows.length + 2, 3);
}

/**
 * Makes the Monthly Spend Limit (B10) follow the plan: this month's income ×
 * (Essentials + Lifestyle), or the custom amount when the switch is N or no
 * income is entered yet. Every formula that uses the limit (Finance's
 * day-by-day "left", the Dashboard's budget figures) follows automatically.
 * The first time, the limit typed in B10 becomes the custom amount.
 */
function ensureSpendLimit_(ss) {
  const sheet = ss.getSheetByName(SHEETS.SETTINGS);
  if (!sheet) return;
  const r = SPLIT_SECTION_ROW + 9;   // B75
  if (sheet.getMaxRows() >= r + 1 && sheet.getRange(r, 1).getValue() === 'Spend Limit From Split') return;
  ensureGrid(sheet, r + 3, 3);
  const typed = sheet.getRange(CFG.MONTHLY_LIMIT).getValue();
  sheet.getRange(r, 1, 2, 3).setValues([
    ['Spend Limit From Split', 'Y', 'Y: monthly spend limit = this month\'s income × (Essentials + Lifestyle)'],
    ['Custom Monthly Limit', typeof typed === 'number' ? typed : '', 'Used when the switch is N, or before this month\'s salary is entered']
  ]);
  markInput(sheet.getRange(r, 2, 2, 1));
  sheet.getRange(r, 2, 2, 1).setFontWeight('bold').setHorizontalAlignment('center');
  sheet.getRange(r, 2).setDataValidation(SpreadsheetApp.newDataValidation().requireValueInList(['Y', 'N'], true).build());
  sheet.getRange(r + 1, 2).setNumberFormat('#,##0.00');
  sheet.getRange(r, 1, 2, 1).setFontWeight('bold');
  sheet.getRange(r, 3, 2, 1).setFontSize(9).setFontColor(THEME.MUTED).setWrap(true);
  styleBlock(sheet, r, 1, 2, 3);

  const salary = "'" + SHEETS.SALARY + "'!";
  const months = salary + '$A$' + SALARY_COLS.FIRST_ROW + ':$A$' + (SALARY_COLS.FIRST_ROW + SALARY_MONTHS - 1);
  const incomes = salary + '$D$' + SALARY_COLS.FIRST_ROW + ':$D$' + (SALARY_COLS.FIRST_ROW + SALARY_MONTHS - 1);
  const income = 'IFERROR(INDEX(' + incomes + ',MATCH(EOMONTH(TODAY(),-1)+1,' + months + ',0)),0)';
  sheet.getRange(CFG.MONTHLY_LIMIT).setFormula(
    '=IF(AND(' + cfg('LIMIT_FROM_SPLIT') + '="Y",N(' + income + ')>0),ROUND(N(' + income + ')*(' + cfg('SPLIT_ESSENTIALS') + '+' + cfg('SPLIT_LIFESTYLE') + '),2),N(' + cfg('CUSTOM_LIMIT') + '))')
    .setBackground(THEME.CALC_BG);
  sheet.getRange(10, 3).setValue('Worked out: see Spend Limit From Split (row ' + r + ')');
}

/** Month by month: each bucket's share of income, and what has gone to it. */
function buildSplitSheet_(ss, main) {
  const headers = ['Month', 'Income\n(' + main + ')'];
  SPLIT_BUCKETS.forEach(b => headers.push(b.name + '\nShare', b.name + '\n' + (b.setAside ? 'Put Aside' : 'Used')));
  ensureTab_(ss, SHEETS.SPLIT, 'SALARY SPLIT  ·  Each month\'s income, divided by your percentages',
    'Share = income × the percentage on Settings ▸ Salary Split. Used / Put Aside fills in from the Finance ledger, money sent home and the Set Aside tab.',
    headers, [90, 100].concat(SPLIT_BUCKETS.flatMap(() => [95, 95])), null);
  writeSplitFormulas_(ss);
}

/** The month rows. Monthly bills (notes ending "Recurring") always count as Essentials. */
function writeSplitFormulas_(ss) {
  const sheet = ss.getSheetByName(SHEETS.SPLIT);
  if (!sheet) return;
  const salary = "'" + SHEETS.SALARY + "'!";
  const ft = "'" + SHEETS.FINANCE + "'!";
  const sa = "'" + SHEETS.SET_ASIDE + "'!";
  const dates = ft + '$A$' + FT.FIRST_ROW + ':$A$' + (FT.FIRST_ROW + FT.MAX_ROWS - 1);
  const cats = ft + '$C$' + FT.FIRST_ROW + ':$C$' + (FT.FIRST_ROW + FT.MAX_ROWS - 1);
  const amounts = ft + '$E$' + FT.FIRST_ROW + ':$E$' + (FT.FIRST_ROW + FT.MAX_ROWS - 1);
  const notes = ft + '$H$' + FT.FIRST_ROW + ':$H$' + (FT.FIRST_ROW + FT.MAX_ROWS - 1);
  const isBill = 'ISNUMBER(SEARCH("Recurring",' + notes + '))';
  const rows = [];
  for (let i = 0; i < SALARY_MONTHS; i++) {
    const row = SPLIT_FIRST_ROW + i;
    const src = SALARY_COLS.FIRST_ROW + i;
    const month = 'TEXT($A' + row + ',"yyyy-mm")';
    const line = ['=' + salary + '$A$' + src, '=IF($A' + row + '="","",N(' + salary + '$D$' + src + '))'];
    SPLIT_BUCKETS.forEach(b => {
      line.push('=IF($A' + row + '="","",ROUND($B' + row + '*' + cfg(b.key) + ',2))');
      let used;
      if (b.setAside) {
        used = 'SUMPRODUCT((TEXT(' + sa + '$B$4:$B$2000,"yyyy-mm")=' + month + ')*(' + sa + '$C$4:$C$2000="' + b.name + '"),' + sa + '$D$4:$D$2000)';
      } else {
        const inCategory = 'ISNUMBER(MATCH(' + cats + ',{"' + b.categories.join('","') + '"},0))';
        const counts = b.name === 'Essentials' ? '((' + inCategory + '+' + isBill + ')>0)' : '(' + inCategory + '*NOT(' + isBill + '))';
        used = 'SUMPRODUCT((TEXT(' + dates + ',"yyyy-mm")=' + month + ')*' + counts + ',' + amounts + ')';
        if (b.sentHome) used += '+N(' + salary + '$F$' + src + ')';
      }
      line.push('=IF($A' + row + '="","",' + used + ')');
    });
    rows.push(line);
  }
  sheet.getRange(SPLIT_FIRST_ROW, 1, rows.length, rows[0].length).setFormulas(rows);
  sheet.getRange(SPLIT_FIRST_ROW, 1, rows.length, 1).setNumberFormat('mmm yyyy');
  sheet.getRange(SPLIT_FIRST_ROW, 2, rows.length, rows[0].length - 1).setNumberFormat('#,##0.00').setBackground(THEME.CALC_BG);
}

function readSetAside_(ss) {
  ensureSplit_(ss);
  ensureGoals_(ss);
  return rowsOf_(ss, SHEETS.SET_ASIDE, SET_ASIDE.FIRST_ROW, SET_ASIDE_GOAL)
    .map(x => ({
      id: String(x.v[SET_ASIDE.ID - 1] || 'row' + x.row),
      date: ymd_(x.v[SET_ASIDE.DATE - 1]),
      bucket: value_(x.v[SET_ASIDE.BUCKET - 1]),
      amount: value_(x.v[SET_ASIDE.AMOUNT - 1]),
      note: value_(x.v[SET_ASIDE.NOTE - 1]),
      goalId: value_(x.v[SET_ASIDE_GOAL_ID - 1]) === null ? null : String(x.v[SET_ASIDE_GOAL_ID - 1])
    }))
    .filter(e => e.date && e.bucket);
}

/** { id, date, bucket, amount, note, goalId } */
function saveSetAside_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureSplit_(ss);
  ensureGoals_(ss);
  const id = requiredId_(p.id);
  if (SET_ASIDE_BUCKETS.indexOf(p.bucket) === -1) throw new Error('Put money aside into: ' + SET_ASIDE_BUCKETS.join(' or ') + '.');
  const amount = num(p.amount);
  if (!(amount > 0)) throw new Error('Enter an amount above zero.');
  upsertById_(ss.getSheetByName(SHEETS.SET_ASIDE), SET_ASIDE.FIRST_ROW, id,
    [id, parseYmd_(p.date), p.bucket, amount, String(p.note || '').slice(0, 200),
     p.goalId || '', p.goalId ? lookupName_(ss, SHEETS.SAVINGS_GOALS, GOALS.FIRST_ROW, GOALS.ID, GOALS.NAME, String(p.goalId)) : '']);
}

function deleteSetAside_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  if (p.id) deleteWhere_(ss, SHEETS.SET_ASIDE, SET_ASIDE.FIRST_ROW, SET_ASIDE.ID, String(p.id));
}
