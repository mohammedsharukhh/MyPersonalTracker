/**
 * YOUR HABITS · The five habit columns of the Daily Tracker are yours to name,
 * give an icon and turn off; water, sleep, steps, weight and screen time can be
 * turned off too. Kept in the HABITS block on Settings.
 *
 * The Daily Score counts only what's on: three habits plus water and sleep is
 * out of 5. Column headers, the success-rate tables, the Dashboard and the
 * Monthly Summary all show your names (formulas reading Settings), so renaming
 * a habit renames it everywhere. What's logged stays in its column.
 */

const HABITS_SECTION_ROW = 78;
const HABIT_DEFAULTS = [
  ['In Office by 9:00 AM', 'office'], ['Breakfast Done', 'breakfast'], ['Lunch Taken', 'lunch'],
  ['Protein in Lunch', 'protein'], ['Workout Done', 'workout']
];
const HABIT_COLS = ['C', 'D', 'E', 'F', 'G'];
const TRACKERS = [['WATER_ON', 'Water'], ['SLEEP_ON', 'Sleep'], ['STEPS_ON', 'Steps'], ['WEIGHT_ON', 'Weight'], ['SCREEN_ON', 'Screen time']];
const HABIT_FORMULAS_VERSION = '2';
const HABIT_FORMULAS_PROPERTY = 'HABIT_FORMULAS';

function ensureHabitSettings_(ss) {
  const sheet = ss.getSheetByName(SHEETS.SETTINGS);
  if (!sheet) return;
  // The Daily Tracker's formulas read the inputs you added, so their tabs come first.
  ensureInputSheets_(ss);
  const r = HABITS_SECTION_ROW;
  if (sheet.getMaxRows() >= r && sheet.getRange(r, 1).getValue() === 'HABITS') {
    upgradeHabitFormulas_(ss);
    return;
  }
  ensureGrid(sheet, r + 14, 4);
  sectionLabel(sheet, r, 1, 4, 'HABITS');
  headerRow(sheet, r + 1, 1, ['Habit', 'Name', 'On', 'Icon']);
  sheet.getRange(r + 2, 1, 5, 4).setValues(HABIT_DEFAULTS.map((h, i) => ['Habit ' + (i + 1), h[0], 'Y', h[1]]));
  sheet.getRange(r + 7, 1, TRACKERS.length, 4).setValues(TRACKERS.map(t => [t[1], '', 'Y', '']));
  sheet.getRange(r + 12, 1, 1, 4).setValues([['Daily Score out of', '', '', 'Habits on (yours too) + water and sleep, if on']]);
  sheet.getRange(r + 12, 2).setFormula(scoreOutOfFormula_());
  markInput(sheet.getRange(r + 2, 2, 5, 3));
  markInput(sheet.getRange(r + 7, 3, TRACKERS.length, 1));
  const yn = SpreadsheetApp.newDataValidation().requireValueInList(['Y', 'N'], true).build();
  sheet.getRange(r + 2, 3, 5 + TRACKERS.length, 1).setDataValidation(yn).setHorizontalAlignment('center').setFontWeight('bold');
  sheet.getRange(r + 7, 3, TRACKERS.length, 1).setDataValidation(yn);
  sheet.getRange(r + 2, 1, 11, 1).setFontWeight('bold');
  styleBlock(sheet, r + 1, 1, 12, 4);
  SpreadsheetApp.flush();
  upgradeHabitFormulas_(ss);
}

/** Score formulas that count only what's on, and labels that follow your names. */
function upgradeHabitFormulas_(ss) {
  const props = PropertiesService.getDocumentProperties();
  if (props.getProperty(HABIT_FORMULAS_PROPERTY) === HABIT_FORMULAS_VERSION) return;
  ensureInputSheets_(ss);
  const settings = ss.getSheetByName(SHEETS.SETTINGS);
  if (settings && settings.getRange(HABITS_SECTION_ROW, 1).getValue() === 'HABITS') {
    settings.getRange(CFG.SCORE_OUT_OF).setFormula(scoreOutOfFormula_());
    settings.getRange(HABITS_SECTION_ROW + 12, 4).setValue('Habits on (yours too) + water and sleep, if on');
  }
  const sheet = ss.getSheetByName(SHEETS.DAILY);
  const rows = getDayCount(ss);
  if (sheet && rows > 0) {
    fillFormula(sheet, DT.FIRST_ROW, DT.HABITS_MET, rows, habitsMetFormula_(DT.FIRST_ROW));
    fillFormula(sheet, DT.FIRST_ROW, DT.SCORE, rows, scoreFormula_(DT.FIRST_ROW));
  }
  applyHabitLabels_(ss);
  props.setProperty(HABIT_FORMULAS_PROPERTY, HABIT_FORMULAS_VERSION);
}

function scoreOutOfFormula_() {
  return '=COUNTIF(' + cfg('HABIT1_ON') + ':' + cfg('HABIT5_ON').split('!')[1] + ',"Y")+IF(' +
    cfg('WATER_ON') + '="Y",1,0)+IF(' + cfg('SLEEP_ON') + '="Y",1,0)+' + customHabitsOnFormula_();
}

function scoreFormula_(r) {
  return '=IF($N' + r + '="","",IF(N(' + cfg('SCORE_OUT_OF') + ')>0,$N' + r + '/' + cfg('SCORE_OUT_OF') + ',""))';
}

/** A habit's label: its name, marked when it's off. */
function habitLabelFormula_(i) {
  const n = i + 1;
  return '=IF(' + cfg('HABIT' + n + '_ON') + '="N",' + cfg('HABIT' + n + '_NAME') + '&" (off)",' + cfg('HABIT' + n + '_NAME') + ')';
}

/** Puts formulas where the default habit names were written (after a build or for older sheets). */
function applyHabitLabels_(ss) {
  const swap = (sheet, texts, formulaFor, range) => {
    if (!sheet) return;
    texts.forEach((text, i) => {
      (range || sheet).createTextFinder(text).matchEntireCell(true).findAll().forEach(cell => cell.setFormula(formulaFor(i)));
    });
  };
  const daily = ss.getSheetByName(SHEETS.DAILY);
  if (daily) {
    swap(daily, ['In Office\nby 9:00 AM', 'Breakfast\nDone', 'Lunch\nTaken', 'Protein in\nLunch', 'Workout\nDone'], habitLabelFormula_,
      daily.getRange(DT.HEADER_ROW, 1, 1, DT.STREAK));
    swap(daily, ['In Office by 9:00 AM', 'Breakfast Done', 'Lunch Taken', 'Protein in Lunch', 'Workout Done'], habitLabelFormula_);
    daily.getRange(DT.HEADER_ROW, DT.HABITS_MET).setFormula('="Habits"&CHAR(10)&"Met (/"&' + cfg('SCORE_OUT_OF') + '&")"');
  }
  swap(ss.getSheetByName(SHEETS.DASHBOARD), ['In Office by 9:00 AM', 'Breakfast Done', 'Lunch Taken', 'Protein in Lunch', 'Workout Done'], habitLabelFormula_);
  const monthly = ss.getSheetByName(SHEETS.MONTHLY);
  if (monthly) swap(monthly, ['Office by\n9:00 AM', 'Breakfast', 'Lunch', 'Protein', 'Workout\nDays'], habitLabelFormula_, monthly.getRange(4, 1, 1, 17));
}

/** Health may tick the 5th habit only while it's a workout habit and on. */
function healthFillsWorkout_(ss) {
  const s = ss.getSheetByName(SHEETS.SETTINGS);
  if (!s || s.getRange(HABITS_SECTION_ROW, 1).getValue() !== 'HABITS') return true;
  return s.getRange(CFG.HABIT5_ON).getValue() !== 'N' && /workout|exercise|gym|run|walk/i.test(String(s.getRange(CFG.HABIT5_NAME).getValue()));
}

function trackerOn_(ss, key) {
  const s = ss.getSheetByName(SHEETS.SETTINGS);
  return !s || s.getRange(HABITS_SECTION_ROW, 1).getValue() !== 'HABITS' || s.getRange(CFG[key]).getValue() !== 'N';
}

/** { habits: [{ name, on, icon } × 5], trackers: { WATER_ON: true, … } } */
function saveHabits_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureHabitSettings_(ss);
  const sheet = ss.getSheetByName(SHEETS.SETTINGS);
  const habits = Array.isArray(p.habits) ? p.habits : [];
  if (habits.length !== 5) throw new Error('Send all five habits.');
  const rows = habits.map((h, i) => {
    const name = String(h.name || '').trim().slice(0, 60) || HABIT_DEFAULTS[i][0];
    return [name, h.on === false ? 'N' : 'Y', String(h.icon || HABIT_DEFAULTS[i][1]).slice(0, 30)];
  });
  sheet.getRange(HABITS_SECTION_ROW + 2, 2, 5, 3).setValues(rows);
  const trackers = p.trackers || {};
  TRACKERS.forEach(([key]) => { if (key in trackers) sheet.getRange(CFG[key]).setValue(trackers[key] === false ? 'N' : 'Y'); });
}
