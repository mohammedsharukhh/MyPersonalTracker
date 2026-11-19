/**
 * SETUP · Building a brand-new workbook in steps.
 *
 * Apps Script stops any single run after 6 minutes, and building every sheet
 * at once can take longer. So setup runs one sheet per step, remembers where
 * it got to, and — when it nears the limit — schedules itself to carry on a
 * minute later. Press Run once; it finishes on its own.
 *
 * Progress shows on Settings (row 42) and in Executions. When it says
 * "Setup complete", open Life & Finance ▸ Show app key.
 */

const SETUP_STEP_PROPERTY = 'SETUP_STEP';
/** Why setup stopped ("Study & body: …"), until the next try. */
const SETUP_ERROR_PROPERTY = 'SETUP_ERROR';
/** When setup last made progress (ms), so a stalled run can be restarted. */
const SETUP_HEARTBEAT_PROPERTY = 'SETUP_HEARTBEAT';
const SETUP_STATUS_CELL = 'A42';
const SETUP_BUDGET_MS = 3.5 * 60 * 1000;   // leave plenty of the 6 minutes spare

const SETUP_STEPS = [
  ['Settings', ss => buildSettings(ss)],
  ['Daily Tracker', ss => { buildDailyTracker(ss, getDayCount(ss)); rememberRowsStart_(ss); }],
  ['Finance Tracker', ss => buildFinanceTracker(ss, getDayCount(ss))],
  ['Salary & Savings', ss => buildSalarySavings(ss)],
  ['Weekly Summary', ss => buildWeeklySummary(ss, getDayCount(ss))],
  ['Monthly Summary', ss => buildMonthlySummary(ss, getDayCount(ss))],
  ['Dashboard', ss => buildDashboard(ss, getDayCount(ss))],
  ['Tidy up', ss => finishWorkbook_(ss)],
  ['Currency', ss => {
    const props = PropertiesService.getDocumentProperties();
    props.deleteProperty(CURRENCY_LABELS_PROPERTY);
    props.deleteProperty(CURRENCY_FORMULAS_PROPERTY);
    props.deleteProperty(RATE_FOR_PROPERTY);   // default QAR/INR rate goes with default QAR/INR
    ensureCurrencySettings_(ss);
  }],
  ['Food, shopping, monthly bills & people', ss => { ensureFoodSheets_(ss); ensureShoppingSheets_(ss); ensureRecurringSheet_(ss); ensurePeopleSheet_(ss); }],
  ['Study & body', ss => { ensureStudySheets_(ss); ensureBodySettings_(ss); }],
  ['Salary split & goals', ss => { ensureSplit_(ss); ensureGoals_(ss); }],
  ['Planner & wardrobe', ss => ensurePlannerSheets_(ss)],
  ['Life goals', ss => ensureLifeGoalSheets_(ss)],
  ['Renewals & loans', ss => { ensureRenewalsSheet_(ss); ensureLoansSheet_(ss); }],
  ['Parents', ss => { ensureParentsSheet_(ss); ensureParentMedicinesSheet_(ss); ensureParentDatesSheet_(ss); ensureTakeHomeSheet_(ss); ensureParentHealthSheet_(ss); }],
  ['Your habits', ss => {
    // Rewrites the score formulas against My Inputs / Input Log as they are now.
    PropertiesService.getDocumentProperties().deleteProperty(HABIT_FORMULAS_PROPERTY);
    ensureHabitSettings_(ss);
    applyHabitLabels_(ss);
  }],
  ['Meal photos (Drive)', ss => { try { authorizeFoodPhotos(); } catch (e) { console.log('Meal photos not connected yet: ' + e); } }],
  ['App key', ss => { if (!PropertiesService.getScriptProperties().getProperty(API_KEY_PROPERTY)) createApiKey(); }]
];

/**
 * ONE-TIME SETUP for a brand-new spreadsheet. Run from the Apps Script editor
 * (choose setupNewWorkbook ▸ Run ▸ allow access). Builds every sheet with a
 * fresh 30-day period from today and creates the app's key. Safe to run again:
 * it starts over and rebuilds from scratch.
 */
function setupNewWorkbook() {
  PropertiesService.getDocumentProperties().setProperty(SETUP_STEP_PROPERTY, '0');
  removeSetupTriggers_();
  runSetup_();
}

/** Carries setup on from where the last run stopped (also run by the timer). */
function continueSetup() {
  // One run at a time: a restart from the app must not overlap a running one.
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(1000)) return;
  try { runSetup_(); } finally { lock.releaseLock(); }
}

function runSetup_() {
  removeSetupTriggers_();
  const props = PropertiesService.getDocumentProperties();
  const started = Date.now();
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let step = Number(props.getProperty(SETUP_STEP_PROPERTY) || 0);
  props.deleteProperty(SETUP_ERROR_PROPERTY);
  props.setProperty(SETUP_HEARTBEAT_PROPERTY, String(Date.now()));

  while (step < SETUP_STEPS.length) {
    if (Date.now() - started > SETUP_BUDGET_MS) {
      ScriptApp.newTrigger('continueSetup').timeBased().after(60 * 1000).create();
      setupStatus_(ss, 'Setting up… ' + step + ' of ' + SETUP_STEPS.length + ' done — continuing automatically in a minute.');
      console.log('Paused after ' + step + ' steps; continuing in a minute.');
      return;
    }
    const [name, run] = SETUP_STEPS[step];
    console.log('Setup step ' + (step + 1) + '/' + SETUP_STEPS.length + ': ' + name);
    try {
      run(ss);
      SpreadsheetApp.flush();
    } catch (e) {
      // Say where and why, instead of looking "in progress" forever.
      const message = name + ': ' + ((e && e.message) || e);
      props.setProperty(SETUP_ERROR_PROPERTY, message);
      setupStatus_(ss, '⚠ Setup stopped at step ' + (step + 1) + ' (' + message + '). Life & Finance ▸ Continue setup to try again.');
      throw e;
    }
    step++;
    props.setProperty(SETUP_STEP_PROPERTY, String(step));
    props.setProperty(SETUP_HEARTBEAT_PROPERTY, String(Date.now()));
    setupStatus_(ss, 'Setting up… ' + step + ' of ' + SETUP_STEPS.length + ' done (' + name + ').');
  }

  props.deleteProperty(SETUP_STEP_PROPERTY);
  props.deleteProperty(SETUP_HEARTBEAT_PROPERTY);
  props.setProperty('SALARY_AUTOFILL_CLEARED', '1');
  setupStatus_(ss, '✓ Setup complete. Open Life & Finance ▸ Show app key and paste it into the app.');
  console.log('Setup complete. App key: ' + PropertiesService.getScriptProperties().getProperty(API_KEY_PROPERTY));
  notify('All set', 'Your workbook is ready with a fresh period starting today.\n\n' +
    'Life & Finance ▸ Show app key gives you the key to paste into the app.');
}

/** Tab order, the stray "Sheet1", and the Dashboard in front. */
function finishWorkbook_(ss) {
  [SHEETS.DASHBOARD, SHEETS.DAILY, SHEETS.FINANCE, SHEETS.SALARY,
   SHEETS.WEEKLY, SHEETS.MONTHLY, SHEETS.SETTINGS].forEach((name, i) => {
    const sheet = ss.getSheetByName(name);
    if (sheet) { ss.setActiveSheet(sheet); ss.moveActiveSheet(i + 1); }
  });
  const leftover = ss.getSheetByName('Sheet1');
  if (leftover && ss.getSheets().length > 1) ss.deleteSheet(leftover);
  ss.setActiveSheet(ss.getSheetByName(SHEETS.DASHBOARD));
}

function setupStatus_(ss, text) {
  const sheet = ss.getSheetByName(SHEETS.SETTINGS);
  if (!sheet) return;
  sheet.getRange(SETUP_STATUS_CELL).setValue(text).setFontWeight('bold').setFontColor(THEME.GOOD);
}

function removeSetupTriggers_() {
  ScriptApp.getProjectTriggers()
    .filter(t => t.getHandlerFunction() === 'continueSetup')
    .forEach(t => ScriptApp.deleteTrigger(t));
}

/** Menu: shows the key the app needs (creates one if there is none yet). */
function showAppKey() {
  const props = PropertiesService.getScriptProperties();
  const key = props.getProperty(API_KEY_PROPERTY) || createApiKey();
  SpreadsheetApp.getUi().alert('App key',
    key + '\n\nIn the app: More ▸ Settings & connection ▸ paste it ▸ Connect & sync.\n' +
    'Anyone with this key can read and change this sheet, so keep it private.',
    SpreadsheetApp.getUi().ButtonSet.OK);
}

/**
 * Wipes every logged thing — days, expenses, salary, meals, meal plan, your
 * foods, monthly bills, people, study and old archives — then rebuilds the workbook fresh
 * from today. Settings go back to defaults. The app key stays the same, so the
 * app keeps working. Meal photos stay in the Drive folder.
 *
 * From the editor: choose resetAllData ▸ Run. From the sheet: Life & Finance ▸ Reset all data.
 */
function resetAllData() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const extra = [SHEETS.FOOD_LOG, SHEETS.MEAL_PLAN, SHEETS.MY_FOODS, SHEETS.RECURRING, SHEETS.PEOPLE,
    SHEETS.STUDY_SUBJECTS, SHEETS.STUDY_TOPICS, SHEETS.STUDY_LOG, SHEETS.STUDY_GOALS, SHEETS.SHOPPING, SHEETS.SHOPPING_TRIPS,
    SHEETS.SET_ASIDE, SHEETS.SPLIT, SHEETS.SAVINGS_GOALS, SHEETS.PLANNER, SHEETS.OUTFITS, SHEETS.WARDROBE,
    SHEETS.LIFE_GOALS, SHEETS.GOAL_STEPS, SHEETS.RENEWALS, SHEETS.LOANS, SHEETS.MY_INPUTS, SHEETS.INPUT_LOG, SHEETS.PARENTS, SHEETS.PARENT_MEDICINES, SHEETS.PARENT_DATES, SHEETS.TAKE_HOME, SHEETS.PARENT_HEALTH];
  ss.getSheets().forEach(sheet => {
    const name = sheet.getName();
    if (extra.indexOf(name) !== -1 || /^Archive /.test(name)) {
      if (ss.getSheets().length > 1) ss.deleteSheet(sheet);
    }
  });
  PropertiesService.getScriptProperties().deleteProperty(CLIENT_WRITES_PROPERTY);
  PropertiesService.getDocumentProperties().deleteProperty(TAKE_HOME_TRIP_PROPERTY);
  setupNewWorkbook();
}

/** Menu version, with a confirmation first. */
function resetAllDataFromMenu() {
  const ui = SpreadsheetApp.getUi();
  const answer = ui.alert('Reset all data?',
    'This erases every day, expense, income entry, meal, meal plan, saved food, monthly bill, ' +
    'friends & family entry and everything you logged for study, ' +
    'and starts a fresh 30-day period from today. It cannot be undone.\n\n' +
    'Tip: File ▸ Make a copy first if you might want the old data.',
    ui.ButtonSet.YES_NO);
  if (answer === ui.Button.YES) resetAllData();
}
