/**
 * JSON API for the MyPersonalTracker app.
 *
 * The app is a front end for THIS spreadsheet: it reads everything through
 * `snapshot` and writes the same inputs a person would type into the yellow
 * cells. Every figure the app shows is the sheet's own formula result, so the
 * two can never disagree.
 *
 * Deployed as a web app ("Execute as: me", "Anyone"), so every request must
 * carry the key created by `createApiKey`. Without it nothing is read or written.
 *
 *   GET  ?action=snapshot&key=…              → { ok, data: snapshot }
 *   POST { key, action, payload }            → { ok, data: snapshot }  (after the write)
 *
 * Write actions: saveDay, addExpense, updateExpense, deleteExpense,
 * saveSalary, saveSettings, saveCurrency, startNextPeriod, saveRecurring,
 * deleteRecurring, saveMeal, deleteMeal, savePlan, copyPlanWeek, saveMyFood,
 * deleteMyFood, savePeopleEntry, deletePeopleEntry, saveStudySubject, deleteStudySubject,
 * saveStudyTopic, deleteStudyTopic, saveStudySession, deleteStudySession, saveStudyGoal,
 * deleteStudyGoal. GET action=photo&id=… returns a meal photo. A write may carry `clientId`: the app retries writes it
 * queued offline, and a repeated clientId is acknowledged without writing twice.
 */

const API_KEY_PROPERTY = 'API_KEY';

/**
 * Run once from the Apps Script editor. The key appears in the execution log;
 * paste it into the app's Settings → Google Sheet. Re-running rotates the key.
 */
function createApiKey() {
  const key = Utilities.getUuid().replace(/-/g, '');
  PropertiesService.getScriptProperties().setProperty(API_KEY_PROPERTY, key);
  console.log('API key: ' + key);
  return key;
}

function doGet(e) {
  return handleApi_((e && e.parameter) || {}, {});
}

function doPost(e) {
  let body;
  try {
    body = JSON.parse((e && e.postData && e.postData.contents) || '{}');
  } catch (err) {
    return json_({ ok: false, error: 'Request body is not valid JSON.' });
  }
  return handleApi_(Object.assign({}, (e && e.parameter) || {}, body), body.payload || {});
}

function handleApi_(params, payload) {
  if (params.action === 'status') {
    try { return json_({ ok: true, data: setupStatus(params) }); } catch (err) { return json_({ ok: false, error: String(err && err.message || err) }); }
  }
  const expected = PropertiesService.getScriptProperties().getProperty(API_KEY_PROPERTY);
  if (!expected || params.key !== expected) {
    return json_({ ok: false, error: 'Unauthorized: check the API key in the app.' });
  }

  const writers = {
    saveDay: saveDay_,
    addExpense: addExpense_,
    updateExpense: updateExpense_,
    deleteExpense: deleteExpense_,
    saveSalary: saveSalary_,
    saveSettings: saveSettings_,
    saveCurrency: saveCurrency_,
    startNextPeriod: startNextPeriod_,
    saveRecurring: saveRecurring_,
    deleteRecurring: deleteRecurring_,
    saveMeal: saveMeal_,
    deleteMeal: deleteMeal_,
    savePlan: savePlan_,
    copyPlanWeek: copyPlanWeek_,
    saveMyFood: saveMyFood_,
    deleteMyFood: deleteMyFood_,
    savePeopleEntry: savePeopleEntry_,
    deletePeopleEntry: deletePeopleEntry_,
    saveStudySubject: saveStudySubject_,
    deleteStudySubject: deleteStudySubject_,
    saveStudyTopic: saveStudyTopic_,
    deleteStudyTopic: deleteStudyTopic_,
    saveStudySession: saveStudySession_,
    deleteStudySession: deleteStudySession_,
    saveStudyGoal: saveStudyGoal_,
    deleteStudyGoal: deleteStudyGoal_,
    saveBody: saveBody_,
    saveShoppingItem: saveShoppingItem_,
    deleteShoppingItem: deleteShoppingItem_,
    checkoutShopping: checkoutShopping_,
    deleteShoppingTrip: deleteShoppingTrip_,
    saveSetAside: saveSetAside_,
    deleteSetAside: deleteSetAside_,
    saveSavingsGoal: saveSavingsGoal_,
    deleteSavingsGoal: deleteSavingsGoal_,
    saveDayPlan: saveDayPlan_,
    deleteDayPlan: deleteDayPlan_,
    saveOutfit: saveOutfit_,
    deleteOutfit: deleteOutfit_,
    saveClothing: saveClothing_,
    deleteClothing: deleteClothing_,
    saveSteps: saveSteps_,
    saveHealth: saveHealth_,
    saveHabits: saveHabits_,
    saveRenewal: saveRenewal_,
    deleteRenewal: deleteRenewal_,
    saveLoan: saveLoan_,
    deleteLoan: deleteLoan_,
    saveLifeGoal: saveLifeGoal_,
    deleteLifeGoal: deleteLifeGoal_,
    saveGoalStep: saveGoalStep_,
    deleteGoalStep: deleteGoalStep_,
    saveInput: saveInput_,
    deleteInput: deleteInput_,
    saveInputValues: saveInputValues_,
    saveParentAsk: saveParentAsk_,
    deleteParentAsk: deleteParentAsk_,
    saveParentMedicine: saveParentMedicine_,
    deleteParentMedicine: deleteParentMedicine_,
    saveParentDate: saveParentDate_,
    deleteParentDate: deleteParentDate_,
    saveTakeHomeItem: saveTakeHomeItem_,
    deleteTakeHomeItem: deleteTakeHomeItem_,
    saveTakeHomeTrip: saveTakeHomeTrip_,
    saveParentReading: saveParentReading_,
    deleteParentReading: deleteParentReading_
  };

  try {
    if (params.action === 'resetAll') {
      const lock = LockService.getScriptLock();
      lock.waitLock(30000);
      try { return json_({ ok: true, data: resetAll_() }); } finally { lock.releaseLock(); }
    }
    // A rebuild is running: nothing to read or write until it finishes.
    const progress = setupProgress_();
    if (progress) {
      const failed = setupError_();
      if (failed) {
        return json_({ ok: false, error: 'Your sheet\'s setup stopped (' + failed + '). Open the sheet ▸ Life & Finance ▸ Continue setup, then pull down to refresh.' });
      }
      restartStalledSetup_();
      return json_({ ok: false, error: 'SETUP_IN_PROGRESS: ' + progress + ' steps done' });
    }
    if (params.action === 'export') {
      return json_({ ok: true, data: export_() });
    }
    if (params.action === 'yearReview') {
      return json_({ ok: true, data: yearReview_((payload && payload.year) || params.year) });
    }
    if (params.action === 'photo') {
      return json_({ ok: true, data: readPhoto_(params.id, params.size) });
    }
    if (params.action === 'snapshot') {
      postDueRecurring_();
      return json_({ ok: true, data: snapshot_() });
    }
    const write = writers[params.action];
    if (!write) return json_({ ok: false, error: 'Unknown action: ' + params.action });

    // One writer at a time: two quick saves must not pick the same free row.
    const lock = LockService.getScriptLock();
    lock.waitLock(30000);
    try {
      const clientId = payload && payload.clientId ? String(payload.clientId) : null;
      if (!clientId || !wasWritten_(clientId)) {
        write(payload);
        SpreadsheetApp.flush();
        if (clientId) rememberWrite_(clientId);
      }
    } finally {
      lock.releaseLock();
    }
    // reply: "ack" — the app shows the change already and reloads once its queue is empty,
    // so a save answers in a second or two instead of waiting for the whole snapshot.
    if (params.reply === 'ack' || (payload && payload.reply === 'ack')) return json_({ ok: true, ack: true });
    postDueRecurring_();
    // Returning the fresh snapshot saves the app a second round trip, and it
    // carries every recalculated formula (score, streak, budget, summaries).
    return json_({ ok: true, data: snapshot_() });
  } catch (err) {
    return json_({ ok: false, error: String((err && err.message) || err) });
  }
}

/** Adds any recurring expenses that have fallen due. Skipped if another request holds the lock. */
function postDueRecurring_() {
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(5000)) return;
  try {
    if (processRecurring_(SpreadsheetApp.getActiveSpreadsheet()) > 0) SpreadsheetApp.flush();
  } finally {
    lock.releaseLock();
  }
}

const CLIENT_WRITES_PROPERTY = 'CLIENT_WRITES';

function wasWritten_(clientId) {
  const ids = JSON.parse(PropertiesService.getScriptProperties().getProperty(CLIENT_WRITES_PROPERTY) || '[]');
  return ids.indexOf(clientId) !== -1;
}

/** Remembers the last 300 write ids — plenty for a phone's offline backlog. */
function rememberWrite_(clientId) {
  const props = PropertiesService.getScriptProperties();
  const ids = JSON.parse(props.getProperty(CLIENT_WRITES_PROPERTY) || '[]');
  ids.push(clientId);
  props.setProperty(CLIENT_WRITES_PROPERTY, JSON.stringify(ids.slice(-300)));
}

function json_(object) {
  return ContentService.createTextOutput(JSON.stringify(object))
    .setMimeType(ContentService.MimeType.JSON);
}

// ---- Dates ---------------------------------------------------------------
// Dates travel as "yyyy-MM-dd" in the SPREADSHEET's time zone, not the
// script's, so "today" in the app is the same day as today's row in the sheet.

function tz_() {
  return SpreadsheetApp.getActiveSpreadsheet().getSpreadsheetTimeZone();
}

function ymd_(value) {
  return value instanceof Date ? Utilities.formatDate(value, tz_(), 'yyyy-MM-dd') : null;
}

function parseYmd_(text) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(text || ''))) throw new Error('Date must be yyyy-MM-dd.');
  return Utilities.parseDate(text, tz_(), 'yyyy-MM-dd');
}

function value_(v) {
  return v === '' || v === null || v === undefined ? null : v;
}

// ---- Snapshot ------------------------------------------------------------

/** Set by the status check to time each part of the snapshot (milliseconds only, no data). */
let SNAPSHOT_TIMES_ = null;
function t_(name, fn) {
  if (!SNAPSHOT_TIMES_) return fn();
  const started = Date.now();
  const result = fn();
  SNAPSHOT_TIMES_[name] = Date.now() - started;
  return result;
}

function snapshot_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  // Sheets built before currencies were configurable get their Currency block here.
  t_('ensureCurrencySettings_', () => ensureCurrencySettings_(ss));
  t_('clearSalaryAutofill_', () => clearSalaryAutofill_(ss));
  // The Study targets live on Settings, read below with the rest.
  t_('ensureStudySettings_', () => ensureStudySettings_(ss));
  t_('ensureBodySettings_', () => ensureBodySettings_(ss));
  t_('ensureSplit_', () => ensureSplit_(ss));
  t_('ensureDailyFormulas_', () => ensureDailyFormulas_(ss));
  t_('ensureHabitSettings_', () => ensureHabitSettings_(ss));
  // Start date changed straight on Settings: move what was typed to its real dates.
  t_('realignIfStartMoved_', () => realignIfStartMoved_(ss));
  return {
    generatedAt: new Date().toISOString(),
    settings: t_('settings', () => readSettings_(ss)),
    lists: { categories: CATEGORIES, methods: PAYMENT_METHODS },
    dashboard: t_('dashboard', () => readDashboardCards_(ss)),
    // Habit success rates, spend by category, at a glance, money snapshot.
    dashboardTables: t_('dashboardTables', () => [1, 6, 11, 14].map(col => readBlock_(ss, SHEETS.DASHBOARD, 16, col))),
    // The Finance sheet's day-by-day budget control (columns J–O).
    budget: t_('budget', () => readBlock_(ss, SHEETS.FINANCE, null, BUDGET.DATE, BUDGET.LEFT - BUDGET.DATE + 1)),
    days: t_('days', () => readDays_(ss)),
    expenses: t_('expenses', () => readExpenses_(ss)),
    salary: t_('salary', () => readSalary_(ss)),
    weekly: t_('weekly', () => readTable_(ss, SHEETS.WEEKLY)),
    monthly: t_('monthly', () => readTable_(ss, SHEETS.MONTHLY)),
    recurring: t_('recurring', () => readRecurring_(ss)),
    food: t_('food', () => readFood_(ss)),
    people: t_('people', () => readPeople_(ss)),
    study: t_('study', () => readStudy_(ss)),
    shopping: t_('shopping', () => readShopping_(ss)),
    setAside: t_('setAside', () => readSetAside_(ss)),
    savingsGoals: t_('savingsGoals', () => readGoals_(ss)),
    planner: t_('planner', () => readPlanner_(ss)),
    lifeGoals: t_('lifeGoals', () => readLifeGoals_(ss)),
    renewals: t_('renewals', () => readRenewals_(ss)),
    loans: t_('loans', () => readLoans_(ss)),
    myInputs: t_('myInputs', () => readMyInputs_(ss, settingsStart_(ss), null)),
    parents: t_('parents', () => readParents_(ss)),
    parentMedicines: t_('parentMedicines', () => readParentMedicines_(ss)),
    parentDates: t_('parentDates', () => readParentDates_(ss)),
    takeHome: t_('takeHome', () => readTakeHome_(ss)),
    parentHealth: t_('parentHealth', () => readParentHealth_(ss)),
    archives: t_('archives', () => ss.getSheets().map(s => s.getName()).filter(n => /^Archive .* · Dashboard$/.test(n)))
      .map(n => n.replace(/^Archive /, '').replace(/ · Dashboard$/, ''))
  };
}

function readSettings_(ss) {
  const sheet = ss.getSheetByName(SHEETS.SETTINGS);
  const out = {};
  Object.keys(CFG).forEach(key => {
    const v = sheet.getRange(CFG[key]).getValue();
    out[key] = v instanceof Date ? ymd_(v) : value_(v);
  });
  out.RATE_FOR = rateFor_();
  return out;
}

/** The twelve cards: label on row r, value on r+1, caption on r+2 (see kpiCard). */
function readDashboardCards_(ss) {
  const sheet = ss.getSheetByName(SHEETS.DASHBOARD);
  const cards = [];
  [4, 8, 12].forEach(row => {
    const block = sheet.getRange(row, 1, 3, 16).getDisplayValues();
    [1, 5, 9, 13].forEach(col => {
      const label = block[0][col - 1];
      if (label) cards.push({ label: label, value: block[1][col - 1], caption: block[2][col - 1] });
    });
  });
  return cards;
}

/** Every day in the period, logged or not, with the sheet's score/status/streak. */
function readDays_(ss) {
  const sheet = ss.getSheetByName(SHEETS.DAILY);
  const count = getDayCount(ss);
  const values = sheet.getRange(DT.FIRST_ROW, DT.DATE, count, DT.STREAK).getValues();
  return values
    .filter(r => r[DT.DATE - 1] instanceof Date)
    .map(r => ({
      date: ymd_(r[DT.DATE - 1]),
      office: value_(r[DT.OFFICE - 1]),
      breakfast: value_(r[DT.BREAKFAST - 1]),
      lunch: value_(r[DT.LUNCH - 1]),
      protein: value_(r[DT.PROTEIN - 1]),
      workout: value_(r[DT.WORKOUT - 1]),
      water: value_(r[DT.WATER - 1]),
      sleep: value_(r[DT.SLEEP - 1]),
      steps: value_(r[DT.STEPS - 1]),
      weight: value_(r[DT.WEIGHT - 1]),
      screen: value_(r[DT.SCREEN - 1]),
      notes: value_(r[DT.NOTES - 1]),
      habitsMet: value_(r[DT.HABITS_MET - 1]),
      score: value_(r[DT.SCORE - 1]),
      status: value_(r[DT.STATUS - 1]),
      streak: value_(r[DT.STREAK - 1])
    }));
}

/** Ledger rows that hold something, with their sheet row so they can be edited. */
function readExpenses_(ss) {
  const sheet = ss.getSheetByName(SHEETS.FINANCE);
  const values = sheet.getRange(FT.FIRST_ROW, FT.DATE, FT.MAX_ROWS, FT.NOTES).getValues();
  const out = [];
  values.forEach((r, i) => {
    if (!(r[FT.DATE - 1] instanceof Date) && r[FT.QAR - 1] === '') return;
    out.push({
      row: FT.FIRST_ROW + i,
      date: ymd_(r[FT.DATE - 1]),
      description: value_(r[FT.DESC - 1]),
      category: value_(r[FT.CATEGORY - 1]),
      method: value_(r[FT.METHOD - 1]),
      qar: value_(r[FT.QAR - 1]),
      inr: value_(r[FT.INR - 1]),
      notes: value_(r[FT.NOTES - 1])
    });
  });
  return out;
}

function readSalary_(ss) {
  const sheet = ss.getSheetByName(SHEETS.SALARY);
  const range = sheet.getRange(SALARY_COLS.FIRST_ROW, 1, SALARY_MONTHS, SALARY_COLS.STATUS);
  const raw = range.getValues();
  const shown = range.getDisplayValues();
  return raw.map((r, i) => ({
    row: SALARY_COLS.FIRST_ROW + i,
    month: r[SALARY_COLS.MONTH - 1] instanceof Date
      ? Utilities.formatDate(r[SALARY_COLS.MONTH - 1], tz_(), 'yyyy-MM') : null,
    label: shown[i][SALARY_COLS.MONTH - 1],
    salary: value_(r[SALARY_COLS.SALARY - 1]),
    otherIncome: value_(r[SALARY_COLS.OTHER - 1]),
    totalIncome: value_(r[SALARY_COLS.TOTAL_INCOME - 1]),
    expenses: value_(r[SALARY_COLS.EXPENSES - 1]),
    remittanceQar: value_(r[SALARY_COLS.REMIT_QAR - 1]),
    remittanceInr: value_(r[SALARY_COLS.REMIT_INR - 1]),
    netSavings: value_(r[SALARY_COLS.NET_SAVINGS - 1]),
    savingsRate: value_(r[SALARY_COLS.RATE - 1]),
    goalSavings: value_(r[SALARY_COLS.GOAL - 1]),
    cumulative: value_(r[SALARY_COLS.CUMULATIVE - 1]),
    status: value_(r[SALARY_COLS.STATUS - 1])
  })).filter(m => m.month);
}

/**
 * A titled table on a sheet: title on `titleRow` (optional), headers on the
 * next row, data until the first blank in the table's first column. Width is
 * taken from the header row when not given.
 */
function readBlock_(ss, name, titleRow, col, width) {
  const sheet = ss.getSheetByName(name);
  const headerRow = titleRow ? titleRow + 1 : 4;
  const title = titleRow ? sheet.getRange(titleRow, col).getDisplayValue() : '';
  if (!width) {
    const row = sheet.getRange(headerRow, col, 1, 8).getDisplayValues()[0];
    width = row.findIndex(h => !h);
    if (width === -1) width = row.length;
  }
  const headers = sheet.getRange(headerRow, col, 1, width).getDisplayValues()[0]
    .map(h => h.replace(/\s*\n\s*/g, ' ').trim());
  const height = Math.max(1, sheet.getLastRow() - headerRow);
  const body = sheet.getRange(headerRow + 1, col, height, width).getDisplayValues();
  const rows = [];
  for (const r of body) {
    if (!r[0]) break;
    rows.push(r);
  }
  return { title: title, headers: headers, rows: rows };
}

/**
 * A summary sheet as the sheet displays it: headers from row 4, rows from 5
 * until the first blank one. Formatted text ("75.0%", "2,015.00") so the app
 * shows the same figures as the sheet without re-deriving formats.
 */
function readTable_(ss, name) {
  const sheet = ss.getSheetByName(name);
  const lastCol = sheet.getLastColumn();
  const headers = sheet.getRange(4, 1, 1, lastCol).getDisplayValues()[0]
    .map(h => h.replace(/\s*\n\s*/g, ' ').trim());
  let width = headers.length;
  while (width > 0 && !headers[width - 1]) width--;
  const lastRow = Math.max(5, sheet.getLastRow());
  const body = sheet.getRange(5, 1, lastRow - 4, width).getDisplayValues();
  const rows = [];
  for (const r of body) {
    if (!r[0]) break;
    rows.push(r);
  }
  return { headers: headers.slice(0, width), rows: rows };
}

// ---- Writes --------------------------------------------------------------

function yn_(v) {
  return v === 'Y' || v === 'N' ? v : '';
}

/** Writes one day's inputs (C:M) to the row with that date. */
function saveDay_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheet = ss.getSheetByName(SHEETS.DAILY);
  const target = ymd_(parseYmd_(p.date));
  const count = getDayCount(ss);
  const dates = sheet.getRange(DT.FIRST_ROW, DT.DATE, count, 1).getValues();
  const index = dates.findIndex(d => ymd_(d[0]) === target);
  if (index === -1) throw new Error(p.date + ' is outside the period set in Settings.');

  sheet.getRange(DT.FIRST_ROW + index, DT.OFFICE, 1, DT.NOTES - DT.OFFICE + 1).setValues([[
    yn_(p.office), yn_(p.breakfast), yn_(p.lunch), yn_(p.protein), yn_(p.workout),
    num(p.water), num(p.sleep), num(p.steps), num(p.weight), num(p.screen),
    p.notes || ''
  ]]);
}

function expenseRow_(p) {
  const amount = num(p.qar);
  if (!amount || amount <= 0) throw new Error('Enter an amount above zero.');
  if (p.category && CATEGORIES.indexOf(p.category) === -1) throw new Error('Unknown category: ' + p.category);
  if (p.method && PAYMENT_METHODS.indexOf(p.method) === -1) throw new Error('Unknown payment method: ' + p.method);
  return {
    left: [parseYmd_(p.date), p.description || '', p.category || '', p.method || ''],
    amount: amount,
    notes: p.notes || ''
  };
}

function writeExpense_(sheet, row, e) {
  sheet.getRange(row, FT.DATE, 1, 4).setValues([e.left]);
  sheet.getRange(row, FT.QAR).setValue(e.amount);
  sheet.getRange(row, FT.NOTES).setValue(e.notes);
}

function ledgerRow_(row) {
  const r = Number(row);
  if (!(r >= FT.FIRST_ROW && r < FT.FIRST_ROW + FT.MAX_ROWS)) throw new Error('Not a ledger row: ' + row);
  return r;
}

/** Appends to the first free ledger row, like the Add expense form. */
function addExpense_(p) {
  const e = expenseRow_(p);
  const sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SHEETS.FINANCE);
  const dates = sheet.getRange(FT.FIRST_ROW, FT.DATE, FT.MAX_ROWS, 1).getValues();
  const amounts = sheet.getRange(FT.FIRST_ROW, FT.QAR, FT.MAX_ROWS, 1).getValues();
  const free = dates.findIndex((d, i) => d[0] === '' && amounts[i][0] === '');
  if (free === -1) throw new Error('The ledger is full — no empty rows left.');
  writeExpense_(sheet, FT.FIRST_ROW + free, e);
  return FT.FIRST_ROW + free;
}

function updateExpense_(p) {
  const sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SHEETS.FINANCE);
  writeExpense_(sheet, ledgerRow_(p.row), expenseRow_(p));
}

/** Clears the typed cells only; the row's formulas (INR, %) stay in place. */
function deleteExpense_(p) {
  const sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SHEETS.FINANCE);
  const row = ledgerRow_(p.row);
  sheet.getRange(row, FT.DATE, 1, 4).clearContent();
  sheet.getRange(row, FT.QAR).clearContent();
  sheet.getRange(row, FT.NOTES).clearContent();
}

/** Salary, other income and remittance for one month (the yellow columns). */
function saveSalary_(p) {
  const sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SHEETS.SALARY);
  const months = sheet.getRange(SALARY_COLS.FIRST_ROW, SALARY_COLS.MONTH, SALARY_MONTHS, 1).getValues();
  const index = months.findIndex(m => m[0] instanceof Date &&
    Utilities.formatDate(m[0], tz_(), 'yyyy-MM') === p.month);
  if (index === -1) throw new Error('No row for month ' + p.month + '.');
  const row = SALARY_COLS.FIRST_ROW + index;
  if ([p.salary, p.otherIncome, p.remittanceQar].some(v => num(v) < 0)) throw new Error('Amounts can\'t be negative.');
  if (p.salary !== undefined) {
    // Left blank means no salary that month, never the Settings salary.
    sheet.getRange(row, SALARY_COLS.SALARY).setValue(num(p.salary));
  }
  if (p.otherIncome !== undefined) sheet.getRange(row, SALARY_COLS.OTHER).setValue(num(p.otherIncome));
  if (p.remittanceQar !== undefined) sheet.getRange(row, SALARY_COLS.REMIT_QAR).setValue(num(p.remittanceQar));
}

/**
 * Targets and limits only. The period dates are not editable here: changing
 * them means re-running "Rebuild tracker rows" in the sheet, which reshapes
 * every tracker and is not something to trigger from a phone.
 */
const EDITABLE_SETTINGS = ['RATE', 'DAILY_LIMIT', 'WATER_TARGET', 'SLEEP_TARGET', 'STEPS_TARGET',
  'SCREEN_LIMIT', 'SALARY', 'SAVINGS_GOAL', 'GREAT_DAY',
  'CALORIE_TARGET', 'PROTEIN_TARGET', 'CARB_TARGET', 'FAT_TARGET', 'FOOD_BUDGET',
  'STUDY_DAILY_TARGET', 'STUDY_WEEKLY_TARGET',
  'SPLIT_ESSENTIALS', 'SPLIT_EMERGENCY', 'SPLIT_SAVINGS', 'SPLIT_FAMILY', 'SPLIT_LIFESTYLE', 'EMERGENCY_GOAL',
  'LIMIT_FROM_SPLIT', 'CUSTOM_LIMIT'];

const NUTRITION_TARGETS = ['CALORIE_TARGET', 'PROTEIN_TARGET', 'CARB_TARGET', 'FAT_TARGET'];

function saveSettings_(p) {
  const sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SHEETS.SETTINGS);
  ensureBodySettings_(SpreadsheetApp.getActiveSpreadsheet());
  ensureSplitSettings_(SpreadsheetApp.getActiveSpreadsheet());
  ensureSpendLimit_(SpreadsheetApp.getActiveSpreadsheet());
  // A target typed by hand stops them following your weight: keep today's numbers as plain values.
  if (Object.keys(p).some(k => NUTRITION_TARGETS.indexOf(k) !== -1) && sheet.getRange(CFG.FOLLOW_WEIGHT).getValue() === 'Y') {
    NUTRITION_TARGETS.forEach(k => { const cell = sheet.getRange(CFG[k]); cell.setValue(cell.getValue()); });
    sheet.getRange(CFG.FOLLOW_WEIGHT).setValue('N');
  }
  Object.keys(p).forEach(key => {
    if (EDITABLE_SETTINGS.indexOf(key) === -1) throw new Error('Setting not editable from the app: ' + key);
    const value = num(p[key]);
    if (value === '') throw new Error('Enter a number for ' + key + '.');
    // The switch travels as 1 / 0 and is kept as Y / N.
    sheet.getRange(CFG[key]).setValue(key === 'LIMIT_FROM_SPLIT' ? (value ? 'Y' : 'N') : value);
    if (key === 'RATE') setRateFor_(currencyCodes_(SpreadsheetApp.getActiveSpreadsheet()));
  });
}
