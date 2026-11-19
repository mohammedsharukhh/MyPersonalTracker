/**
 * MY INPUTS · Habits and health inputs you add yourself, beyond the five habit
 * columns and the built-in trackers on the Daily Tracker.
 *
 *   My Inputs — one row per input: a Habit (done / missed, like the five) or a
 *               Health number (pages read, glasses of tea, minutes meditated…)
 *               with a unit, an optional daily target and a step for the +/−.
 *   Input Log — one row per day and input: what you logged.
 *
 * Your habits that are on count toward the Daily Score, like the five: the
 * Daily Tracker's Habits Met adds them up from Input Log's Counts column, and
 * Settings' "Daily Score out of" counts them. Health numbers are tracked, not
 * scored. Turning an input off hides it and stops it counting; deleting it
 * deletes what was logged for it.
 */

const MY_INPUT = { ID: 1, SECTION: 2, NAME: 3, ICON: 4, UNIT: 5, TARGET: 6, STEP: 7, ON: 8, ORDER: 9, CREATED: 10, FIRST_ROW: 4 };
const INPUT_LOG = { DATE: 1, INPUT_ID: 2, VALUE: 3, UPDATED: 4, NAME: 5, COUNTS: 6, FIRST_ROW: 4 };
const INPUT_SECTIONS = ['Habit', 'Health'];

function ensureInputSheets_(ss) {
  ensureTab_(ss, SHEETS.MY_INPUTS, 'MY INPUTS  ·  Habits and health inputs you added',
    'Added from the app (Daily ▸ log a day ▸ +). Habits that are on count toward the Daily Score.',
    ['Input ID', 'Section', 'Name', 'Icon', 'Unit', 'Daily Target', 'Step', 'On', 'Order', 'Created'],
    [110, 80, 220, 90, 70, 90, 60, 50, 60, 90],
    sheet => {
      sheet.getRange(MY_INPUT.FIRST_ROW, MY_INPUT.CREATED, 500, 1).setNumberFormat('dd-mmm-yy');
      sheet.getRange(MY_INPUT.FIRST_ROW, MY_INPUT.SECTION, 500, 1).setDataValidation(
        SpreadsheetApp.newDataValidation().requireValueInList(INPUT_SECTIONS, true).setAllowInvalid(false).build());
      sheet.getRange(MY_INPUT.FIRST_ROW, MY_INPUT.ON, 500, 1).setDataValidation(
        SpreadsheetApp.newDataValidation().requireValueInList(['Y', 'N'], true).build()).setHorizontalAlignment('center');
    });
  ensureTab_(ss, SHEETS.INPUT_LOG, 'INPUT LOG  ·  What you logged for your own inputs',
    'One row per day and input, written by the app. Counts is 1 for a done habit that is on.',
    ['Date', 'Input ID', 'Value', 'Updated', 'Input', 'Counts'],
    [90, 110, 80, 90, 220, 60],
    sheet => {
      sheet.getRange(INPUT_LOG.FIRST_ROW, INPUT_LOG.DATE, 500, 1).setNumberFormat('dd-mmm-yy');
      sheet.getRange(INPUT_LOG.FIRST_ROW, INPUT_LOG.UPDATED, 500, 1).setNumberFormat('dd-mmm-yy');
      const inputs = "'" + SHEETS.MY_INPUTS + "'!";
      sheet.getRange(3, INPUT_LOG.NAME).setFormula('=ARRAYFORMULA(IF(ROW(B3:B)=3,"Input",IF(B3:B="","",' +
        'IFERROR(VLOOKUP(B3:B,{' + inputs + 'A:A,' + inputs + 'C:C},2,FALSE),"(deleted)"))))');
      sheet.getRange(3, INPUT_LOG.COUNTS).setFormula('=ARRAYFORMULA(IF(ROW(B3:B)=3,"Counts",IF(B3:B="","",' +
        'IF((C3:C="Y")*(COUNTIFS(' + inputs + 'A:A,B3:B,' + inputs + 'B:B,"Habit",' + inputs + 'H:H,"Y")>0),1,0))))');
      markCalculated(sheet.getRange(INPUT_LOG.FIRST_ROW, INPUT_LOG.NAME, 500, 2));
    });
}

/** The Daily Tracker formulas' pieces that read Input Log and My Inputs. */
function inputLogRef_(col) { return "'" + SHEETS.INPUT_LOG + "'!" + col + ':' + col; }
function customLoggedFormula_(r) { return 'COUNTIF(' + inputLogRef_('A') + ',$A' + r + ')'; }
function customMetFormula_(r) { return 'SUMIFS(' + inputLogRef_('F') + ',' + inputLogRef_('A') + ',$A' + r + ')'; }
function customHabitsOnFormula_() {
  const inputs = "'" + SHEETS.MY_INPUTS + "'!";
  return 'COUNTIFS(' + inputs + 'B:B,"Habit",' + inputs + 'H:H,"Y")';
}

function readMyInputs_(ss, from, to) {
  ensureInputSheets_(ss);
  const inputs = rowsOf_(ss, SHEETS.MY_INPUTS, MY_INPUT.FIRST_ROW, MY_INPUT.CREATED)
    .map(x => ({
      id: String(x.v[MY_INPUT.ID - 1] || 'row' + x.row),
      section: x.v[MY_INPUT.SECTION - 1] === 'Health' ? 'Health' : 'Habit',
      name: value_(x.v[MY_INPUT.NAME - 1]),
      icon: value_(x.v[MY_INPUT.ICON - 1]),
      unit: value_(x.v[MY_INPUT.UNIT - 1]),
      target: value_(x.v[MY_INPUT.TARGET - 1]),
      step: value_(x.v[MY_INPUT.STEP - 1]),
      on: x.v[MY_INPUT.ON - 1] !== 'N',
      order: value_(x.v[MY_INPUT.ORDER - 1])
    }))
    .filter(i => i.name);
  const log = rowsOf_(ss, SHEETS.INPUT_LOG, INPUT_LOG.FIRST_ROW, INPUT_LOG.VALUE)
    .map(x => ({ date: ymd_(x.v[INPUT_LOG.DATE - 1]), id: String(x.v[INPUT_LOG.INPUT_ID - 1] || ''), value: value_(x.v[INPUT_LOG.VALUE - 1]) }))
    .filter(l => l.date && l.id && l.value !== null && (!from || l.date >= from) && (!to || l.date <= to))
    .map(l => ({ date: l.date, id: l.id, value: String(l.value) }));
  return { inputs: inputs, log: log };
}

/** { id, section, name, icon, unit, target, step, on, order } */
function saveInput_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureInputSheets_(ss);
  const id = requiredId_(p.id);
  const name = String(p.name || '').trim();
  if (!name) throw new Error('Name it.');
  const section = p.section === 'Health' ? 'Health' : 'Habit';
  const sheet = ss.getSheetByName(SHEETS.MY_INPUTS);
  const numberOr = (v, fallback) => v === '' || v === null || v === undefined || isNaN(Number(v)) ? fallback : Number(v);
  let created = parseYmd_(ymd_(new Date()));
  if (sheet.getLastRow() >= MY_INPUT.FIRST_ROW) {
    const rows = sheet.getRange(MY_INPUT.FIRST_ROW, 1, sheet.getLastRow() - MY_INPUT.FIRST_ROW + 1, MY_INPUT.CREATED).getValues();
    const existing = rows.find(r => String(r[0]) === id);
    if (existing && existing[MY_INPUT.CREATED - 1] instanceof Date) created = existing[MY_INPUT.CREATED - 1];
  }
  upsertById_(sheet, MY_INPUT.FIRST_ROW, id, [
    id, section, name.slice(0, 60), String(p.icon || (section === 'Habit' ? 'checklist' : 'star')).slice(0, 30),
    section === 'Health' ? String(p.unit || '').slice(0, 12) : '',
    section === 'Health' ? numberOr(p.target, '') : '',
    section === 'Health' ? numberOr(p.step, 1) : '',
    p.on === false ? 'N' : 'Y', numberOr(p.order, ''), created
  ]);
}

/** Deletes the input and everything logged for it. */
function deleteInput_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const id = String(p.id || '');
  if (!id) return;
  deleteWhere_(ss, SHEETS.MY_INPUTS, MY_INPUT.FIRST_ROW, MY_INPUT.ID, id);
  deleteWhere_(ss, SHEETS.INPUT_LOG, INPUT_LOG.FIRST_ROW, INPUT_LOG.INPUT_ID, id);
}

/** { date, values: { inputId: "Y" | "N" | "12.5" | "" } } — "" clears that day's value. */
function saveInputValues_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureInputSheets_(ss);
  const date = String(p.date || '');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) throw new Error('Missing date.');
  const values = p.values || {};
  const ids = Object.keys(values);
  if (!ids.length) return;
  const sheet = ss.getSheetByName(SHEETS.INPUT_LOG);
  const last = sheet.getLastRow();
  const rows = last >= INPUT_LOG.FIRST_ROW
    ? sheet.getRange(INPUT_LOG.FIRST_ROW, 1, last - INPUT_LOG.FIRST_ROW + 1, 2).getValues() : [];
  const rowOf = {};
  rows.forEach((r, i) => { if (ymd_(r[0]) === date) rowOf[String(r[1])] = INPUT_LOG.FIRST_ROW + i; });
  const today = parseYmd_(ymd_(new Date()));
  const day = parseYmd_(date);
  const clear = [];
  const append = [];
  ids.forEach(id => {
    const text = values[id] === null || values[id] === undefined ? '' : String(values[id]).trim();
    const value = text === '' ? '' : (text === 'Y' || text === 'N' ? text : (isNaN(Number(text)) ? '' : Number(text)));
    const row = rowOf[id];
    if (value === '') { if (row) clear.push(row); return; }
    if (row) sheet.getRange(row, INPUT_LOG.VALUE, 1, 2).setValues([[value, today]]);
    else append.push([day, id, value, today]);
  });
  if (append.length) {
    const firstEmpty = Math.max(INPUT_LOG.FIRST_ROW, sheet.getLastRow() + 1);
    sheet.getRange(firstEmpty, 1, append.length, 4).setValues(append);
  }
  clear.sort((a, b) => b - a).forEach(row => sheet.deleteRow(row));
}
