/**
 * PLANNER · What's on each day of the week, and what you'll wear.
 *
 *   Planner  — one row per plan: day, optional time, what, where, done.
 *   Outfits  — one row per day: the clothes picked (Wardrobe IDs, with their
 *              names alongside so the tab reads on its own) and a note.
 *   Wardrobe — the clothes you own, by type and colour, so a day's outfit is
 *              a few taps and the app can say when you last wore something.
 *
 * Deleting something from the wardrobe leaves past outfits as they were.
 */

const PLAN = { ID: 1, DATE: 2, TIME: 3, TITLE: 4, PLACE: 5, NOTES: 6, DONE: 7, FIRST_ROW: 4 };
const OUTFIT = { ID: 1, DATE: 2, ITEM_IDS: 3, ITEMS: 4, NOTE: 5, FIRST_ROW: 4 };
const WARDROBE = { ID: 1, NAME: 2, TYPE: 3, COLOUR: 4, NOTES: 5, ADDED: 6, FIRST_ROW: 4 };

function ensurePlannerSheets_(ss) {
  ensureTab_(ss, SHEETS.PLANNER, 'PLANNER  ·  What is on each day',
    'Added from the app (Daily ▸ Planner). Time is optional, as typed (18:30).',
    ['Plan ID', 'Date', 'Time', 'Plan', 'Where', 'Notes', 'Done'],
    [110, 90, 60, 240, 160, 240, 60],
    sheet => {
      sheet.getRange(PLAN.FIRST_ROW, PLAN.DATE, 500, 1).setNumberFormat('ddd dd-mmm-yy');
      sheet.getRange(PLAN.FIRST_ROW, PLAN.TIME, 500, 1).setNumberFormat('@');
    });
  ensureTab_(ss, SHEETS.OUTFITS, 'OUTFITS  ·  What to wear each day',
    'One row per day. Clothes come from the Wardrobe tab.',
    ['Outfit ID', 'Date', 'Wardrobe IDs', 'Clothes', 'Note'],
    [110, 90, 160, 320, 240],
    sheet => sheet.getRange(OUTFIT.FIRST_ROW, OUTFIT.DATE, 500, 1).setNumberFormat('ddd dd-mmm-yy'));
  ensureTab_(ss, SHEETS.WARDROBE, 'WARDROBE  ·  The clothes you have',
    'Pick from these for each day\'s outfit.',
    ['Item ID', 'Item', 'Type', 'Colour', 'Notes', 'Added'],
    [110, 200, 110, 100, 240, 90],
    sheet => sheet.getRange(WARDROBE.FIRST_ROW, WARDROBE.ADDED, 500, 1).setNumberFormat('dd-mmm-yy'));
}

function readPlanner_(ss) {
  ensurePlannerSheets_(ss);
  const plans = rowsOf_(ss, SHEETS.PLANNER, PLAN.FIRST_ROW, PLAN.DONE)
    .map(x => ({
      id: String(x.v[PLAN.ID - 1] || 'row' + x.row),
      date: ymd_(x.v[PLAN.DATE - 1]),
      time: timeText_(x.v[PLAN.TIME - 1]),
      title: value_(x.v[PLAN.TITLE - 1]),
      place: value_(x.v[PLAN.PLACE - 1]),
      notes: value_(x.v[PLAN.NOTES - 1]),
      done: x.v[PLAN.DONE - 1] === 'Y'
    }))
    .filter(p => p.title && p.date);
  const outfits = rowsOf_(ss, SHEETS.OUTFITS, OUTFIT.FIRST_ROW, OUTFIT.NOTE)
    .map(x => ({
      id: String(x.v[OUTFIT.ID - 1] || 'row' + x.row),
      date: ymd_(x.v[OUTFIT.DATE - 1]),
      items: String(x.v[OUTFIT.ITEM_IDS - 1] || '').split(',').map(s => s.trim()).filter(String),
      names: value_(x.v[OUTFIT.ITEMS - 1]),
      note: value_(x.v[OUTFIT.NOTE - 1])
    }))
    .filter(o => o.date);
  const wardrobe = rowsOf_(ss, SHEETS.WARDROBE, WARDROBE.FIRST_ROW, WARDROBE.ADDED)
    .map(x => ({
      id: String(x.v[WARDROBE.ID - 1] || 'row' + x.row),
      name: value_(x.v[WARDROBE.NAME - 1]),
      type: value_(x.v[WARDROBE.TYPE - 1]),
      colour: value_(x.v[WARDROBE.COLOUR - 1]),
      notes: value_(x.v[WARDROBE.NOTES - 1]),
      added: ymd_(x.v[WARDROBE.ADDED - 1])
    }))
    .filter(w => w.name);
  return { plans: plans, outfits: outfits, wardrobe: wardrobe };
}

/** { id, date, time, title, place, notes, done } */
function saveDayPlan_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensurePlannerSheets_(ss);
  const id = requiredId_(p.id);
  const title = String(p.title || '').trim();
  if (!title) throw new Error('Say what the plan is.');
  if (!p.date) throw new Error('Pick a day.');
  upsertById_(ss.getSheetByName(SHEETS.PLANNER), PLAN.FIRST_ROW, id, [
    id, parseYmd_(p.date), String(p.time || '').trim(), title.slice(0, 200), String(p.place || '').trim().slice(0, 120),
    p.notes || '', p.done ? 'Y' : ''
  ]);
}

function deleteDayPlan_(p) {
  deleteWhere_(SpreadsheetApp.getActiveSpreadsheet(), SHEETS.PLANNER, PLAN.FIRST_ROW, PLAN.ID, String(p.id || ''));
}

/** { id, date, items: [wardrobe id], note } — one per day. */
function saveOutfit_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensurePlannerSheets_(ss);
  const id = requiredId_(p.id);
  if (!p.date) throw new Error('Pick a day.');
  const items = (Array.isArray(p.items) ? p.items : String(p.items || '').split(',')).map(s => String(s).trim()).filter(String);
  upsertById_(ss.getSheetByName(SHEETS.OUTFITS), OUTFIT.FIRST_ROW, id, [
    id, parseYmd_(p.date), items.join(','), clothesNames_(ss, items), p.note || ''
  ]);
}

function deleteOutfit_(p) {
  deleteWhere_(SpreadsheetApp.getActiveSpreadsheet(), SHEETS.OUTFITS, OUTFIT.FIRST_ROW, OUTFIT.ID, String(p.id || ''));
}

/** { id, name, type, colour, notes, added } */
function saveClothing_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensurePlannerSheets_(ss);
  const id = requiredId_(p.id);
  const name = String(p.name || '').trim();
  if (!name) throw new Error('Name the item.');
  upsertById_(ss.getSheetByName(SHEETS.WARDROBE), WARDROBE.FIRST_ROW, id, [
    id, name.slice(0, 120), String(p.type || 'Other'), String(p.colour || '').trim(), p.notes || '',
    optionalDate_(p.added) || parseYmd_(ymd_(new Date()))
  ]);
  refreshOutfitNames_(ss, id);
}

/** Removes it from the wardrobe; outfits it was part of keep its name. */
function deleteClothing_(p) {
  deleteWhere_(SpreadsheetApp.getActiveSpreadsheet(), SHEETS.WARDROBE, WARDROBE.FIRST_ROW, WARDROBE.ID, String(p.id || ''));
}

function clothesNames_(ss, ids) {
  const names = {};
  rowsOf_(ss, SHEETS.WARDROBE, WARDROBE.FIRST_ROW, WARDROBE.NAME).forEach(x => { names[String(x.v[0])] = String(x.v[1]); });
  return ids.map(id => names[id]).filter(Boolean).join(' + ');
}

/** After a rename, rewrites the Clothes column of outfits that include `id`. */
function refreshOutfitNames_(ss, id) {
  const sheet = ss.getSheetByName(SHEETS.OUTFITS);
  if (!sheet || sheet.getLastRow() < OUTFIT.FIRST_ROW) return;
  const count = sheet.getLastRow() - OUTFIT.FIRST_ROW + 1;
  const range = sheet.getRange(OUTFIT.FIRST_ROW, OUTFIT.ITEM_IDS, count, 2);
  const rows = range.getValues();
  let changed = false;
  rows.forEach(r => {
    const ids = String(r[0] || '').split(',').map(s => s.trim()).filter(String);
    if (ids.indexOf(id) === -1) return;
    const names = clothesNames_(ss, ids);
    if (names !== r[1]) { r[1] = names; changed = true; }
  });
  if (changed) range.setValues(rows);
}
