/**
 * SAVINGS GOALS · Named things you're saving for: "Trip to Kerala", "New phone".
 *
 *   Savings Goals — one row per goal: target amount, optional date, done.
 *   Set Aside     — each entry can say which goal it's for (Goal ID / Goal
 *                   columns); a goal's progress is the sum of those entries.
 *
 * Deleting a goal keeps the money you put aside; it just stops counting toward it.
 */

const GOALS = { ID: 1, NAME: 2, TARGET: 3, DUE: 4, CREATED: 5, DONE: 6, DONE_ON: 7, NOTES: 8, FIRST_ROW: 4 };
const SET_ASIDE_GOAL_ID = 6;
const SET_ASIDE_GOAL = 7;

function ensureGoals_(ss) {
  const main = currencyCodes_(ss).main;
  ensureTab_(ss, SHEETS.SAVINGS_GOALS, 'SAVINGS GOALS  ·  What you are saving for',
    'Progress is the money put aside for each goal (Set Aside tab, Goal column).',
    ['Goal ID', 'Goal', 'Target\n(' + main + ')', 'By', 'Created', 'Done', 'Done On', 'Notes'],
    [110, 220, 100, 90, 90, 60, 90, 240],
    sheet => {
      [GOALS.DUE, GOALS.CREATED, GOALS.DONE_ON].forEach(c => sheet.getRange(GOALS.FIRST_ROW, c, 500, 1).setNumberFormat('dd-mmm-yy'));
      sheet.getRange(GOALS.FIRST_ROW, GOALS.TARGET, 500, 1).setNumberFormat('#,##0.00');
    });
  // Set Aside tabs made before goals get the two goal columns.
  const setAside = ss.getSheetByName(SHEETS.SET_ASIDE);
  if (setAside && setAside.getRange(3, SET_ASIDE_GOAL_ID).getValue() === '') {
    ensureGrid(setAside, 60, SET_ASIDE_GOAL);
    headerRow(setAside, 3, SET_ASIDE_GOAL_ID, ['Goal ID', 'Goal']);
    setAside.setColumnWidth(SET_ASIDE_GOAL_ID, 110);
    setAside.setColumnWidth(SET_ASIDE_GOAL, 200);
  }
}

function readGoals_(ss) {
  ensureGoals_(ss);
  return rowsOf_(ss, SHEETS.SAVINGS_GOALS, GOALS.FIRST_ROW, GOALS.NOTES)
    .map(x => ({
      id: String(x.v[GOALS.ID - 1] || 'row' + x.row),
      name: value_(x.v[GOALS.NAME - 1]),
      target: value_(x.v[GOALS.TARGET - 1]),
      due: ymd_(x.v[GOALS.DUE - 1]),
      created: ymd_(x.v[GOALS.CREATED - 1]),
      done: x.v[GOALS.DONE - 1] === 'Y',
      doneOn: ymd_(x.v[GOALS.DONE_ON - 1]),
      notes: value_(x.v[GOALS.NOTES - 1])
    }))
    .filter(g => g.name);
}

/** { id, name, target, due, created, done, doneOn, notes } */
function saveSavingsGoal_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureGoals_(ss);
  const id = requiredId_(p.id);
  const name = String(p.name || '').trim();
  if (!name) throw new Error('Name the goal.');
  const target = num(p.target);
  if (!(target > 0)) throw new Error('Set a target above zero.');
  upsertById_(ss.getSheetByName(SHEETS.SAVINGS_GOALS), GOALS.FIRST_ROW, id, [
    id, name.slice(0, 120), target, optionalDate_(p.due), optionalDate_(p.created) || parseYmd_(ymd_(new Date())),
    p.done ? 'Y' : '', p.done ? (optionalDate_(p.doneOn) || parseYmd_(ymd_(new Date()))) : '', p.notes || ''
  ]);
  renameIn_(ss, SHEETS.SET_ASIDE, SET_ASIDE.FIRST_ROW, SET_ASIDE_GOAL_ID, SET_ASIDE_GOAL, id, name);
}

/** Removes the goal; money put aside for it stays, no longer linked. */
function deleteSavingsGoal_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const id = String(p.id || '');
  if (!id) return;
  deleteWhere_(ss, SHEETS.SAVINGS_GOALS, GOALS.FIRST_ROW, GOALS.ID, id);
  const sheet = ss.getSheetByName(SHEETS.SET_ASIDE);
  if (!sheet || sheet.getLastRow() < SET_ASIDE.FIRST_ROW) return;
  const count = sheet.getLastRow() - SET_ASIDE.FIRST_ROW + 1;
  const links = sheet.getRange(SET_ASIDE.FIRST_ROW, SET_ASIDE_GOAL_ID, count, 2).getValues()
    .map(r => String(r[0]) === id ? ['', ''] : r);
  sheet.getRange(SET_ASIDE.FIRST_ROW, SET_ASIDE_GOAL_ID, count, 2).setValues(links);
}
