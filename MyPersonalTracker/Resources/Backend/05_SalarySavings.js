/**
 * SALARY & SAVINGS · Income, remittance to India and savings rate.
 *
 * Two corrections to the source workbook, both of which changed the headline
 * numbers substantially:
 *
 *   1. Remittance is savings, not spend. The original computed
 *      Net Savings = Income - Expenses - Remittance, so money sent home was
 *      subtracted as if consumed. August read 13.9% against a 95% goal purely
 *      because 1,000 QAR had been sent to India. Controlled by the
 *      "Remittance counts as savings" switch in Settings.
 *
 *   2. Only months that have actually happened count. The original totalled
 *      12 months of salary — 11 of them unearned — and reported 27,985 QAR
 *      "net savings" and a 90.3% rate for a period 4 days into its log.
 *      Future rows still project, but the TOTAL row and every dashboard figure
 *      count months up to today only.
 */
function buildSalarySavings(ss) {
  const sheet = resetSheet(ss, SHEETS.SALARY);
  ensureGrid(sheet, SALARY_COLS.FIRST_ROW + SALARY_MONTHS + 8, 13);
  const rows = SALARY_MONTHS;
  const r = SALARY_COLS.FIRST_ROW;
  const lastRow = r + rows - 1;

  // See the note in buildDailyTracker: frozen columns are set before anything
  // merges, so the merge helpers can split at the boundary.
  freezeColumnsIfPossible(sheet, 1);

  titleBar(sheet, 'SALARY & SAVINGS  ·  Income, Money Sent Home and Savings Rate',
    'Expenses pull automatically from the Finance Tracker ledger. Yellow cells are yours.', 12, 1);

  headerRow(sheet, SALARY_COLS.HEADER_ROW, 1, [
    'Month', 'Salary\n(QAR)', 'Other Income\n(QAR)', 'Total Income\n(QAR)', 'Expenses\n(QAR)',
    'Sent\nHome (QAR)', 'Remittance\n(INR)', 'Net Savings\n(QAR)', 'Savings\nRate',
    'Goal Savings\n(QAR)', 'Cumulative\nSavings (QAR)', 'Status'
  ]);

  // Month column: first of each month, starting from the period start.
  fillFormula(sheet, r, SALARY_COLS.MONTH, rows,
    '=EOMONTH(' + cfg('START_DATE') + ',ROW()-' + r + '-1)+1');
  sheet.getRange(r, SALARY_COLS.MONTH, rows, 1)
    .setNumberFormat('mmm yyyy').setHorizontalAlignment('center').setFontWeight('bold');
  markCalculated(sheet.getRange(r, SALARY_COLS.MONTH, rows, 1));

  // Salary is typed per month: one month's pay never shows up in another.
  markInput(sheet.getRange(r, SALARY_COLS.SALARY, rows, 1));
  markInput(sheet.getRange(r, SALARY_COLS.OTHER, rows, 1));
  markInput(sheet.getRange(r, SALARY_COLS.REMIT_QAR, rows, 1));

  fillFormula(sheet, r, SALARY_COLS.TOTAL_INCOME, rows,
    '=N($B' + r + ')+N($C' + r + ')');

  // Expenses pull straight from the ledger for that calendar month.
  fillFormula(sheet, r, SALARY_COLS.EXPENSES, rows,
    '=SUMIFS(' + ftRange(FT.QAR) + ',' +
      ftRange(FT.DATE) + ',">="&$A' + r + ',' +
      ftRange(FT.DATE) + ',"<="&EOMONTH($A' + r + ',0))');

  fillFormula(sheet, r, SALARY_COLS.REMIT_INR, rows,
    '=N($F' + r + ')*' + cfg('RATE'));

  /**
   * Net savings.
   *
   * When remittance counts as savings it is NOT subtracted — the money still
   * belongs to you, it has only moved country. Set the Settings switch to N to
   * get the original behaviour.
   */
  fillFormula(sheet, r, SALARY_COLS.NET_SAVINGS, rows,
    '=$D' + r + '-$E' + r + '-IF(' + cfg('REMIT_AS_SAVING') + '="Y",0,N($F' + r + '))');

  fillFormula(sheet, r, SALARY_COLS.RATE, rows,
    '=IFERROR(IF($D' + r + '=0,"",$H' + r + '/$D' + r + '),"")');
  sheet.getRange(r, SALARY_COLS.RATE, rows, 1).setNumberFormat('0.0%');

  fillFormula(sheet, r, SALARY_COLS.GOAL, rows,
    '=$D' + r + '*' + cfg('SAVINGS_GOAL'));

  // Running total across every month, projected months included, as the
  // source workbook had it.
  fillFormula(sheet, r, SALARY_COLS.CUMULATIVE, rows,
    '=SUMIFS($H$' + r + ':$H$' + lastRow + ',' +
    '$A$' + r + ':$A$' + lastRow + ',"<="&$A' + r + ')');

  fillFormula(sheet, r, SALARY_COLS.STATUS, rows,
    '=IF($D' + r + '=0,"No income logged",' +
      'IF($I' + r + '>=' + cfg('SAVINGS_GOAL') + ',"Goal met","Below goal"))');

  [SALARY_COLS.TOTAL_INCOME, SALARY_COLS.EXPENSES, SALARY_COLS.NET_SAVINGS,
   SALARY_COLS.GOAL, SALARY_COLS.CUMULATIVE].forEach(col => {
    sheet.getRange(r, col, rows, 1).setNumberFormat('#,##0.00');
    markCalculated(sheet.getRange(r, col, rows, 1));
  });
  sheet.getRange(r, SALARY_COLS.REMIT_INR, rows, 1).setNumberFormat('#,##0');
  markCalculated(sheet.getRange(r, SALARY_COLS.REMIT_INR, rows, 1));
  markCalculated(sheet.getRange(r, SALARY_COLS.RATE, rows, 1));
  markCalculated(sheet.getRange(r, SALARY_COLS.STATUS, rows, 1));

  sheet.getRange(r, SALARY_COLS.SALARY, rows, 1).setNumberFormat('#,##0.00');
  sheet.getRange(r, SALARY_COLS.OTHER, rows, 1).setNumberFormat('#,##0.00');
  sheet.getRange(r, SALARY_COLS.REMIT_QAR, rows, 1).setNumberFormat('#,##0.00');
  sheet.getRange(r, 2, rows, 11).setHorizontalAlignment('center');
  sheet.getRange(r, SALARY_COLS.STATUS, rows, 1).setFontWeight('bold');

  styleBlock(sheet, SALARY_COLS.HEADER_ROW, 1, rows + 1, 12);

  // ---- Totals: every month, projections included -------------------------
  const tRow = lastRow + 1;

  sheet.getRange(tRow, 1).setValue('TOTAL / AVERAGE').setFontWeight('bold');
  [
    [SALARY_COLS.SALARY,       '=SUM($B$' + r + ':$B$' + lastRow + ')'],
    [SALARY_COLS.OTHER,        '=SUM($C$' + r + ':$C$' + lastRow + ')'],
    [SALARY_COLS.TOTAL_INCOME, '=SUM($D$' + r + ':$D$' + lastRow + ')'],
    [SALARY_COLS.EXPENSES,     '=SUM($E$' + r + ':$E$' + lastRow + ')'],
    [SALARY_COLS.REMIT_QAR,    '=SUM($F$' + r + ':$F$' + lastRow + ')'],
    [SALARY_COLS.REMIT_INR,    '=SUM($G$' + r + ':$G$' + lastRow + ')'],
    [SALARY_COLS.NET_SAVINGS,  '=SUM($H$' + r + ':$H$' + lastRow + ')'],
    [SALARY_COLS.GOAL,         '=SUM($J$' + r + ':$J$' + lastRow + ')']
  ].forEach(([col, formula]) => {
    sheet.getRange(tRow, col).setFormula(formula).setNumberFormat('#,##0.00');
  });
  sheet.getRange(tRow, SALARY_COLS.REMIT_INR).setNumberFormat('#,##0');
  sheet.getRange(tRow, SALARY_COLS.RATE)
    .setFormula('=IFERROR(IF($D$' + tRow + '=0,0,$H$' + tRow + '/$D$' + tRow + '),0)')
    .setNumberFormat('0.0%');
  sheet.getRange(tRow, SALARY_COLS.CUMULATIVE)
    .setFormula('=$H$' + tRow + '').setNumberFormat('#,##0.00');

  sheet.getRange(tRow, 1, 1, 12)
    .setBackground(THEME.HEADER_BG).setFontColor('#FFFFFF')
    .setFontWeight('bold').setHorizontalAlignment('center');
  sheet.getRange(tRow, 1).setHorizontalAlignment('left');

  const rules = [];
  const statusRange = sheet.getRange(r, SALARY_COLS.STATUS, rows, 1);
  rules.push(ruleTextEquals(statusRange, 'Goal met',   THEME.GOOD, THEME.GOOD_BG));
  rules.push(ruleTextEquals(statusRange, 'Below goal', THEME.BAD,  THEME.BAD_BG));
  rules.push(ruleTextEquals(statusRange, 'Upcoming',   THEME.MUTED, '#EDF1F7'));
  rules.push(ruleTextEquals(statusRange, 'No income logged', THEME.WARN, THEME.WARN_BG));
  rules.push(ruleFormula(sheet.getRange(r, 1, rows, 12),
    '=TEXT($A' + r + ',"yyyy-mm")=TEXT(TODAY(),"yyyy-mm")', null, '#FFF9C4', false));
  rules.push(ruleGradient(sheet.getRange(r, SALARY_COLS.RATE, rows, 1)));
  sheet.setConditionalFormatRules(rules);

  footnote(sheet, tRow + 2, 1, 12,
    'HOW TO USE  ·  Yellow columns are yours: Salary (type it for each month you were paid — ' +
    'nothing is filled in for you), Other Income, and money Sent Home. Expenses pull month by month straight from ' +
    'the Finance Tracker, so you never retype them. Remittance is treated as SAVINGS, not spending — ' +
    'money sent home still belongs to you, it has just moved. Flip "Remittance counts as ' +
    'savings" to N in Settings if you would rather subtract it. The TOTAL row counts only months that ' +
    'have actually started, so it never reports unearned future salary as money saved.');

  sheet.setColumnWidth(1, 90);
  for (let c = 2; c <= 11; c++) sheet.setColumnWidth(c, 100);
  sheet.setColumnWidth(12, 115);

  sheet.setFrozenRows(SALARY_COLS.HEADER_ROW);
  sheet.setHiddenGridlines(true);
  return sheet;
}

/**
 * Older workbooks filled every started month from the Settings salary by
 * formula, so one month's pay showed up in months nobody entered. Clears
 * those formula cells once; typed salaries are left alone.
 */
function clearSalaryAutofill_(ss) {
  const props = PropertiesService.getDocumentProperties();
  if (props.getProperty('SALARY_AUTOFILL_CLEARED') === '1') return;
  const settings = ss.getSheetByName(SHEETS.SETTINGS);
  const sheet = ss.getSheetByName(SHEETS.SALARY);
  if (!settings || !sheet) return;
  sheet.getRange(SALARY_COLS.FIRST_ROW, SALARY_COLS.SALARY, SALARY_MONTHS, 1).getFormulas().forEach((f, i) => {
    if (f[0]) sheet.getRange(SALARY_COLS.FIRST_ROW + i, SALARY_COLS.SALARY).setValue('');
  });
  settings.getRange(CFG.SALARY).offset(0, 1).setValue(SALARY_NOTE);
  props.setProperty('SALARY_AUTOFILL_CLEARED', '1');
}
