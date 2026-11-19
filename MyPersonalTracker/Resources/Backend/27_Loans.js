/**
 * LOANS & EMIs · Car loans, personal loans, phone or furniture instalments.
 *
 * Each has a monthly instalment, the first payment date and how many months it
 * runs. The app works out how many are paid (by the date, unless you set it),
 * what's left and when it's paid off.
 *
 * A loan can have its own monthly bill (Recurring tab, "EMI · …"): the
 * instalment is then posted to the Finance ledger on its day each month and
 * counts in Essentials. The bill follows the loan — same name, amount and day
 * when the loan is edited — and stops once it's paid off, closed or deleted.
 */

const LOAN = {
  ID: 1, NAME: 2, LENDER: 3, EMI: 4, FIRST_PAYMENT: 5, MONTHS: 6, PAID: 7, PRINCIPAL: 8, RATE: 9, CLOSED: 10, NOTES: 11, BILL_ROW: 12, FIRST_ROW: 4
};

function ensureLoansSheet_(ss) {
  const main = currencyCodes_(ss).main;
  ensureTab_(ss, SHEETS.LOANS, 'LOANS & EMIs  ·  What you owe and when it ends',
    'Added from the app (Finance ▸ Budget). Paid months count up by date unless "Paid so far" is set.',
    ['Loan ID', 'Loan', 'Lender', 'Monthly instalment\n(' + main + ')', 'First payment', 'Months', 'Paid so far\n(months)',
      'Amount borrowed\n(' + main + ')', 'Interest %\n(a year)', 'Closed', 'Notes', 'Bill row'],
    [110, 200, 140, 110, 100, 70, 90, 110, 80, 60, 240, 70],
    sheet => {
      sheet.getRange(LOAN.FIRST_ROW, LOAN.FIRST_PAYMENT, 500, 1).setNumberFormat('dd-mmm-yy');
      [LOAN.EMI, LOAN.PRINCIPAL].forEach(c => sheet.getRange(LOAN.FIRST_ROW, c, 500, 1).setNumberFormat('#,##0.00'));
    });
  // Tabs made before loans had bills get the Bill row column.
  const sheet = ss.getSheetByName(SHEETS.LOANS);
  if (sheet && sheet.getRange(3, LOAN.BILL_ROW).getValue() === '') {
    ensureGrid(sheet, 60, LOAN.BILL_ROW);
    headerRow(sheet, 3, LOAN.BILL_ROW, ['Bill row']);
    sheet.setColumnWidth(LOAN.BILL_ROW, 70);
  }
}

function readLoans_(ss) {
  ensureLoansSheet_(ss);
  return rowsOf_(ss, SHEETS.LOANS, LOAN.FIRST_ROW, LOAN.BILL_ROW)
    .map(x => ({
      id: String(x.v[LOAN.ID - 1] || 'row' + x.row),
      name: value_(x.v[LOAN.NAME - 1]),
      lender: value_(x.v[LOAN.LENDER - 1]),
      emi: value_(x.v[LOAN.EMI - 1]),
      firstPayment: ymd_(x.v[LOAN.FIRST_PAYMENT - 1]),
      months: value_(x.v[LOAN.MONTHS - 1]),
      paid: value_(x.v[LOAN.PAID - 1]),
      principal: value_(x.v[LOAN.PRINCIPAL - 1]),
      rate: value_(x.v[LOAN.RATE - 1]),
      closed: x.v[LOAN.CLOSED - 1] === 'Y',
      notes: value_(x.v[LOAN.NOTES - 1]),
      bill: !!loanBillRow_(ss, x.v[LOAN.BILL_ROW - 1])
    }))
    .filter(l => l.name);
}

/** { id, name, lender, emi, firstPayment, months, paid, principal, rate, closed, notes } */
function saveLoan_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureLoansSheet_(ss);
  const id = requiredId_(p.id);
  const name = String(p.name || '').trim();
  if (!name) throw new Error('Name the loan.');
  const emi = num(p.emi);
  if (!(emi > 0)) throw new Error('Enter the monthly instalment.');
  const months = Math.round(num(p.months));
  if (!(months >= 1 && months <= 600)) throw new Error('Months must be 1–600.');
  if (!p.firstPayment) throw new Error('Pick the first payment date.');
  const paid = p.paid === '' || p.paid === null || p.paid === undefined ? '' : Math.max(0, Math.min(months, Math.round(num(p.paid))));
  const sheet = ss.getSheetByName(SHEETS.LOANS);
  const at = loanRowOf_(sheet, id);
  const billRow = at ? loanBillRow_(ss, sheet.getRange(at, LOAN.BILL_ROW).getValue()) : null;
  upsertById_(sheet, LOAN.FIRST_ROW, id, [
    id, name.slice(0, 120), String(p.lender || '').trim(), emi, parseYmd_(p.firstPayment), months, paid,
    num(p.principal), num(p.rate), p.closed ? 'Y' : '', p.notes || '', billRow || ''
  ]);
  // { bill: true/false } turns the monthly bill on or off; left out, it stays as it was.
  const want = p.bill === undefined || p.bill === null ? !!billRow : p.bill === true;
  syncLoanBill_(ss, loanRowOf_(sheet, id), want);
}

/** Deletes the loan; its monthly bill stops (its past instalments stay in the ledger). */
function deleteLoan_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheet = ss.getSheetByName(SHEETS.LOANS);
  const at = sheet ? loanRowOf_(sheet, String(p.id || '')) : null;
  if (at) {
    const bill = loanBillRow_(ss, sheet.getRange(at, LOAN.BILL_ROW).getValue());
    if (bill) ss.getSheetByName(SHEETS.RECURRING).getRange(bill, RECURRING.ACTIVE).setValue('N');
  }
  deleteWhere_(ss, SHEETS.LOANS, LOAN.FIRST_ROW, LOAN.ID, String(p.id || ''));
}

function loanRowOf_(sheet, id) {
  if (!sheet || sheet.getLastRow() < LOAN.FIRST_ROW) return null;
  const ids = sheet.getRange(LOAN.FIRST_ROW, LOAN.ID, sheet.getLastRow() - LOAN.FIRST_ROW + 1, 1).getValues();
  const i = ids.findIndex(v => String(v[0]) === id);
  return i === -1 ? null : LOAN.FIRST_ROW + i;
}

/** The Recurring row a loan's bill is on, if it's still that loan's bill. */
function loanBillRow_(ss, value) {
  const row = Number(value);
  const sheet = ss.getSheetByName(SHEETS.RECURRING);
  if (!sheet || !(row >= RECURRING.FIRST_ROW) || row > sheet.getLastRow()) return null;
  return String(sheet.getRange(row, RECURRING.NOTES).getValue()).indexOf('EMI') === 0 ? row : null;
}

/** The loan's last instalment date ("yyyy-MM-dd") and whether it's done (closed or past it). */
function loanEnd_(r) {
  const first = r[LOAN.FIRST_PAYMENT - 1];
  const months = Number(r[LOAN.MONTHS - 1]) || 0;
  if (!(first instanceof Date) || months < 1) return { last: null, done: r[LOAN.CLOSED - 1] === 'Y' };
  const firstYmd = ymd_(first);
  const lastMonth = shiftMonth_(firstYmd.slice(0, 7), months - 1);
  const day = Math.min(Number(firstYmd.slice(8, 10)), daysInMonth_(lastMonth));
  const last = lastMonth + '-' + String(day).padStart(2, '0');
  return { last: last, done: r[LOAN.CLOSED - 1] === 'Y' || ymd_(new Date()) > last };
}

/** Creates, updates or stops the loan's monthly bill to match the loan row. */
function syncLoanBill_(ss, loanRow, want) {
  if (!loanRow) return;
  const loans = ss.getSheetByName(SHEETS.LOANS);
  const r = loans.getRange(loanRow, 1, 1, LOAN.BILL_ROW).getValues()[0];
  const recurring = ensureRecurringSheet_(ss);
  let bill = loanBillRow_(ss, r[LOAN.BILL_ROW - 1]);
  const end = loanEnd_(r);
  if (!want || !end.last) {
    if (bill) recurring.getRange(bill, RECURRING.ACTIVE).setValue('N');
    return;
  }
  const firstYmd = ymd_(r[LOAN.FIRST_PAYMENT - 1]);
  const day = Number(firstYmd.slice(8, 10));
  const notes = 'EMI · ' + r[LOAN.MONTHS - 1] + ' months, last ' + end.last;
  const active = end.done ? 'N' : 'Y';
  if (bill) {
    recurring.getRange(bill, RECURRING.DESC).setValue(String(r[LOAN.NAME - 1]));
    recurring.getRange(bill, RECURRING.AMOUNT, 1, 3).setValues([[r[LOAN.EMI - 1], day, active]]);
    recurring.getRange(bill, RECURRING.NOTES).setValue(notes);
  } else {
    const descs = recurring.getRange(RECURRING.FIRST_ROW, 1, Math.max(1, recurring.getMaxRows() - RECURRING.FIRST_ROW + 1), RECURRING.AMOUNT).getValues();
    const free = descs.findIndex(x => x[0] === '' && x[RECURRING.AMOUNT - 1] === '');
    bill = free === -1 ? recurring.getLastRow() + 1 : RECURRING.FIRST_ROW + free;
    // Posted from its next instalment on: one already paid (or before this month) isn't added again.
    const thisMonth = ymd_(new Date()).slice(0, 7);
    const firstMonth = firstYmd.slice(0, 7);
    const last = firstMonth > thisMonth ? shiftMonth_(firstMonth, -1) : initialLastAdded_(day, false);
    recurring.getRange(bill, 1, 1, RECURRING.NOTES).setValues([[String(r[LOAN.NAME - 1]), 'Other', '', r[LOAN.EMI - 1], day, active, last, notes]]);
    loans.getRange(loanRow, LOAN.BILL_ROW).setValue(bill);
  }
  SpreadsheetApp.flush();
  processRecurring_(ss);
}

/** Bills of loans that have ended stop before anything is posted (runs ahead of each posting). */
function endFinishedLoanBills_(ss) {
  const loans = ss.getSheetByName(SHEETS.LOANS);
  const recurring = ss.getSheetByName(SHEETS.RECURRING);
  if (!loans || !recurring || loans.getLastRow() < LOAN.FIRST_ROW) return;
  loans.getRange(LOAN.FIRST_ROW, 1, loans.getLastRow() - LOAN.FIRST_ROW + 1, LOAN.BILL_ROW).getValues().forEach(r => {
    const bill = loanBillRow_(ss, r[LOAN.BILL_ROW - 1]);
    if (bill && loanEnd_(r).done && recurring.getRange(bill, RECURRING.ACTIVE).getValue() !== 'N') {
      recurring.getRange(bill, RECURRING.ACTIVE).setValue('N');
    }
  });
}
