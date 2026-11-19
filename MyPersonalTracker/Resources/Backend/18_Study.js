/**
 * STUDY · What you're studying, the concepts in it, the time you put in, and your goals.
 *
 *   Study Subjects — one row per subject or course: why you're learning it,
 *                    the book/course you use, a weekly hours target, a target
 *                    date (exam, deadline) and whether it's active.
 *   Study Topics   — the concepts inside each subject: stage (To learn →
 *                    Learning → Revising → Mastered), how confident you are,
 *                    your notes, and when it's next due for revision.
 *   Study Log      — one row per study session: date, subject, concept,
 *                    minutes, kind (Learn, Practice, Revise, Test, Project),
 *                    focus and notes.
 *   Study Goals    — hours to put in, concepts to master, or a milestone
 *                    (pass an exam, finish a course), each with a due date.
 *   Settings       — Study block: daily minutes and weekly hours targets.
 *
 * Every row carries an ID the app makes, so saving the same thing again
 * replaces its row. Subject and concept names are copied next to their IDs so
 * the tabs read well on their own; renaming a subject updates those copies.
 */

const STUDY_SUBJECTS = {
  ID: 1, NAME: 2, WHY: 3, RESOURCE: 4, WEEKLY_HOURS: 5, TARGET_DATE: 6, STATUS: 7, COLOR: 8, STARTED: 9,
  FIRST_ROW: 4
};
const STUDY_TOPICS = {
  ID: 1, SUBJECT_ID: 2, SUBJECT: 3, TOPIC: 4, STAGE: 5, CONFIDENCE: 6, LAST_STUDIED: 7, NEXT_REVIEW: 8,
  REVIEWS: 9, NOTES: 10, LINK: 11, ADDED: 12,
  FIRST_ROW: 4
};
const STUDY_LOG = {
  ID: 1, DATE: 2, SUBJECT_ID: 3, SUBJECT: 4, TOPIC_ID: 5, TOPIC: 6, MINUTES: 7, KIND: 8, FOCUS: 9, NOTES: 10, SCORE: 11,
  FIRST_ROW: 4
};
const STUDY_GOALS = {
  ID: 1, TITLE: 2, KIND: 3, SUBJECT_ID: 4, SUBJECT: 5, TARGET: 6, START: 7, DUE: 8, DONE: 9, DONE_ON: 10, NOTES: 11,
  FIRST_ROW: 4
};
const SUBJECT_STATUSES = ['Active', 'Paused', 'Completed'];
const TOPIC_STAGES = ['To learn', 'Learning', 'Revising', 'Mastered'];
const SESSION_KINDS = ['Learn', 'Practice', 'Revise', 'Test', 'Project'];
const GOAL_KINDS = ['Hours', 'Topics', 'Milestone'];
const STUDY_SECTION_ROW = 44;

// ---- Sheets ------------------------------------------------------------------

/** Creates the study tabs and the Study settings the first time they're needed. Never clears anything. */
function ensureStudySheets_(ss) {
  ensureStudySettings_(ss);
  ensureTab_(ss, SHEETS.STUDY_SUBJECTS, 'STUDY SUBJECTS  ·  What you are studying',
    'One row per subject or course. Weekly target in hours; target date is an exam or deadline (optional).',
    ['Subject ID', 'Subject', 'Why / what for', 'Book, course or link', 'Weekly\nTarget (h)', 'Target\nDate', 'Status', 'Colour', 'Started'],
    [110, 180, 240, 220, 80, 90, 90, 60, 90],
    sheet => {
      sheet.getRange(STUDY_SUBJECTS.FIRST_ROW, STUDY_SUBJECTS.TARGET_DATE, 500, 1).setNumberFormat('dd-mmm-yy');
      sheet.getRange(STUDY_SUBJECTS.FIRST_ROW, STUDY_SUBJECTS.STARTED, 500, 1).setNumberFormat('dd-mmm-yy');
      sheet.getRange(STUDY_SUBJECTS.FIRST_ROW, STUDY_SUBJECTS.STATUS, 500, 1).setDataValidation(
        SpreadsheetApp.newDataValidation().requireValueInList(SUBJECT_STATUSES, true).build());
    });
  ensureTab_(ss, SHEETS.STUDY_TOPICS, 'STUDY TOPICS  ·  Concepts, how well you know them, when to revise',
    'Stage: To learn → Learning → Revising → Mastered. Confidence 1–5. Next review is when it is due for revision.',
    ['Topic ID', 'Subject ID', 'Subject', 'Topic / concept', 'Stage', 'Confidence\n(1–5)', 'Last\nStudied', 'Next\nReview', 'Reviews', 'Notes / key points', 'Link', 'Added'],
    [110, 110, 150, 220, 90, 80, 90, 90, 70, 300, 180, 90],
    sheet => {
      [STUDY_TOPICS.LAST_STUDIED, STUDY_TOPICS.NEXT_REVIEW, STUDY_TOPICS.ADDED].forEach(col =>
        sheet.getRange(STUDY_TOPICS.FIRST_ROW, col, 3000, 1).setNumberFormat('dd-mmm-yy'));
      sheet.getRange(STUDY_TOPICS.FIRST_ROW, STUDY_TOPICS.STAGE, 3000, 1).setDataValidation(
        SpreadsheetApp.newDataValidation().requireValueInList(TOPIC_STAGES, true).build());
      sheet.getRange(STUDY_TOPICS.FIRST_ROW, STUDY_TOPICS.NOTES, 3000, 1).setWrap(true);
    });
  ensureTab_(ss, SHEETS.STUDY_LOG, 'STUDY LOG  ·  Every study session',
    'Minutes studied, on what, and how it went. Focus 1–5. Score is a test or quiz result in % (optional).',
    ['Session ID', 'Date', 'Subject ID', 'Subject', 'Topic ID', 'Topic / concept', 'Minutes', 'Kind', 'Focus\n(1–5)', 'Notes', 'Score\n(%)'],
    [110, 90, 110, 150, 110, 200, 70, 80, 70, 280, 70],
    sheet => {
      sheet.getRange(STUDY_LOG.FIRST_ROW, STUDY_LOG.DATE, 5000, 1).setNumberFormat('dd-mmm-yy');
      sheet.getRange(STUDY_LOG.FIRST_ROW, STUDY_LOG.KIND, 5000, 1).setDataValidation(
        SpreadsheetApp.newDataValidation().requireValueInList(SESSION_KINDS, true).build());
    });
  ensureTab_(ss, SHEETS.STUDY_GOALS, 'STUDY GOALS  ·  What you are aiming for',
    'Hours: study this many hours. Topics: master this many concepts. Milestone: tick it off yourself (exam, course, project).',
    ['Goal ID', 'Goal', 'Kind', 'Subject ID', 'Subject', 'Target', 'Start', 'Due', 'Done', 'Done On', 'Notes'],
    [110, 240, 90, 110, 150, 70, 90, 90, 60, 90, 240],
    sheet => {
      [STUDY_GOALS.START, STUDY_GOALS.DUE, STUDY_GOALS.DONE_ON].forEach(col =>
        sheet.getRange(STUDY_GOALS.FIRST_ROW, col, 500, 1).setNumberFormat('dd-mmm-yy'));
      sheet.getRange(STUDY_GOALS.FIRST_ROW, STUDY_GOALS.KIND, 500, 1).setDataValidation(
        SpreadsheetApp.newDataValidation().requireValueInList(GOAL_KINDS, true).build());
    });
}

/** Daily minutes and weekly hours targets, below the setup status line on Settings. */
function ensureStudySettings_(ss) {
  const sheet = ss.getSheetByName(SHEETS.SETTINGS);
  if (!sheet) return;
  const r = STUDY_SECTION_ROW;
  if (sheet.getMaxRows() >= r + 3 && sheet.getRange(r + 2, 1).getValue() === 'Daily Study Target (min)') return;
  ensureGrid(sheet, r + 6, 3);
  sectionLabel(sheet, r, 1, 3, 'STUDY');
  headerRow(sheet, r + 1, 1, ['Parameter', 'Value', 'What it drives']);
  sheet.getRange(r + 2, 1, 2, 3).setValues([
    ['Daily Study Target (min)', 60, 'Today ring on the Study tab'],
    ['Weekly Study Target (h)', 7, 'This week bar on the Study tab']
  ]);
  const values = sheet.getRange(r + 2, 2, 2, 1);
  markInput(values);
  values.setFontWeight('bold').setHorizontalAlignment('center').setNumberFormat('#,##0.##');
  sheet.getRange(r + 2, 1, 2, 1).setFontWeight('bold');
  sheet.getRange(r + 2, 3, 2, 1).setFontSize(9).setFontColor(THEME.MUTED);
  styleBlock(sheet, r + 1, 1, 3, 3);
}

// ---- Reading -------------------------------------------------------------------

function readStudy_(ss) {
  ensureStudySheets_(ss);
  const since = ymd_(new Date(Date.now() - 400 * 86400000));
  return {
    subjects: rowsOf_(ss, SHEETS.STUDY_SUBJECTS, STUDY_SUBJECTS.FIRST_ROW, STUDY_SUBJECTS.STARTED)
      .map(x => ({
        id: String(x.v[STUDY_SUBJECTS.ID - 1] || 'row' + x.row),
        name: value_(x.v[STUDY_SUBJECTS.NAME - 1]),
        why: value_(x.v[STUDY_SUBJECTS.WHY - 1]),
        resource: value_(x.v[STUDY_SUBJECTS.RESOURCE - 1]),
        weeklyHours: value_(x.v[STUDY_SUBJECTS.WEEKLY_HOURS - 1]),
        targetDate: ymd_(x.v[STUDY_SUBJECTS.TARGET_DATE - 1]),
        status: value_(x.v[STUDY_SUBJECTS.STATUS - 1]) || 'Active',
        color: value_(x.v[STUDY_SUBJECTS.COLOR - 1]),
        started: ymd_(x.v[STUDY_SUBJECTS.STARTED - 1])
      }))
      .filter(s => s.name),
    topics: rowsOf_(ss, SHEETS.STUDY_TOPICS, STUDY_TOPICS.FIRST_ROW, STUDY_TOPICS.ADDED)
      .map(x => ({
        id: String(x.v[STUDY_TOPICS.ID - 1] || 'row' + x.row),
        subjectId: String(x.v[STUDY_TOPICS.SUBJECT_ID - 1] || ''),
        topic: value_(x.v[STUDY_TOPICS.TOPIC - 1]),
        stage: value_(x.v[STUDY_TOPICS.STAGE - 1]) || 'To learn',
        confidence: value_(x.v[STUDY_TOPICS.CONFIDENCE - 1]),
        lastStudied: ymd_(x.v[STUDY_TOPICS.LAST_STUDIED - 1]),
        nextReview: ymd_(x.v[STUDY_TOPICS.NEXT_REVIEW - 1]),
        reviews: value_(x.v[STUDY_TOPICS.REVIEWS - 1]),
        notes: value_(x.v[STUDY_TOPICS.NOTES - 1]),
        link: value_(x.v[STUDY_TOPICS.LINK - 1]),
        added: ymd_(x.v[STUDY_TOPICS.ADDED - 1])
      }))
      .filter(t => t.topic),
    sessions: rowsOf_(ss, SHEETS.STUDY_LOG, STUDY_LOG.FIRST_ROW, STUDY_LOG.SCORE)
      .map(x => ({
        id: String(x.v[STUDY_LOG.ID - 1] || 'row' + x.row),
        date: ymd_(x.v[STUDY_LOG.DATE - 1]),
        subjectId: String(x.v[STUDY_LOG.SUBJECT_ID - 1] || ''),
        subject: value_(x.v[STUDY_LOG.SUBJECT - 1]),
        topicId: String(x.v[STUDY_LOG.TOPIC_ID - 1] || ''),
        topic: value_(x.v[STUDY_LOG.TOPIC - 1]),
        minutes: value_(x.v[STUDY_LOG.MINUTES - 1]),
        kind: value_(x.v[STUDY_LOG.KIND - 1]) || 'Learn',
        focus: value_(x.v[STUDY_LOG.FOCUS - 1]),
        notes: value_(x.v[STUDY_LOG.NOTES - 1]),
        score: value_(x.v[STUDY_LOG.SCORE - 1])
      }))
      .filter(s => s.date && s.date >= since),
    goals: rowsOf_(ss, SHEETS.STUDY_GOALS, STUDY_GOALS.FIRST_ROW, STUDY_GOALS.NOTES)
      .map(x => ({
        id: String(x.v[STUDY_GOALS.ID - 1] || 'row' + x.row),
        title: value_(x.v[STUDY_GOALS.TITLE - 1]),
        kind: value_(x.v[STUDY_GOALS.KIND - 1]) || 'Milestone',
        subjectId: String(x.v[STUDY_GOALS.SUBJECT_ID - 1] || ''),
        target: value_(x.v[STUDY_GOALS.TARGET - 1]),
        start: ymd_(x.v[STUDY_GOALS.START - 1]),
        due: ymd_(x.v[STUDY_GOALS.DUE - 1]),
        done: x.v[STUDY_GOALS.DONE - 1] === 'Y',
        doneOn: ymd_(x.v[STUDY_GOALS.DONE_ON - 1]),
        notes: value_(x.v[STUDY_GOALS.NOTES - 1])
      }))
      .filter(g => g.title)
  };
}

// ---- Writing -------------------------------------------------------------------

/** { id, name, why, resource, weeklyHours, targetDate, status, color, started } */
function saveStudySubject_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureStudySheets_(ss);
  const id = requiredId_(p.id);
  const name = String(p.name || '').trim();
  if (!name) throw new Error('Give the subject a name.');
  const status = SUBJECT_STATUSES.indexOf(p.status) !== -1 ? p.status : 'Active';
  upsertById_(ss.getSheetByName(SHEETS.STUDY_SUBJECTS), STUDY_SUBJECTS.FIRST_ROW, id, [
    id, name.slice(0, 120), p.why || '', p.resource || '', num(p.weeklyHours),
    optionalDate_(p.targetDate), status, num(p.color), optionalDate_(p.started) || parseYmd_(ymd_(new Date()))
  ]);
  // Keep the readable copies of the name in step.
  renameIn_(ss, SHEETS.STUDY_TOPICS, STUDY_TOPICS.FIRST_ROW, STUDY_TOPICS.SUBJECT_ID, STUDY_TOPICS.SUBJECT, id, name);
  renameIn_(ss, SHEETS.STUDY_LOG, STUDY_LOG.FIRST_ROW, STUDY_LOG.SUBJECT_ID, STUDY_LOG.SUBJECT, id, name);
  renameIn_(ss, SHEETS.STUDY_GOALS, STUDY_GOALS.FIRST_ROW, STUDY_GOALS.SUBJECT_ID, STUDY_GOALS.SUBJECT, id, name);
}

/** Removes the subject with its topics and goals. Past sessions stay in the log as history. */
function deleteStudySubject_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const id = String(p.id || '');
  if (!id) return;
  deleteWhere_(ss, SHEETS.STUDY_SUBJECTS, STUDY_SUBJECTS.FIRST_ROW, STUDY_SUBJECTS.ID, id);
  deleteWhere_(ss, SHEETS.STUDY_TOPICS, STUDY_TOPICS.FIRST_ROW, STUDY_TOPICS.SUBJECT_ID, id);
  deleteWhere_(ss, SHEETS.STUDY_GOALS, STUDY_GOALS.FIRST_ROW, STUDY_GOALS.SUBJECT_ID, id);
}

/** { id, subjectId, topic, stage, confidence, lastStudied, nextReview, reviews, notes, link, added } */
function saveStudyTopic_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureStudySheets_(ss);
  const id = requiredId_(p.id);
  const topic = String(p.topic || '').trim();
  if (!topic) throw new Error('Name the topic or concept.');
  const subjectId = String(p.subjectId || '');
  const stage = TOPIC_STAGES.indexOf(p.stage) !== -1 ? p.stage : 'To learn';
  const confidence = num(p.confidence);
  upsertById_(ss.getSheetByName(SHEETS.STUDY_TOPICS), STUDY_TOPICS.FIRST_ROW, id, [
    id, subjectId, subjectName_(ss, subjectId), topic.slice(0, 200), stage,
    confidence === '' ? '' : Math.max(1, Math.min(5, Math.round(confidence))),
    optionalDate_(p.lastStudied), optionalDate_(p.nextReview), num(p.reviews),
    String(p.notes || '').slice(0, 5000), p.link || '', optionalDate_(p.added) || parseYmd_(ymd_(new Date()))
  ]);
  renameIn_(ss, SHEETS.STUDY_LOG, STUDY_LOG.FIRST_ROW, STUDY_LOG.TOPIC_ID, STUDY_LOG.TOPIC, id, topic);
}

function deleteStudyTopic_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  if (p.id) deleteWhere_(ss, SHEETS.STUDY_TOPICS, STUDY_TOPICS.FIRST_ROW, STUDY_TOPICS.ID, String(p.id));
}

/** { id, date, subjectId, topicId, minutes, kind, focus, notes, score } */
function saveStudySession_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureStudySheets_(ss);
  const id = requiredId_(p.id);
  const minutes = num(p.minutes);
  if (!(minutes > 0)) throw new Error('Enter how many minutes you studied.');
  const subjectId = String(p.subjectId || '');
  const topicId = String(p.topicId || '');
  const focus = num(p.focus);
  upsertById_(ss.getSheetByName(SHEETS.STUDY_LOG), STUDY_LOG.FIRST_ROW, id, [
    id, parseYmd_(p.date), subjectId, subjectName_(ss, subjectId) || p.subject || '',
    topicId, topicName_(ss, topicId) || p.topic || '', Math.round(minutes),
    SESSION_KINDS.indexOf(p.kind) !== -1 ? p.kind : 'Learn',
    focus === '' ? '' : Math.max(1, Math.min(5, Math.round(focus))),
    String(p.notes || '').slice(0, 5000), num(p.score)
  ]);
}

function deleteStudySession_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  if (p.id) deleteWhere_(ss, SHEETS.STUDY_LOG, STUDY_LOG.FIRST_ROW, STUDY_LOG.ID, String(p.id));
}

/** { id, title, kind, subjectId, target, start, due, done, doneOn, notes } */
function saveStudyGoal_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ensureStudySheets_(ss);
  const id = requiredId_(p.id);
  const title = String(p.title || '').trim();
  if (!title) throw new Error('Describe the goal.');
  const kind = GOAL_KINDS.indexOf(p.kind) !== -1 ? p.kind : 'Milestone';
  const target = num(p.target);
  if (kind !== 'Milestone' && !(target > 0)) throw new Error('Set a target above zero.');
  const subjectId = String(p.subjectId || '');
  upsertById_(ss.getSheetByName(SHEETS.STUDY_GOALS), STUDY_GOALS.FIRST_ROW, id, [
    id, title.slice(0, 200), kind, subjectId, subjectName_(ss, subjectId), kind === 'Milestone' ? '' : target,
    optionalDate_(p.start) || parseYmd_(ymd_(new Date())), optionalDate_(p.due),
    p.done ? 'Y' : '', p.done ? (optionalDate_(p.doneOn) || parseYmd_(ymd_(new Date()))) : '', p.notes || ''
  ]);
}

function deleteStudyGoal_(p) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  if (p.id) deleteWhere_(ss, SHEETS.STUDY_GOALS, STUDY_GOALS.FIRST_ROW, STUDY_GOALS.ID, String(p.id));
}

// ---- Helpers -------------------------------------------------------------------

function requiredId_(id) {
  const text = String(id || '').trim();
  if (!text) throw new Error('Missing id.');
  return text;
}

function optionalDate_(text) {
  return text ? parseYmd_(text) : '';
}

/** Replaces the row whose first column is `id`, or appends one. */
function upsertById_(sheet, firstRow, id, values) {
  const last = sheet.getLastRow();
  let row = null;
  if (last >= firstRow) {
    const ids = sheet.getRange(firstRow, 1, last - firstRow + 1, 1).getValues();
    const i = ids.findIndex(v => String(v[0]) === id);
    if (i !== -1) row = firstRow + i;
  }
  sheet.getRange(row || Math.max(firstRow, last + 1), 1, 1, values.length).setValues([values]);
}

/** Deletes every row whose `col` equals `value`, bottom up. */
function deleteWhere_(ss, name, firstRow, col, value) {
  const sheet = ss.getSheetByName(name);
  if (!sheet || sheet.getLastRow() < firstRow) return;
  const values = sheet.getRange(firstRow, col, sheet.getLastRow() - firstRow + 1, 1).getValues();
  for (let i = values.length - 1; i >= 0; i--) {
    if (String(values[i][0]) === value) sheet.deleteRow(firstRow + i);
  }
}

/** Writes `name` into `nameCol` on every row whose `idCol` is `id`. */
function renameIn_(ss, sheetName, firstRow, idCol, nameCol, id, name) {
  const sheet = ss.getSheetByName(sheetName);
  if (!id || !sheet || sheet.getLastRow() < firstRow) return;
  const count = sheet.getLastRow() - firstRow + 1;
  const ids = sheet.getRange(firstRow, idCol, count, 1).getValues();
  const names = sheet.getRange(firstRow, nameCol, count, 1).getValues();
  let changed = false;
  ids.forEach((v, i) => {
    if (String(v[0]) === id && names[i][0] !== name) { names[i][0] = name; changed = true; }
  });
  if (changed) sheet.getRange(firstRow, nameCol, count, 1).setValues(names);
}

function lookupName_(ss, sheetName, firstRow, idCol, nameCol, id) {
  const sheet = ss.getSheetByName(sheetName);
  if (!id || !sheet || sheet.getLastRow() < firstRow) return '';
  const count = sheet.getLastRow() - firstRow + 1;
  const ids = sheet.getRange(firstRow, idCol, count, 1).getValues();
  const i = ids.findIndex(v => String(v[0]) === id);
  return i === -1 ? '' : String(sheet.getRange(firstRow + i, nameCol).getValue() || '');
}

function subjectName_(ss, id) {
  return lookupName_(ss, SHEETS.STUDY_SUBJECTS, STUDY_SUBJECTS.FIRST_ROW, STUDY_SUBJECTS.ID, STUDY_SUBJECTS.NAME, id);
}

function topicName_(ss, id) {
  return lookupName_(ss, SHEETS.STUDY_TOPICS, STUDY_TOPICS.FIRST_ROW, STUDY_TOPICS.ID, STUDY_TOPICS.TOPIC, id);
}
