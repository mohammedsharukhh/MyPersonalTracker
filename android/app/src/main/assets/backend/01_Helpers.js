/**
 * Shared formatting and formula helpers.
 *
 * The sheet builders below read as layout descriptions rather than a wall of
 * setBackground/setFontWeight chains because the repetitive parts live here.
 */

/** Wipes a sheet and returns it, creating it if absent. */
function resetSheet(ss, name) {
  let sheet = ss.getSheetByName(name);
  if (sheet) {
    sheet.clear();
    sheet.clearConditionalFormatRules();
    // Charts and notes survive clear(), so they are removed explicitly —
    // otherwise a rebuild stacks a second copy of every chart on the sheet.
    sheet.getCharts().forEach(c => sheet.removeChart(c));
    const filter = sheet.getFilter();
    if (filter) filter.remove();
  } else {
    sheet = ss.insertSheet(name);
  }
  return sheet;
}

/**
 * The dark title bar every sheet opens with.
 *
 * `frozenCols` matters: Google Sheets refuses to freeze columns that would cut
 * a merged cell in half ("you can't freeze columns which contain only part of
 * a merged cell"). So on a sheet with frozen columns the banner is merged in
 * two segments — one covering the frozen columns, one covering the rest —
 * which looks identical because both carry the same background, while leaving
 * the freeze boundary on a clean edge.
 */
function titleBar(sheet, title, subtitle, width, frozenCols) {
  const split = frozenCols || 0;

  const paint = (row, text, bg, fontSize, bold) => {
    const segments = split > 0 && split < width
      ? [[1, split], [split + 1, width - split]]
      : [[1, width]];

    segments.forEach(([startCol, numCols], i) => {
      const range = sheet.getRange(row, startCol, 1, numCols);
      if (numCols > 1) range.merge();
      range.setBackground(bg)
        .setFontColor(THEME.HEADER_TEXT)
        .setFontSize(fontSize)
        .setVerticalAlignment('middle');
      if (bold) range.setFontWeight('bold');
      // The text lives in the first segment; the second is a colour continuation.
      if (i === 0) range.setValue(text);
    });
  };

  paint(1, title, THEME.HEADER_BG, 14, true);
  sheet.setRowHeight(1, 34);

  paint(2, subtitle, THEME.SUBHEADER_BG, 9, false);
  sheet.setRowHeight(2, 20);
}

/** A table header row. */
function headerRow(sheet, row, col, labels) {
  const range = sheet.getRange(row, col, 1, labels.length);
  range.setValues([labels])
    .setBackground(THEME.TABLE_HEAD_BG)
    .setFontColor(THEME.HEADER_TEXT)
    .setFontWeight('bold')
    .setFontSize(9)
    .setHorizontalAlignment('center')
    .setVerticalAlignment('middle')
    .setWrap(true)
    .setBorder(true, true, true, true, true, true, THEME.BORDER, SpreadsheetApp.BorderStyle.SOLID);
  sheet.setRowHeight(row, 32);
  return range;
}

/**
 * Grows the sheet so `rows` x `cols` exists.
 *
 * A new Google Sheet is 1000 x 26. The Finance ledger alone wants 1000 rows
 * plus a total row and a footnote beneath it, so writing to those cells fails
 * with "coordinates of the range are outside the dimensions of the sheet"
 * unless the grid is extended first. Shrinking is deliberately not done here —
 * that would destroy data.
 */
function ensureGrid(sheet, rows, cols) {
  const maxRows = sheet.getMaxRows();
  if (rows > maxRows) sheet.insertRowsAfter(maxRows, rows - maxRows);

  const maxCols = sheet.getMaxColumns();
  if (cols > maxCols) sheet.insertColumnsAfter(maxCols, cols - maxCols);
}

/**
 * Freezes columns, tolerating a layout that forbids it.
 *
 * Sheets refuses to freeze a boundary that would split a merged cell. Frozen
 * columns are a convenience — being able to see the date while scrolling
 * sideways — so a layout that cannot support them should lose the freeze, not
 * fail the whole build.
 */
function freezeColumnsIfPossible(sheet, count) {
  try {
    sheet.setFrozenColumns(count);
  } catch (e) {
    console.log('Skipped frozen columns on "' + sheet.getName() + '": ' + e.message);
  }
}

/** Marks a range as user-input (yellow) or calculated (grey). */
function markInput(range)      { range.setBackground(THEME.INPUT_BG); }
function markCalculated(range) { range.setBackground(THEME.CALC_BG).setFontColor(THEME.MUTED); }

/** Light banding + borders on a data block. */
function styleBlock(sheet, row, col, numRows, numCols) {
  sheet.getRange(row, col, numRows, numCols)
    .setBorder(true, true, true, true, true, true, THEME.BORDER, SpreadsheetApp.BorderStyle.SOLID)
    .setFontSize(9)
    .setVerticalAlignment('middle');
}

/**
 * Fills a column with one formula, pattern-filled down.
 *
 * Apps Script's setFormula translates relative references per row, so writing
 * the first row and copying beats building N formula strings in a loop — both
 * to read and to run.
 */
function fillFormula(sheet, row, col, numRows, formula) {
  const first = sheet.getRange(row, col);
  first.setFormula(formula);
  if (numRows > 1) {
    first.copyTo(sheet.getRange(row + 1, col, numRows - 1, 1));
  }
  return sheet.getRange(row, col, numRows, 1);
}

/** A KPI card: coloured label bar, big number, caption. */
function kpiCard(sheet, row, col, label, formula, caption, color, numberFormat) {
  sheet.getRange(row, col, 1, 2).merge()
    .setValue(label)
    .setBackground(color)
    .setFontColor('#FFFFFF')
    .setFontWeight('bold')
    .setFontSize(9)
    .setVerticalAlignment('middle')
    .setHorizontalAlignment('left');
  sheet.setRowHeight(row, 22);

  const value = sheet.getRange(row + 1, col, 1, 2).merge();
  value.setFormula(formula)
    .setFontSize(18)
    .setFontWeight('bold')
    .setHorizontalAlignment('center')
    .setVerticalAlignment('middle')
    .setBackground('#FFFFFF')
    .setFontColor('#1A1A1A');
  if (numberFormat) value.setNumberFormat(numberFormat);
  sheet.setRowHeight(row + 1, 40);

  sheet.getRange(row + 2, col, 1, 2).merge()
    .setValue(caption)
    .setFontSize(8)
    .setFontColor(THEME.MUTED)
    .setHorizontalAlignment('center')
    .setVerticalAlignment('middle')
    .setBackground('#FFFFFF');
  sheet.setRowHeight(row + 2, 16);

  sheet.getRange(row, col, 3, 2)
    .setBorder(true, true, true, true, null, null, THEME.CARD_BORDER, SpreadsheetApp.BorderStyle.SOLID);
}

/**
 * Merges a strip without crossing the sheet's frozen-column boundary.
 *
 * A merge that spans the boundary makes the freeze illegal — anywhere on the
 * sheet, not just in the frozen rows. Splitting the merge at the boundary
 * keeps both. Returns the range holding the text (the first segment).
 */
function mergeRespectingFreeze(sheet, row, col, width) {
  const frozen = sheet.getFrozenColumns();
  const crosses = frozen > 0 && col <= frozen && col + width - 1 > frozen;

  if (!crosses) {
    const range = sheet.getRange(row, col, 1, width);
    if (width > 1) range.merge();
    return range;
  }

  const firstWidth = frozen - col + 1;
  const first = sheet.getRange(row, col, 1, firstWidth);
  if (firstWidth > 1) first.merge();

  const rest = sheet.getRange(row, frozen + 1, 1, width - firstWidth);
  if (width - firstWidth > 1) rest.merge();

  return first;
}

/** A section heading strip above a sub-table. */
function sectionLabel(sheet, row, col, width, text, color) {
  const bg = color || THEME.SUBHEADER_BG;
  // Both segments painted, so a split merge still reads as one bar.
  sheet.getRange(row, col, 1, width).setBackground(bg);

  mergeRespectingFreeze(sheet, row, col, width)
    .setValue(text)
    .setBackground(bg)
    .setFontColor('#FFFFFF')
    .setFontWeight('bold')
    .setFontSize(9)
    .setVerticalAlignment('middle');
  sheet.setRowHeight(row, 20);
}

/** Italic footnote block used at the foot of each sheet. */
function footnote(sheet, row, col, width, text) {
  mergeRespectingFreeze(sheet, row, col, width)
    .setValue(text)
    .setFontSize(8)
    .setFontStyle('italic')
    .setFontColor(THEME.MUTED)
    .setWrap(true)
    .setVerticalAlignment('top');
  sheet.setRowHeight(row, 40);
}

/** Y/N dropdown. */
function ynValidation(sheet, row, col, numRows) {
  const rule = SpreadsheetApp.newDataValidation()
    .requireValueInList(['Y', 'N'], true)
    .setAllowInvalid(false)
    .build();
  sheet.getRange(row, col, numRows, 1).setDataValidation(rule);
}

/** Dropdown fed by a literal list. */
function listValidation(sheet, row, col, numRows, values) {
  const rule = SpreadsheetApp.newDataValidation()
    .requireValueInList(values, true)
    .setAllowInvalid(false)
    .build();
  sheet.getRange(row, col, numRows, 1).setDataValidation(rule);
}

/** Conditional rule: text equals `text` → coloured. */
function ruleTextEquals(range, text, fontColor, bg) {
  return SpreadsheetApp.newConditionalFormatRule()
    .whenTextEqualTo(text)
    .setFontColor(fontColor)
    .setBackground(bg)
    .setBold(true)
    .setRanges([range])
    .build();
}

/** Conditional rule from a custom formula. */
function ruleFormula(range, formula, fontColor, bg, bold) {
  let builder = SpreadsheetApp.newConditionalFormatRule()
    .whenFormulaSatisfied(formula)
    .setRanges([range]);
  if (fontColor) builder = builder.setFontColor(fontColor);
  if (bg) builder = builder.setBackground(bg);
  if (bold) builder = builder.setBold(true);
  return builder.build();
}

/** Green→red gradient across a percentage column. */
function ruleGradient(range) {
  return SpreadsheetApp.newConditionalFormatRule()
    .setGradientMinpointWithValue('#F4CCCC', SpreadsheetApp.InterpolationType.NUMBER, '0')
    .setGradientMidpointWithValue('#FFF2CC', SpreadsheetApp.InterpolationType.NUMBER, '0.6')
    .setGradientMaxpointWithValue('#D9EAD3', SpreadsheetApp.InterpolationType.NUMBER, '1')
    .setRanges([range])
    .build();
}

/** The Daily Tracker data range as an A1 string, for cross-sheet formulas. */
function dtRange(col, lastRow) {
  const letter = colLetter(col);
  return "'" + SHEETS.DAILY + "'!$" + letter + '$' + DT.FIRST_ROW + ':$' + letter + '$' + lastRow;
}

/** The Finance Tracker ledger range as an A1 string. */
function ftRange(col) {
  const letter = colLetter(col);
  const last = FT.FIRST_ROW + FT.MAX_ROWS - 1;
  return "'" + SHEETS.FINANCE + "'!$" + letter + '$' + FT.FIRST_ROW + ':$' + letter + '$' + last;
}

/** 1 → A, 27 → AA. */
function colLetter(col) {
  let letter = '';
  let n = col;
  while (n > 0) {
    const rem = (n - 1) % 26;
    letter = String.fromCharCode(65 + rem) + letter;
    n = Math.floor((n - 1) / 26);
  }
  return letter;
}
