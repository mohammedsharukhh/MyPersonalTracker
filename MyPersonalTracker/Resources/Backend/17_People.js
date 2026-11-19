/**
 * PEOPLE · Money with friends and family.
 *
 * One row per movement of money with someone:
 *   Lent        you gave money they'll pay back      → they owe you more
 *   Got back    they paid you back                    → they owe you less
 *   Borrowed    they gave you money you'll pay back   → you owe them more
 *   Paid back   you paid them back                    → you owe them less
 *   Gift given  money you gave away                   → added to Finance as Gifts & Family spending
 *   Gift got    money you were given                  → recorded only
 *
 * Balance per person = Lent − Got back − Borrowed + Paid back:
 * positive means they owe you, negative means you owe them.
 */

const PEOPLE = {
  ID: 1, DATE: 2, PERSON: 3, TYPE: 4, AMOUNT: 5, NOTE: 6, LEDGER_ROW: 7,
  HEADER_ROW: 3, FIRST_ROW: 4
};
const PEOPLE_TYPES = ['Lent', 'Got back', 'Borrowed', 'Paid back', 'Gift given', 'Gift got'];

function ensurePeopleSheet_(ss) {
  if (ss.getSheetByName(SHEETS.PEOPLE)) return;
  ensureTab_(ss, SHEETS.PEOPLE, 'PEOPLE  ·  Money with friends & family',
    'Lent / Got back / Borrowed / Paid back keep a running balance per person. Gifts given also count as spending in Finance.',
    ['Entry ID', 'Date', 'Person', 'Type', 'Amount\n(' + currencyCodes_(ss).main + ')', 'Note', 'Ledger\nRow'],
    [110, 90, 160, 100, 100, 260, 60],
    sheet => {
      sheet.getRange(PEOPLE.FIRST_ROW, PEOPLE.DATE, 2000, 1).setNumberFormat('dd-mmm-yy');
      sheet.getRange(PEOPLE.FIRST_ROW, PEOPLE.AMOUNT, 2000, 1).setNumberFormat('#,##0.00');
      sheet.getRange(PEOPLE.FIRST_ROW, PEOPLE.TYPE, 2000, 1).setDataValidation(
        SpreadsheetApp.newDataValidation().requireValueInList(PEOPLE_TYPES, true).build());
    });
}

function readPeople_(ss) {
  ensurePeopleSheet_(ss);
  return rowsOf_(ss, SHEETS.PEOPLE, PEOPLE.FIRST_ROW, PEOPLE.LEDGER_ROW)
    .map(x => ({
      row: x.row,
      id: String(x.v[PEOPLE.ID - 1] || 'row' + x.row),
      date: ymd_(x.v[PEOPLE.DATE - 1]),
      person: value_(x.v[PEOPLE.PERSON - 1]),
      type: value_(x.v[PEOPLE.TYPE - 1]),
      amount: value_(x.v[PEOPLE.AMOUNT - 1]),
      note: value_(x.v[PEOPLE.NOTE - 1]),
      ledgerRow: value_(x.v[PEOPLE.LEDGER_ROW - 1])
    }))
    .filter(e => e.person && e.type);
}

/** Adds or replaces one entry: { id, date, person, type, amount, note, method }. */
function savePeopleEntry_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensurePeopleSheet_(ss);
  const sheet = ss.getSheetByName(SHEETS.PEOPLE);
  const id = String(p.id || '').trim();
  const person = String(p.person || '').trim();
  if (!id) throw new Error('An entry needs an id.');
  if (!person) throw new Error('Who was it with?');
  if (PEOPLE_TYPES.indexOf(p.type) === -1) throw new Error('Type must be one of: ' + PEOPLE_TYPES.join(', ') + '.');
  const amount = num(p.amount);
  if (!(amount > 0)) throw new Error('Enter an amount above zero.');
  const date = parseYmd_(p.date);

  const existing = peopleRow_(sheet, id);
  let ledger = existing ? peopleLedger_(ss, sheet, existing) : null;

  // A gift you give is spending: keep it in step with the Finance ledger.
  if (p.type === 'Gift given') {
    const expense = { date: p.date, description: 'Gift to ' + person, category: 'Gifts & Family',
      method: PAYMENT_METHODS.indexOf(p.method) !== -1 ? p.method : PAYMENT_METHODS[0], qar: amount, notes: 'From People' };
    if (ledger) updateExpense_(Object.assign({ row: ledger }, expense));
    else ledger = addExpense_(expense);
  } else if (ledger) {
    deleteExpense_({ row: ledger });
    ledger = null;
  }

  const values = [[id, date, person, p.type, amount, p.note || '', ledger || '']];
  const row = existing || Math.max(PEOPLE.FIRST_ROW, sheet.getLastRow() + 1);
  sheet.getRange(row, 1, 1, PEOPLE.LEDGER_ROW).setValues(values);
  sheet.getRange(row, PEOPLE.DATE).setNumberFormat('dd-mmm-yy');
}

function deletePeopleEntry_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheet = ss.getSheetByName(SHEETS.PEOPLE);
  if (!sheet) return;
  const row = peopleRow_(sheet, String(p.id || ''));
  if (!row) return;
  const ledger = peopleLedger_(ss, sheet, row);
  if (ledger) deleteExpense_({ row: ledger });
  sheet.deleteRow(row);
}

function peopleRow_(sheet, id) {
  if (!id || sheet.getLastRow() < PEOPLE.FIRST_ROW) return null;
  const ids = sheet.getRange(PEOPLE.FIRST_ROW, PEOPLE.ID, sheet.getLastRow() - PEOPLE.FIRST_ROW + 1, 1).getValues();
  const i = ids.findIndex(v => String(v[0]) === id);
  return i === -1 ? null : PEOPLE.FIRST_ROW + i;
}

/** The entry's ledger row, only if that row is still this entry's expense. */
function peopleLedger_(ss, sheet, row) {
  const ledger = Number(sheet.getRange(row, PEOPLE.LEDGER_ROW).getValue()) || null;
  if (!ledger) return null;
  const notes = ss.getSheetByName(SHEETS.FINANCE).getRange(ledger, FT.NOTES).getValue();
  return String(notes).indexOf('From People') !== -1 ? ledger : null;
}
