/**
 * WEEKLY SUMMARY · Habits and spend, week by week.
 * MONTHLY SUMMARY · Month-over-month performance.
 *
 * Both are entirely calculated. Weeks that the period has not reached show
 * blank rather than a row of zeros — the source workbook filled them with 0.0%
 * and 0 steps, which made an untouched future week look like a failed one.
 */
function buildWeeklySummary(ss, dayCount) {
  const sheet = resetSheet(ss, SHEETS.WEEKLY);
  ensureGrid(sheet, 5 + WEEK_ROWS + 6, 14);
  const dtLast = DT.FIRST_ROW + dayCount - 1;
  const rows = WEEK_ROWS;
  const r = 5;

  titleBar(sheet, 'WEEKLY SUMMARY  ·  Habits and Spend, Week by Week',
    'Weeks run from the Start Date in Settings. Everything here is calculated — nothing to fill in.', 13);

  headerRow(sheet, 4, 1, [
    'Week', 'Week Start', 'Week End', 'Days\nLogged', 'Avg Habit\nScore',
    'Avg Water\n(L)', 'Avg Sleep\n(hrs)', 'Total\nSteps', 'Workouts',
    'Spend\n(QAR)', 'Spend\n(INR)', 'Weekly Budget\n(QAR)', 'Variance\n(QAR)'
  ]);

  // A week exists only if its start falls inside the period.
  const weekStart = cfg('START_DATE') + '+(ROW()-' + r + ')*7';
  const exists = '(' + weekStart + ')<=' + cfg('END_DATE');

  fillFormula(sheet, r, 1, rows, '=IF(NOT(' + exists + '),"",ROW()-' + (r - 1) + ')');
  fillFormula(sheet, r, 2, rows, '=IF(NOT(' + exists + '),"",' + weekStart + ')');
  fillFormula(sheet, r, 3, rows,
    '=IF($B' + r + '="","",MIN($B' + r + '+6,' + cfg('END_DATE') + '))');
  sheet.getRange(r, 2, rows, 2).setNumberFormat('dd-mmm-yy');

  const dateR  = dtRange(DT.DATE, dtLast);
  const scoreR = dtRange(DT.SCORE, dtLast);
  const waterR = dtRange(DT.WATER, dtLast);
  const sleepR = dtRange(DT.SLEEP, dtLast);
  const stepsR = dtRange(DT.STEPS, dtLast);
  const workR  = dtRange(DT.WORKOUT, dtLast);
  const between = dateR + ',">="&$B' + r + ',' + dateR + ',"<="&$C' + r;

  fillFormula(sheet, r, 4, rows,
    '=IF($B' + r + '="","",COUNTIFS(' + between + ',' + scoreR + ',">=0"))');

  // AVERAGEIFS returns an error for a week with nothing logged; blank is the
  // honest answer there, not 0%.
  fillFormula(sheet, r, 5, rows,
    '=IF($B' + r + '="","",IFERROR(AVERAGEIFS(' + scoreR + ',' + between + '),""))');
  sheet.getRange(r, 5, rows, 1).setNumberFormat('0.0%');

  fillFormula(sheet, r, 6, rows,
    '=IF($B' + r + '="","",IFERROR(AVERAGEIFS(' + waterR + ',' + between + '),""))');
  sheet.getRange(r, 6, rows, 1).setNumberFormat('0.0');

  fillFormula(sheet, r, 7, rows,
    '=IF($B' + r + '="","",IFERROR(AVERAGEIFS(' + sleepR + ',' + between + '),""))');
  sheet.getRange(r, 7, rows, 1).setNumberFormat('0.0');

  fillFormula(sheet, r, 8, rows,
    '=IF($B' + r + '="","",SUMIFS(' + stepsR + ',' + between + '))');
  sheet.getRange(r, 8, rows, 1).setNumberFormat('#,##0');

  fillFormula(sheet, r, 9, rows,
    '=IF($B' + r + '="","",COUNTIFS(' + between + ',' + workR + ',"Y"))');

  fillFormula(sheet, r, 10, rows,
    '=IF($B' + r + '="","",SUMIFS(' + ftRange(FT.QAR) + ',' +
      ftRange(FT.DATE) + ',">="&$B' + r + ',' + ftRange(FT.DATE) + ',"<="&$C' + r + '))');
  sheet.getRange(r, 10, rows, 1).setNumberFormat('#,##0.00');

  fillFormula(sheet, r, 11, rows, '=IF($J' + r + '="","",$J' + r + '*' + cfg('RATE') + ')');
  sheet.getRange(r, 11, rows, 1).setNumberFormat('#,##0');

  // Budget scales with the days the week actually covers — a truncated final
  // week gets a smaller allowance, not a full seven days' worth.
  fillFormula(sheet, r, 12, rows,
    '=IF($B' + r + '="","",' + cfg('DAILY_LIMIT') + '*($C' + r + '-$B' + r + '+1))');
  sheet.getRange(r, 12, rows, 1).setNumberFormat('#,##0.00');

  fillFormula(sheet, r, 13, rows, '=IF($B' + r + '="","",$L' + r + '-$J' + r + ')');
  sheet.getRange(r, 13, rows, 1).setNumberFormat('#,##0.00;[Red](#,##0.00)');

  markCalculated(sheet.getRange(r, 1, rows, 13));
  sheet.getRange(r, 1, rows, 13).setHorizontalAlignment('center');
  styleBlock(sheet, 4, 1, rows + 1, 13);

  const rules = [];
  rules.push(ruleGradient(sheet.getRange(r, 5, rows, 1)));
  rules.push(ruleFormula(sheet.getRange(r, 13, rows, 1),
    '=AND($M' + r + '<>"",$M' + r + '<0)', THEME.BAD, THEME.BAD_BG, true));
  rules.push(ruleFormula(sheet.getRange(r, 13, rows, 1),
    '=AND($M' + r + '<>"",$M' + r + '>=0)', THEME.GOOD, null, true));
  rules.push(ruleFormula(sheet.getRange(r, 1, rows, 13),
    '=AND($B' + r + '<>"",TODAY()>=$B' + r + ',TODAY()<=$C' + r + ')', null, '#FFF9C4', false));
  sheet.setConditionalFormatRules(rules);

  footnote(sheet, r + rows + 2, 1, 13,
    'Weekly Budget = Daily Spend Limit x the days that week actually covers, so a short final week is ' +
    'not judged against a full seven days. Variance is budget minus actual: green is money left over, ' +
    'red is an overspend. A week with nothing logged shows blank rather than 0%, so an untouched week ' +
    'never reads as a failed one.');

  sheet.setColumnWidth(1, 55);
  sheet.setColumnWidth(2, 90);
  sheet.setColumnWidth(3, 90);
  for (let c = 4; c <= 13; c++) sheet.setColumnWidth(c, 92);

  sheet.setFrozenRows(4);
  sheet.setHiddenGridlines(true);
  return sheet;
}

function buildMonthlySummary(ss, dayCount) {
  const sheet = resetSheet(ss, SHEETS.MONTHLY);
  ensureGrid(sheet, 5 + SALARY_MONTHS + 8, 18);
  const dtLast = DT.FIRST_ROW + dayCount - 1;
  const rows = SALARY_MONTHS;
  const r = 5;
  const sr = SALARY_COLS.FIRST_ROW;
  const sLast = sr + SALARY_MONTHS - 1;

  // See the note in buildDailyTracker: frozen before any merge.
  freezeColumnsIfPossible(sheet, 1);

  titleBar(sheet, 'MONTHLY SUMMARY  ·  Month-over-Month Performance',
    'Every figure is calculated from the Daily Tracker, Finance Tracker and Salary & Savings sheets.', 17, 1);

  headerRow(sheet, 4, 1, [
    'Month', 'Days\nLogged', 'Avg Habit\nScore', 'Office by\n9:00 AM', 'Breakfast', 'Lunch', 'Protein',
    'Workout\nDays', 'Avg Water\n(L)', 'Avg Sleep\n(hrs)', 'Total\nSteps', 'Avg Weight\n(kg)',
    'Spend\n(QAR)', 'Spend\n(INR)', 'Income\n(QAR)', 'Net Savings\n(QAR)', 'Savings\nRate'
  ]);

  fillFormula(sheet, r, 1, rows,
    '=EOMONTH(' + cfg('START_DATE') + ',ROW()-' + r + '-1)+1');
  sheet.getRange(r, 1, rows, 1)
    .setNumberFormat('mmm yyyy').setFontWeight('bold').setHorizontalAlignment('center');

  const dateR = dtRange(DT.DATE, dtLast);
  const within = dateR + ',">="&$A' + r + ',' + dateR + ',"<="&EOMONTH($A' + r + ',0)';

  fillFormula(sheet, r, 2, rows,
    '=COUNTIFS(' + within + ',' + dtRange(DT.SCORE, dtLast) + ',">=0")');

  fillFormula(sheet, r, 3, rows,
    '=IFERROR(AVERAGEIFS(' + dtRange(DT.SCORE, dtLast) + ',' + within + '),"")');
  sheet.getRange(r, 3, rows, 1).setNumberFormat('0.0%');

  // Habit rates: days marked Y over days that habit was logged at all.
  const habitCols = [
    [4, DT.OFFICE], [5, DT.BREAKFAST], [6, DT.LUNCH], [7, DT.PROTEIN]
  ];
  habitCols.forEach(([outCol, srcCol]) => {
    const src = dtRange(srcCol, dtLast);
    fillFormula(sheet, r, outCol, rows,
      '=IFERROR(COUNTIFS(' + within + ',' + src + ',"Y")/COUNTIFS(' + within + ',' + src + ',"<>"),"")');
    sheet.getRange(r, outCol, rows, 1).setNumberFormat('0.0%');
  });

  fillFormula(sheet, r, 8, rows,
    '=COUNTIFS(' + within + ',' + dtRange(DT.WORKOUT, dtLast) + ',"Y")');

  fillFormula(sheet, r, 9, rows,
    '=IFERROR(AVERAGEIFS(' + dtRange(DT.WATER, dtLast) + ',' + within + '),"")');
  sheet.getRange(r, 9, rows, 1).setNumberFormat('0.0');

  fillFormula(sheet, r, 10, rows,
    '=IFERROR(AVERAGEIFS(' + dtRange(DT.SLEEP, dtLast) + ',' + within + '),"")');
  sheet.getRange(r, 10, rows, 1).setNumberFormat('0.0');

  fillFormula(sheet, r, 11, rows,
    '=SUMIFS(' + dtRange(DT.STEPS, dtLast) + ',' + within + ')');
  sheet.getRange(r, 11, rows, 1).setNumberFormat('#,##0');

  fillFormula(sheet, r, 12, rows,
    '=IFERROR(AVERAGEIFS(' + dtRange(DT.WEIGHT, dtLast) + ',' + within + '),"")');
  sheet.getRange(r, 12, rows, 1).setNumberFormat('0.0');

  fillFormula(sheet, r, 13, rows,
    '=SUMIFS(' + ftRange(FT.QAR) + ',' + ftRange(FT.DATE) + ',">="&$A' + r + ',' +
    ftRange(FT.DATE) + ',"<="&EOMONTH($A' + r + ',0))');
  sheet.getRange(r, 13, rows, 1).setNumberFormat('#,##0.00');

  fillFormula(sheet, r, 14, rows, '=$M' + r + '*' + cfg('RATE'));
  sheet.getRange(r, 14, rows, 1).setNumberFormat('#,##0');

  // Income / savings mirror the Salary sheet rather than recomputing them.
  const salaryMonths = "'" + SHEETS.SALARY + "'!$A$" + sr + ':$A$' + sLast;
  fillFormula(sheet, r, 15, rows,
    '=IFERROR(INDEX(' + "'" + SHEETS.SALARY + "'!$D$" + sr + ':$D$' + sLast +
    ',MATCH($A' + r + ',' + salaryMonths + ',0)),0)');
  fillFormula(sheet, r, 16, rows,
    '=IFERROR(INDEX(' + "'" + SHEETS.SALARY + "'!$H$" + sr + ':$H$' + sLast +
    ',MATCH($A' + r + ',' + salaryMonths + ',0)),0)');
  sheet.getRange(r, 15, rows, 2).setNumberFormat('#,##0.00');

  fillFormula(sheet, r, 17, rows,
    '=IFERROR(IF($O' + r + '=0,"",$P' + r + '/$O' + r + '),"")');
  sheet.getRange(r, 17, rows, 1).setNumberFormat('0.0%');

  markCalculated(sheet.getRange(r, 1, rows, 17));
  sheet.getRange(r, 2, rows, 16).setHorizontalAlignment('center');
  styleBlock(sheet, 4, 1, rows + 1, 17);

  const rules = [];
  [3, 4, 5, 6, 7, 17].forEach(col => {
    rules.push(ruleGradient(sheet.getRange(r, col, rows, 1)));
  });
  rules.push(ruleFormula(sheet.getRange(r, 1, rows, 17),
    '=TEXT($A' + r + ',"yyyy-mm")=TEXT(TODAY(),"yyyy-mm")', null, '#FFF9C4', false));
  sheet.setConditionalFormatRules(rules);

  footnote(sheet, r + rows + 2, 1, 17,
    'Habit percentages are "days marked Y" divided by "days you logged that habit", so skipping a day ' +
    'does not punish the rate. A month with nothing logged shows blank, not 0%. Income, Net Savings ' +
    'and Savings Rate mirror the Salary & Savings sheet.');

  sheet.setColumnWidth(1, 90);
  for (let c = 2; c <= 17; c++) sheet.setColumnWidth(c, 82);

  sheet.setFrozenRows(4);
  sheet.setHiddenGridlines(true);
  return sheet;
}
