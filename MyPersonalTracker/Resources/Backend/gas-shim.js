/*
 * gas-shim.js — lets the sheet's own Apps Script (the .js copies of the .gs
 * files next to this) run inside the app, on a copy of the workbook in memory.
 *
 * The app loads the workbook through the Google Sheets API, calls
 * __load(workbook), runs one request with __request(action, payload), and gets
 * back the reply plus an ordered list of changes (__ops) to send to the real
 * sheet:
 *   { t: 'set',   sheet, row, col, values }   values as typed (dates as { $d: wall time })
 *   { t: 'clear', sheet, row, col, rows, cols }
 *   { t: 'req',   req }                       a Sheets API batchUpdate request (formatting,
 *                                             rows, tabs, charts, validation…)
 *   { t: 'dropChartsOf', s }                  delete the charts of a tab copied in this run
 *   { t: 'trash', id }                        move a Drive file to the trash
 * New tabs get temporary negative sheet ids; the app swaps in real ones.
 *
 * Loaded first; the .gs files follow; __overrides() runs last.
 */

var __WB = null;          // { id, name, tz, sheets: [Sheet_] }
var __ops = [];
var __uploads = {};       // base64 photo → Drive id, uploaded by the app beforehand
var __nextTempId = -1;
var __active = null;      // the "active" tab, for moveActiveSheet
var APP_DATA = 'App Data';
var LOCAL_KEY = '__local__';

function __req(request) { __ops.push({ t: 'req', req: request }); }

// ---- Time zones ------------------------------------------------------------------

var __formatters = {};
function __parts(ms, tz) {
  var f = __formatters[tz] || (__formatters[tz] = new Intl.DateTimeFormat('en-US', { timeZone: tz, hourCycle: 'h23', year: 'numeric',
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', weekday: 'short' }));
  var out = {};
  f.formatToParts(new Date(ms)).forEach(function (p) { out[p.type] = p.value; });
  return { y: +out.year, M: +out.month, d: +out.day, H: +out.hour % 24, m: +out.minute, s: +out.second, wd: out.weekday };
}

/** The instant of a wall-clock time in `tz`. */
function __zoned(y, M, d, H, m, s, tz) {
  var target = Date.UTC(y, M - 1, d, H, m, s), guess = target;
  for (var i = 0; i < 2; i++) {
    var p = __parts(guess, tz);
    guess += target - Date.UTC(p.y, p.M - 1, p.d, p.H, p.m, p.s);
  }
  return new Date(guess);
}

/** A day serial (days since 1899-12-30, wall time in the sheet's zone) as a Date. */
function __fromSerial(serial) {
  var whole = Math.floor(serial), ms = Math.round((serial - whole) * 86400000);
  var base = new Date(Date.UTC(1899, 11, 30) + whole * 86400000);
  return __zoned(base.getUTCFullYear(), base.getUTCMonth() + 1, base.getUTCDate(),
    Math.floor(ms / 3600000), Math.floor(ms % 3600000 / 60000), Math.floor(ms % 60000 / 1000), __WB.tz);
}

function __pad(n, w) { n = String(n); while (n.length < (w || 2)) n = '0' + n; return n; }
var __MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
var __DAYS = { Sun: 'Sunday', Mon: 'Monday', Tue: 'Tuesday', Wed: 'Wednesday', Thu: 'Thursday', Fri: 'Friday', Sat: 'Saturday' };

/** Java SimpleDateFormat patterns, as Utilities.formatDate takes them. */
function __formatDate(date, tz, pattern) {
  var p = __parts(date.getTime(), tz || __WB.tz);
  return String(pattern).replace(/'([^']*)'|y+|M+|d+|E+|H+|h+|m+|s+|a|S+/g, function (t, quoted) {
    if (quoted !== undefined) return quoted;
    switch (t[0]) {
      case 'y': return t.length === 2 ? __pad(p.y % 100) : String(p.y);
      case 'M': return t.length >= 4 ? __MONTHS[p.M - 1] : t.length === 3 ? __MONTHS[p.M - 1].slice(0, 3) : __pad(p.M, t.length);
      case 'd': return __pad(p.d, t.length);
      case 'E': return t.length >= 4 ? __DAYS[p.wd] : p.wd;
      case 'H': return __pad(p.H, t.length);
      case 'h': return __pad(p.H % 12 || 12, t.length);
      case 'm': return __pad(p.m, t.length);
      case 's': return __pad(p.s, t.length);
      case 'a': return p.H < 12 ? 'AM' : 'PM';
      case 'S': return __pad(date.getTime() % 1000, t.length);
      default: return t;
    }
  });
}

/** Utilities.parseDate for yyyy-MM-dd, optionally with HH:mm[:ss]. */
function __parseDate(text, tz, pattern) {
  var m = /^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{1,2}):(\d{2})(?::(\d{2}))?)?$/.exec(String(text).trim());
  if (!m) throw new Error('Unparseable date: "' + text + '" (' + pattern + ')');
  return __zoned(+m[1], +m[2], +m[3], +(m[4] || 0), +(m[5] || 0), +(m[6] || 0), tz || __WB.tz);
}

/** A Date as the text the sheet reads like typing: "2026-10-19", "2026-10-19 08:30:00" or "08:30:00". */
function __wall(date) {
  var p = __parts(date.getTime(), __WB.tz);
  if (p.y === 1899 && p.M === 12 && p.d === 30) return __pad(p.H) + ':' + __pad(p.m) + ':' + __pad(p.s);
  var day = p.y + '-' + __pad(p.M) + '-' + __pad(p.d);
  return p.H || p.m || p.s ? day + ' ' + __pad(p.H) + ':' + __pad(p.m) + ':' + __pad(p.s) : day;
}

// ---- Lenient objects: calls not modelled are accepted and chain ----------------------

function __noop() {
  var self = new Proxy(function () { return self; }, {
    get: function (t, k) {
      if (k === 'then' || typeof k === 'symbol') return undefined;
      if (k === 'toString') return function () { return ''; };
      return self;
    },
    apply: function () { return self; }
  });
  return self;
}

function __lenient(impl) {
  var proxy = new Proxy(impl, {
    get: function (t, k) {
      if (k in t) { var v = t[k]; return typeof v === 'function' ? v.bind(t) : v; }
      if (typeof k === 'symbol' || k === 'then') return undefined;
      return function () { return proxy; };
    }
  });
  impl.__proxy = proxy;
  return proxy;
}

// ---- Values ----------------------------------------------------------------------

function __cellText(v) {
  if (v === '' || v === null || v === undefined) return '';
  if (v instanceof Date) return __formatDate(v, __WB.tz, 'dd-MMM-yy');
  return String(v);
}

function __encode(v) {
  if (v instanceof Date) return { $d: __wall(v) };
  if (v === null || v === undefined) return '';
  if (typeof v === 'number' && !isFinite(v)) return '';
  return v;
}

function __color(hex) {
  var m = /^#?([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i.exec(String(hex || ''));
  if (!m) return undefined;
  return { rgbColor: { red: parseInt(m[1], 16) / 255, green: parseInt(m[2], 16) / 255, blue: parseInt(m[3], 16) / 255 } };
}

function __a1Column(n) { var s = ''; while (n > 0) { var r = (n - 1) % 26; s = String.fromCharCode(65 + r) + s; n = Math.floor((n - 1) / 26); } return s; }
function __a1ToRowCol(a1) {
  var m = /^\$?([A-Z]+)\$?(\d+)$/.exec(a1);
  if (!m) throw new Error('Unsupported range: ' + a1);
  var col = 0;
  for (var i = 0; i < m[1].length; i++) col = col * 26 + (m[1].charCodeAt(i) - 64);
  return [+m[2], col];
}

/** An Apps Script number format as the Sheets API wants it. */
function __numberFormat(pattern) {
  pattern = String(pattern);
  if (pattern === '@') return { type: 'TEXT' };
  var lower = pattern.toLowerCase().replace(/"[^"]*"/g, '');
  var date = /d|y|mmm/.test(lower), time = /h|ss|am\/pm/.test(lower);
  var type = date && time ? 'DATE_TIME' : date ? 'DATE' : time ? 'TIME' : /%/.test(lower) ? 'PERCENT' : 'NUMBER';
  return { type: type, pattern: pattern };
}

// ---- Tabs ------------------------------------------------------------------------

function Sheet_(data) {
  this.id = data.id;
  this.title = data.title;
  this.index = data.index;
  this.hidden = !!data.hidden;
  this.maxRows = data.maxRows || 1000;
  this.maxCols = data.maxCols || 26;
  this.frozenRows = data.frozenRows || 0;
  this.frozenCols = data.frozenCols || 0;
  this.charts = (data.charts || []).map(function (c) { return { id: c.chartId, spec: c.spec || {} }; });
  this.cf = data.cfCount || 0;
  this.values = []; this.formulas = []; this.display = [];
  var rows = Math.max((data.values || []).length, (data.formulas || []).length, (data.display || []).length);
  for (var r = 0; r < rows; r++) {
    this.values.push(((data.values || [])[r] || []).map(function (v) { return v && typeof v === 'object' && '$s' in v ? __fromSerial(v.$s) : v; }));
    this.formulas.push(((data.formulas || [])[r] || []).slice());
    this.display.push(((data.display || [])[r] || []).slice());
  }
}
Sheet_.prototype._get = function (grid, r, c) { var row = grid[r - 1]; var v = row ? row[c - 1] : undefined; return v === undefined || v === null ? '' : v; };
Sheet_.prototype._put = function (r, c, value, formula) {
  while (this.values.length < r) { this.values.push([]); this.formulas.push([]); this.display.push([]); }
  this.values[r - 1][c - 1] = value; this.formulas[r - 1][c - 1] = formula; this.display[r - 1][c - 1] = __cellText(value);
  if (r > this.maxRows) this.maxRows = r;
  if (c > this.maxCols) this.maxCols = c;
};
Sheet_.prototype.lastRow = function () {
  for (var r = this.values.length; r >= 1; r--) {
    var v = this.values[r - 1] || [], f = this.formulas[r - 1] || [];
    for (var c = 0; c < Math.max(v.length, f.length); c++) if ((v[c] !== '' && v[c] !== undefined && v[c] !== null) || f[c]) return r;
  }
  return 0;
};
Sheet_.prototype.lastColumn = function () {
  var last = 0;
  for (var r = 0; r < this.values.length; r++) {
    var v = this.values[r] || [], f = this.formulas[r] || [];
    for (var c = Math.max(v.length, f.length); c > last; c--) if ((v[c - 1] !== '' && v[c - 1] !== undefined && v[c - 1] !== null) || f[c - 1]) { last = c; break; }
  }
  return last;
};
Sheet_.prototype.clone = function (id, title, index) {
  var copy = Object.create(Sheet_.prototype);
  for (var k in this) if (Object.prototype.hasOwnProperty.call(this, k) && k !== 'api') copy[k] = this[k];
  copy.id = id; copy.title = title; copy.index = index;
  copy.values = this.values.map(function (r) { return r.slice(); });
  copy.formulas = this.formulas.map(function (r) { return r.slice(); });
  copy.display = this.display.map(function (r) { return r.slice(); });
  copy.charts = this.charts.map(function () { return { id: null, copied: true }; });
  return copy;
};

function SheetApi_(model) { this.m = model; }
var SA = SheetApi_.prototype;
SA.getName = function () { return this.m.title; };
SA.getSheetName = SA.getName;
SA.getSheetId = function () { return this.m.id; };
SA.getIndex = function () { return this.m.index + 1; };
SA.getParent = function () { return __ss; };
SA.getLastRow = function () { return this.m.lastRow(); };
SA.getLastColumn = function () { return this.m.lastColumn(); };
SA.getMaxRows = function () { return Math.max(this.m.maxRows, this.m.lastRow()); };
SA.getMaxColumns = function () { return Math.max(this.m.maxCols, this.m.lastColumn()); };
SA.getFrozenRows = function () { return this.m.frozenRows; };
SA.getFrozenColumns = function () { return this.m.frozenCols; };
SA.isSheetHidden = function () { return this.m.hidden; };
SA.getRange = function (a, b, c, d) {
  if (typeof a === 'string') {
    var ref = a.replace(/^.*!/, '').split(':');
    var from = __a1ToRowCol(ref[0]), to = ref[1] ? __a1ToRowCol(ref[1]) : from;
    return __range(this.m, from[0], from[1], to[0] - from[0] + 1, to[1] - from[1] + 1);
  }
  return __range(this.m, a, b, c === undefined ? 1 : c, d === undefined ? 1 : d);
};
SA.getDataRange = function () { return __range(this.m, 1, 1, Math.max(1, this.m.lastRow()), Math.max(1, this.m.lastColumn())); };
SA._dimension = function (dimension, start, end) { return { sheetId: this.m.id, dimension: dimension, startIndex: start, endIndex: end }; };

SA.deleteRows = function (row, count) {
  count = count || 1;
  this.m.values.splice(row - 1, count); this.m.formulas.splice(row - 1, count); this.m.display.splice(row - 1, count);
  this.m.maxRows = Math.max(1, this.m.maxRows - count);
  __req({ deleteDimension: { range: this._dimension('ROWS', row - 1, row - 1 + count) } });
  return this.__proxy;
};
SA.deleteRow = function (row) { return this.deleteRows(row, 1); };
SA.insertRowsAfter = function (after, count) {
  for (var i = 0; i < count; i++) { this.m.values.splice(after, 0, []); this.m.formulas.splice(after, 0, []); this.m.display.splice(after, 0, []); }
  this.m.maxRows += count;
  __req({ insertDimension: { range: this._dimension('ROWS', after, after + count), inheritFromBefore: after > 0 } });
  return this.__proxy;
};
SA.insertRowAfter = function (after) { return this.insertRowsAfter(after, 1); };
SA.insertRowsBefore = function (before, count) { return this.insertRowsAfter(before - 1, count); };
SA.insertRowBefore = function (before) { return this.insertRowsAfter(before - 1, 1); };
SA.insertColumnsAfter = function (after, count) {
  [this.m.values, this.m.formulas, this.m.display].forEach(function (grid) {
    grid.forEach(function (row) { if (row.length > after) { var args = [after, 0]; for (var i = 0; i < count; i++) args.push(undefined); row.splice.apply(row, args); } });
  });
  this.m.maxCols += count;
  __req({ insertDimension: { range: this._dimension('COLUMNS', after, after + count), inheritFromBefore: after > 0 } });
  return this.__proxy;
};
SA.insertColumnAfter = function (after) { return this.insertColumnsAfter(after, 1); };
SA.deleteColumns = function (col, count) {
  count = count || 1;
  [this.m.values, this.m.formulas, this.m.display].forEach(function (grid) { grid.forEach(function (row) { row.splice(col - 1, count); }); });
  this.m.maxCols = Math.max(1, this.m.maxCols - count);
  __req({ deleteDimension: { range: this._dimension('COLUMNS', col - 1, col - 1 + count) } });
  return this.__proxy;
};
SA.deleteColumn = function (col) { return this.deleteColumns(col, 1); };
SA.appendRow = function (values) { var r = this.m.lastRow() + 1; __range(this.m, r, 1, 1, values.length).setValues([values]); return this.__proxy; };

SA._size = function (dimension, start, count, pixels) {
  __req({ updateDimensionProperties: { range: this._dimension(dimension, start - 1, start - 1 + count), properties: { pixelSize: pixels }, fields: 'pixelSize' } });
  return this.__proxy;
};
SA.setColumnWidth = function (col, px) { return this._size('COLUMNS', col, 1, px); };
SA.setColumnWidths = function (col, count, px) { return this._size('COLUMNS', col, count, px); };
SA.setRowHeight = function (row, px) { return this._size('ROWS', row, 1, px); };
SA.setRowHeights = function (row, count, px) { return this._size('ROWS', row, count, px); };
SA.setRowHeightsForced = SA.setRowHeights;
SA._hide = function (dimension, start, count, hidden) {
  __req({ updateDimensionProperties: { range: this._dimension(dimension, start - 1, start - 1 + (count || 1)), properties: { hiddenByUser: hidden }, fields: 'hiddenByUser' } });
  return this.__proxy;
};
SA.hideColumns = function (col, count) { return this._hide('COLUMNS', col, count, true); };
SA.showColumns = function (col, count) { return this._hide('COLUMNS', col, count, false); };
SA.hideRows = function (row, count) { return this._hide('ROWS', row, count, true); };
SA.showRows = function (row, count) { return this._hide('ROWS', row, count, false); };
SA._grid = function (props, fields) {
  __req({ updateSheetProperties: { properties: { sheetId: this.m.id, gridProperties: props }, fields: fields } });
  return this.__proxy;
};
SA.setFrozenRows = function (n) { this.m.frozenRows = n; return this._grid({ frozenRowCount: n }, 'gridProperties.frozenRowCount'); };
SA.setFrozenColumns = function (n) { this.m.frozenCols = n; return this._grid({ frozenColumnCount: n }, 'gridProperties.frozenColumnCount'); };
SA.setHiddenGridlines = function (hide) { return this._grid({ hideGridlines: !!hide }, 'gridProperties.hideGridlines'); };
SA.setTabColor = function (hex) {
  __req({ updateSheetProperties: { properties: { sheetId: this.m.id, tabColorStyle: __color(hex) }, fields: 'tabColorStyle' } });
  return this.__proxy;
};
SA.setName = function (name) {
  this.m.title = String(name);
  __req({ updateSheetProperties: { properties: { sheetId: this.m.id, title: this.m.title }, fields: 'title' } });
  return this.__proxy;
};
SA.hideSheet = function () {
  this.m.hidden = true;
  __req({ updateSheetProperties: { properties: { sheetId: this.m.id, hidden: true }, fields: 'hidden' } });
  return this.__proxy;
};
SA.showSheet = function () {
  this.m.hidden = false;
  __req({ updateSheetProperties: { properties: { sheetId: this.m.id, hidden: false }, fields: 'hidden' } });
  return this.__proxy;
};
SA.activate = function () { __active = this.m; return this.__proxy; };

/** Contents and formatting go; the tab, its size and its charts stay (as in Apps Script). */
SA.clear = function (options) {
  var contents = !options || options.contentsOnly || !options.formatOnly;
  var formats = !options || options.formatOnly || !options.contentsOnly;
  if (contents) { this.m.values = []; this.m.formulas = []; this.m.display = []; }
  var fields = [contents ? 'userEnteredValue' : null, formats ? 'userEnteredFormat' : null].filter(Boolean).join(',');
  __req({ repeatCell: { range: { sheetId: this.m.id }, cell: {}, fields: fields } });
  if (formats) __req({ unmergeCells: { range: { sheetId: this.m.id } } });
  return this.__proxy;
};
SA.clearContents = function () { return this.clear({ contentsOnly: true }); };
SA.clearFormats = function () { return this.clear({ formatOnly: true }); };

// Conditional formatting: rules already on the tab are kept by position.
SA.getConditionalFormatRules = function () {
  var out = [];
  for (var i = 0; i < this.m.cf; i++) out.push({ __existing: i, getRanges: function () { return []; } });
  return out;
};
SA.setConditionalFormatRules = function (rules) {
  var keep = {};
  rules.forEach(function (r) { if (r && r.__existing !== undefined) keep[r.__existing] = true; });
  for (var i = this.m.cf - 1; i >= 0; i--) if (!keep[i]) __req({ deleteConditionalFormatRule: { sheetId: this.m.id, index: i } });
  var count = Object.keys(keep).length;
  rules.forEach(function (r) {
    if (r && r.__cf) { __req({ addConditionalFormatRule: { rule: r.__cf, index: count } }); count++; }
  });
  this.m.cf = count;
  return this.__proxy;
};
SA.clearConditionalFormatRules = function () { return this.setConditionalFormatRules([]); };

// Charts.
SA.getCharts = function () {
  var model = this.m;
  return model.charts.map(function (c) {
    return {
      __chart: c,
      getChartId: function () { return c.id; },
      getOptions: function () { return { get: function (k) { return k === 'title' ? (c.spec && c.spec.title) : undefined; } }; },
      modify: function () { return __chartBuilder(model, c); }
    };
  });
};
SA.removeChart = function (chart) {
  var c = chart.__chart;
  this.m.charts = this.m.charts.filter(function (x) { return x !== c; });
  if (c.copied) {
    if (!this.m.chartsDropped) { this.m.chartsDropped = true; __ops.push({ t: 'dropChartsOf', s: this.m.id }); }
  } else if (c.id !== null && c.id !== undefined) {
    __req({ deleteEmbeddedObject: { objectId: c.id } });
  }
  return this.__proxy;
};
SA.newChart = function () { return __chartBuilder(this.m, null); };
SA.insertChart = function (built) {
  __req({ addChart: { chart: { spec: built.spec, position: built.position } } });
  this.m.charts.push({ id: null, spec: built.spec });
  return this.__proxy;
};
SA.updateChart = function (built) {
  if (built.chartId !== null && built.chartId !== undefined) __req({ updateChartSpec: { chartId: built.chartId, spec: built.spec } });
  return this.__proxy;
};

/** A copy of this tab (values, formulas, formatting, charts), as the archive makes. */
SA.copyTo = function (spreadsheet) {
  var id = __nextTempId--, title = 'Copy of ' + this.m.title, index = __WB.sheets.length;
  var copy = this.m.clone(id, title, index);
  __WB.sheets.push(copy);
  __req({ duplicateSheet: { sourceSheetId: this.m.id, insertSheetIndex: index, newSheetId: id, newSheetName: title } });
  return __sheetApi(copy);
};
SA.getFilter = function () { return null; };
SA.createTextFinder = function (text) { return __finder(this.m, 1, 1, this.m.values.length, this.m.lastColumn(), text); };

function __sheetApi(model) { if (!model.api) model.api = __lenient(new SheetApi_(model)); return model.api; }

// ---- Ranges ----------------------------------------------------------------------

function Range_(model, row, col, rows, cols) {
  if (row < 1 || col < 1 || rows < 1 || cols < 1) throw new Error('The coordinates of the range are outside the dimensions of the sheet.');
  this.m = model; this.row = row; this.col = col; this.rows = rows; this.cols = cols;
}
var RA = Range_.prototype;
RA._gridRange = function () {
  return { sheetId: this.m.id, startRowIndex: this.row - 1, endRowIndex: this.row - 1 + this.rows,
           startColumnIndex: this.col - 1, endColumnIndex: this.col - 1 + this.cols };
};
RA._grid = function (grid, map) {
  var out = [];
  for (var r = 0; r < this.rows; r++) {
    var line = [];
    for (var c = 0; c < this.cols; c++) line.push(map(this.m._get(grid, this.row + r, this.col + c), this.row + r, this.col + c));
    out.push(line);
  }
  return out;
};
RA.getValues = function () { return this._grid(this.m.values, function (v) { return v; }); };
RA.getValue = function () { return this.m._get(this.m.values, this.row, this.col); };
RA.getDisplayValues = function () {
  var m = this.m;
  return this._grid(m.display, function (d, r, c) { return d !== '' ? String(d) : __cellText(m._get(m.values, r, c)); });
};
RA.getDisplayValue = function () { return this.getDisplayValues()[0][0]; };
RA.getFormulas = function () { return this._grid(this.m.formulas, function (f) { return f || ''; }); };
RA.getFormula = function () { return this.getFormulas()[0][0]; };
RA.getRow = function () { return this.row; };
RA.getColumn = function () { return this.col; };
RA.getNumRows = function () { return this.rows; };
RA.getNumColumns = function () { return this.cols; };
RA.getLastRow = function () { return this.row + this.rows - 1; };
RA.getLastColumn = function () { return this.col + this.cols - 1; };
RA.getSheet = function () { return __sheetApi(this.m); };
RA.getA1Notation = function () {
  var from = __a1Column(this.col) + this.row;
  return this.rows === 1 && this.cols === 1 ? from : from + ':' + __a1Column(this.col + this.cols - 1) + (this.row + this.rows - 1);
};
RA.offset = function (r, c, rows, cols) { return __range(this.m, this.row + r, this.col + c, rows || this.rows, cols || this.cols); };
RA.isBlank = function () {
  return this.getValues().every(function (r) { return r.every(function (v) { return v === ''; }); }) &&
    !this.getFormulas().some(function (r) { return r.some(String); });
};
RA._fill = function (v) { var o = []; for (var r = 0; r < this.rows; r++) { var l = []; for (var c = 0; c < this.cols; c++) l.push(v); o.push(l); } return o; };

RA.setValues = function (values) {
  if (!values || values.length !== this.rows || values.some(function (r) { return !r || r.length !== this.cols; }, this)) {
    throw new Error('The number of rows or columns in the data does not match the range (' + this.rows + 'x' + this.cols + ').');
  }
  var out = [];
  for (var r = 0; r < this.rows; r++) {
    var line = [];
    for (var c = 0; c < this.cols; c++) {
      var v = values[r][c];
      var formula = typeof v === 'string' && v.charAt(0) === '=' ? v : '';
      this.m._put(this.row + r, this.col + c, formula ? '' : (v === null || v === undefined ? '' : v), formula);
      line.push(__encode(v));
    }
    out.push(line);
  }
  __ops.push({ t: 'set', sheet: this.m.title, row: this.row, col: this.col, values: out });
  return this.__proxy;
};
RA.setValue = function (v) { return this.setValues(this._fill(v)); };
RA.setFormulas = function (formulas) {
  for (var r = 0; r < this.rows; r++) for (var c = 0; c < this.cols; c++) this.m._put(this.row + r, this.col + c, '', formulas[r][c] || '');
  __ops.push({ t: 'set', sheet: this.m.title, row: this.row, col: this.col, values: formulas.map(function (l) { return l.map(String); }) });
  return this.__proxy;
};
RA.setFormula = function (f) { return this.setFormulas(this._fill(f)); };
RA.clearContent = function () {
  for (var r = 0; r < this.rows; r++) for (var c = 0; c < this.cols; c++) {
    if (this.m.values[this.row + r - 1]) this.m._put(this.row + r, this.col + c, '', '');
  }
  __ops.push({ t: 'clear', sheet: this.m.title, row: this.row, col: this.col, rows: this.rows, cols: this.cols });
  return this.__proxy;
};
RA.clearFormat = function () { __req({ repeatCell: { range: this._gridRange(), cell: {}, fields: 'userEnteredFormat' } }); return this.__proxy; };
RA.clear = function (options) {
  if (!options || !options.formatOnly) this.clearContent();
  if (!options || !options.contentsOnly) this.clearFormat();
  return this.__proxy;
};
RA.clearDataValidations = function () { __req({ setDataValidation: { range: this._gridRange() } }); return this.__proxy; };

// Formatting, sent as it's asked for.
RA._format = function (format, fields) { __req({ repeatCell: { range: this._gridRange(), cell: { userEnteredFormat: format }, fields: fields } }); return this.__proxy; };
RA._text = function (key, value) { var t = {}; t[key] = value; return this._format({ textFormat: t }, 'userEnteredFormat.textFormat.' + key); };
RA.setNumberFormat = function (pattern) { return this._format({ numberFormat: __numberFormat(pattern) }, 'userEnteredFormat.numberFormat'); };
RA.setNumberFormats = function (patterns) { return this.setNumberFormat(patterns[0][0]); };
RA.setBackground = function (hex) { return this._format({ backgroundColorStyle: __color(hex) }, 'userEnteredFormat.backgroundColorStyle'); };
RA.setBackgroundColor = RA.setBackground;
RA.setFontColor = function (hex) { return this._text('foregroundColorStyle', __color(hex)); };
RA.setFontWeight = function (w) { return this._text('bold', w === 'bold'); };
RA.setFontStyle = function (s) { return this._text('italic', s === 'italic'); };
RA.setFontSize = function (n) { return this._text('fontSize', n); };
RA.setFontFamily = function (f) { return this._text('fontFamily', f); };
RA.setFontLine = function (l) { return this._text(l === 'line-through' ? 'strikethrough' : 'underline', l !== 'none'); };
RA.setHorizontalAlignment = function (a) {
  var map = { left: 'LEFT', center: 'CENTER', right: 'RIGHT', normal: 'LEFT' };
  return this._format({ horizontalAlignment: map[String(a).toLowerCase()] || 'LEFT' }, 'userEnteredFormat.horizontalAlignment');
};
RA.setVerticalAlignment = function (a) {
  var map = { top: 'TOP', middle: 'MIDDLE', bottom: 'BOTTOM' };
  return this._format({ verticalAlignment: map[String(a).toLowerCase()] || 'BOTTOM' }, 'userEnteredFormat.verticalAlignment');
};
RA.setWrap = function (wrap) { return this._format({ wrapStrategy: wrap ? 'WRAP' : 'OVERFLOW_CELL' }, 'userEnteredFormat.wrapStrategy'); };
RA.setWrapStrategy = function (s) { return this._format({ wrapStrategy: String(s) }, 'userEnteredFormat.wrapStrategy'); };
RA.setBorder = function (top, left, bottom, right, vertical, horizontal, color, style) {
  var line = { style: style ? String(style) : 'SOLID', colorStyle: __color(color || '#000000') };
  var none = { style: 'NONE' };
  var req = { range: this._gridRange() };
  [['top', top], ['left', left], ['bottom', bottom], ['right', right], ['innerVertical', vertical], ['innerHorizontal', horizontal]]
    .forEach(function (side) { if (side[1] === true) req[side[0]] = line; else if (side[1] === false) req[side[0]] = none; });
  __req({ updateBorders: req });
  return this.__proxy;
};
RA.merge = function () { if (this.rows * this.cols > 1) __req({ mergeCells: { range: this._gridRange(), mergeType: 'MERGE_ALL' } }); return this.__proxy; };
RA.mergeAcross = function () { if (this.cols > 1) __req({ mergeCells: { range: this._gridRange(), mergeType: 'MERGE_ROWS' } }); return this.__proxy; };
RA.mergeVertically = function () { if (this.rows > 1) __req({ mergeCells: { range: this._gridRange(), mergeType: 'MERGE_COLUMNS' } }); return this.__proxy; };
RA.breakApart = function () { __req({ unmergeCells: { range: this._gridRange() } }); return this.__proxy; };
RA.setDataValidation = function (rule) {
  __req({ setDataValidation: rule && rule.__rule ? { range: this._gridRange(), rule: rule.__rule } : { range: this._gridRange() } });
  return this.__proxy;
};
RA.setNote = function (note) {
  __req({ repeatCell: { range: this._gridRange(), cell: { note: note ? String(note) : '' }, fields: 'note' } });
  return this.__proxy;
};

/** Copies to another range: values only in place (archiving), or everything (filling a formula down). */
RA.copyTo = function (dest, options) {
  var target = dest.__target;
  var source = this, values = this.getValues(), formulas = this.getFormulas();
  if (options && options.contentsOnly && target === this) {
    // Formulas become their values, in place — done by the sheet itself, so text stays text.
    for (var r0 = 0; r0 < this.rows; r0++) for (var c0 = 0; c0 < this.cols; c0++) {
      if (formulas[r0][c0]) this.m._put(this.row + r0, this.col + c0, values[r0][c0], '');
    }
    __req({ copyPaste: { source: this._gridRange(), destination: this._gridRange(), pasteType: 'PASTE_VALUES' } });
    return this.__proxy;
  }
  for (var r = 0; r < target.rows; r++) for (var c = 0; c < target.cols; c++) {
    var sr = r % source.rows, sc = c % source.cols;
    target.m._put(target.row + r, target.col + c, formulas[sr][sc] ? '' : values[sr][sc], formulas[sr][sc]);
  }
  __req({ copyPaste: { source: this._gridRange(), destination: target._gridRange(),
                       pasteType: options && options.contentsOnly ? 'PASTE_VALUES' : options && options.formatOnly ? 'PASTE_FORMAT' : 'PASTE_NORMAL' } });
  return this.__proxy;
};
RA.createTextFinder = function (text) { return __finder(this.m, this.row, this.col, this.rows, this.cols, text); };

function __range(model, row, col, rows, cols) {
  var impl = new Range_(model, row, col, rows, cols);
  var proxy = __lenient(impl);
  impl.__target = impl;
  return proxy;
}

function __finder(model, row, col, rows, cols, text) {
  var entire = false, matchCase = false;
  var f = {
    matchEntireCell: function (b) { entire = b !== false; return f; },
    matchCase: function (b) { matchCase = b !== false; return f; },
    findAll: function () {
      var found = [];
      for (var r = row; r < row + rows; r++) for (var c = col; c < col + cols; c++) {
        var v = model._get(model.display, r, c) || __cellText(model._get(model.values, r, c));
        var a = matchCase ? v : v.toLowerCase(), b = matchCase ? text : String(text).toLowerCase();
        if (entire ? a === b : a.indexOf(b) !== -1) found.push(__range(model, r, c, 1, 1));
      }
      return found;
    },
    findNext: function () { return f.findAll()[0] || null; }
  };
  return f;
}

// ---- Builders: validation, conditional formatting, charts ----------------------------

function __values(list) { return list.map(function (v) { return { userEnteredValue: String(v) }; }); }

function __validationBuilder() {
  var rule = { condition: null, strict: true, showCustomUi: true };
  var b = __lenient({
    requireValueInList: function (list, dropdown) { rule.condition = { type: 'ONE_OF_LIST', values: __values(list) }; rule.showCustomUi = dropdown !== false; return b; },
    requireNumberBetween: function (lo, hi) { rule.condition = { type: 'NUMBER_BETWEEN', values: __values([lo, hi]) }; return b; },
    requireNumberGreaterThanOrEqualTo: function (n) { rule.condition = { type: 'NUMBER_GREATER_THAN_EQ', values: __values([n]) }; return b; },
    requireNumberLessThanOrEqualTo: function (n) { rule.condition = { type: 'NUMBER_LESS_THAN_EQ', values: __values([n]) }; return b; },
    requireFormulaSatisfied: function (f) { rule.condition = { type: 'CUSTOM_FORMULA', values: __values([f]) }; return b; },
    requireCheckbox: function () { rule.condition = { type: 'BOOLEAN' }; return b; },
    requireDate: function () { rule.condition = { type: 'DATE_IS_VALID' }; return b; },
    setAllowInvalid: function (allow) { rule.strict = !allow; return b; },
    setHelpText: function (text) { rule.inputMessage = String(text); return b; },
    build: function () { return { __rule: JSON.parse(JSON.stringify(rule)) }; }
  });
  return b;
}

function __conditionalBuilder() {
  var rule = { ranges: [], condition: null, format: {}, gradient: null };
  function text(key, value) { rule.format.textFormat = rule.format.textFormat || {}; rule.format.textFormat[key] = value; return b; }
  function when(type, values) { rule.condition = values ? { type: type, values: __values(values) } : { type: type }; return b; }
  function point(key) {
    return function (hex, type, value) {
      rule.gradient = rule.gradient || {};
      rule.gradient[key] = { colorStyle: __color(hex), type: String(type || 'NUMBER'), value: String(value) };
      return b;
    };
  }
  var b = __lenient({
    whenTextEqualTo: function (t) { return when('TEXT_EQ', [t]); },
    whenTextContains: function (t) { return when('TEXT_CONTAINS', [t]); },
    whenFormulaSatisfied: function (f) { return when('CUSTOM_FORMULA', [f]); },
    whenNumberGreaterThan: function (n) { return when('NUMBER_GREATER', [n]); },
    whenNumberGreaterThanOrEqualTo: function (n) { return when('NUMBER_GREATER_THAN_EQ', [n]); },
    whenNumberLessThan: function (n) { return when('NUMBER_LESS', [n]); },
    whenNumberLessThanOrEqualTo: function (n) { return when('NUMBER_LESS_THAN_EQ', [n]); },
    whenNumberBetween: function (lo, hi) { return when('NUMBER_BETWEEN', [lo, hi]); },
    whenNumberEqualTo: function (n) { return when('NUMBER_EQ', [n]); },
    whenCellEmpty: function () { return when('BLANK'); },
    whenCellNotEmpty: function () { return when('NOT_BLANK'); },
    setBackground: function (hex) { rule.format.backgroundColorStyle = __color(hex); return b; },
    setFontColor: function (hex) { return text('foregroundColorStyle', __color(hex)); },
    setBold: function (v) { return text('bold', !!v); },
    setItalic: function (v) { return text('italic', !!v); },
    setStrikethrough: function (v) { return text('strikethrough', !!v); },
    setGradientMinpointWithValue: point('minpoint'),
    setGradientMidpointWithValue: point('midpoint'),
    setGradientMaxpointWithValue: point('maxpoint'),
    setRanges: function (ranges) { rule.ranges = ranges.map(function (r) { return r.__target._gridRange(); }); return b; },
    build: function () {
      var out = { ranges: rule.ranges };
      if (rule.gradient) out.gradientRule = rule.gradient;
      else out.booleanRule = { condition: rule.condition, format: rule.format };
      return { __cf: JSON.parse(JSON.stringify(out)) };
    }
  });
  return b;
}

/** Apps Script's EmbeddedChartBuilder, for the charts the Dashboard draws. */
function __chartBuilder(model, existing) {
  var state = { type: 'LINE', ranges: [], row: 1, col: 1, offX: 0, offY: 0, options: {} };
  var b = __lenient({
    setChartType: function (t) { state.type = String(t); return b; },
    asLineChart: function () { state.type = 'LINE'; return b; },
    asColumnChart: function () { state.type = 'COLUMN'; return b; },
    asBarChart: function () { state.type = 'BAR'; return b; },
    addRange: function (r) { state.ranges.push(r.__target._gridRange()); return b; },
    setPosition: function (row, col, offX, offY) { state.row = row; state.col = col; state.offX = offX || 0; state.offY = offY || 0; return b; },
    setOption: function (key, value) { state.options[key] = value; return b; },
    build: function () {
      if (existing) {
        var spec = JSON.parse(JSON.stringify(existing.spec || {}));
        if (typeof state.options.title === 'string') spec.title = state.options.title;
        return { chartId: existing.id, spec: spec };
      }
      var o = state.options, legend = (o.legend && o.legend.position) || 'right';
      var legendMap = { none: 'NO_LEGEND', bottom: 'BOTTOM_LEGEND', top: 'TOP_LEGEND', left: 'LEFT_LEGEND', right: 'RIGHT_LEGEND' };
      var horizontal = state.type === 'BAR';
      var valueAxis = horizontal ? 'BOTTOM_AXIS' : 'LEFT_AXIS', domainAxis = horizontal ? 'LEFT_AXIS' : 'BOTTOM_AXIS';
      var colors = o.colors || [];
      var axis = [{ position: domainAxis, title: ((horizontal ? o.vAxis : o.hAxis) || {}).title }];
      var valueOptions = (horizontal ? o.hAxis : o.vAxis) || {};
      var valueAxisSpec = { position: valueAxis, title: valueOptions.title };
      if (valueOptions.viewWindow) {
        valueAxisSpec.viewWindowOptions = { viewWindowMin: valueOptions.viewWindow.min, viewWindowMax: valueOptions.viewWindow.max };
      }
      axis.push(valueAxisSpec);
      var source = function (range) { return { sourceRange: { sources: [range] } }; };
      var spec = {
        title: o.title,
        titleTextFormat: o.titleTextStyle ? { fontSize: o.titleTextStyle.fontSize, bold: !!o.titleTextStyle.bold } : undefined,
        basicChart: {
          chartType: ['LINE', 'COLUMN', 'BAR', 'AREA', 'SCATTER', 'COMBO', 'STEPPED_AREA'].indexOf(state.type) !== -1 ? state.type : 'LINE',
          legendPosition: legendMap[legend] || 'RIGHT_LEGEND',
          axis: axis,
          headerCount: 0,
          lineSmoothing: o.curveType === 'function',
          stackedType: o.isStacked ? 'STACKED' : undefined,
          domains: state.ranges.slice(0, 1).map(function (r) { return { domain: source(r) }; }),
          series: state.ranges.slice(1).map(function (r, i) {
            var s = { series: source(r), targetAxis: valueAxis };
            if (colors[i]) s.colorStyle = __color(colors[i]);
            if (o.pointSize) s.pointStyle = { size: o.pointSize, shape: 'CIRCLE' };
            return s;
          })
        }
      };
      var position = { overlayPosition: { anchorCell: { sheetId: model.id, rowIndex: state.row - 1, columnIndex: state.col - 1 },
        offsetXPixels: state.offX, offsetYPixels: state.offY, widthPixels: o.width || 600, heightPixels: o.height || 371 } };
      return JSON.parse(JSON.stringify({ spec: spec, position: position }));
    }
  });
  return b;
}

// ---- Spreadsheet -----------------------------------------------------------------

var __ss = __lenient({
  getId: function () { return __WB.id; },
  getName: function () { return __WB.name || 'Life & Finance Command Center'; },
  getSpreadsheetTimeZone: function () { return __WB.tz; },
  getSheets: function () { return __WB.sheets.slice().sort(function (a, b) { return a.index - b.index; }).map(__sheetApi); },
  getNumSheets: function () { return __WB.sheets.length; },
  getSheetByName: function (name) {
    for (var i = 0; i < __WB.sheets.length; i++) if (__WB.sheets[i].title === name) return __sheetApi(__WB.sheets[i]);
    return null;
  },
  getSheetById: function (id) {
    for (var i = 0; i < __WB.sheets.length; i++) if (__WB.sheets[i].id === id) return __sheetApi(__WB.sheets[i]);
    return null;
  },
  insertSheet: function (name, index) {
    if (typeof name === 'number') { index = name; name = undefined; }
    var title = name === undefined ? 'Sheet' + (__WB.sheets.length + 1) : String(name);
    if (__ss.getSheetByName(title)) throw new Error('A sheet with the name "' + title + '" already exists.');
    var at = typeof index === 'number' ? Math.min(index, __WB.sheets.length) : __WB.sheets.length;
    __WB.sheets.forEach(function (s) { if (s.index >= at) s.index++; });
    var model = new Sheet_({ id: __nextTempId--, title: title, index: at, maxRows: 1000, maxCols: 26 });
    __WB.sheets.push(model);
    __req({ addSheet: { properties: { sheetId: model.id, title: title, index: at, gridProperties: { rowCount: 1000, columnCount: 26 } } } });
    __active = model;
    return __sheetApi(model);
  },
  deleteSheet: function (sheet) {
    var model = sheet.m;
    __WB.sheets = __WB.sheets.filter(function (s) { return s !== model; });
    __WB.sheets.forEach(function (s) { if (s.index > model.index) s.index--; });
    __req({ deleteSheet: { sheetId: model.id } });
  },
  setActiveSheet: function (sheet) { __active = sheet.m; return sheet; },
  getActiveSheet: function () { return __active ? __sheetApi(__active) : __ss.getSheets()[0]; },
  moveActiveSheet: function (position) {
    if (!__active) return;
    var to = Math.max(0, Math.min(position - 1, __WB.sheets.length - 1));
    var from = __active.index;
    __WB.sheets.forEach(function (s) {
      if (s === __active) return;
      if (from < to && s.index > from && s.index <= to) s.index--;
      if (from > to && s.index >= to && s.index < from) s.index++;
    });
    __active.index = to;
    // The Sheets API's index counts the tab's own old place when moving right.
    __req({ updateSheetProperties: { properties: { sheetId: __active.id, index: to > from ? to + 1 : to }, fields: 'index' } });
  },
  getUrl: function () { return 'https://docs.google.com/spreadsheets/d/' + __WB.id + '/edit'; },
  toast: function () {}
});

var SpreadsheetApp = __lenient({
  getActiveSpreadsheet: function () { return __ss; },
  getActive: function () { return __ss; },
  openById: function () { return __ss; },
  flush: function () {},
  getUi: function () { throw new Error('No menus here.'); },
  newDataValidation: function () { return __validationBuilder(); },
  newConditionalFormatRule: function () { return __conditionalBuilder(); },
  newRichTextValue: function () { return __noop(); },
  newTextStyle: function () { return __noop(); },
  BorderStyle: { SOLID: 'SOLID', SOLID_MEDIUM: 'SOLID_MEDIUM', SOLID_THICK: 'SOLID_THICK', DOTTED: 'DOTTED', DASHED: 'DASHED', DOUBLE: 'DOUBLE' },
  InterpolationType: { NUMBER: 'NUMBER', PERCENT: 'PERCENT', PERCENTILE: 'PERCENTILE', MIN: 'MIN', MAX: 'MAX' },
  WrapStrategy: { WRAP: 'WRAP', OVERFLOW: 'OVERFLOW_CELL', CLIP: 'CLIP' },
  DataValidationCriteria: __noop(), ProtectionType: __noop(), Dimension: __noop(), CopyPasteType: __noop()
});
var Charts = { ChartType: { LINE: 'LINE', COLUMN: 'COLUMN', BAR: 'BAR', AREA: 'AREA', PIE: 'PIE', SCATTER: 'SCATTER', COMBO: 'COMBO' } };

// ---- Properties: kept on the hidden "App Data" tab ---------------------------------

function __appData(create) {
  var sheet = __ss.getSheetByName(APP_DATA);
  if (!sheet && create) {
    var active = __active;
    sheet = __ss.insertSheet(APP_DATA, __WB.sheets.length);
    sheet.hideSheet();
    sheet.getRange(1, 1, 1, 2).setValues([['Key', 'Value — kept by the app, please leave as it is']]);
    __active = active;
  }
  return sheet;
}
function __props(scope) {
  var prefix = scope + ':';
  function find(key) {
    var sheet = __appData(false);
    if (!sheet) return null;
    var keys = sheet.getRange(1, 1, Math.max(1, sheet.getLastRow()), 1).getValues();
    for (var i = 1; i < keys.length; i++) if (keys[i][0] === prefix + key) return { sheet: sheet, row: i + 1 };
    return null;
  }
  var api = {
    getProperty: function (key) {
      if (scope === 'script' && key === 'API_KEY') return LOCAL_KEY;
      var at = find(key);
      if (!at) return null;
      var v = at.sheet.getRange(at.row, 2).getValue();
      return v === '' ? null : String(v);
    },
    setProperty: function (key, value) {
      var at = find(key);
      var sheet = at ? at.sheet : __appData(true);
      var row = at ? at.row : Math.max(2, sheet.getLastRow() + 1);
      // Kept as text: a property is always a string.
      sheet.getRange(row, 1, 1, 2).setValues([[prefix + key, "'" + String(value)]]);
      sheet.getRange(row, 2).m._put(row, 2, String(value), '');
      return api;
    },
    deleteProperty: function (key) { var at = find(key); if (at) at.sheet.deleteRow(at.row); return api; },
    getProperties: function () {
      var out = {}, sheet = __appData(false);
      if (!sheet) return out;
      sheet.getRange(1, 1, Math.max(1, sheet.getLastRow()), 2).getValues().forEach(function (r, i) {
        if (i > 0 && String(r[0]).indexOf(prefix) === 0) out[String(r[0]).slice(prefix.length)] = String(r[1]);
      });
      return out;
    },
    setProperties: function (props) { Object.keys(props).forEach(function (k) { api.setProperty(k, props[k]); }); return api; },
    deleteAllProperties: function () { Object.keys(api.getProperties()).forEach(api.deleteProperty); return api; },
    getKeys: function () { return Object.keys(api.getProperties()); }
  };
  return api;
}
var PropertiesService = {
  getDocumentProperties: function () { return __props('doc'); },
  getScriptProperties: function () { return __props('script'); },
  getUserProperties: function () { return __props('user'); }
};

// ---- The other Apps Script globals the scripts touch ----------------------------------

var Utilities = {
  formatDate: function (date, tz, pattern) { return __formatDate(date, tz, pattern); },
  parseDate: function (text, tz, pattern) { return __parseDate(text, tz, pattern); },
  getUuid: function () {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
      var r = Math.random() * 16 | 0; return (c === 'x' ? r : (r & 3 | 8)).toString(16);
    });
  },
  sleep: function () {},
  base64Encode: function () { throw new Error('Not available here.'); },
  base64Decode: function () { throw new Error('Not available here.'); },
  newBlob: function () { throw new Error('Not available here.'); }
};
var Session = { getScriptTimeZone: function () { return __WB.tz; }, getActiveUser: function () { return __noop(); } };
var LockService = (function () {
  var lock = { tryLock: function () { return true; }, waitLock: function () {}, releaseLock: function () {}, hasLock: function () { return true; } };
  return { getScriptLock: function () { return lock; }, getDocumentLock: function () { return lock; }, getUserLock: function () { return lock; } };
})();
var ScriptApp = { newTrigger: function () { return __noop(); }, getProjectTriggers: function () { return []; }, deleteTrigger: function () {} };
var ContentService = {
  MimeType: { JSON: 'JSON', TEXT: 'TEXT' },
  createTextOutput: function (s) { var o = { __text: s, setMimeType: function () { return o; }, getContent: function () { return s; } }; return o; }
};
var HtmlService = __noop(), DriveApp = __noop(), CacheService = __noop(), MailApp = __noop(), UrlFetchApp = __noop();
var Logger = { log: function () { console.log.apply(console, arguments); } };
if (typeof console === 'undefined') { var console = { log: function () {} }; }

// ---- Entry points (called by the app) ------------------------------------------------

function __load(workbook) {
  // The time zone first: reading the cells turns date serials into Dates in it.
  __WB = { id: workbook.id, name: workbook.name, tz: workbook.tz || 'UTC', sheets: [] };
  __WB.sheets = workbook.sheets.map(function (s) { return new Sheet_(s); });
  __ops = [];
  __active = null;
  __uploads = workbook.uploads || {};
}

/** One API request, answered exactly as the web app would answer it. */
function __request(action, payload, reply) {
  __ops = [];
  var params = { action: action, key: LOCAL_KEY };
  if (reply) params.reply = reply;
  var out = handleApi_(params, payload || {});
  var body = out && out.__text !== undefined ? JSON.parse(out.__text) : out;
  return JSON.stringify({ reply: body, ops: __ops });
}

/** Runs a script function by name (setupNewWorkbook, continueSetup…) and returns the changes. */
function __call(name) {
  __ops = [];
  var result = this[name]();
  return JSON.stringify({ reply: { ok: true, data: result === undefined ? null : result }, ops: __ops });
}

/** Replaces the parts that need the network; runs after the .gs files load. */
function __overrides() {
  // Photos: the app uploads them before the request and trashes replaced ones after.
  savePhotoTo_ = function (folderOf, base64, name) { return __uploads[String(base64)] || ''; };
  trashPhoto_ = function (id) { if (id) __ops.push({ t: 'trash', id: String(id) }); };
  photoFolder_ = function () { return __noop(); };
  parentPhotoFolder_ = function () { return __noop(); };
  authorizeFoodPhotos = function () {};
  // Rebuilding a workbook from scratch: the app makes a fresh one from its blueprint instead.
  resetAll_ = function () { throw new Error('RESET_NATIVE'); };
}
