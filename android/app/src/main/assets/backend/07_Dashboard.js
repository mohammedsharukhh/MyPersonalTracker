/**
 * DASHBOARD · The single read-only overview.
 *
 * Nothing here is typed. Every card and table reads from the tracker sheets,
 * so this page is always in step with the log.
 *
 * Figures mirror the source workbook exactly, including its treatment of
 * unlogged days and projected months.
 */
function buildDashboard(ss, dayCount) {
  const sheet = resetSheet(ss, SHEETS.DASHBOARD);
  // Cards, four tables, four charts, then the read-me.
  ensureGrid(sheet, 70, 17);
  const dtLast = DT.FIRST_ROW + dayCount - 1;
  const sr = SALARY_COLS.FIRST_ROW;
  const sLast = sr + SALARY_MONTHS - 1;
  const salaryTotalRow = sLast + 1;

  titleBar(sheet, 'LIFE & FINANCE COMMAND CENTER',
    '=' + '"Period: "&TEXT(' + cfg('START_DATE') + ',"dd-mmm-yyyy")&"  to  "&TEXT(' +
    cfg('END_DATE') + ',"dd-mmm-yyyy")&"     |     Day "&' +
    'MAX(0,MIN(TODAY(),' + cfg('END_DATE') + ')-' + cfg('START_DATE') + '+1)&" of "&(' +
    cfg('END_DATE') + '-' + cfg('START_DATE') + '+1)&' +
    '"     |     Rate: 1 QAR = "&TEXT(' + cfg('RATE') + ',"0.00")&" INR"&' +
    '"     |     Daily cap: "&TEXT(' + cfg('DAILY_LIMIT') + ',"0.00")&" QAR"', 16);
  // The subtitle is a formula, so it has to be set as one.
  sheet.getRange(2, 1, 1, 16).merge();
  sheet.getRange(2, 1).setFormula(
    '="Period: "&TEXT(' + cfg('START_DATE') + ',"dd-mmm-yyyy")&"  to  "&TEXT(' +
    cfg('END_DATE') + ',"dd-mmm-yyyy")&"     |     Day "&' +
    'MAX(0,MIN(TODAY(),' + cfg('END_DATE') + ')-' + cfg('START_DATE') + '+1)&" of "&(' +
    cfg('END_DATE') + '-' + cfg('START_DATE') + '+1)&' +
    '"     |     Rate: 1 QAR = "&TEXT(' + cfg('RATE') + ',"0.00")&" INR"&' +
    '"     |     Daily cap: "&TEXT(' + cfg('DAILY_LIMIT') + ',"0.00")&" QAR"');

  const scoreR  = dtRange(DT.SCORE, dtLast);
  const statusR = dtRange(DT.STATUS, dtLast);
  const streakR = dtRange(DT.STREAK, dtLast);
  const dateR   = dtRange(DT.DATE, dtLast);
  const qarR    = ftRange(FT.QAR);
  const ftDateR = ftRange(FT.DATE);

  // Days elapsed, capped at the period end — the divisor for "average daily".
  const elapsed = 'MAX(1,MIN(TODAY(),' + cfg('END_DATE') + ')-' + cfg('START_DATE') + '+1)';

  // ---- KPI cards, two rows of four ---------------------------------------
  const cards = [
    // row 4
    [4, 1,  'OVERALL HABIT SCORE', '=IFERROR(AVERAGE(' + scoreR + '),0)',
     'average of your daily scores', THEME.SUBHEADER_BG, '0.0%'],
    [4, 5,  'CURRENT STREAK', '=IFERROR(LOOKUP(2,1/(' + streakR + '<>""),' + streakR + '),0)',
     'consecutive great days', '#1E7B34', '#,##0'],
    [4, 9,  'DAYS LOGGED', '=COUNT(' + scoreR + ')',
     'days with entries', '#0F6674', '#,##0'],
    [4, 13, 'EXCELLENT DAYS', '=COUNTIF(' + statusR + ',"Excellent")',
     'days at or above the threshold', '#6B3FA0', '#,##0'],
    // row 8
    [8, 1,  'TOTAL SPEND (QAR)', '=SUM(' + qarR + ')',
     'period to date', '#9C6B0B', '#,##0.00'],
    [8, 5,  'TOTAL SPEND (INR)', '=SUM(' + qarR + ')*' + cfg('RATE'),
     'at the rate in Settings', '#9C6B0B', '#,##0'],
    [8, 9,  'AVG DAILY SPEND', '=IFERROR(SUM(' + qarR + ')/' + elapsed + ',0)',
     'per elapsed day (QAR)', '#0F6674', '#,##0.00'],
    [8, 13, 'DAYS OVER LIMIT',
     '=SUMPRODUCT((' + "'" + SHEETS.FINANCE + "'!$K$" + BUDGET.FIRST_ROW + ':$K$' +
       (BUDGET.FIRST_ROW + dayCount - 1) + '>' + cfg('DAILY_LIMIT') + ')*(' +
       "'" + SHEETS.FINANCE + "'!$J$" + BUDGET.FIRST_ROW + ':$J$' +
       (BUDGET.FIRST_ROW + dayCount - 1) + '<=TODAY()))',
     'elapsed days above the cap', THEME.BAD, '#,##0'],
    // row 12
    [12, 1,  'AVG WATER (L / DAY)', '=IFERROR(AVERAGE(' + dtRange(DT.WATER, dtLast) + '),0)',
     'target set in Settings', '#1B5E8C', '0.0'],
    [12, 5,  'AVG SLEEP (HOURS)', '=IFERROR(AVERAGE(' + dtRange(DT.SLEEP, dtLast) + '),0)',
     'target set in Settings', '#4A3C8C', '0.0'],
    [12, 9,  'BUDGET LEFT (QAR)',
     '=' + cfg('MONTHLY_LIMIT') + '-SUMIFS(' + qarR + ',' +
       ftDateR + ',">="&EOMONTH(TODAY(),-1)+1,' + ftDateR + ',"<="&EOMONTH(TODAY(),0))',
     'monthly limit minus spend', '#0F6674', '#,##0.00;[Red](#,##0.00)'],
    [12, 13, 'NET SAVINGS (QAR)',
     '=' + "'" + SHEETS.SALARY + "'!$H$" + salaryTotalRow,
     'all months combined', '#1E7B34', '#,##0.00']
  ];
  cards.forEach(c => kpiCard(sheet, c[0], c[1], c[2], c[3], c[4], c[5], c[6]));

  buildDashboardTables(sheet, dayCount, dtLast, salaryTotalRow);
  buildDashboardCharts(sheet, dayCount, dtLast);

  footnote(sheet, 52, 1, 16,
    'READ ME  ·  Nothing on this page is typed — it all calculates. Log your day on "Daily Tracker" ' +
    'and every purchase on "Finance Tracker"; put salary and remittance on "Salary & Savings". ' +
    'Targets, spending limits, the exchange rate and the category list all live on "Settings" — change ' +
    'them there and every sheet follows. Use the "Life & Finance" menu for quick entry forms and to ' +
    'rebuild tracker rows after changing the period.');

  for (let c = 1; c <= 16; c++) sheet.setColumnWidth(c, 88);
  sheet.setHiddenGridlines(true);
  sheet.setFrozenRows(2);
  return sheet;
}

/** The four breakdown tables under the KPI cards. */
function buildDashboardTables(sheet, dayCount, dtLast, salaryTotalRow) {
  const top = 16;
  const sr = SALARY_COLS.FIRST_ROW;
  const sLast = sr + SALARY_MONTHS - 1;

  // ---- Habit success rates (mirrors the Daily Tracker summary) ------------
  sectionLabel(sheet, top, 1, 4, 'HABIT SUCCESS RATES');
  headerRow(sheet, top + 1, 1, ['Habit', 'Logged', 'Done', 'Rate']);

  const habits = [
    ['In Office by 9:00 AM', DT.OFFICE,    'Y'],
    ['Breakfast Done',       DT.BREAKFAST, 'Y'],
    ['Lunch Taken',          DT.LUNCH,     'Y'],
    ['Protein in Lunch',     DT.PROTEIN,   'Y'],
    ['Workout Done',         DT.WORKOUT,   'Y']
  ];
  let row = top + 2;
  habits.forEach(([label, col]) => {
    const range = dtRange(col, dtLast);
    sheet.getRange(row, 1).setValue(label).setFontSize(9);
    sheet.getRange(row, 2).setFormula('=COUNTA(' + range + ')');
    sheet.getRange(row, 3).setFormula('=COUNTIF(' + range + ',"Y")');
    sheet.getRange(row, 4).setFormula('=IFERROR(C' + row + '/B' + row + ',0)').setNumberFormat('0.0%');
    row++;
  });
  [['Water Target Met', DT.WATER, '>=', 'WATER_TARGET'],
   ['Sleep Target Met', DT.SLEEP, '>=', 'SLEEP_TARGET'],
   ['Steps Target Met', DT.STEPS, '>=', 'STEPS_TARGET'],
   ['Screen Time In Limit', DT.SCREEN, '<=', 'SCREEN_LIMIT']].forEach(([label, col, op, setting]) => {
    const range = dtRange(col, dtLast);
    sheet.getRange(row, 1).setValue(label).setFontSize(9);
    sheet.getRange(row, 2).setFormula('=COUNT(' + range + ')');
    sheet.getRange(row, 3).setFormula('=COUNTIF(' + range + ',"' + op + '"&' + cfg(setting) + ')');
    sheet.getRange(row, 4).setFormula('=IFERROR(C' + row + '/B' + row + ',0)').setNumberFormat('0.0%');
    row++;
  });
  const habitRows = row - (top + 2);
  sheet.getRange(top + 2, 2, habitRows, 3).setHorizontalAlignment('center');
  markCalculated(sheet.getRange(top + 2, 2, habitRows, 3));
  styleBlock(sheet, top + 1, 1, habitRows + 1, 4);

  // ---- Spend by category --------------------------------------------------
  sectionLabel(sheet, top, 6, 4, 'SPEND BY CATEGORY');
  headerRow(sheet, top + 1, 6, ['Category', 'QAR', 'INR', '% of Total']);

  const catStart = top + 2;
  sheet.getRange(catStart, 6, CATEGORIES.length, 1)
    .setValues(CATEGORIES.map(c => [c])).setFontSize(9);

  fillFormula(sheet, catStart, 7, CATEGORIES.length,
    '=SUMIF(' + ftRange(FT.CATEGORY) + ',$F' + catStart + ',' + ftRange(FT.QAR) + ')');
  sheet.getRange(catStart, 7, CATEGORIES.length, 1).setNumberFormat('#,##0.00');

  fillFormula(sheet, catStart, 8, CATEGORIES.length, '=$G' + catStart + '*' + cfg('RATE'));
  sheet.getRange(catStart, 8, CATEGORIES.length, 1).setNumberFormat('#,##0');

  fillFormula(sheet, catStart, 9, CATEGORIES.length,
    '=IFERROR($G' + catStart + '/SUM($G$' + catStart + ':$G$' + (catStart + CATEGORIES.length - 1) + '),0)');
  sheet.getRange(catStart, 9, CATEGORIES.length, 1).setNumberFormat('0.0%');

  const catTotal = catStart + CATEGORIES.length;
  sheet.getRange(catTotal, 6).setValue('TOTAL').setFontWeight('bold');
  sheet.getRange(catTotal, 7)
    .setFormula('=SUM($G$' + catStart + ':$G$' + (catTotal - 1) + ')').setNumberFormat('#,##0.00');
  sheet.getRange(catTotal, 8)
    .setFormula('=SUM($H$' + catStart + ':$H$' + (catTotal - 1) + ')').setNumberFormat('#,##0');
  sheet.getRange(catTotal, 9)
    .setFormula('=IF($G$' + catTotal + '=0,0,1)').setNumberFormat('0.0%');
  sheet.getRange(catTotal, 6, 1, 4)
    .setFontWeight('bold').setBackground(THEME.HEADER_BG).setFontColor('#FFFFFF');

  markCalculated(sheet.getRange(catStart, 7, CATEGORIES.length, 3));
  sheet.getRange(catStart, 7, CATEGORIES.length, 3).setHorizontalAlignment('center');
  styleBlock(sheet, top + 1, 6, CATEGORIES.length + 2, 4);

  // An expense with no category would otherwise vanish from this table.
  sheet.getRange(catTotal + 1, 6, 1, 4).merge()
    .setFormula('="Uncategorised: "&TEXT(SUMIF(' + ftRange(FT.CATEGORY) + ',"",' +
                ftRange(FT.QAR) + '),"#,##0.00")&" QAR"')
    .setFontSize(8).setFontStyle('italic').setFontColor(THEME.MUTED);

  // ---- This period at a glance -------------------------------------------
  sectionLabel(sheet, top, 11, 2, 'THIS PERIOD AT A GLANCE');
  headerRow(sheet, top + 1, 11, ['Metric', 'Value']);

  const streakR = dtRange(DT.STREAK, dtLast);
  const budgetDateCol = "'" + SHEETS.FINANCE + "'!$J$" + BUDGET.FIRST_ROW + ':$J$' +
                        (BUDGET.FIRST_ROW + dayCount - 1);
  const budgetSpentCol = "'" + SHEETS.FINANCE + "'!$K$" + BUDGET.FIRST_ROW + ':$K$' +
                         (BUDGET.FIRST_ROW + dayCount - 1);

  const glance = [
    ['Best streak (days)',        '=IFERROR(MAX(' + streakR + '),0)', '#,##0'],
    ['On track days',             '=COUNTIF(' + dtRange(DT.STATUS, dtLast) + ',"On Track")', '#,##0'],
    ['Needs work days',           '=COUNTIF(' + dtRange(DT.STATUS, dtLast) + ',"Needs Work")', '#,##0'],
    ['Average steps per day',     '=IFERROR(AVERAGE(' + dtRange(DT.STEPS, dtLast) + '),0)', '#,##0'],
    ['Average screen time (hrs)', '=IFERROR(AVERAGE(' + dtRange(DT.SCREEN, dtLast) + '),0)', '0.0'],
    ['Latest weight (kg)',
     '=IFERROR(LOOKUP(2,1/(' + dtRange(DT.WEIGHT, dtLast) + '<>""),' +
       dtRange(DT.WEIGHT, dtLast) + '),0)', '0.0'],
    ['Expense entries logged',    '=COUNT(' + ftRange(FT.QAR) + ')', '#,##0'],
    // Every dated day with no spend, as the source workbook counted it.
    ['Zero-spend days so far',
     '=SUMPRODUCT((' + budgetSpentCol + '=0)*(' + budgetDateCol + '<>""))', '#,##0'],
    ['Highest single expense (QAR)', '=IFERROR(MAX(' + ftRange(FT.QAR) + '),0)', '#,##0.00'],
    ['Biggest spend day (QAR)',   '=IFERROR(MAX(' + budgetSpentCol + '),0)', '#,##0.00']
  ];
  glance.forEach((g, i) => {
    const gRow = top + 2 + i;
    sheet.getRange(gRow, 11).setValue(g[0]).setFontSize(9);
    sheet.getRange(gRow, 12).setFormula(g[1]).setNumberFormat(g[2])
      .setHorizontalAlignment('center').setFontWeight('bold');
  });
  markCalculated(sheet.getRange(top + 2, 12, glance.length, 1));
  styleBlock(sheet, top + 1, 11, glance.length + 1, 2);

  // ---- Money snapshot -----------------------------------------------------
  sectionLabel(sheet, top, 14, 2, 'MONEY SNAPSHOT');
  headerRow(sheet, top + 1, 14, ['Metric', 'Value']);

  const S = "'" + SHEETS.SALARY + "'!";
  const money = [
    ['Total income (QAR)',      '=' + S + '$D$' + salaryTotalRow, '#,##0.00'],
    ['Total expenses (QAR)',    '=' + S + '$E$' + salaryTotalRow, '#,##0.00'],
    ['Remittance sent (QAR)',   '=' + S + '$F$' + salaryTotalRow, '#,##0.00'],
    ['Remittance sent (INR)',   '=' + S + '$G$' + salaryTotalRow, '#,##0'],
    ['Net savings (QAR)',       '=' + S + '$H$' + salaryTotalRow, '#,##0.00'],
    ['Savings rate',            '=' + S + '$I$' + salaryTotalRow, '0.0%'],
    ['Savings goal',            '=' + cfg('SAVINGS_GOAL'), '0.0%'],
    ['This month\'s allowance (QAR)',
     '=' + cfg('MONTHLY_LIMIT'), '#,##0.00'],
    ['Spent this month (QAR)',
     '=SUMIFS(' + ftRange(FT.QAR) + ',' + ftRange(FT.DATE) + ',">="&EOMONTH(TODAY(),-1)+1,' +
       ftRange(FT.DATE) + ',"<="&EOMONTH(TODAY(),0))', '#,##0.00'],
    ['Spend vs allowance',
     '=IFERROR(O' + (top + 10) + '/O' + (top + 9) + ',0)', '0.0%'],
    // Projects the current month from its average daily spend so far.
    ['Projected month-end (QAR)',
     '=IFERROR(O' + (top + 10) + '/MAX(1,DAY(TODAY()))*DAY(EOMONTH(TODAY(),0)),0)', '#,##0.00']
  ];
  money.forEach((m, i) => {
    const mRow = top + 2 + i;
    sheet.getRange(mRow, 14).setValue(m[0]).setFontSize(9);
    sheet.getRange(mRow, 15).setFormula(m[1]).setNumberFormat(m[2])
      .setHorizontalAlignment('center').setFontWeight('bold');
  });
  markCalculated(sheet.getRange(top + 2, 15, money.length, 1));
  styleBlock(sheet, top + 1, 14, money.length + 1, 2);

  const rules = [];
  rules.push(ruleGradient(sheet.getRange(top + 2, 4, habitRows, 1)));
  // Spend-vs-allowance: red over 100%, green under.
  const vsRow = top + 2 + 9;
  rules.push(ruleFormula(sheet.getRange(vsRow, 15), '=$O$' + vsRow + '>1', THEME.BAD, THEME.BAD_BG, true));
  rules.push(ruleFormula(sheet.getRange(vsRow, 15), '=$O$' + vsRow + '<=1', THEME.GOOD, null, true));
  sheet.setConditionalFormatRules(
    sheet.getConditionalFormatRules().concat(rules));
}

/** The four charts, matching the source workbook's layout. */
function buildDashboardCharts(sheet, dayCount, dtLast) {
  const ss = sheet.getParent();
  const daily = ss.getSheetByName(SHEETS.DAILY);
  const finance = ss.getSheetByName(SHEETS.FINANCE);
  const weekly = ss.getSheetByName(SHEETS.WEEKLY);

  const chartTop = 34;

  // Daily habit score, line.
  sheet.insertChart(sheet.newChart()
    .setChartType(Charts.ChartType.LINE)
    .addRange(daily.getRange(DT.FIRST_ROW, DT.DATE, dayCount, 1))
    .addRange(daily.getRange(DT.FIRST_ROW, DT.SCORE, dayCount, 1))
    .setPosition(chartTop, 1, 0, 0)
    .setOption('title', 'Daily Habit Score')
    .setOption('titleTextStyle', { fontSize: 12, bold: true })
    .setOption('width', 620).setOption('height', 260)
    .setOption('legend', { position: 'none' })
    .setOption('colors', ['#2F5597'])
    .setOption('vAxis', { format: 'percent', viewWindow: { min: 0, max: 1 } })
    .setOption('hAxis', { format: 'dd-MMM', slantedText: true, slantedTextAngle: 60 })
    .setOption('curveType', 'none')
    .setOption('pointSize', 3)
    .build());

  // Daily spend, column.
  sheet.insertChart(sheet.newChart()
    .setChartType(Charts.ChartType.COLUMN)
    .addRange(finance.getRange(BUDGET.FIRST_ROW, BUDGET.DATE, dayCount, 1))
    .addRange(finance.getRange(BUDGET.FIRST_ROW, BUDGET.SPENT_QAR, dayCount, 1))
    .setPosition(chartTop, 9, 0, 0)
    .setOption('title', 'Daily Spend (QAR)')
    .setOption('titleTextStyle', { fontSize: 12, bold: true })
    .setOption('width', 620).setOption('height', 260)
    .setOption('legend', { position: 'none' })
    .setOption('colors', ['#B7791F'])
    .setOption('hAxis', { format: 'dd-MMM', slantedText: true, slantedTextAngle: 60 })
    .build());

  // Spend by category, horizontal bar.
  const catStart = 18;
  sheet.insertChart(sheet.newChart()
    .setChartType(Charts.ChartType.BAR)
    .addRange(sheet.getRange(catStart, 6, CATEGORIES.length, 1))
    .addRange(sheet.getRange(catStart, 7, CATEGORIES.length, 1))
    .setPosition(chartTop + 14, 1, 0, 0)
    .setOption('title', 'Spend by Category (QAR)')
    .setOption('titleTextStyle', { fontSize: 12, bold: true })
    .setOption('width', 620).setOption('height', 300)
    .setOption('legend', { position: 'none' })
    .setOption('colors', ['#0F6674'])
    .build());

  // Weekly habit score, column.
  sheet.insertChart(sheet.newChart()
    .setChartType(Charts.ChartType.COLUMN)
    .addRange(weekly.getRange(5, 2, WEEK_ROWS, 1))
    .addRange(weekly.getRange(5, 5, WEEK_ROWS, 1))
    .setPosition(chartTop + 14, 9, 0, 0)
    .setOption('title', 'Weekly Habit Score')
    .setOption('titleTextStyle', { fontSize: 12, bold: true })
    .setOption('width', 620).setOption('height', 300)
    .setOption('legend', { position: 'none' })
    .setOption('colors', ['#6B3FA0'])
    .setOption('vAxis', { format: 'percent', viewWindow: { min: 0, max: 1 } })
    .setOption('hAxis', { format: 'dd-MMM' })
    .build());
}
