/**
 * BODY · Calorie and protein targets worked out from your weight.
 *
 * The standard Mifflin–St Jeor formula:
 *   BMR (calories at rest) = 10 × weight kg + 6.25 × height cm − 5 × age, then +5 (male) or −161 (female)
 *   Maintenance           = BMR × activity (1.2 sedentary … 1.9 very active)
 *   Calorie target        = maintenance + goal (−500 lose fat, −250 lose slowly, 0 maintain, +300 build muscle)
 *   Protein               = weight × grams per kg (about 1.6–2.2)
 *   Fat                   = 25% of calories ÷ 9;  Carbs = what's left ÷ 4
 *
 * Weight is the newest one logged on the Daily Tracker, or the weight on this
 * block when none is logged yet. With "Follow my weight" on, the Nutrition
 * targets are formulas pointing here, so they move as your weight does.
 * Typing a target by hand (or from the app's Settings) switches that off.
 */

const BODY_SECTION_ROW = 50;
const SEXES = ['Male', 'Female'];
const ACTIVITY_LEVELS = [
  ['Sedentary', 1.2], ['Light', 1.375], ['Moderate', 1.55], ['Active', 1.725], ['Very active', 1.9]
];
const BODY_GOALS = [
  ['Lose fat', -500], ['Lose slowly', -250], ['Maintain', 0], ['Build muscle', 300]
];
/** Settings keys a person types into the Body block. */
const BODY_INPUTS = ['SEX', 'BIRTH_YEAR', 'HEIGHT_CM', 'BODY_WEIGHT', 'ACTIVITY', 'BODY_GOAL', 'PROTEIN_PER_KG', 'FOLLOW_WEIGHT'];

function ensureBodySettings_(ss) {
  const sheet = ss.getSheetByName(SHEETS.SETTINGS);
  if (!sheet) return;
  const r = BODY_SECTION_ROW;
  if (sheet.getMaxRows() >= r + 13 && sheet.getRange(r + 2, 1).getValue() === 'Sex') return;
  ensureGrid(sheet, r + 16, 3);
  sectionLabel(sheet, r, 1, 3, 'BODY & GOAL  ·  targets from your weight');
  headerRow(sheet, r + 1, 1, ['Parameter', 'Value', 'What it is']);
  sheet.getRange(r + 2, 1, 12, 3).setValues([
    ['Sex', 'Male', 'Male or Female (the formula differs by 166 kcal)'],
    ['Birth Year', '', 'Age is worked out from this'],
    ['Height (cm)', '', ''],
    ['Weight (kg)', '', 'Used until you log a weight on the Daily Tracker'],
    ['Activity', 'Light', 'Sedentary, Light, Moderate, Active or Very active'],
    ['Goal', 'Maintain', 'Lose fat, Lose slowly, Maintain or Build muscle'],
    ['Protein (g per kg)', 1.6, '1.6 is a good default; 2.0 when losing fat'],
    ['Follow my weight', 'N', 'Y: Nutrition targets above follow these formulas'],
    ['Current Weight (kg)', '', 'Newest weight logged, else the weight above'],
    ['BMR (kcal)', '', 'Calories your body burns at rest'],
    ['Maintenance (kcal)', '', 'BMR × activity'],
    ['Suggested Calories', '', 'Maintenance adjusted for your goal']
  ]);
  const inputs = sheet.getRange(r + 2, 2, 8, 1);
  markInput(inputs);
  inputs.setFontWeight('bold').setHorizontalAlignment('center');
  sheet.getRange(r + 3, 2).setNumberFormat('0');
  sheet.getRange(r + 4, 2, 2, 1).setNumberFormat('0.#');
  sheet.getRange(r + 8, 2).setNumberFormat('0.0#');
  sheet.getRange(r + 2, 2).setDataValidation(SpreadsheetApp.newDataValidation().requireValueInList(SEXES, true).build());
  sheet.getRange(r + 6, 2).setDataValidation(SpreadsheetApp.newDataValidation().requireValueInList(ACTIVITY_LEVELS.map(a => a[0]), true).build());
  sheet.getRange(r + 7, 2).setDataValidation(SpreadsheetApp.newDataValidation().requireValueInList(BODY_GOALS.map(g => g[0]), true).build());
  sheet.getRange(r + 9, 2).setDataValidation(SpreadsheetApp.newDataValidation().requireValueInList(['Y', 'N'], true).build());

  // Worked out, grey.
  const weightCol = "'" + SHEETS.DAILY + "'!$K$" + DT.FIRST_ROW + ':$K';
  sheet.getRange(CFG.CURRENT_WEIGHT).setFormula(
    '=IFERROR(INDEX(FILTER(' + weightCol + ',ISNUMBER(' + weightCol + ')),ROWS(FILTER(' + weightCol + ',ISNUMBER(' + weightCol + ')))),' +
    'IF(' + cfg('BODY_WEIGHT') + '="","",' + cfg('BODY_WEIGHT') + '))');
  sheet.getRange(CFG.BMR).setFormula(
    '=IF(OR(' + cfg('CURRENT_WEIGHT') + '="",' + cfg('HEIGHT_CM') + '="",' + cfg('BIRTH_YEAR') + '=""),"",' +
    'ROUND(10*' + cfg('CURRENT_WEIGHT') + '+6.25*' + cfg('HEIGHT_CM') + '-5*(YEAR(TODAY())-' + cfg('BIRTH_YEAR') + ')+IF(' + cfg('SEX') + '="Female",-161,5)))');
  sheet.getRange(CFG.TDEE).setFormula(
    '=IF(' + cfg('BMR') + '="","",ROUND(' + cfg('BMR') + '*' + switchFormula_(cfg('ACTIVITY'), ACTIVITY_LEVELS, 1.375) + '))');
  sheet.getRange(CFG.SUGGESTED_CALORIES).setFormula(
    '=IF(' + cfg('TDEE') + '="","",MAX(IF(' + cfg('SEX') + '="Female",1200,1500),ROUND(' + cfg('TDEE') + '+' +
    switchFormula_(cfg('BODY_GOAL'), BODY_GOALS, 0) + ',-1)))');
  const calc = sheet.getRange(r + 10, 2, 4, 1);
  calc.setBackground(THEME.CALC_BG).setFontWeight('bold').setHorizontalAlignment('center').setNumberFormat('#,##0.#');
  sheet.getRange(r + 2, 1, 12, 1).setFontWeight('bold');
  sheet.getRange(r + 2, 3, 12, 1).setFontSize(9).setFontColor(THEME.MUTED);
  styleBlock(sheet, r + 1, 1, 13, 3);
}

/** IF chain picking a number by label, e.g. activity → factor. */
function switchFormula_(ref, pairs, fallback) {
  return 'SWITCH(' + ref + ',' + pairs.map(p => '"' + p[0] + '",' + p[1]).join(',') + ',' + fallback + ')';
}

/** The Nutrition targets as formulas over the Body block. */
function nutritionFormulas_() {
  const cal = cfg('SUGGESTED_CALORIES');
  const protein = 'ROUND(' + cfg('CURRENT_WEIGHT') + '*' + cfg('PROTEIN_PER_KG') + ')';
  const fat = 'ROUND(' + cal + '*0.25/9)';
  return {
    CALORIE_TARGET: '=IF(' + cal + '="",2200,' + cal + ')',
    PROTEIN_TARGET: '=IF(' + cfg('CURRENT_WEIGHT') + '="",120,' + protein + ')',
    FAT_TARGET: '=IF(' + cal + '="",70,' + fat + ')',
    CARB_TARGET: '=IF(' + cal + '="",250,MAX(0,ROUND((' + cal + '-4*' + protein + '-9*' + fat + ')/4)))'
  };
}

/**
 * API: { sex, birthYear, height, weight, activity, goal, proteinPerKg, follow }.
 * Saves the body profile; with `follow` the Nutrition targets become formulas
 * that track your weight, otherwise they're set once to today's numbers.
 */
function saveBody_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureBodySettings_(ss);
  const sheet = ss.getSheetByName(SHEETS.SETTINGS);
  if (SEXES.indexOf(p.sex) === -1) throw new Error('Sex must be Male or Female.');
  if (!ACTIVITY_LEVELS.some(a => a[0] === p.activity)) throw new Error('Unknown activity level: ' + p.activity);
  if (!BODY_GOALS.some(g => g[0] === p.goal)) throw new Error('Unknown goal: ' + p.goal);
  const birthYear = num(p.birthYear), height = num(p.height), weight = num(p.weight), perKg = num(p.proteinPerKg);
  if (!(birthYear > 1900)) throw new Error('Enter your age.');
  if (!(height > 50 && height < 260)) throw new Error('Enter your height in cm.');
  if (!(weight > 20 && weight < 400)) throw new Error('Enter your weight in kg.');
  if (!(perKg > 0.5 && perKg < 4)) throw new Error('Protein per kg should be between 0.6 and 3.5.');

  const values = { SEX: p.sex, BIRTH_YEAR: birthYear, HEIGHT_CM: height, BODY_WEIGHT: weight, ACTIVITY: p.activity,
    BODY_GOAL: p.goal, PROTEIN_PER_KG: perKg, FOLLOW_WEIGHT: p.follow ? 'Y' : 'N' };
  Object.keys(values).forEach(k => sheet.getRange(CFG[k]).setValue(values[k]));
  // The weight typed in the app counts as logged today, if today's row is empty.
  logWeightToday_(ss, weight);

  const formulas = nutritionFormulas_();
  Object.keys(formulas).forEach(k => sheet.getRange(CFG[k]).setFormula(formulas[k]));
  if (!p.follow) {
    SpreadsheetApp.flush();
    Object.keys(formulas).forEach(k => {
      const cell = sheet.getRange(CFG[k]);
      cell.setValue(cell.getValue());
    });
  }
}

function logWeightToday_(ss, weight) {
  const sheet = ss.getSheetByName(SHEETS.DAILY);
  const count = getDayCount(ss);
  const today = ymd_(new Date());
  const dates = sheet.getRange(DT.FIRST_ROW, DT.DATE, count, 1).getValues();
  const i = dates.findIndex(d => ymd_(d[0]) === today);
  if (i === -1) return;
  const cell = sheet.getRange(DT.FIRST_ROW + i, DT.WEIGHT);
  if (cell.getValue() === '') cell.setValue(weight);
}

/** Before a period rebuild: keep the newest logged weight on the Body block, so targets don't jump back. */
function carryWeight_(ss) {
  const sheet = ss.getSheetByName(SHEETS.SETTINGS);
  if (!sheet || sheet.getMaxRows() < BODY_SECTION_ROW + 13 || sheet.getRange(BODY_SECTION_ROW + 2, 1).getValue() !== 'Sex') return;
  const current = sheet.getRange(CFG.CURRENT_WEIGHT).getValue();
  if (typeof current === 'number' && current > 0) sheet.getRange(CFG.BODY_WEIGHT).setValue(current);
}
