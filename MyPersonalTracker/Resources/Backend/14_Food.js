/**
 * FOOD · What you eat, what it gives you, what it costs, and the week's plan.
 *
 *   Food Log   — one row per food eaten. Rows sharing a Meal ID are one meal
 *                (its photo and cost sit on the meal's first row).
 *   Meal Plan  — one row per planned food: date, meal, amount, nutrition, cost.
 *   My Foods   — your own foods and favourites, for quick adding.
 *   Settings   — Nutrition block: daily calorie/protein/carb/fat targets and a
 *                monthly food budget.
 *
 * A meal with a cost can also be added to the Finance ledger (Food & Dining
 * or Groceries); the ledger row is remembered so editing or deleting the meal
 * keeps the two in step. Meal photos go to a Drive folder.
 */

const FOOD_LOG = {
  MEAL_ID: 1, DATE: 2, TIME: 3, MEAL: 4, FOOD: 5, AMOUNT: 6, UNIT: 7,
  CALORIES: 8, PROTEIN: 9, CARBS: 10, FAT: 11, COST: 12, PHOTO: 13, LEDGER_ROW: 14, NOTES: 15,
  HEADER_ROW: 3, FIRST_ROW: 4
};
const MEAL_PLAN = {
  DATE: 1, MEAL: 2, FOOD: 3, AMOUNT: 4, UNIT: 5, CALORIES: 6, PROTEIN: 7, CARBS: 8, FAT: 9, COST: 10, EATEN: 11,
  HEADER_ROW: 3, FIRST_ROW: 4
};
const MY_FOODS = {
  NAME: 1, AMOUNT: 2, UNIT: 3, CALORIES: 4, PROTEIN: 5, CARBS: 6, FAT: 7, COST: 8, BARCODE: 9,
  HEADER_ROW: 3, FIRST_ROW: 4
};
const MEALS = ['Breakfast', 'Lunch', 'Dinner', 'Snack'];
const NUTRITION_SECTION_ROW = 32;
const PHOTO_FOLDER_PROPERTY = 'FOOD_PHOTO_FOLDER';
const PHOTO_ERROR_PROPERTY = 'FOOD_PHOTO_ERROR';

// ---- Sheets ------------------------------------------------------------------

/** Creates the food tabs and the Nutrition settings the first time they're needed. Never clears anything. */
function ensureFoodSheets_(ss) {
  ensureNutritionSettings_(ss);
  const main = currencyCodes_(ss).main;
  ensureTab_(ss, SHEETS.FOOD_LOG, 'FOOD LOG  ·  What you ate, its nutrition and cost',
    'Added from the app. Rows with the same Meal ID are one meal; its photo and cost are on the first row.',
    ['Meal ID', 'Date', 'Time', 'Meal', 'Food', 'Amount', 'Unit', 'Calories\n(kcal)', 'Protein\n(g)', 'Carbs\n(g)', 'Fat\n(g)',
     'Cost\n(' + main + ')', 'Photo', 'Ledger\nRow', 'Notes'],
    [110, 90, 60, 90, 200, 70, 70, 80, 80, 80, 80, 80, 90, 60, 200],
    sheet => {
      sheet.getRange(FOOD_LOG.FIRST_ROW, FOOD_LOG.DATE, 2000, 1).setNumberFormat('dd-mmm-yy');
      sheet.getRange(FOOD_LOG.FIRST_ROW, FOOD_LOG.TIME, 2000, 1).setNumberFormat('@');
      sheet.getRange(FOOD_LOG.FIRST_ROW, FOOD_LOG.CALORIES, 2000, 5).setNumberFormat('#,##0.0');
    });
  ensureTab_(ss, SHEETS.MEAL_PLAN, 'MEAL PLAN  ·  The week ahead',
    'Planned in the app (or here). Eaten = Y once it has been logged to the Food Log.',
    ['Date', 'Meal', 'Food', 'Amount', 'Unit', 'Calories\n(kcal)', 'Protein\n(g)', 'Carbs\n(g)', 'Fat\n(g)', 'Est. Cost\n(' + main + ')', 'Eaten'],
    [90, 90, 200, 70, 70, 80, 80, 80, 80, 90, 60],
    sheet => sheet.getRange(MEAL_PLAN.FIRST_ROW, MEAL_PLAN.DATE, 2000, 1).setNumberFormat('dd-mmm-yy'));
  ensureTab_(ss, SHEETS.MY_FOODS, 'MY FOODS  ·  Your own foods and favourites',
    'Nutrition is per the amount shown. Cost is optional — it fills in the meal cost when you add the food.',
    ['Food', 'Amount', 'Unit', 'Calories\n(kcal)', 'Protein\n(g)', 'Carbs\n(g)', 'Fat\n(g)', 'Cost\n(' + main + ')', 'Barcode'],
    [220, 70, 70, 80, 80, 80, 80, 80, 130],
    sheet => sheet.getRange(MY_FOODS.FIRST_ROW, MY_FOODS.BARCODE, 1000, 1).setNumberFormat('@'));
}

function ensureTab_(ss, name, title, subtitle, headers, widths, extra) {
  if (ss.getSheetByName(name)) return;
  const sheet = ss.insertSheet(name, ss.getNumSheets());
  ensureGrid(sheet, 60, headers.length);
  titleBar(sheet, title, subtitle, headers.length);
  headerRow(sheet, 3, 1, headers);
  widths.forEach((w, i) => sheet.setColumnWidth(i + 1, w));
  sheet.setFrozenRows(3);
  sheet.setHiddenGridlines(false);
  if (extra) extra(sheet);
}

/** Daily targets and the monthly food budget, below the Currency block on Settings. */
function ensureNutritionSettings_(ss) {
  const sheet = ss.getSheetByName(SHEETS.SETTINGS);
  if (!sheet) return;
  const r = NUTRITION_SECTION_ROW;
  if (sheet.getRange(r + 2, 1).getValue() === 'Calorie Target (kcal/day)') return;
  sectionLabel(sheet, r, 1, 3, 'NUTRITION');
  headerRow(sheet, r + 1, 1, ['Parameter', 'Value', 'What it drives']);
  sheet.getRange(r + 2, 1, 5, 3).setValues([
    ['Calorie Target (kcal/day)', 2200, 'Calorie ring on the Food tab'],
    ['Protein Target (g/day)', 120, 'Protein bar and protein-days count'],
    ['Carb Target (g/day)', 250, 'Carbs bar'],
    ['Fat Target (g/day)', 70, 'Fat bar'],
    ['Monthly Food Budget', 600, 'Food spending vs budget (Groceries + Food & Dining)']
  ]);
  const values = sheet.getRange(r + 2, 2, 5, 1);
  markInput(values);
  values.setFontWeight('bold').setHorizontalAlignment('center').setNumberFormat('#,##0');
  sheet.getRange(r + 2, 1, 5, 1).setFontWeight('bold');
  sheet.getRange(r + 2, 3, 5, 1).setFontSize(9).setFontColor(THEME.MUTED);
  styleBlock(sheet, r + 1, 1, 6, 3);
}

// ---- Reading -------------------------------------------------------------------

function readFood_(ss) {
  ensureFoodSheets_(ss);
  const today = new Date();
  const from = ymd_(new Date(today.getTime() - 62 * 86400000));
  const planFrom = ymd_(new Date(today.getTime() - 14 * 86400000));
  const planTo = ymd_(new Date(today.getTime() + 35 * 86400000));
  return {
    log: rowsOf_(ss, SHEETS.FOOD_LOG, FOOD_LOG.FIRST_ROW, FOOD_LOG.NOTES)
      .map(x => ({
        row: x.row,
        mealId: String(x.v[FOOD_LOG.MEAL_ID - 1] || ''),
        date: ymd_(x.v[FOOD_LOG.DATE - 1]),
        time: timeText_(x.v[FOOD_LOG.TIME - 1]),
        meal: value_(x.v[FOOD_LOG.MEAL - 1]),
        food: value_(x.v[FOOD_LOG.FOOD - 1]),
        amount: value_(x.v[FOOD_LOG.AMOUNT - 1]),
        unit: value_(x.v[FOOD_LOG.UNIT - 1]),
        calories: value_(x.v[FOOD_LOG.CALORIES - 1]),
        protein: value_(x.v[FOOD_LOG.PROTEIN - 1]),
        carbs: value_(x.v[FOOD_LOG.CARBS - 1]),
        fat: value_(x.v[FOOD_LOG.FAT - 1]),
        cost: value_(x.v[FOOD_LOG.COST - 1]),
        photo: value_(x.v[FOOD_LOG.PHOTO - 1]),
        ledgerRow: value_(x.v[FOOD_LOG.LEDGER_ROW - 1]),
        notes: value_(x.v[FOOD_LOG.NOTES - 1])
      }))
      .filter(f => f.date && f.date >= from),
    plan: rowsOf_(ss, SHEETS.MEAL_PLAN, MEAL_PLAN.FIRST_ROW, MEAL_PLAN.EATEN)
      .map(x => ({
        row: x.row,
        date: ymd_(x.v[MEAL_PLAN.DATE - 1]),
        meal: value_(x.v[MEAL_PLAN.MEAL - 1]),
        food: value_(x.v[MEAL_PLAN.FOOD - 1]),
        amount: value_(x.v[MEAL_PLAN.AMOUNT - 1]),
        unit: value_(x.v[MEAL_PLAN.UNIT - 1]),
        calories: value_(x.v[MEAL_PLAN.CALORIES - 1]),
        protein: value_(x.v[MEAL_PLAN.PROTEIN - 1]),
        carbs: value_(x.v[MEAL_PLAN.CARBS - 1]),
        fat: value_(x.v[MEAL_PLAN.FAT - 1]),
        cost: value_(x.v[MEAL_PLAN.COST - 1]),
        eaten: x.v[MEAL_PLAN.EATEN - 1] === 'Y'
      }))
      .filter(p => p.date && p.date >= planFrom && p.date <= planTo),
    myFoods: rowsOf_(ss, SHEETS.MY_FOODS, MY_FOODS.FIRST_ROW, MY_FOODS.BARCODE)
      .map(x => ({
        row: x.row,
        name: value_(x.v[MY_FOODS.NAME - 1]),
        amount: value_(x.v[MY_FOODS.AMOUNT - 1]),
        unit: value_(x.v[MY_FOODS.UNIT - 1]),
        calories: value_(x.v[MY_FOODS.CALORIES - 1]),
        protein: value_(x.v[MY_FOODS.PROTEIN - 1]),
        carbs: value_(x.v[MY_FOODS.CARBS - 1]),
        fat: value_(x.v[MY_FOODS.FAT - 1]),
        cost: value_(x.v[MY_FOODS.COST - 1]),
        barcode: value_(x.v[MY_FOODS.BARCODE - 1]) === null ? null : String(x.v[MY_FOODS.BARCODE - 1])
      }))
      .filter(f => f.name),
    photoError: PropertiesService.getDocumentProperties().getProperty(PHOTO_ERROR_PROPERTY)
  };
}

/** Non-empty data rows of a tab, with their sheet row numbers. */
function rowsOf_(ss, name, firstRow, width) {
  const sheet = ss.getSheetByName(name);
  if (!sheet || sheet.getLastRow() < firstRow) return [];
  return sheet.getRange(firstRow, 1, sheet.getLastRow() - firstRow + 1, width).getValues()
    .map((v, i) => ({ row: firstRow + i, v: v }))
    .filter(x => x.v.some(c => c !== '' && c !== null));
}

function timeText_(v) {
  if (v instanceof Date) return Utilities.formatDate(v, tz_(), 'HH:mm');
  return v === '' || v === null ? null : String(v);
}

// ---- Meals ---------------------------------------------------------------------

/**
 * Adds or replaces a meal: { mealId, date, time, meal, items: [{food, amount, unit,
 * calories, protein, carbs, fat}], cost, notes, photo: base64 JPEG | null,
 * keepPhoto: Drive id | null, finance: { add, category, method } }.
 */
function saveMeal_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureFoodSheets_(ss);
  const sheet = ss.getSheetByName(SHEETS.FOOD_LOG);
  const mealId = String(p.mealId || '').trim();
  if (!mealId) throw new Error('A meal needs an id.');
  if (MEALS.indexOf(p.meal) === -1) throw new Error('Meal must be Breakfast, Lunch, Dinner or Snack.');
  const date = parseYmd_(p.date);
  const items = (p.items || []).filter(i => i && String(i.food || '').trim());
  if (!items.length) throw new Error('Add at least one food to the meal.');

  // What the meal had before: its photo and its ledger expense.
  const before = mealRows_(sheet, mealId);
  const previousPhoto = before.length ? String(sheet.getRange(before[0], FOOD_LOG.PHOTO).getValue() || '') : '';
  const previousLedger = before.length ? stillLinked_(ss, Number(sheet.getRange(before[0], FOOD_LOG.LEDGER_ROW).getValue()) || null) : null;

  let photo = p.keepPhoto ? String(p.keepPhoto) : '';
  if (p.photo) {
    const saved = savePhoto_(p.photo, p.date + ' ' + p.meal + ' ' + mealId.slice(0, 8));
    if (saved) {
      photo = saved;
      if (previousPhoto && previousPhoto !== saved) trashPhoto_(previousPhoto);
    }
  } else if (!p.keepPhoto && previousPhoto && p.removePhoto) {
    trashPhoto_(previousPhoto);
  } else if (!p.keepPhoto && previousPhoto && !p.removePhoto) {
    photo = previousPhoto;
  }

  // Keep the Finance ledger in step with the meal's cost.
  const cost = num(p.cost);
  const finance = p.finance || {};
  let ledgerRow = null;
  if (finance.add && cost > 0) {
    const expense = {
      date: p.date,
      description: (p.meal + ': ' + items.map(i => i.food).join(', ')).slice(0, 120),
      category: CATEGORIES.indexOf(finance.category) !== -1 ? finance.category : 'Food & Dining',
      method: PAYMENT_METHODS.indexOf(finance.method) !== -1 ? finance.method : PAYMENT_METHODS[0],
      qar: cost,
      notes: 'From Food Log'
    };
    if (previousLedger) {
      updateExpense_(Object.assign({ row: previousLedger }, expense));
      ledgerRow = previousLedger;
    } else {
      ledgerRow = addExpense_(expense);
    }
  } else if (previousLedger) {
    deleteExpense_({ row: previousLedger });
  }

  deleteRows_(sheet, before);
  const rows = items.map((i, n) => [
    mealId, date, p.time || '', p.meal, String(i.food).slice(0, 120), num(i.amount), i.unit || '',
    num(i.calories), num(i.protein), num(i.carbs), num(i.fat),
    n === 0 ? (cost === '' ? '' : cost) : '', n === 0 ? photo : '', n === 0 && ledgerRow ? ledgerRow : '',
    n === 0 ? (p.notes || '') : ''
  ]);
  const start = Math.max(FOOD_LOG.FIRST_ROW, sheet.getLastRow() + 1);
  sheet.getRange(start, 1, rows.length, FOOD_LOG.NOTES).setValues(rows);
  sheet.getRange(start, FOOD_LOG.DATE, rows.length, 1).setNumberFormat('dd-mmm-yy');
  sheet.getRange(start, FOOD_LOG.TIME, rows.length, 1).setNumberFormat('@');
}

/**
 * The ledger row a meal points at, only if it is still that meal's expense —
 * if it was deleted in Finance and the row reused, the meal mustn't touch it.
 */
function stillLinked_(ss, row) {
  if (!row) return null;
  const notes = ss.getSheetByName(SHEETS.FINANCE).getRange(row, FT.NOTES).getValue();
  return String(notes).indexOf('From Food Log') !== -1 ? row : null;
}

function deleteMeal_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheet = ss.getSheetByName(SHEETS.FOOD_LOG);
  if (!sheet) return;
  const rows = mealRows_(sheet, String(p.mealId || ''));
  if (!rows.length) return;
  const photo = String(sheet.getRange(rows[0], FOOD_LOG.PHOTO).getValue() || '');
  const ledger = stillLinked_(ss, Number(sheet.getRange(rows[0], FOOD_LOG.LEDGER_ROW).getValue()) || null);
  if (ledger) deleteExpense_({ row: ledger });
  if (photo) trashPhoto_(photo);
  deleteRows_(sheet, rows);
}

function mealRows_(sheet, mealId) {
  if (!mealId || sheet.getLastRow() < FOOD_LOG.FIRST_ROW) return [];
  const ids = sheet.getRange(FOOD_LOG.FIRST_ROW, FOOD_LOG.MEAL_ID, sheet.getLastRow() - FOOD_LOG.FIRST_ROW + 1, 1).getValues();
  const out = [];
  ids.forEach((v, i) => { if (String(v[0]) === mealId) out.push(FOOD_LOG.FIRST_ROW + i); });
  return out;
}

/** Deletes rows bottom-up so earlier row numbers stay valid. */
function deleteRows_(sheet, rows) {
  rows.slice().sort((a, b) => b - a).forEach(r => sheet.deleteRow(r));
}

// ---- Photos (Google Drive) -------------------------------------------------------

/**
 * Run once from the Apps Script editor: it asks for permission to keep meal
 * photos in your Google Drive. Until then meals save without their photo.
 */
function authorizeFoodPhotos() {
  const folder = photoFolder_();
  PropertiesService.getDocumentProperties().deleteProperty(PHOTO_ERROR_PROPERTY);
  console.log('Meal photos will be saved to Drive folder: ' + folder.getName());
}

function photoFolder_() {
  const props = PropertiesService.getDocumentProperties();
  const id = props.getProperty(PHOTO_FOLDER_PROPERTY);
  if (id) {
    try { return DriveApp.getFolderById(id); } catch (e) { /* deleted: make a new one */ }
  }
  const folder = DriveApp.createFolder('Life & Finance · Meal photos');
  props.setProperty(PHOTO_FOLDER_PROPERTY, folder.getId());
  return folder;
}

/** Saves a base64 JPEG to the meal photo folder; returns its Drive id, or '' (and remembers why) on failure. */
function savePhoto_(base64, name) {
  return savePhotoTo_(null, base64, name);
}

/**
 * Saves a base64 JPEG to the folder `folderOf()` gives (null: the meal photo folder); returns its
 * Drive id, or '' (and remembers why) on failure — finding the folder needs Drive access too.
 */
function savePhotoTo_(folderOf, base64, name) {
  try {
    const blob = Utilities.newBlob(Utilities.base64Decode(String(base64)), 'image/jpeg', name + '.jpg');
    const file = (folderOf ? folderOf() : photoFolder_()).createFile(blob);
    PropertiesService.getDocumentProperties().deleteProperty(PHOTO_ERROR_PROPERTY);
    return file.getId();
  } catch (err) {
    PropertiesService.getDocumentProperties().setProperty(PHOTO_ERROR_PROPERTY,
      'Photos can\'t be saved to Google Drive yet. In the sheet\'s Apps Script, run authorizeFoodPhotos once and allow access.');
    return '';
  }
}

function trashPhoto_(id) {
  try { DriveApp.getFileById(id).setTrashed(true); } catch (e) { /* already gone or no access */ }
}

/** API (GET action=photo&id=…&size=thumb|full): the photo as base64, for the app to show and cache. */
function readPhoto_(id, size) {
  const file = DriveApp.getFileById(String(id));
  const blob = size === 'thumb' ? (file.getThumbnail() || file.getBlob()) : file.getBlob();
  return { id: String(id), mimeType: blob.getContentType(), base64: Utilities.base64Encode(blob.getBytes()) };
}

// ---- Meal plan ------------------------------------------------------------------

/** Replaces one date+meal of the plan: { date, meal, items: [...], eaten }. Empty items clears it. */
function savePlan_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureFoodSheets_(ss);
  const sheet = ss.getSheetByName(SHEETS.MEAL_PLAN);
  if (MEALS.indexOf(p.meal) === -1) throw new Error('Meal must be Breakfast, Lunch, Dinner or Snack.');
  const key = ymd_(parseYmd_(p.date));
  deleteRows_(sheet, planRows_(sheet, r => ymd_(r[MEAL_PLAN.DATE - 1]) === key && r[MEAL_PLAN.MEAL - 1] === p.meal));
  appendPlan_(sheet, (p.items || []).filter(i => i && String(i.food || '').trim())
    .map(i => planRow_(parseYmd_(p.date), p.meal, i, p.eaten)));
}

/** Copies a week of the plan: { from: Monday 'yyyy-MM-dd', to: Monday, replace: true }. */
function copyPlanWeek_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureFoodSheets_(ss);
  const sheet = ss.getSheetByName(SHEETS.MEAL_PLAN);
  const from = parseYmd_(p.from), to = parseYmd_(p.to);
  const shift = Math.round((to - from) / 86400000);
  const inWeek = (d, start) => d instanceof Date && d >= start && d < new Date(start.getTime() + 7 * 86400000);
  const source = planRows_(sheet, r => inWeek(r[MEAL_PLAN.DATE - 1], from), true);
  if (p.replace !== false) deleteRows_(sheet, planRows_(sheet, r => inWeek(r[MEAL_PLAN.DATE - 1], to)));
  appendPlan_(sheet, source.map(r => {
    const copy = r.slice();
    copy[MEAL_PLAN.DATE - 1] = new Date(r[MEAL_PLAN.DATE - 1].getTime() + shift * 86400000);
    copy[MEAL_PLAN.EATEN - 1] = '';
    return copy;
  }));
}

function planRow_(date, meal, i, eaten) {
  return [date, meal, String(i.food).slice(0, 120), num(i.amount), i.unit || '', num(i.calories), num(i.protein),
          num(i.carbs), num(i.fat), num(i.cost), eaten ? 'Y' : ''];
}

/** Row numbers (or, with `values`, the row values) of plan rows matching `test`. */
function planRows_(sheet, test, values) {
  if (sheet.getLastRow() < MEAL_PLAN.FIRST_ROW) return [];
  const data = sheet.getRange(MEAL_PLAN.FIRST_ROW, 1, sheet.getLastRow() - MEAL_PLAN.FIRST_ROW + 1, MEAL_PLAN.EATEN).getValues();
  const out = [];
  data.forEach((r, i) => { if (test(r)) out.push(values ? r : MEAL_PLAN.FIRST_ROW + i); });
  return out;
}

function appendPlan_(sheet, rows) {
  if (!rows.length) return;
  const start = Math.max(MEAL_PLAN.FIRST_ROW, sheet.getLastRow() + 1);
  sheet.getRange(start, 1, rows.length, MEAL_PLAN.EATEN).setValues(rows);
  sheet.getRange(start, MEAL_PLAN.DATE, rows.length, 1).setNumberFormat('dd-mmm-yy');
}

// ---- My foods -------------------------------------------------------------------

function saveMyFood_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureFoodSheets_(ss);
  const sheet = ss.getSheetByName(SHEETS.MY_FOODS);
  const name = String(p.name || '').trim();
  if (!name) throw new Error('Give the food a name.');
  const values = [[name, num(p.amount) || 1, p.unit || 'serving', num(p.calories), num(p.protein), num(p.carbs),
                   num(p.fat), num(p.cost), p.barcode ? String(p.barcode) : '']];
  let row = p.row ? Number(p.row) : null;
  if (!row) {
    // Same name or barcode: update rather than duplicate.
    const existing = rowsOf_(ss, SHEETS.MY_FOODS, MY_FOODS.FIRST_ROW, MY_FOODS.BARCODE)
      .find(x => String(x.v[0]).toLowerCase() === name.toLowerCase() || (p.barcode && String(x.v[MY_FOODS.BARCODE - 1]) === String(p.barcode)));
    row = existing ? existing.row : Math.max(MY_FOODS.FIRST_ROW, sheet.getLastRow() + 1);
  }
  sheet.getRange(row, 1, 1, MY_FOODS.BARCODE).setValues(values);
}

function deleteMyFood_(p) {
  const sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SHEETS.MY_FOODS);
  const row = Number(p.row);
  if (!sheet || !(row >= MY_FOODS.FIRST_ROW && row <= sheet.getLastRow())) throw new Error('Not a My Foods row: ' + p.row);
  sheet.deleteRow(row);
}
