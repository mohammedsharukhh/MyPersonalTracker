/**
 * SHOPPING · The grocery list, and every shopping trip.
 *
 *   Shopping        — one row per thing to buy: item, quantity ("2 L"), aisle,
 *                     status (Need → In cart → Bought), price, and the trip it
 *                     was bought on.
 *   Shopping Trips  — one row per trip: date, shop, how many items, total paid,
 *                     how you paid, and the Finance ledger row it was added as.
 *
 * Finishing a trip marks its items Bought and (optionally) adds one Groceries
 * expense to Finance; deleting the trip takes that expense out again.
 */

const SHOP = {
  ID: 1, ITEM: 2, QUANTITY: 3, CATEGORY: 4, STATUS: 5, PRICE: 6, ADDED: 7, BOUGHT_ON: 8, TRIP_ID: 9, FROM_PLAN: 10, NOTES: 11,
  FIRST_ROW: 4
};
const TRIP = {
  ID: 1, DATE: 2, STORE: 3, ITEMS: 4, TOTAL: 5, METHOD: 6, LEDGER_ROW: 7, NOTES: 8,
  FIRST_ROW: 4
};
const SHOP_STATUSES = ['Need', 'In cart', 'Bought'];
const SHOP_CATEGORIES = ['Vegetables', 'Fruits', 'Dairy & Eggs', 'Meat & Fish', 'Grains & Bread', 'Spices & Oils',
  'Snacks & Drinks', 'Frozen', 'Household', 'Personal care', 'Other'];

function ensureShoppingSheets_(ss) {
  const main = currencyCodes_(ss).main;
  ensureTab_(ss, SHEETS.SHOPPING, 'SHOPPING  ·  What to buy, and what you bought',
    'Status: Need → In cart → Bought. Finishing a trip in the app marks its items Bought and can add one Groceries expense.',
    ['Item ID', 'Item', 'Quantity', 'Aisle', 'Status', 'Price\n(' + main + ')', 'Added', 'Bought On', 'Trip ID', 'From\nPlan', 'Notes'],
    [110, 200, 90, 120, 80, 80, 90, 90, 110, 60, 200],
    sheet => {
      [SHOP.ADDED, SHOP.BOUGHT_ON].forEach(col => sheet.getRange(SHOP.FIRST_ROW, col, 3000, 1).setNumberFormat('dd-mmm-yy'));
      sheet.getRange(SHOP.FIRST_ROW, SHOP.PRICE, 3000, 1).setNumberFormat('#,##0.00');
      sheet.getRange(SHOP.FIRST_ROW, SHOP.QUANTITY, 3000, 1).setNumberFormat('@');
      sheet.getRange(SHOP.FIRST_ROW, SHOP.STATUS, 3000, 1).setDataValidation(
        SpreadsheetApp.newDataValidation().requireValueInList(SHOP_STATUSES, true).build());
      sheet.getRange(SHOP.FIRST_ROW, SHOP.CATEGORY, 3000, 1).setDataValidation(
        SpreadsheetApp.newDataValidation().requireValueInList(SHOP_CATEGORIES, true).build());
    });
  ensureTab_(ss, SHEETS.SHOPPING_TRIPS, 'SHOPPING TRIPS  ·  Each time you shopped',
    'Added when you finish shopping in the app. Ledger Row is the Groceries expense in Finance, if one was added.',
    ['Trip ID', 'Date', 'Shop', 'Items', 'Total\n(' + main + ')', 'Paid With', 'Ledger\nRow', 'Notes'],
    [110, 90, 160, 60, 90, 130, 60, 220],
    sheet => {
      sheet.getRange(TRIP.FIRST_ROW, TRIP.DATE, 1000, 1).setNumberFormat('dd-mmm-yy');
      sheet.getRange(TRIP.FIRST_ROW, TRIP.TOTAL, 1000, 1).setNumberFormat('#,##0.00');
    });
}

function readShopping_(ss) {
  ensureShoppingSheets_(ss);
  const since = ymd_(new Date(Date.now() - 180 * 86400000));
  const items = rowsOf_(ss, SHEETS.SHOPPING, SHOP.FIRST_ROW, SHOP.NOTES)
    .map(x => ({
      id: String(x.v[SHOP.ID - 1] || 'row' + x.row),
      item: value_(x.v[SHOP.ITEM - 1]),
      quantity: x.v[SHOP.QUANTITY - 1] === '' ? null : String(x.v[SHOP.QUANTITY - 1]),
      category: value_(x.v[SHOP.CATEGORY - 1]) || 'Other',
      status: value_(x.v[SHOP.STATUS - 1]) || 'Need',
      price: value_(x.v[SHOP.PRICE - 1]),
      added: ymd_(x.v[SHOP.ADDED - 1]),
      boughtOn: ymd_(x.v[SHOP.BOUGHT_ON - 1]),
      tripId: value_(x.v[SHOP.TRIP_ID - 1]) === null ? null : String(x.v[SHOP.TRIP_ID - 1]),
      fromPlan: x.v[SHOP.FROM_PLAN - 1] === 'Y',
      notes: value_(x.v[SHOP.NOTES - 1])
    }))
    // Everything still to buy, and what was bought in the last six months.
    .filter(i => i.item && (i.status !== 'Bought' || !i.boughtOn || i.boughtOn >= since));
  const trips = rowsOf_(ss, SHEETS.SHOPPING_TRIPS, TRIP.FIRST_ROW, TRIP.NOTES)
    .map(x => ({
      id: String(x.v[TRIP.ID - 1] || 'row' + x.row),
      date: ymd_(x.v[TRIP.DATE - 1]),
      store: value_(x.v[TRIP.STORE - 1]),
      items: value_(x.v[TRIP.ITEMS - 1]),
      total: value_(x.v[TRIP.TOTAL - 1]),
      method: value_(x.v[TRIP.METHOD - 1]),
      ledgerRow: value_(x.v[TRIP.LEDGER_ROW - 1]),
      notes: value_(x.v[TRIP.NOTES - 1])
    }))
    .filter(t => t.date && t.date >= since);
  return { items: items, trips: trips };
}

/** { id, item, quantity, category, status, price, added, fromPlan, notes } — adds or replaces one item. */
function saveShoppingItem_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureShoppingSheets_(ss);
  const id = requiredId_(p.id);
  const name = String(p.item || '').trim();
  if (!name) throw new Error('Name the item.');
  const sheet = ss.getSheetByName(SHEETS.SHOPPING);
  const existing = findRowById_(sheet, SHOP.FIRST_ROW, id);
  const before = existing ? sheet.getRange(existing, 1, 1, SHOP.NOTES).getValues()[0] : null;
  const status = SHOP_STATUSES.indexOf(p.status) !== -1 ? p.status : 'Need';
  upsertById_(sheet, SHOP.FIRST_ROW, id, [
    id, name.slice(0, 120), String(p.quantity || '').slice(0, 40),
    SHOP_CATEGORIES.indexOf(p.category) !== -1 ? p.category : 'Other', status, num(p.price),
    optionalDate_(p.added) || parseYmd_(ymd_(new Date())),
    // A bought item keeps its trip; moving it back to the list clears it.
    status === 'Bought' && before ? before[SHOP.BOUGHT_ON - 1] : '',
    status === 'Bought' && before ? before[SHOP.TRIP_ID - 1] : '',
    p.fromPlan ? 'Y' : '', p.notes || ''
  ]);
}

function deleteShoppingItem_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  if (p.id) deleteWhere_(ss, SHEETS.SHOPPING, SHOP.FIRST_ROW, SHOP.ID, String(p.id));
}

/**
 * Finishes a shopping trip: { tripId, date, store, total, method, addToFinance,
 * notes, items: [{ id, price }] }. Those items become Bought on this trip.
 */
function checkoutShopping_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureShoppingSheets_(ss);
  const tripId = requiredId_(p.tripId);
  const date = parseYmd_(p.date);
  const lines = (p.items || []).filter(i => i && i.id);
  if (!lines.length) throw new Error('Tick at least one item into the cart.');
  const sheet = ss.getSheetByName(SHEETS.SHOPPING);
  lines.forEach(line => {
    const row = findRowById_(sheet, SHOP.FIRST_ROW, String(line.id));
    if (!row) return;
    sheet.getRange(row, SHOP.STATUS).setValue('Bought');
    if (num(line.price) !== '') sheet.getRange(row, SHOP.PRICE).setValue(num(line.price));
    sheet.getRange(row, SHOP.BOUGHT_ON).setValue(date).setNumberFormat('dd-mmm-yy');
    sheet.getRange(row, SHOP.TRIP_ID).setValue(tripId);
  });

  const total = num(p.total);
  const store = String(p.store || '').trim();
  let ledger = '';
  if (p.addToFinance && total > 0) {
    ledger = addExpense_({
      date: p.date,
      description: ('Groceries' + (store ? ' · ' + store : '') + ' (' + lines.length + ' item' + (lines.length === 1 ? '' : 's') + ')').slice(0, 120),
      category: 'Groceries',
      method: PAYMENT_METHODS.indexOf(p.method) !== -1 ? p.method : PAYMENT_METHODS[0],
      qar: total,
      notes: 'From Shopping'
    });
  }
  upsertById_(ss.getSheetByName(SHEETS.SHOPPING_TRIPS), TRIP.FIRST_ROW, tripId, [
    tripId, date, store.slice(0, 80), lines.length, total, p.method || '', ledger, p.notes || ''
  ]);
}

/** Removes a trip, its Finance expense, and the items bought on it. */
function deleteShoppingTrip_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const tripId = String(p.tripId || '');
  if (!tripId) return;
  const trips = ss.getSheetByName(SHEETS.SHOPPING_TRIPS);
  const row = trips ? findRowById_(trips, TRIP.FIRST_ROW, tripId) : null;
  if (row) {
    const ledger = Number(trips.getRange(row, TRIP.LEDGER_ROW).getValue()) || null;
    if (ledger && String(ss.getSheetByName(SHEETS.FINANCE).getRange(ledger, FT.NOTES).getValue()).indexOf('From Shopping') !== -1) {
      deleteExpense_({ row: ledger });
    }
    trips.deleteRow(row);
  }
  deleteWhere_(ss, SHEETS.SHOPPING, SHOP.FIRST_ROW, SHOP.TRIP_ID, tripId);
}

function findRowById_(sheet, firstRow, id) {
  if (!id || sheet.getLastRow() < firstRow) return null;
  const ids = sheet.getRange(firstRow, 1, sheet.getLastRow() - firstRow + 1, 1).getValues();
  const i = ids.findIndex(v => String(v[0]) === id);
  return i === -1 ? null : firstRow + i;
}
