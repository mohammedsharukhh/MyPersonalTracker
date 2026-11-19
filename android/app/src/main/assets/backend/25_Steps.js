/**
 * HEALTH FROM THE PHONE · Apple Health on iPhone, Health Connect on Android.
 *
 * The app sends each day's steps, sleep, weight and whether you worked out.
 * Steps go in raised-only (a bigger number typed by hand stays); sleep, weight
 * and Workout only fill a cell that's empty, so anything you typed wins.
 * None of these on their own make a day "logged": that takes one of the
 * habits you answer yourself (office, breakfast, lunch, protein), water or
 * screen time — so the phone can't turn a day you skipped into a scored one.
 */

const DAILY_FORMULAS_VERSION = '3';            // 3: what the phone fills in doesn't make a day "logged"
const DAILY_FORMULAS_PROPERTY = 'DAILY_FORMULAS';

/** Sheets made before steps could come from the phone get the new "logged" rule. */
function ensureDailyFormulas_(ss) {
  const props = PropertiesService.getDocumentProperties();
  if (props.getProperty(DAILY_FORMULAS_PROPERTY) === DAILY_FORMULAS_VERSION) return;
  const sheet = ss.getSheetByName(SHEETS.DAILY);
  if (!sheet) return;
  const rows = getDayCount(ss);
  if (rows > 0) fillFormula(sheet, DT.FIRST_ROW, DT.HABITS_MET, rows, habitsMetFormula_(DT.FIRST_ROW));
  props.setProperty(DAILY_FORMULAS_PROPERTY, DAILY_FORMULAS_VERSION);
}

/** Old name, from app versions that only sent steps. */
function saveSteps_(p) { return saveHealth_(p); }

/** { days: [{ date: 'yyyy-MM-dd', steps?, sleep?, weight?, workout? }] } — days outside the period are skipped. */
function saveHealth_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureDailyFormulas_(ss);
  const sheet = ss.getSheetByName(SHEETS.DAILY);
  const count = getDayCount(ss);
  if (!sheet || count < 1) return { updated: 0 };
  const dates = sheet.getRange(DT.FIRST_ROW, DT.DATE, count, 1).getValues().map(r => ymd_(r[0]));
  // Workout (G) … Weight (K) in one read and one write.
  const range = sheet.getRange(DT.FIRST_ROW, DT.WORKOUT, count, DT.WEIGHT - DT.WORKOUT + 1);
  const rows = range.getValues();
  const col = c => c - DT.WORKOUT;
  const fillWorkout = healthFillsWorkout_(ss);
  const on = { steps: trackerOn_(ss, 'STEPS_ON'), sleep: trackerOn_(ss, 'SLEEP_ON'), weight: trackerOn_(ss, 'WEIGHT_ON') };
  let updated = 0;
  // A day after tomorrow can't have health data (a day's grace for a phone in another time zone).
  const latest = addDays_(ymd_(new Date()), 1);
  (Array.isArray(p.days) ? p.days : []).forEach(d => {
    const date = String(d.date || '').slice(0, 10);
    const i = dates.indexOf(date);
    if (i === -1 || date > latest) return;
    const r = rows[i];
    const steps = Math.round(num(d.steps) || 0);
    if (on.steps && steps > 0 && steps > (num(r[col(DT.STEPS)]) || 0)) { r[col(DT.STEPS)] = steps; updated++; }
    const sleep = Math.round((num(d.sleep) || 0) * 10) / 10;
    if (on.sleep && sleep > 0 && r[col(DT.SLEEP)] === '') { r[col(DT.SLEEP)] = sleep; updated++; }
    const weight = Math.round((num(d.weight) || 0) * 10) / 10;
    if (on.weight && weight > 0 && r[col(DT.WEIGHT)] === '') { r[col(DT.WEIGHT)] = weight; updated++; }
    if (fillWorkout && d.workout === true && r[col(DT.WORKOUT)] === '') { r[col(DT.WORKOUT)] = 'Y'; updated++; }
  });
  if (updated) range.setValues(rows);
  return { updated: updated };
}
