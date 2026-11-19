/**
 * PARENTS · What Mom and Dad asked you to do, so it doesn't slip.
 *
 * One row per request: who asked, what, the day to do it, the time to be
 * reminded, how often it comes back (once, every day / week / month), and
 * whether to keep reminding every day until it's done. The app sends the
 * notifications; a repeating request moves to its next date when it's done.
 * Up to three photos each (a prescription, the product, the bill) go to a Drive
 * folder; what it cost can go to Finance when it's done.
 */

const PARENT = { ID: 1, TASK: 2, WHO: 3, DATE: 4, TIME: 5, REPEAT: 6, NAG: 7, ASKED: 8, DONE: 9, NOTES: 10,
  PHOTOS: 11, COST: 12, FINANCE: 13, FIRST_ROW: 4 };
const PARENT_HEADERS = ['Request ID', 'What they asked', 'Who', 'Do on', 'Remind at', 'Repeat', 'Keep reminding', 'Asked on', 'Done on', 'Notes',
  'Photos (Drive)', 'Cost', 'Add to Finance'];
const PARENT_PHOTO_FOLDER_PROPERTY = 'parentPhotoFolder';

function ensureParentsSheet_(ss) {
  ensureTab_(ss, SHEETS.PARENTS, 'PARENTS  ·  What Mom and Dad asked for',
    'Added from the app (the Parents tab). Reminders go out at the time given until each one is done.',
    PARENT_HEADERS,
    [110, 260, 70, 90, 70, 100, 80, 90, 90, 240, 160, 90, 90],
    sheet => {
      [PARENT.DATE, PARENT.ASKED, PARENT.DONE].forEach(c => sheet.getRange(PARENT.FIRST_ROW, c, 500, 1).setNumberFormat('ddd dd-mmm-yy'));
      sheet.getRange(PARENT.FIRST_ROW, PARENT.TIME, 500, 1).setNumberFormat('@');
      sheet.getRange(PARENT.FIRST_ROW, PARENT.COST, 500, 1).setNumberFormat('#,##0.00');
    });
  // Tabs made before photos and cost: add those columns' headers.
  const sheet = ss.getSheetByName(SHEETS.PARENTS);
  if (sheet.getMaxColumns() < PARENT_HEADERS.length) sheet.insertColumnsAfter(sheet.getMaxColumns(), PARENT_HEADERS.length - sheet.getMaxColumns());
  if (!sheet.getRange(3, PARENT.FINANCE).getValue()) {
    headerRow(sheet, 3, PARENT.PHOTOS, PARENT_HEADERS.slice(PARENT.PHOTOS - 1));
    sheet.getRange(PARENT.FIRST_ROW, PARENT.COST, 500, 1).setNumberFormat('#,##0.00');
  }
}

function readParents_(ss) {
  ensureParentsSheet_(ss);
  return rowsOf_(ss, SHEETS.PARENTS, PARENT.FIRST_ROW, PARENT.FINANCE)
    .map(x => ({
      id: String(x.v[PARENT.ID - 1] || 'row' + x.row),
      task: value_(x.v[PARENT.TASK - 1]),
      who: value_(x.v[PARENT.WHO - 1]),
      date: ymd_(x.v[PARENT.DATE - 1]),
      time: timeText_(x.v[PARENT.TIME - 1]),
      repeats: value_(x.v[PARENT.REPEAT - 1]),
      nag: x.v[PARENT.NAG - 1] === 'Y',
      asked: ymd_(x.v[PARENT.ASKED - 1]),
      done: ymd_(x.v[PARENT.DONE - 1]),
      notes: value_(x.v[PARENT.NOTES - 1]),
      photos: photoIds_(x.v[PARENT.PHOTOS - 1]),
      cost: value_(x.v[PARENT.COST - 1]),
      finance: x.v[PARENT.FINANCE - 1] === 'Y'
    }))
    .filter(r => r.task && r.date);
}

/**
 * { id, task, who, date, time, repeats, nag, asked, done, notes, cost, finance,
 *   photos: [Drive id | 'local:<key>'], newPhotos: { key: base64 JPEG } }.
 * New photos are saved to Drive; photos no longer listed are moved to the bin.
 */
function saveParentAsk_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureParentsSheet_(ss);
  const id = requiredId_(p.id);
  const task = String(p.task || '').trim();
  if (!task) throw new Error('Say what they asked for.');
  if (!p.date) throw new Error('Pick the day to do it.');
  const time = String(p.time || '').trim();
  if (time && !/^\d{1,2}:\d{2}$/.test(time)) throw new Error('Time must be HH:mm.');
  const sheet = ss.getSheetByName(SHEETS.PARENTS);
  const before = parentPhotos_(sheet, id);
  const added = p.newPhotos || {};
  const photos = (p.photos || []).map(ref => {
    ref = String(ref || '');
    if (ref.indexOf('local:') !== 0) return ref;
    const data = added[ref.slice(6)];
    return data ? savePhotoTo_(parentPhotoFolder_, data, p.date + ' ' + task.slice(0, 40)) : '';
  }).filter(String).slice(0, 3);
  before.filter(old => photos.indexOf(old) === -1).forEach(trashPhoto_);
  upsertById_(sheet, PARENT.FIRST_ROW, id, [
    id, task.slice(0, 200), String(p.who || 'Both'), parseYmd_(p.date), time, String(p.repeats || 'Once'),
    p.nag ? 'Y' : '', optionalDate_(p.asked), optionalDate_(p.done), p.notes || '',
    photos.join(','), num(p.cost), p.finance ? 'Y' : ''
  ]);
}

function deleteParentAsk_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheet = ss.getSheetByName(SHEETS.PARENTS);
  if (sheet) parentPhotos_(sheet, String(p.id || '')).forEach(trashPhoto_);
  deleteWhere_(ss, SHEETS.PARENTS, PARENT.FIRST_ROW, PARENT.ID, String(p.id || ''));
}

/** The Drive ids in a request's Photos cell. */
function parentPhotos_(sheet, id) {
  const last = sheet.getLastRow();
  if (!id || last < PARENT.FIRST_ROW) return [];
  const rows = sheet.getRange(PARENT.FIRST_ROW, 1, last - PARENT.FIRST_ROW + 1, PARENT.PHOTOS).getValues();
  const row = rows.find(r => String(r[0]) === id);
  return row ? photoIds_(row[PARENT.PHOTOS - 1]) : [];
}

function photoIds_(cell) {
  return String(cell || '').split(',').map(s => s.trim()).filter(String);
}

/** Request photos get their own Drive folder, next to the meal photos. */
function parentPhotoFolder_() {
  const props = PropertiesService.getDocumentProperties();
  const id = props.getProperty(PARENT_PHOTO_FOLDER_PROPERTY);
  if (id) {
    try { return DriveApp.getFolderById(id); } catch (e) { /* deleted: make a new one */ }
  }
  const folder = DriveApp.createFolder('Life & Finance · Mom & Dad photos');
  props.setProperty(PARENT_PHOTO_FOLDER_PROPERTY, folder.getId());
  return folder;
}

// ---- Medicines -------------------------------------------------------------------

/*
 * PARENT MEDICINES · What Mom and Dad take, and how many are left. The app counts
 * down from the last count (Counted on) at so many a day and warns before they run out.
 */
const PARENT_MED = { ID: 1, WHO: 2, NAME: 3, DOSE: 4, PER_DAY: 5, LEFT: 6, COUNTED: 7, WARN: 8, PACK: 9, COST: 10,
  FINANCE: 11, STOPPED: 12, NOTES: 13, FIRST_ROW: 4 };

function ensureParentMedicinesSheet_(ss) {
  ensureTab_(ss, SHEETS.PARENT_MEDICINES, 'PARENT MEDICINES  ·  What Mom and Dad take',
    'Added from the app (Parents ▸ Medicines). "Left" is the count on "Counted on"; the app works out the rest.',
    ['Medicine ID', 'Who', 'Medicine', 'Dose', 'A day', 'Left', 'Counted on', 'Warn (days before)', 'Pack of', 'Pack cost', 'Add to Finance', 'Stopped', 'Notes'],
    [110, 70, 200, 160, 60, 60, 90, 90, 70, 80, 90, 70, 220],
    sheet => {
      sheet.getRange(PARENT_MED.FIRST_ROW, PARENT_MED.COUNTED, 500, 1).setNumberFormat('dd-mmm-yy');
      sheet.getRange(PARENT_MED.FIRST_ROW, PARENT_MED.COST, 500, 1).setNumberFormat('#,##0.00');
    });
}

function readParentMedicines_(ss) {
  ensureParentMedicinesSheet_(ss);
  return rowsOf_(ss, SHEETS.PARENT_MEDICINES, PARENT_MED.FIRST_ROW, PARENT_MED.NOTES)
    .map(x => ({
      id: String(x.v[PARENT_MED.ID - 1] || 'row' + x.row),
      who: value_(x.v[PARENT_MED.WHO - 1]),
      name: value_(x.v[PARENT_MED.NAME - 1]),
      dose: value_(x.v[PARENT_MED.DOSE - 1]),
      perDay: value_(x.v[PARENT_MED.PER_DAY - 1]),
      left: value_(x.v[PARENT_MED.LEFT - 1]),
      counted: ymd_(x.v[PARENT_MED.COUNTED - 1]),
      warn: value_(x.v[PARENT_MED.WARN - 1]),
      pack: value_(x.v[PARENT_MED.PACK - 1]),
      cost: value_(x.v[PARENT_MED.COST - 1]),
      finance: x.v[PARENT_MED.FINANCE - 1] === 'Y',
      stopped: x.v[PARENT_MED.STOPPED - 1] === 'Y',
      notes: value_(x.v[PARENT_MED.NOTES - 1])
    }))
    .filter(m => m.name);
}

/** { id, who, name, dose, perDay, left, counted, warn, pack, cost, finance, stopped, notes } */
function saveParentMedicine_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureParentMedicinesSheet_(ss);
  const id = requiredId_(p.id);
  const name = String(p.name || '').trim();
  if (!name) throw new Error('Say which medicine.');
  upsertById_(ss.getSheetByName(SHEETS.PARENT_MEDICINES), PARENT_MED.FIRST_ROW, id, [
    id, String(p.who || 'Both'), name.slice(0, 120), String(p.dose || '').slice(0, 120), num(p.perDay), num(p.left),
    optionalDate_(p.counted), num(p.warn), num(p.pack), num(p.cost), p.finance ? 'Y' : '', p.stopped ? 'Y' : '', p.notes || ''
  ]);
}

function deleteParentMedicine_(p) {
  deleteWhere_(SpreadsheetApp.getActiveSpreadsheet(), SHEETS.PARENT_MEDICINES, PARENT_MED.FIRST_ROW, PARENT_MED.ID, String(p.id || ''));
}

// ---- Important dates --------------------------------------------------------------

/*
 * PARENT DATES · Birthdays, anniversaries and remembrance days, every year. The year
 * is optional (with it the app says "turns 60"); reminders go the days before, as listed.
 */
const PARENT_DATE = { ID: 1, TITLE: 2, WHO: 3, KIND: 4, DAY: 5, YEAR: 6, REMIND: 7, NOTES: 8, FIRST_ROW: 4 };

function ensureParentDatesSheet_(ss) {
  ensureTab_(ss, SHEETS.PARENT_DATES, 'PARENT DATES  ·  Birthdays, anniversaries, remembrance days',
    'Added from the app (Parents ▸ Dates). Day is month-day (10-24); the year is optional.',
    ['Date ID', 'What', 'Who', 'Kind', 'Day (MM-DD)', 'Year', 'Remind (days before)', 'Notes'],
    [110, 220, 70, 110, 90, 60, 120, 240],
    sheet => sheet.getRange(PARENT_DATE.FIRST_ROW, PARENT_DATE.DAY, 500, 1).setNumberFormat('@')
      .offset(0, PARENT_DATE.REMIND - PARENT_DATE.DAY).setNumberFormat('@'));
}

function readParentDates_(ss) {
  ensureParentDatesSheet_(ss);
  return rowsOf_(ss, SHEETS.PARENT_DATES, PARENT_DATE.FIRST_ROW, PARENT_DATE.NOTES)
    .map(x => ({
      id: String(x.v[PARENT_DATE.ID - 1] || 'row' + x.row),
      title: value_(x.v[PARENT_DATE.TITLE - 1]),
      who: value_(x.v[PARENT_DATE.WHO - 1]),
      kind: value_(x.v[PARENT_DATE.KIND - 1]),
      day: monthDay_(x.v[PARENT_DATE.DAY - 1]),
      year: value_(x.v[PARENT_DATE.YEAR - 1]),
      remind: String(x.v[PARENT_DATE.REMIND - 1] || '7,1,0'),
      notes: value_(x.v[PARENT_DATE.NOTES - 1])
    }))
    .filter(d => d.title && d.day);
}

/** "10-24" whether typed as text or turned into a date by the sheet. */
function monthDay_(v) {
  if (v instanceof Date) return Utilities.formatDate(v, tz_(), 'MM-dd');
  const m = /^(\d{1,2})-(\d{1,2})$/.exec(String(v || '').trim());
  return m ? ('0' + m[1]).slice(-2) + '-' + ('0' + m[2]).slice(-2) : null;
}

/** { id, title, who, kind, day: 'MM-dd', year, remind: '7,1,0', notes } */
function saveParentDate_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureParentDatesSheet_(ss);
  const id = requiredId_(p.id);
  const title = String(p.title || '').trim();
  if (!title) throw new Error('Say what the day is.');
  const day = monthDay_(p.day);
  if (!day) throw new Error('Day must be MM-dd.');
  const remind = String(p.remind || '7,1,0').split(',').map(s => parseInt(s, 10)).filter(n => n >= 0 && n <= 60).join(',');
  upsertById_(ss.getSheetByName(SHEETS.PARENT_DATES), PARENT_DATE.FIRST_ROW, id, [
    id, title.slice(0, 120), String(p.who || 'Both'), String(p.kind || 'Other'), day, num(p.year), remind || '7,1,0', p.notes || ''
  ]);
}

function deleteParentDate_(p) {
  deleteWhere_(SpreadsheetApp.getActiveSpreadsheet(), SHEETS.PARENT_DATES, PARENT_DATE.FIRST_ROW, PARENT_DATE.ID, String(p.id || ''));
}

// ---- Things to take home ------------------------------------------------------------

/*
 * TAKE HOME · What to bring on the next trip home ("Mom wants dates and saffron").
 * The trip's date is kept with the script (it isn't a row); the app counts down to it.
 */
const TAKE_HOME = { ID: 1, ITEM: 2, WHO: 3, GOT: 4, NOTES: 5, ADDED: 6, FIRST_ROW: 4 };
const TAKE_HOME_TRIP_PROPERTY = 'takeHomeTrip';

function ensureTakeHomeSheet_(ss) {
  ensureTab_(ss, SHEETS.TAKE_HOME, 'TAKE HOME  ·  What to bring on the next trip home',
    'Added from the app (Parents ▸ Trip). Tick Got it once it\'s bought.',
    ['Item ID', 'What to bring', 'For', 'Got it', 'Notes', 'Added'],
    [110, 240, 80, 60, 240, 90],
    sheet => sheet.getRange(TAKE_HOME.FIRST_ROW, TAKE_HOME.ADDED, 500, 1).setNumberFormat('dd-mmm-yy'));
}

function readTakeHome_(ss) {
  ensureTakeHomeSheet_(ss);
  const items = rowsOf_(ss, SHEETS.TAKE_HOME, TAKE_HOME.FIRST_ROW, TAKE_HOME.ADDED)
    .map(x => ({
      id: String(x.v[TAKE_HOME.ID - 1] || 'row' + x.row),
      item: value_(x.v[TAKE_HOME.ITEM - 1]),
      who: value_(x.v[TAKE_HOME.WHO - 1]),
      got: x.v[TAKE_HOME.GOT - 1] === 'Y',
      notes: value_(x.v[TAKE_HOME.NOTES - 1]),
      added: ymd_(x.v[TAKE_HOME.ADDED - 1])
    }))
    .filter(i => i.item);
  return { trip: PropertiesService.getDocumentProperties().getProperty(TAKE_HOME_TRIP_PROPERTY), items: items };
}

/** { id, item, who, got, notes, added } */
function saveTakeHomeItem_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureTakeHomeSheet_(ss);
  const id = requiredId_(p.id);
  const item = String(p.item || '').trim();
  if (!item) throw new Error('Say what to bring.');
  upsertById_(ss.getSheetByName(SHEETS.TAKE_HOME), TAKE_HOME.FIRST_ROW, id, [
    id, item.slice(0, 160), String(p.who || 'Both'), p.got ? 'Y' : '', p.notes || '', optionalDate_(p.added)
  ]);
}

function deleteTakeHomeItem_(p) {
  deleteWhere_(SpreadsheetApp.getActiveSpreadsheet(), SHEETS.TAKE_HOME, TAKE_HOME.FIRST_ROW, TAKE_HOME.ID, String(p.id || ''));
}

/** { date: 'yyyy-MM-dd' | '' } — the next trip home ('' = none planned). */
function saveTakeHomeTrip_(p) {
  const props = PropertiesService.getDocumentProperties();
  if (!p.date) { props.deleteProperty(TAKE_HOME_TRIP_PROPERTY); return; }
  props.setProperty(TAKE_HOME_TRIP_PROPERTY, ymd_(parseYmd_(p.date)));
}

// ---- Health notes ---------------------------------------------------------------------

/*
 * PARENT HEALTH · Readings they tell you on calls: blood pressure, sugar, weight,
 * pulse. Kept as told, to see the trend and share with their doctor.
 */
const PARENT_HEALTH = { ID: 1, DATE: 2, TIME: 3, WHO: 4, KIND: 5, VALUE: 6, VALUE2: 7, NOTE: 8, FIRST_ROW: 4 };

function ensureParentHealthSheet_(ss) {
  ensureTab_(ss, SHEETS.PARENT_HEALTH, 'PARENT HEALTH  ·  Readings Mom and Dad tell you',
    'Added from the app (Parents ▸ Health). Blood pressure is Value / Value 2 (systolic / diastolic).',
    ['Reading ID', 'Date', 'Time', 'Who', 'Reading', 'Value', 'Value 2', 'Note'],
    [110, 90, 60, 70, 140, 70, 70, 260],
    sheet => {
      sheet.getRange(PARENT_HEALTH.FIRST_ROW, PARENT_HEALTH.DATE, 500, 1).setNumberFormat('ddd dd-mmm-yy');
      sheet.getRange(PARENT_HEALTH.FIRST_ROW, PARENT_HEALTH.TIME, 500, 1).setNumberFormat('@');
    });
}

function readParentHealth_(ss) {
  ensureParentHealthSheet_(ss);
  return rowsOf_(ss, SHEETS.PARENT_HEALTH, PARENT_HEALTH.FIRST_ROW, PARENT_HEALTH.NOTE)
    .map(x => ({
      id: String(x.v[PARENT_HEALTH.ID - 1] || 'row' + x.row),
      date: ymd_(x.v[PARENT_HEALTH.DATE - 1]),
      time: timeText_(x.v[PARENT_HEALTH.TIME - 1]),
      who: value_(x.v[PARENT_HEALTH.WHO - 1]),
      kind: value_(x.v[PARENT_HEALTH.KIND - 1]),
      value: value_(x.v[PARENT_HEALTH.VALUE - 1]),
      value2: value_(x.v[PARENT_HEALTH.VALUE2 - 1]),
      note: value_(x.v[PARENT_HEALTH.NOTE - 1])
    }))
    .filter(r => r.date && r.kind && r.value !== null);
}

/** { id, date, time, who, kind, value, value2, note } */
function saveParentReading_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureParentHealthSheet_(ss);
  const id = requiredId_(p.id);
  if (!p.date) throw new Error('Pick the day.');
  if (num(p.value) === '') throw new Error('Type the reading.');
  upsertById_(ss.getSheetByName(SHEETS.PARENT_HEALTH), PARENT_HEALTH.FIRST_ROW, id, [
    id, parseYmd_(p.date), String(p.time || '').trim(), String(p.who || 'Dad'), String(p.kind || 'Blood pressure'),
    num(p.value), num(p.value2), String(p.note || '').slice(0, 300)
  ]);
}

function deleteParentReading_(p) {
  deleteWhere_(SpreadsheetApp.getActiveSpreadsheet(), SHEETS.PARENT_HEALTH, PARENT_HEALTH.FIRST_ROW, PARENT_HEALTH.ID, String(p.id || ''));
}
