/**
 * DAILY TRACKER · Routine, nutrition, hydration, sleep and activity.
 *
 * Yellow columns C-M are yours. Everything from N rightwards is formula.
 *
 * Scoring matches the source workbook: 5 Y/N habits + water + sleep, out of 7.
 * Steps is logged and reported as a success rate but does not feed the score.
 *
 * Unlogged days score blank rather than 0%, so a day you simply have not
 * filled in yet does not average into the summaries as a failure.
 */
function buildDailyTracker(ss, dayCount) {
  ensureHabitSettings_(ss);
  const sheet = resetSheet(ss, SHEETS.DAILY);
  const rows = dayCount;
  const lastRow = DT.FIRST_ROW + rows - 1;

  // Log rows, then two summary tables beneath; columns T:W hold the hidden
  // target mirrors the conditional rules read.
  ensureGrid(sheet, lastRow + 40, 24);

  // Frozen first: the merge helpers read the frozen count to avoid straddling
  // the boundary, which Sheets rejects.
  freezeColumnsIfPossible(sheet, 2);

  titleBar(sheet, 'DAILY TRACKER  ·  Routine, Nutrition, Hydration, Sleep & Activity',
           'Fill the yellow columns each day — grey columns calculate themselves.', DT.STREAK, 2);

  headerRow(sheet, DT.HEADER_ROW, 1, [
    'Date', 'Day',
    'In Office\nby 9:00 AM', 'Breakfast\nDone', 'Lunch\nTaken', 'Protein in\nLunch', 'Workout\nDone',
    'Water\n(Litres)', 'Sleep\n(Hours)', 'Steps', 'Weight\n(kg)', 'Screen Time\n(Hours)',
    'Notes',
    'Habits\nMet (/' + SCORE_DENOMINATOR + ')', 'Daily\nScore', 'Status', 'Streak'
  ]);

  // Dates are generated from Settings, so the period is changed in one place.
  fillFormula(sheet, DT.FIRST_ROW, DT.DATE, rows,
    '=IF(ROW()-' + DT.FIRST_ROW + '>=' + cfg('END_DATE') + '-' + cfg('START_DATE') + '+1,"",' +
    cfg('START_DATE') + '+ROW()-' + DT.FIRST_ROW + ')');
  sheet.getRange(DT.FIRST_ROW, DT.DATE, rows, 1).setNumberFormat('dd-mmm-yy');

  fillFormula(sheet, DT.FIRST_ROW, DT.DAY, rows,
    '=IF(A' + DT.FIRST_ROW + '="","",TEXT(A' + DT.FIRST_ROW + ',"ddd"))');

  markCalculated(sheet.getRange(DT.FIRST_ROW, DT.DATE, rows, 2));
  sheet.getRange(DT.FIRST_ROW, DT.DATE, rows, 2)
    .setHorizontalAlignment('center').setFontColor('#1A1A1A');

  // Input columns.
  markInput(sheet.getRange(DT.FIRST_ROW, DT.OFFICE, rows, DT.NOTES - DT.OFFICE + 1));
  for (let col = DT.OFFICE; col <= DT.WORKOUT; col++) {
    ynValidation(sheet, DT.FIRST_ROW, col, rows);
    sheet.getRange(DT.FIRST_ROW, col, rows, 1)
      .setHorizontalAlignment('center').setFontWeight('bold');
  }
  sheet.getRange(DT.FIRST_ROW, DT.WATER, rows, 1).setNumberFormat('0.0');
  sheet.getRange(DT.FIRST_ROW, DT.SLEEP, rows, 1).setNumberFormat('0.0');
  sheet.getRange(DT.FIRST_ROW, DT.STEPS, rows, 1).setNumberFormat('#,##0');
  sheet.getRange(DT.FIRST_ROW, DT.WEIGHT, rows, 1).setNumberFormat('0.0');
  sheet.getRange(DT.FIRST_ROW, DT.SCREEN, rows, 1).setNumberFormat('0.0');
  sheet.getRange(DT.FIRST_ROW, DT.WATER, rows, DT.SCREEN - DT.WATER + 1)
    .setHorizontalAlignment('center');

  const r = DT.FIRST_ROW;

  fillFormula(sheet, r, DT.HABITS_MET, rows, habitsMetFormula_(r));

  fillFormula(sheet, r, DT.SCORE, rows, scoreFormula_(r));
  sheet.getRange(r, DT.SCORE, rows, 1).setNumberFormat('0.0%');

  // Tiers as the source workbook had them: Excellent at the Great Day
  // threshold, On Track at a fixed 70%.
  fillFormula(sheet, r, DT.STATUS, rows,
    '=IF($O' + r + '="","",' +
      'IF($O' + r + '>=' + cfg('GREAT_DAY') + ',"Excellent",' +
      'IF($O' + r + '>=0.7,"On Track","Needs Work")))');

  /**
   * Streak: consecutive days at or above the Great Day threshold.
   *
   * Counts up from the row above, so an unlogged day breaks the chain rather
   * than silently continuing it.
   */
  fillFormula(sheet, r, DT.STREAK, rows,
    '=IF($O' + r + '="","",' +
      'IF($O' + r + '>=' + cfg('GREAT_DAY') + ',' +
        'IF(ROW()=' + r + ',1,N(INDIRECT("Q"&ROW()-1))+1),0))');

  markCalculated(sheet.getRange(r, DT.HABITS_MET, rows, 4));
  sheet.getRange(r, DT.HABITS_MET, rows, 4)
    .setHorizontalAlignment('center').setFontWeight('bold');

  sheet.getRange(r, DT.NOTES, rows, 1).setFontSize(9).setWrap(false);
  styleBlock(sheet, DT.HEADER_ROW, 1, rows + 1, DT.STREAK);

  applyDailyFormatting(sheet, rows, lastRow);
  buildDailySummary(sheet, rows, lastRow);

  // Column widths.
  sheet.setColumnWidth(DT.DATE, 82);
  sheet.setColumnWidth(DT.DAY, 42);
  for (let c = DT.OFFICE; c <= DT.WORKOUT; c++) sheet.setColumnWidth(c, 72);
  sheet.setColumnWidth(DT.WATER, 58);
  sheet.setColumnWidth(DT.SLEEP, 58);
  sheet.setColumnWidth(DT.STEPS, 62);
  sheet.setColumnWidth(DT.WEIGHT, 58);
  sheet.setColumnWidth(DT.SCREEN, 70);
  sheet.setColumnWidth(DT.NOTES, 250);
  sheet.setColumnWidth(DT.HABITS_MET, 58);
  sheet.setColumnWidth(DT.SCORE, 58);
  sheet.setColumnWidth(DT.STATUS, 82);
  sheet.setColumnWidth(DT.STREAK, 52);

  sheet.setFrozenRows(DT.HEADER_ROW);
  sheet.setHiddenGridlines(true);
  return sheet;
}

/**
 * Local mirrors of the Settings targets.
 *
 * Google Sheets refuses a conditional format rule whose formula references
 * another sheet ("Conditional format rule cannot reference a different
 * sheet"), so the four numeric targets are mirrored into cells on this sheet
 * and the rules point at those. They are still formulas reading Settings, so
 * changing a target there updates the colours here — the mirror adds an
 * indirection, not a second source of truth.
 *
 * Parked in row 1 beyond the visible table, then hidden.
 */
const DT_MIRROR = {
  WATER:  'T1',
  SLEEP:  'U1',
  STEPS:  'V1',
  SCREEN: 'W1'
};

function writeTargetMirrors(sheet) {
  sheet.getRange(DT_MIRROR.WATER).setFormula('=' + cfg('WATER_TARGET'));
  sheet.getRange(DT_MIRROR.SLEEP).setFormula('=' + cfg('SLEEP_TARGET'));
  sheet.getRange(DT_MIRROR.STEPS).setFormula('=' + cfg('STEPS_TARGET'));
  sheet.getRange(DT_MIRROR.SCREEN).setFormula('=' + cfg('SCREEN_LIMIT'));
  sheet.hideColumns(20, 4);   // T:W
}

/** A mirror cell as an absolute, same-sheet reference. */
function mirror(key) {
  return '$' + DT_MIRROR[key].charAt(0) + '$' + DT_MIRROR[key].slice(1);
}

/** Colour rules: Y/N, status words, score gradient, target misses. */
function applyDailyFormatting(sheet, rows, lastRow) {
  const r = DT.FIRST_ROW;
  const rules = [];

  writeTargetMirrors(sheet);

  const ynRange = sheet.getRange(r, DT.OFFICE, rows, 5);
  rules.push(ruleTextEquals(ynRange, 'Y', THEME.GOOD, THEME.GOOD_BG));
  rules.push(ruleTextEquals(ynRange, 'N', THEME.BAD, THEME.BAD_BG));

  const statusRange = sheet.getRange(r, DT.STATUS, rows, 1);
  rules.push(ruleTextEquals(statusRange, 'Excellent',  THEME.GOOD, THEME.GOOD_BG));
  rules.push(ruleTextEquals(statusRange, 'On Track',   THEME.WARN, THEME.WARN_BG));
  rules.push(ruleTextEquals(statusRange, 'Needs Work', THEME.BAD,  THEME.BAD_BG));

  rules.push(ruleGradient(sheet.getRange(r, DT.SCORE, rows, 1)));

  // Numeric targets: green when met, red when logged but missed.
  const water = sheet.getRange(r, DT.WATER, rows, 1);
  rules.push(ruleFormula(water, '=AND($H' + r + '<>"",$H' + r + '>=' + mirror('WATER') + ')', THEME.GOOD, THEME.GOOD_BG));
  rules.push(ruleFormula(water, '=AND($H' + r + '<>"",$H' + r + '<' + mirror('WATER') + ')', THEME.BAD, THEME.BAD_BG));

  const sleep = sheet.getRange(r, DT.SLEEP, rows, 1);
  rules.push(ruleFormula(sleep, '=AND($I' + r + '<>"",$I' + r + '>=' + mirror('SLEEP') + ')', THEME.GOOD, THEME.GOOD_BG));
  rules.push(ruleFormula(sleep, '=AND($I' + r + '<>"",$I' + r + '<' + mirror('SLEEP') + ')', THEME.BAD, THEME.BAD_BG));

  const steps = sheet.getRange(r, DT.STEPS, rows, 1);
  rules.push(ruleFormula(steps, '=AND($J' + r + '<>"",$J' + r + '>=' + mirror('STEPS') + ')', THEME.GOOD, THEME.GOOD_BG));
  rules.push(ruleFormula(steps, '=AND($J' + r + '<>"",$J' + r + '<' + mirror('STEPS') + ')', THEME.BAD, THEME.BAD_BG));

  const screen = sheet.getRange(r, DT.SCREEN, rows, 1);
  rules.push(ruleFormula(screen, '=AND($L' + r + '<>"",$L' + r + '<=' + mirror('SCREEN') + ')', THEME.GOOD, THEME.GOOD_BG));
  rules.push(ruleFormula(screen, '=AND($L' + r + '<>"",$L' + r + '>' + mirror('SCREEN') + ')', THEME.BAD, THEME.BAD_BG));

  // Today's row, so the cursor lands in the right place on open.
  rules.push(ruleFormula(sheet.getRange(r, 1, rows, DT.STREAK),
    '=$A' + r + '=TODAY()', null, '#FFF9C4', false));

  // Weekends, lightly shaded.
  rules.push(ruleFormula(sheet.getRange(r, DT.DATE, rows, 2),
    '=WEEKDAY($A' + r + ',2)>5', null, '#EDF1F7', false));

  sheet.setConditionalFormatRules(rules);
}

/**
 * Period summary tables below the log.
 *
 * Every rate divides by "days you logged that habit", not days elapsed, so
 * skipping a day never counts against you.
 */
function buildDailySummary(sheet, rows, lastRow) {
  const top = lastRow + 3;
  const r = DT.FIRST_ROW;

  sectionLabel(sheet, top, 1, 5, 'PERIOD SUMMARY  ·  HABIT SUCCESS RATES');
  headerRow(sheet, top + 1, 1, ['Habit', 'Days Logged', 'Achieved', 'Success Rate', 'Target']);

  const habitRows = [
    ['In Office by 9:00 AM', 'C', 'Y', 'Every working day'],
    ['Breakfast Done',       'D', 'Y', 'Every day'],
    ['Lunch Taken',          'E', 'Y', 'Every day'],
    ['Protein in Lunch',     'F', 'Y', 'Every day'],
    ['Workout Done',         'G', 'Y', 'Every day']
  ];

  let row = top + 2;
  habitRows.forEach(([label, col, , target]) => {
    const range = '$' + col + '$' + r + ':$' + col + '$' + lastRow;
    sheet.getRange(row, 1).setValue(label).setFontWeight('bold').setFontSize(9);
    sheet.getRange(row, 2).setFormula('=COUNTA(' + range + ')');
    sheet.getRange(row, 3).setFormula('=COUNTIF(' + range + ',"Y")');
    sheet.getRange(row, 4).setFormula('=IFERROR(C' + row + '/B' + row + ',0)').setNumberFormat('0.0%');
    sheet.getRange(row, 5).setValue(target).setFontSize(9).setFontColor(THEME.MUTED);
    row++;
  });

  // Numeric targets, counted the same way.
  const numeric = [
    ['Water Target Met',      'H', '>=', 'WATER_TARGET', '4.0 L / day'],
    ['Sleep Target Met',      'I', '>=', 'SLEEP_TARGET', '7.5 hrs / night'],
    ['Steps Target Met',      'J', '>=', 'STEPS_TARGET', '8,000 steps / day'],
    ['Screen Time In Limit',  'L', '<=', 'SCREEN_LIMIT', '3.0 hrs / day']
  ];
  numeric.forEach(([label, col, op, setting, target]) => {
    const range = '$' + col + '$' + r + ':$' + col + '$' + lastRow;
    sheet.getRange(row, 1).setValue(label).setFontWeight('bold').setFontSize(9);
    sheet.getRange(row, 2).setFormula('=COUNT(' + range + ')');
    sheet.getRange(row, 3).setFormula('=COUNTIF(' + range + ',"' + op + '"&' + cfg(setting) + ')');
    sheet.getRange(row, 4).setFormula('=IFERROR(C' + row + '/B' + row + ',0)').setNumberFormat('0.0%');
    sheet.getRange(row, 5).setValue(target).setFontSize(9).setFontColor(THEME.MUTED);
    row++;
  });

  const rateRows = row - (top + 2);
  sheet.getRange(top + 2, 2, rateRows, 2).setHorizontalAlignment('center');
  sheet.getRange(top + 2, 4, rateRows, 1).setHorizontalAlignment('center').setFontWeight('bold');
  markCalculated(sheet.getRange(top + 2, 2, rateRows, 3));
  styleBlock(sheet, top + 1, 1, rateRows + 1, 5);

  const rules = sheet.getConditionalFormatRules();
  rules.push(ruleGradient(sheet.getRange(top + 2, 4, rateRows, 1)));
  sheet.setConditionalFormatRules(rules);

  // Averages / score / streaks.
  const aTop = row + 2;
  sectionLabel(sheet, aTop, 1, 2, 'PERIOD SUMMARY  ·  AVERAGES, SCORE & STREAKS');
  headerRow(sheet, aTop + 1, 1, ['Metric', 'Value']);

  const scoreRange  = dtRange(DT.SCORE, lastRow);
  const statusRange = dtRange(DT.STATUS, lastRow);
  const streakRange = dtRange(DT.STREAK, lastRow);

  const metrics = [
    ['Days Logged',            '=COUNT(' + dtRange(DT.SCORE, lastRow) + ')',        '#,##0'],
    ['Overall Habit Score',    '=IFERROR(AVERAGE(' + scoreRange + '),0)',           '0.0%'],
    ['Average Water (L)',      '=IFERROR(AVERAGE(' + dtRange(DT.WATER, lastRow) + '),0)', '0.0'],
    ['Average Sleep (hrs)',    '=IFERROR(AVERAGE(' + dtRange(DT.SLEEP, lastRow) + '),0)', '0.0'],
    ['Average Steps',          '=IFERROR(AVERAGE(' + dtRange(DT.STEPS, lastRow) + '),0)', '#,##0'],
    ['Average Weight (kg)',    '=IFERROR(AVERAGE(' + dtRange(DT.WEIGHT, lastRow) + '),0)', '0.0'],
    ['Latest Weight (kg)',     '=IFERROR(LOOKUP(2,1/(' + dtRange(DT.WEIGHT, lastRow) + '<>""),' + dtRange(DT.WEIGHT, lastRow) + '),0)', '0.0'],
    ['Average Screen (hrs)',   '=IFERROR(AVERAGE(' + dtRange(DT.SCREEN, lastRow) + '),0)', '0.0'],
    ['Best Streak (days)',     '=IFERROR(MAX(' + streakRange + '),0)',              '#,##0'],
    ['Current Streak (days)',  '=IFERROR(LOOKUP(2,1/(' + streakRange + '<>""),' + streakRange + '),0)', '#,##0'],
    ['Excellent Days',         '=COUNTIF(' + statusRange + ',"Excellent")',          '#,##0'],
    ['On Track Days',          '=COUNTIF(' + statusRange + ',"On Track")',           '#,##0'],
    ['Needs Work Days',        '=COUNTIF(' + statusRange + ',"Needs Work")',         '#,##0']
  ];

  metrics.forEach((m, i) => {
    const mRow = aTop + 2 + i;
    sheet.getRange(mRow, 1).setValue(m[0]).setFontWeight('bold').setFontSize(9);
    sheet.getRange(mRow, 2).setFormula(m[1]).setNumberFormat(m[2])
      .setHorizontalAlignment('center').setFontWeight('bold');
  });
  markCalculated(sheet.getRange(aTop + 2, 2, metrics.length, 1));
  styleBlock(sheet, aTop + 1, 1, metrics.length + 1, 2);

  footnote(sheet, aTop + metrics.length + 3, 1, DT.STREAK,
    'HOW TO USE  ·  Yellow cells are yours: columns C-G take Y or N, H-L take numbers, M is free text. ' +
    'Everything grey is a formula — do not type over it. "Habits Met" counts the 5 Y/N habits plus the ' +
    'water and sleep targets, so the Daily Score is out of ' + SCORE_DENOMINATOR + '. A day at or ' +
    'above the Great Day Threshold extends your streak. Rates divide by days you actually logged, so ' +
    'skipping a day never counts against you. Dates come from Settings — change them there, not here.');
}

/**
 * Habits met: each habit that's on and answered Y, plus water and sleep (if on)
 * at their targets. Steps is tracked and reported but doesn't feed the score.
 * The Daily Score divides by SCORE_OUT_OF on Settings — see 29_Habits.gs.
 *
 * A row counts as "logged" once you've entered something only you can answer:
 * habits 1–4 (C..F), water (H) or screen time (L) — and habit 5 (G) too, unless
 * it's the workout habit the phone can tick by itself (25_Steps.gs). Sleep,
 * steps and weight can also come from the phone, so they don't count on their
 * own. This guard also keeps empty future rows out of every average.
 */
function habitsMetFormula_(r) {
  const fifthIsWorkout = 'REGEXMATCH(LOWER(' + cfg('HABIT5_NAME') + '),"workout|exercise|gym|run|walk")';
  const logged = '(COUNTA($C' + r + ':$F' + r + ')+COUNTA($H' + r + ')+COUNTA($L' + r + ')+IF(' + fifthIsWorkout + ',0,COUNTA($G' + r + '))+' +
    customLoggedFormula_(r) + ')>0';
  const habit = (col, n) => 'IF(AND(' + cfg('HABIT' + n + '_ON') + '<>"N",$' + col + r + '="Y"),1,0)';
  return '=IF(NOT(' + logged + '),"",' +
    ['C', 'D', 'E', 'F', 'G'].map((col, i) => habit(col, i + 1)).join('+') +
    '+IF(AND(' + cfg('WATER_ON') + '<>"N",N($H' + r + ')>=' + cfg('WATER_TARGET') + '),1,0)' +
    '+IF(AND(' + cfg('SLEEP_ON') + '<>"N",N($I' + r + ')>=' + cfg('SLEEP_TARGET') + '),1,0)' +
    '+' + customMetFormula_(r) + ')';
}
