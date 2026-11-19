/**
 * DATA TOOLS · Export everything, and reset from the app.
 *
 *   export   — every table as rows of text (what the sheet shows), so the
 *              app can save them as CSV files before a reset or uninstall.
 *   resetAll — wipes all logged data and rebuilds fresh from today, in the
 *              background (setup runs in steps; see 15_Setup.gs). While it
 *              runs, every other request answers SETUP_IN_PROGRESS.
 */

/** Which tables to export: [file name, sheet, header row, first column, width or null, how rows end]. */
const EXPORT_TABLES = [
  ['Daily Tracker', SHEETS.DAILY, DT.HEADER_ROW, 1, DT.STREAK, 'contiguous'],
  ['Expenses', SHEETS.FINANCE, FT.HEADER_ROW, 1, FT.NOTES, 'ledger'],
  ['Daily Budget', SHEETS.FINANCE, BUDGET.HEADER_ROW, BUDGET.DATE, BUDGET.LEFT - BUDGET.DATE + 1, 'contiguous'],
  ['Salary & Savings', SHEETS.SALARY, SALARY_COLS.HEADER_ROW, 1, SALARY_COLS.STATUS, 'contiguous'],
  ['Weekly Summary', SHEETS.WEEKLY, 4, 1, null, 'contiguous'],
  ['Monthly Summary', SHEETS.MONTHLY, 4, 1, null, 'contiguous'],
  ['Food Log', SHEETS.FOOD_LOG, 3, 1, null, 'all'],
  ['Meal Plan', SHEETS.MEAL_PLAN, 3, 1, null, 'all'],
  ['My Foods', SHEETS.MY_FOODS, 3, 1, null, 'all'],
  ['Monthly Bills', SHEETS.RECURRING, 3, 1, null, 'all'],
  ['People', SHEETS.PEOPLE, 3, 2, 5, 'all'],
  ['Study Subjects', SHEETS.STUDY_SUBJECTS, 3, 2, 8, 'all'],
  ['Study Topics', SHEETS.STUDY_TOPICS, 3, 3, 10, 'all'],
  ['Study Log', SHEETS.STUDY_LOG, 3, 2, 10, 'all'],
  ['Study Goals', SHEETS.STUDY_GOALS, 3, 2, 10, 'all'],
  ['Shopping', SHEETS.SHOPPING, 3, 2, 10, 'all'],
  ['Shopping Trips', SHEETS.SHOPPING_TRIPS, 3, 2, 7, 'all'],
  ['Set Aside', SHEETS.SET_ASIDE, 3, 2, 6, 'all'],
  ['Salary Split', SHEETS.SPLIT, 3, 1, null, 'contiguous'],
  ['Savings Goals', SHEETS.SAVINGS_GOALS, 3, 2, 7, 'all'],
  ['Planner', SHEETS.PLANNER, 3, 2, 6, 'all'],
  ['Outfits', SHEETS.OUTFITS, 3, 2, 4, 'all'],
  ['Wardrobe', SHEETS.WARDROBE, 3, 2, 5, 'all'],
  ['Life Goals', SHEETS.LIFE_GOALS, 3, 2, 13, 'all'],
  ['Goal Steps', SHEETS.GOAL_STEPS, 3, 3, 6, 'all'],
  ['Renewals', SHEETS.RENEWALS, 3, 2, 7, 'all'],
  ['Loans', SHEETS.LOANS, 3, 2, 10, 'all'],
  ['My Inputs', SHEETS.MY_INPUTS, 3, 2, 10, 'all'],
  ['Input Log', SHEETS.INPUT_LOG, 3, 2, 6, 'all'],
  ['Parents', SHEETS.PARENTS, 3, 2, 12, 'all'],
  ['Parent Medicines', SHEETS.PARENT_MEDICINES, 3, 2, 12, 'all'],
  ['Parent Dates', SHEETS.PARENT_DATES, 3, 2, 7, 'all'],
  ['Take Home', SHEETS.TAKE_HOME, 3, 2, 5, 'all'],
  ['Parent Health', SHEETS.PARENT_HEALTH, 3, 2, 7, 'all']
];

function export_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const files = [];
  const settings = readSettings_(ss);
  files.push({ name: 'Settings', rows: [['Setting', 'Value']].concat(Object.keys(settings).map(k => [k, settings[k] === null ? '' : String(settings[k])])) });
  EXPORT_TABLES.forEach(([name, sheetName, headerRow, col, width, mode]) => {
    const sheet = ss.getSheetByName(sheetName);
    if (!sheet || sheet.getLastRow() < headerRow) return;
    if (!width) {
      const header = sheet.getRange(headerRow, col, 1, sheet.getLastColumn() - col + 1).getDisplayValues()[0];
      width = header.findIndex(h => !h);
      if (width === -1) width = header.length;
    }
    if (width < 1) return;
    const last = mode === 'ledger' ? Math.min(sheet.getLastRow(), FT.FIRST_ROW + FT.MAX_ROWS - 1) : sheet.getLastRow();
    const values = sheet.getRange(headerRow, col, last - headerRow + 1, width).getDisplayValues();
    const header = values[0].map(h => h.replace(/\s*\n\s*/g, ' ').trim());
    const rows = [];
    for (let i = 1; i < values.length; i++) {
      const r = values[i];
      const blank = r.every(c => c === '');
      if (mode === 'contiguous' && r[0] === '') break;
      if (blank) continue;
      rows.push(r);
    }
    files.push({ name: name, rows: [header].concat(rows) });
  });
  return { generatedAt: new Date().toISOString(), spreadsheet: ss.getName(), files: files };
}

/** Starts a full reset in the background; the app shows progress until it's done. */
function resetAll_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const extra = [SHEETS.FOOD_LOG, SHEETS.MEAL_PLAN, SHEETS.MY_FOODS, SHEETS.RECURRING, SHEETS.PEOPLE,
    SHEETS.STUDY_SUBJECTS, SHEETS.STUDY_TOPICS, SHEETS.STUDY_LOG, SHEETS.STUDY_GOALS, SHEETS.SHOPPING, SHEETS.SHOPPING_TRIPS,
    SHEETS.SET_ASIDE, SHEETS.SPLIT, SHEETS.SAVINGS_GOALS, SHEETS.PLANNER, SHEETS.OUTFITS, SHEETS.WARDROBE,
    SHEETS.LIFE_GOALS, SHEETS.GOAL_STEPS, SHEETS.RENEWALS, SHEETS.LOANS, SHEETS.MY_INPUTS, SHEETS.INPUT_LOG, SHEETS.PARENTS, SHEETS.PARENT_MEDICINES, SHEETS.PARENT_DATES, SHEETS.TAKE_HOME, SHEETS.PARENT_HEALTH];
  ss.getSheets().forEach(sheet => {
    const name = sheet.getName();
    if ((extra.indexOf(name) !== -1 || /^Archive /.test(name)) && ss.getSheets().length > 1) ss.deleteSheet(sheet);
  });
  PropertiesService.getScriptProperties().deleteProperty(CLIENT_WRITES_PROPERTY);
  PropertiesService.getDocumentProperties().deleteProperty(TAKE_HOME_TRIP_PROPERTY);
  const props = PropertiesService.getDocumentProperties();
  props.setProperty(SETUP_STEP_PROPERTY, '0');
  props.deleteProperty(SETUP_ERROR_PROPERTY);
  props.setProperty(SETUP_HEARTBEAT_PROPERTY, String(Date.now()));
  removeSetupTriggers_();
  ScriptApp.newTrigger('continueSetup').timeBased().after(5 * 1000).create();
  return { resetting: true, steps: SETUP_STEPS.length };
}

/** While setup or a reset is running: "4 of 12", else null. */
function setupProgress_() {
  const step = PropertiesService.getDocumentProperties().getProperty(SETUP_STEP_PROPERTY);
  return step === null ? null : step + ' of ' + SETUP_STEPS.length;
}

/** Why setup stopped, if it did. */
function setupError_() {
  return PropertiesService.getDocumentProperties().getProperty(SETUP_ERROR_PROPERTY);
}

/**
 * Setup is meant to be running but nothing has happened for a while and no
 * timer is waiting (a trigger that never fired): start it again.
 */
function restartStalledSetup_() {
  const props = PropertiesService.getDocumentProperties();
  if (props.getProperty(SETUP_STEP_PROPERTY) === null || setupError_()) return false;
  const beat = Number(props.getProperty(SETUP_HEARTBEAT_PROPERTY) || 0);
  if (Date.now() - beat < 6 * 60 * 1000) return false;
  if (ScriptApp.getProjectTriggers().some(t => t.getHandlerFunction() === 'continueSetup')) return false;
  props.setProperty(SETUP_HEARTBEAT_PROPERTY, String(Date.now()));
  ScriptApp.newTrigger('continueSetup').timeBased().after(5 * 1000).create();
  return true;
}

/** No key needed: setup progress only, never any data. */
function setupStatus(params) {
  const settings = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SHEETS.SETTINGS);
  return {
    setup: setupProgress_(),
    error: setupError_(),
    status: settings ? String(settings.getRange(SETUP_STATUS_CELL).getValue() || '') : 'No Settings tab',
    triggers: ScriptApp.getProjectTriggers().filter(t => t.getHandlerFunction() === 'continueSetup').length,
    // Builds what the app downloads, but reports only whether that worked.
    snapshot: setupProgress_() ? 'skipped' : (() => {
      try { snapshot_(); return 'ok'; } catch (e) { return 'error: ' + ((e && e.message) || e) + (e && e.stack ? ' @ ' + String(e.stack).split('\n').slice(0, 4).join(' | ') : ''); }
    })(),
    // check=timing: how long each part of the snapshot takes, in ms (no data).
    timing: params && params.check === 'timing' && !setupProgress_() ? (() => {
      SNAPSHOT_TIMES_ = {};
      const started = Date.now();
      try { snapshot_(); } catch (e) { SNAPSHOT_TIMES_.error = String((e && e.message) || e); }
      const out = Object.assign({ total: Date.now() - started }, SNAPSHOT_TIMES_);
      SNAPSHOT_TIMES_ = null;
      return out;
    })() : undefined,
    // Same for the year in review (on request only: it reads every archive).
    yearReview: params && params.check === 'year' && !setupProgress_() ? (() => {
      try { const r = yearReview_(); return 'ok · ' + r.days.tracked + ' days, ' + r.money.byMonth.length + ' months of spending'; }
      catch (e) { return 'error: ' + ((e && e.message) || e) + (e && e.stack ? ' @ ' + String(e.stack).split('\n').slice(0, 4).join(' | ') : ''); }
    })() : undefined
  };
}
