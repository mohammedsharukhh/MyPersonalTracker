/**
 * RENEWALS · Things that expire: QID, passport, visa, driving licence, car
 * registration (Istimara), insurance, rent contract, subscriptions.
 *
 * The app reminds you before each one (30, 7 and 1 days by default) and, when
 * you renew, moves the date on. Only the expiry date and a label are kept —
 * no document numbers.
 */

const RENEWAL = { ID: 1, ITEM: 2, KIND: 3, EXPIRES: 4, REMIND: 5, COST: 6, LAST_RENEWED: 7, NOTES: 8, FIRST_ROW: 4 };

function ensureRenewalsSheet_(ss) {
  const main = currencyCodes_(ss).main;
  ensureTab_(ss, SHEETS.RENEWALS, 'RENEWALS  ·  Documents and contracts that expire',
    'Added from the app (More ▸ Renewals). Reminders go out the days before, as listed.',
    ['Renewal ID', 'Item', 'Kind', 'Expires', 'Remind (days before)', 'Renewal cost\n(' + main + ')', 'Last Renewed', 'Notes'],
    [110, 220, 130, 90, 120, 100, 100, 260],
    sheet => {
      [RENEWAL.EXPIRES, RENEWAL.LAST_RENEWED].forEach(c => sheet.getRange(RENEWAL.FIRST_ROW, c, 500, 1).setNumberFormat('dd-mmm-yy'));
      sheet.getRange(RENEWAL.FIRST_ROW, RENEWAL.REMIND, 500, 1).setNumberFormat('@');
      sheet.getRange(RENEWAL.FIRST_ROW, RENEWAL.COST, 500, 1).setNumberFormat('#,##0.00');
    });
}

function readRenewals_(ss) {
  ensureRenewalsSheet_(ss);
  return rowsOf_(ss, SHEETS.RENEWALS, RENEWAL.FIRST_ROW, RENEWAL.NOTES)
    .map(x => ({
      id: String(x.v[RENEWAL.ID - 1] || 'row' + x.row),
      item: value_(x.v[RENEWAL.ITEM - 1]),
      kind: value_(x.v[RENEWAL.KIND - 1]),
      expires: ymd_(x.v[RENEWAL.EXPIRES - 1]),
      remind: String(x.v[RENEWAL.REMIND - 1] || '30,7,1'),
      cost: value_(x.v[RENEWAL.COST - 1]),
      lastRenewed: ymd_(x.v[RENEWAL.LAST_RENEWED - 1]),
      notes: value_(x.v[RENEWAL.NOTES - 1])
    }))
    .filter(r => r.item && r.expires);
}

/** { id, item, kind, expires, remind, cost, lastRenewed, notes } */
function saveRenewal_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureRenewalsSheet_(ss);
  const id = requiredId_(p.id);
  const item = String(p.item || '').trim();
  if (!item) throw new Error('Say what it is.');
  if (!p.expires) throw new Error('Pick the expiry date.');
  const remind = String(p.remind || '30,7,1').split(',').map(s => parseInt(s, 10)).filter(n => n >= 0 && n <= 365).join(',');
  upsertById_(ss.getSheetByName(SHEETS.RENEWALS), RENEWAL.FIRST_ROW, id, [
    id, item.slice(0, 120), String(p.kind || 'Other'), parseYmd_(p.expires), remind || '30,7,1',
    num(p.cost), optionalDate_(p.lastRenewed), p.notes || ''
  ]);
}

function deleteRenewal_(p) {
  deleteWhere_(SpreadsheetApp.getActiveSpreadsheet(), SHEETS.RENEWALS, RENEWAL.FIRST_ROW, RENEWAL.ID, String(p.id || ''));
}
