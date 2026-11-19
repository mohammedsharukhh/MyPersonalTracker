/**
 * LIFE GOALS · What you're working toward, short term and long term.
 *
 *   Life Goals — one row per goal: Short term (the next few months) or Long term
 *                (a year or more), an area of life, why it matters, a date, and
 *                optionally the long-term goal a short-term one builds toward.
 *   Goal Steps — the checklist under each goal.
 *
 * Progress is worked out in the app: a goal's steps done, plus the short-term
 * goals under it; a goal with neither uses the Progress % typed here.
 * Deleting a goal deletes its steps; short-term goals under it stay, unlinked.
 */

const LIFE_GOAL = {
  ID: 1, TITLE: 2, HORIZON: 3, AREA: 4, WHY: 5, DUE: 6, PARENT_ID: 7, PARENT: 8, STATUS: 9,
  PROGRESS: 10, CREATED: 11, DONE_ON: 12, UPDATED: 13, NOTES: 14, FIRST_ROW: 4
};
const GOAL_STEP = { ID: 1, GOAL_ID: 2, GOAL: 3, STEP: 4, DONE: 5, DONE_ON: 6, ORDER: 7, DUE: 8, FIRST_ROW: 4 };
const LIFE_GOAL_STATUSES = ['Active', 'Reached', 'Dropped'];

function ensureLifeGoalSheets_(ss) {
  ensureTab_(ss, SHEETS.LIFE_GOALS, 'LIFE GOALS  ·  Short term and long term',
    'Added from the app (Daily ▸ Goals). Progress comes from the steps on Goal Steps.',
    ['Goal ID', 'Goal', 'Horizon', 'Area', 'Why it matters', 'By', 'Part of (ID)', 'Part of', 'Status',
      'Progress %', 'Created', 'Reached On', 'Updated', 'Notes'],
    [110, 240, 90, 110, 240, 90, 110, 200, 80, 80, 90, 90, 90, 220],
    sheet => {
      [LIFE_GOAL.DUE, LIFE_GOAL.CREATED, LIFE_GOAL.DONE_ON, LIFE_GOAL.UPDATED]
        .forEach(c => sheet.getRange(LIFE_GOAL.FIRST_ROW, c, 500, 1).setNumberFormat('dd-mmm-yy'));
      sheet.getRange(LIFE_GOAL.FIRST_ROW, LIFE_GOAL.PROGRESS, 500, 1).setNumberFormat('0%');
      sheet.getRange(LIFE_GOAL.FIRST_ROW, LIFE_GOAL.HORIZON, 500, 1).setDataValidation(
        SpreadsheetApp.newDataValidation().requireValueInList(['Short term', 'Long term'], true).setAllowInvalid(false).build());
      sheet.getRange(LIFE_GOAL.FIRST_ROW, LIFE_GOAL.STATUS, 500, 1).setDataValidation(
        SpreadsheetApp.newDataValidation().requireValueInList(LIFE_GOAL_STATUSES, true).setAllowInvalid(false).build());
    });
  ensureTab_(ss, SHEETS.GOAL_STEPS, 'GOAL STEPS  ·  The checklist under each goal',
    'Ticked off in the app.',
    ['Step ID', 'Goal ID', 'Goal', 'Step', 'Done', 'Done On', 'Order', 'By'],
    [110, 110, 200, 280, 60, 90, 60, 90],
    sheet => [GOAL_STEP.DONE_ON, GOAL_STEP.DUE].forEach(c => sheet.getRange(GOAL_STEP.FIRST_ROW, c, 500, 1).setNumberFormat('dd-mmm-yy')));
}

function readLifeGoals_(ss) {
  ensureLifeGoalSheets_(ss);
  const goals = rowsOf_(ss, SHEETS.LIFE_GOALS, LIFE_GOAL.FIRST_ROW, LIFE_GOAL.NOTES)
    .map(x => ({
      id: String(x.v[LIFE_GOAL.ID - 1] || 'row' + x.row),
      title: value_(x.v[LIFE_GOAL.TITLE - 1]),
      horizon: String(x.v[LIFE_GOAL.HORIZON - 1] || 'Short term'),
      area: value_(x.v[LIFE_GOAL.AREA - 1]),
      why: value_(x.v[LIFE_GOAL.WHY - 1]),
      due: ymd_(x.v[LIFE_GOAL.DUE - 1]),
      parentId: value_(x.v[LIFE_GOAL.PARENT_ID - 1]) === null ? null : String(x.v[LIFE_GOAL.PARENT_ID - 1]),
      status: String(x.v[LIFE_GOAL.STATUS - 1] || 'Active'),
      progress: value_(x.v[LIFE_GOAL.PROGRESS - 1]),
      created: ymd_(x.v[LIFE_GOAL.CREATED - 1]),
      doneOn: ymd_(x.v[LIFE_GOAL.DONE_ON - 1]),
      updated: ymd_(x.v[LIFE_GOAL.UPDATED - 1]),
      notes: value_(x.v[LIFE_GOAL.NOTES - 1])
    }))
    .filter(g => g.title);
  const steps = rowsOf_(ss, SHEETS.GOAL_STEPS, GOAL_STEP.FIRST_ROW, GOAL_STEP.DUE)
    .map(x => ({
      id: String(x.v[GOAL_STEP.ID - 1] || 'row' + x.row),
      goalId: String(x.v[GOAL_STEP.GOAL_ID - 1] || ''),
      step: value_(x.v[GOAL_STEP.STEP - 1]),
      done: x.v[GOAL_STEP.DONE - 1] === 'Y',
      doneOn: ymd_(x.v[GOAL_STEP.DONE_ON - 1]),
      order: value_(x.v[GOAL_STEP.ORDER - 1]),
      due: ymd_(x.v[GOAL_STEP.DUE - 1])
    }))
    .filter(s => s.step && s.goalId);
  return { goals: goals, steps: steps };
}

/** { id, title, horizon, area, why, due, parentId, status, progress, created, doneOn, notes } */
function saveLifeGoal_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureLifeGoalSheets_(ss);
  const id = requiredId_(p.id);
  const title = String(p.title || '').trim();
  if (!title) throw new Error('Name the goal.');
  const horizon = p.horizon === 'Long term' ? 'Long term' : 'Short term';
  const status = LIFE_GOAL_STATUSES.indexOf(p.status) === -1 ? 'Active' : p.status;
  const parentId = horizon === 'Short term' && p.parentId && p.parentId !== id ? String(p.parentId) : '';
  const progress = p.progress === '' || p.progress === null || p.progress === undefined ? '' : Math.max(0, Math.min(1, num(p.progress)));
  const today = parseYmd_(ymd_(new Date()));
  upsertById_(ss.getSheetByName(SHEETS.LIFE_GOALS), LIFE_GOAL.FIRST_ROW, id, [
    id, title.slice(0, 200), horizon, String(p.area || 'Personal'), p.why || '', optionalDate_(p.due),
    parentId, parentId ? lookupName_(ss, SHEETS.LIFE_GOALS, LIFE_GOAL.FIRST_ROW, LIFE_GOAL.ID, LIFE_GOAL.TITLE, parentId) : '',
    status, progress, optionalDate_(p.created) || today,
    status === 'Reached' ? (optionalDate_(p.doneOn) || today) : '', today, p.notes || ''
  ]);
  renameIn_(ss, SHEETS.LIFE_GOALS, LIFE_GOAL.FIRST_ROW, LIFE_GOAL.PARENT_ID, LIFE_GOAL.PARENT, id, title);
  renameIn_(ss, SHEETS.GOAL_STEPS, GOAL_STEP.FIRST_ROW, GOAL_STEP.GOAL_ID, GOAL_STEP.GOAL, id, title);
}

/** Deletes the goal and its steps; short-term goals under it stay, no longer linked. */
function deleteLifeGoal_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const id = String(p.id || '');
  if (!id) return;
  deleteWhere_(ss, SHEETS.LIFE_GOALS, LIFE_GOAL.FIRST_ROW, LIFE_GOAL.ID, id);
  deleteWhere_(ss, SHEETS.GOAL_STEPS, GOAL_STEP.FIRST_ROW, GOAL_STEP.GOAL_ID, id);
  const sheet = ss.getSheetByName(SHEETS.LIFE_GOALS);
  if (!sheet || sheet.getLastRow() < LIFE_GOAL.FIRST_ROW) return;
  const count = sheet.getLastRow() - LIFE_GOAL.FIRST_ROW + 1;
  const range = sheet.getRange(LIFE_GOAL.FIRST_ROW, LIFE_GOAL.PARENT_ID, count, 2);
  const links = range.getValues();
  if (links.some(r => String(r[0]) === id)) range.setValues(links.map(r => String(r[0]) === id ? ['', ''] : r));
}

/** { id, goalId, step, done, doneOn, order, due } — also marks the goal as updated today. */
function saveGoalStep_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureLifeGoalSheets_(ss);
  const id = requiredId_(p.id);
  const goalId = requiredId_(p.goalId);
  const step = String(p.step || '').trim();
  if (!step) throw new Error('Say what the step is.');
  const today = parseYmd_(ymd_(new Date()));
  upsertById_(ss.getSheetByName(SHEETS.GOAL_STEPS), GOAL_STEP.FIRST_ROW, id, [
    id, goalId, lookupName_(ss, SHEETS.LIFE_GOALS, LIFE_GOAL.FIRST_ROW, LIFE_GOAL.ID, LIFE_GOAL.TITLE, goalId),
    step.slice(0, 200), p.done ? 'Y' : '', p.done ? (optionalDate_(p.doneOn) || today) : '',
    p.order === undefined || p.order === null || p.order === '' ? '' : num(p.order), optionalDate_(p.due)
  ]);
  touchLifeGoal_(ss, goalId);
}

function deleteGoalStep_(p) {
  deleteWhere_(SpreadsheetApp.getActiveSpreadsheet(), SHEETS.GOAL_STEPS, GOAL_STEP.FIRST_ROW, GOAL_STEP.ID, String(p.id || ''));
}

function touchLifeGoal_(ss, goalId) {
  const sheet = ss.getSheetByName(SHEETS.LIFE_GOALS);
  if (!sheet || sheet.getLastRow() < LIFE_GOAL.FIRST_ROW) return;
  const ids = sheet.getRange(LIFE_GOAL.FIRST_ROW, LIFE_GOAL.ID, sheet.getLastRow() - LIFE_GOAL.FIRST_ROW + 1, 1).getValues();
  const i = ids.findIndex(v => String(v[0]) === goalId);
  if (i !== -1) sheet.getRange(LIFE_GOAL.FIRST_ROW + i, LIFE_GOAL.UPDATED).setValue(parseYmd_(ymd_(new Date())));
}
