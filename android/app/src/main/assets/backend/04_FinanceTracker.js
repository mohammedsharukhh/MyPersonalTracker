/**
 * FINANCE TRACKER · Expense ledger, categories and daily budget control.
 *
 * Left block (A-H) is a free ledger: one row per purchase, as many rows a day
 * as you like. Right block (J-O) is generated per calendar day and calculates
 * itself from the ledger.
 *
 * The source workbook ran its ledger one-row-per-day, which forced a second
 * purchase on the same day into a different date's row. Here the two are
 * separated: log freely on the left, read the day-by-day truth on the right.
 */
function buildFinanceTracker(ss, dayCount) {
  const sheet = resetSheet(ss, SHEETS.FINANCE);
  const ledgerRows = FT.MAX_ROWS;
  const budgetRows = dayCount;

  // The ledger runs to row 1004 with the total, status and footnote beneath —
  // past the 1000 rows a new sheet ships with.
  ensureGrid(sheet, FT.FIRST_ROW + ledgerRows + 6, 16);

  titleBar(sheet, 'FINANCE TRACKER  ·  Expense Ledger, Categories & Daily Budget Control',
    'Log every purchase on the left. The day-by-day budget on the right calculates itself.', 15);

  // ---- Ledger -------------------------------------------------------------
  headerRow(sheet, FT.HEADER_ROW, 1, [
    'Date', 'Expense Description', 'Category', 'Payment Method',
    'Amount\n(QAR)', 'Amount\n(INR)', '% of\nPeriod Total', 'Notes'
  ]);

  markInput(sheet.getRange(FT.FIRST_ROW, FT.DATE, ledgerRows, FT.METHOD - FT.DATE + 1));
  markInput(sheet.getRange(FT.FIRST_ROW, FT.QAR, ledgerRows, 1));
  markInput(sheet.getRange(FT.FIRST_ROW, FT.NOTES, ledgerRows, 1));

  sheet.getRange(FT.FIRST_ROW, FT.DATE, ledgerRows, 1)
    .setNumberFormat('dd-mmm-yy').setHorizontalAlignment('center');
  sheet.getRange(FT.FIRST_ROW, FT.QAR, ledgerRows, 1).setNumberFormat('#,##0.00');

  listValidation(sheet, FT.FIRST_ROW, FT.CATEGORY, ledgerRows, CATEGORIES);
  listValidation(sheet, FT.FIRST_ROW, FT.METHOD, ledgerRows, PAYMENT_METHODS);

  const fr = FT.FIRST_ROW;

  fillFormula(sheet, fr, FT.INR, ledgerRows,
    '=IF($E' + fr + '="","",$E' + fr + '*' + cfg('RATE') + ')');
  sheet.getRange(fr, FT.INR, ledgerRows, 1).setNumberFormat('#,##0');

  fillFormula(sheet, fr, FT.PCT, ledgerRows,
    '=IF($E' + fr + '="","",IFERROR($E' + fr + '/SUM(' + ftRange(FT.QAR) + '),0))');
  sheet.getRange(fr, FT.PCT, ledgerRows, 1).setNumberFormat('0.0%');

  markCalculated(sheet.getRange(fr, FT.INR, ledgerRows, 2));
  sheet.getRange(fr, FT.INR, ledgerRows, 2).setHorizontalAlignment('center');
  sheet.getRange(fr, FT.NOTES, ledgerRows, 1).setFontSize(9);

  styleBlock(sheet, FT.HEADER_ROW, 1, ledgerRows + 1, FT.NOTES);

  // ---- Day-by-day budget control -----------------------------------------
  sectionLabel(sheet, 3, BUDGET.DATE, 6, 'DAY-BY-DAY BUDGET CONTROL  (calculated)');
  headerRow(sheet, BUDGET.HEADER_ROW, BUDGET.DATE, [
    'Date', 'Spent\n(QAR)', 'Spent\n(INR)', 'vs Daily\nLimit', 'Month-to-Date\n(QAR)', 'Budget Left\n(QAR)'
  ]);

  const br = BUDGET.FIRST_ROW;

  fillFormula(sheet, br, BUDGET.DATE, budgetRows,
    '=IF(ROW()-' + br + '>=' + cfg('END_DATE') + '-' + cfg('START_DATE') + '+1,"",' +
    cfg('START_DATE') + '+ROW()-' + br + ')');
  sheet.getRange(br, BUDGET.DATE, budgetRows, 1)
    .setNumberFormat('dd-mmm-yy').setHorizontalAlignment('center');

  fillFormula(sheet, br, BUDGET.SPENT_QAR, budgetRows,
    '=IF($J' + br + '="","",SUMIF(' + ftRange(FT.DATE) + ',$J' + br + ',' + ftRange(FT.QAR) + '))');
  sheet.getRange(br, BUDGET.SPENT_QAR, budgetRows, 1).setNumberFormat('#,##0.00');

  fillFormula(sheet, br, BUDGET.SPENT_INR, budgetRows,
    '=IF($K' + br + '="","",$K' + br + '*' + cfg('RATE') + ')');
  sheet.getRange(br, BUDGET.SPENT_INR, budgetRows, 1).setNumberFormat('#,##0');

  // Every dated day is judged, as the source workbook did.
  fillFormula(sheet, br, BUDGET.VS_LIMIT, budgetRows,
    '=IF($J' + br + '="","",IF($K' + br + '>' + cfg('DAILY_LIMIT') + ',"OVER","OK"))');
  sheet.getRange(br, BUDGET.VS_LIMIT, budgetRows, 1)
    .setHorizontalAlignment('center').setFontWeight('bold');

  // Month-to-date resets each calendar month.
  fillFormula(sheet, br, BUDGET.MTD, budgetRows,
    '=IF($J' + br + '="","",SUMIFS(' + ftRange(FT.QAR) + ',' +
      ftRange(FT.DATE) + ',">="&EOMONTH($J' + br + ',-1)+1,' +
      ftRange(FT.DATE) + ',"<="&$J' + br + '))');
  sheet.getRange(br, BUDGET.MTD, budgetRows, 1).setNumberFormat('#,##0.00');

  // Month-to-date against the typed monthly limit from Settings.
  fillFormula(sheet, br, BUDGET.LEFT, budgetRows,
    '=IF($J' + br + '="","",' + cfg('MONTHLY_LIMIT') + '-$N' + br + ')');
  sheet.getRange(br, BUDGET.LEFT, budgetRows, 1).setNumberFormat('#,##0.00;[Red](#,##0.00)');

  markCalculated(sheet.getRange(br, BUDGET.DATE, budgetRows, 6));
  sheet.getRange(br, BUDGET.SPENT_QAR, budgetRows, 5).setHorizontalAlignment('center');
  styleBlock(sheet, BUDGET.HEADER_ROW, BUDGET.DATE, budgetRows + 1, 6);

  // ---- Period total -------------------------------------------------------
  const totalRow = FT.FIRST_ROW + ledgerRows + 1;
  sheet.getRange(totalRow, 1, 1, 4).merge().setValue('PERIOD TOTAL')
    .setFontWeight('bold').setFontColor('#FFFFFF').setBackground(THEME.HEADER_BG);
  sheet.getRange(totalRow, FT.QAR).setFormula('=SUM(' + ftRange(FT.QAR) + ')')
    .setNumberFormat('#,##0.00');
  sheet.getRange(totalRow, FT.INR).setFormula('=SUM(' + ftRange(FT.INR) + ')')
    .setNumberFormat('#,##0');
  sheet.getRange(totalRow, FT.PCT).setFormula('=IF(E' + totalRow + '=0,0,1)')
    .setNumberFormat('0.0%');
  sheet.getRange(totalRow, FT.QAR, 1, 3)
    .setFontWeight('bold').setBackground(THEME.HEADER_BG).setFontColor('#FFFFFF')
    .setHorizontalAlignment('center');
  sheet.getRange(totalRow, FT.NOTES).setBackground(THEME.HEADER_BG);

  const statusRow = totalRow + 1;
  sheet.getRange(statusRow, 1, 1, 4).merge().setValue('Budget Status')
    .setFontWeight('bold').setFontSize(9);
  sheet.getRange(statusRow, FT.QAR, 1, 4).merge()
    .setFormula(
      '=LET(spend,SUMIFS(' + ftRange(FT.QAR) + ',' + ftRange(FT.DATE) + ',">="&EOMONTH(TODAY(),-1)+1,' +
      ftRange(FT.DATE) + ',"<="&EOMONTH(TODAY(),0)),' +
      'budget,' + cfg('MONTHLY_LIMIT') + ',' +
      'IF(spend>budget,"OVER this month\'s limit by "&TEXT(spend-budget,"#,##0.00")&" QAR",' +
      '"Within budget — "&TEXT(budget-spend,"#,##0.00")&" QAR left this month"))')
    .setFontWeight('bold').setFontSize(9);

  const rules = [];
  const vsRange = sheet.getRange(br, BUDGET.VS_LIMIT, budgetRows, 1);
  rules.push(ruleTextEquals(vsRange, 'OVER', THEME.BAD, THEME.BAD_BG));
  rules.push(ruleTextEquals(vsRange, 'OK',   THEME.GOOD, THEME.GOOD_BG));
  rules.push(ruleFormula(sheet.getRange(br, BUDGET.LEFT, budgetRows, 1),
    '=AND($O' + br + '<>"",$O' + br + '<0)', THEME.BAD, THEME.BAD_BG, true));
  rules.push(ruleFormula(sheet.getRange(br, BUDGET.DATE, budgetRows, 6),
    '=$J' + br + '=TODAY()', null, '#FFF9C4', false));
  rules.push(ruleFormula(sheet.getRange(statusRow, FT.QAR, 1, 4),
    '=LEFT($E$' + statusRow + ',4)="OVER"', THEME.BAD, THEME.BAD_BG, true));
  rules.push(ruleFormula(sheet.getRange(statusRow, FT.QAR, 1, 4),
    '=LEFT($E$' + statusRow + ',6)="Within"', THEME.GOOD, THEME.GOOD_BG, true));
  sheet.setConditionalFormatRules(rules);

  footnote(sheet, statusRow + 2, 1, FT.NOTES,
    'HOW TO USE  ·  Add one row per purchase in the yellow columns — as many rows a day as you like. ' +
    'Category and Payment Method are dropdowns. The home-currency amount, share of total and the whole day-by-day block on ' +
    'the right calculate themselves. A day above the daily limit shows OVER; "Budget Left" turns red ' +
    'once the month\'s spend passes that month\'s allowance (daily limit x days in the month). Use ' +
    '"Life & Finance ▸ Add expense" for a quick mobile-friendly entry form.');

  sheet.setColumnWidth(FT.DATE, 85);
  sheet.setColumnWidth(FT.DESC, 200);
  sheet.setColumnWidth(FT.CATEGORY, 130);
  sheet.setColumnWidth(FT.METHOD, 140);
  sheet.setColumnWidth(FT.QAR, 80);
  sheet.setColumnWidth(FT.INR, 80);
  sheet.setColumnWidth(FT.PCT, 75);
  sheet.setColumnWidth(FT.NOTES, 180);
  sheet.setColumnWidth(9, 22);
  sheet.setColumnWidth(BUDGET.DATE, 85);
  sheet.setColumnWidth(BUDGET.SPENT_QAR, 72);
  sheet.setColumnWidth(BUDGET.SPENT_INR, 72);
  sheet.setColumnWidth(BUDGET.VS_LIMIT, 68);
  sheet.setColumnWidth(BUDGET.MTD, 95);
  sheet.setColumnWidth(BUDGET.LEFT, 90);

  sheet.setFrozenRows(FT.HEADER_ROW);
  sheet.setHiddenGridlines(true);
  return sheet;
}
