/**
 * YEAR IN REVIEW · One year's days, money and income, read across the current
 * period and every archived one ("Archive … · Daily / Finance / Salary").
 *
 * Archives are plain-value copies with the same layout, so they read like the
 * live tabs. A day that appears in more than one period counts once (the live
 * tab wins, then the newest archive); a ledger line that was carried into the
 * next period counts once too. The app adds study, goals and savings, which
 * aren't period-based.
 */

function yearReview_(year) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const y = String(year || new Date().getFullYear());
  const today = ymd_(new Date());
  const archives = suffix => ss.getSheets().map(s => s.getName())
    .filter(n => /^Archive /.test(n) && n.endsWith(' · ' + suffix)).reverse();   // newest first

  // ---- Days
  const days = {};
  [SHEETS.DAILY].concat(archives('Daily')).forEach(name => {
    const sheet = ss.getSheetByName(name);
    if (!sheet || sheet.getLastRow() < DT.FIRST_ROW) return;
    sheet.getRange(DT.FIRST_ROW, 1, sheet.getLastRow() - DT.FIRST_ROW + 1, DT.STREAK).getValues().forEach(r => {
      if (!(r[DT.DATE - 1] instanceof Date)) return;
      const date = ymd_(r[DT.DATE - 1]);
      if (date.slice(0, 4) !== y || date > today || days[date]) return;
      days[date] = r;
    });
  });
  const dayList = Object.keys(days).sort().map(d => ({ date: d, r: days[d] }));
  const n = v => typeof v === 'number' && !isNaN(v) ? v : null;
  const logged = dayList.filter(d => n(d.r[DT.SCORE - 1]) !== null);
  const steps = dayList.filter(d => n(d.r[DT.STEPS - 1]) > 0);
  const sleep = dayList.filter(d => n(d.r[DT.SLEEP - 1]) > 0);
  const weights = dayList.filter(d => n(d.r[DT.WEIGHT - 1]) > 0);
  const bestSteps = steps.reduce((b, d) => !b || d.r[DT.STEPS - 1] > b.r[DT.STEPS - 1] ? d : b, null);
  const byMonthScore = {};
  logged.forEach(d => { const m = d.date.slice(5, 7); (byMonthScore[m] = byMonthScore[m] || []).push(d.r[DT.SCORE - 1]); });

  // ---- Money
  const lineCounts = {};       // key → highest count in any one tab
  const lines = {};
  [SHEETS.FINANCE].concat(archives('Finance')).forEach(name => {
    const sheet = ss.getSheetByName(name);
    if (!sheet || sheet.getLastRow() < FT.FIRST_ROW) return;
    const here = {};
    sheet.getRange(FT.FIRST_ROW, 1, sheet.getLastRow() - FT.FIRST_ROW + 1, FT.NOTES).getValues().forEach(r => {
      const amount = n(r[FT.QAR - 1]);
      if (!(r[FT.DATE - 1] instanceof Date) || !(amount > 0)) return;
      const date = ymd_(r[FT.DATE - 1]);
      if (date.slice(0, 4) !== y) return;
      const line = { date: date, description: String(r[FT.DESC - 1] || ''), category: String(r[FT.CATEGORY - 1] || 'Other'), amount: amount };
      const key = [date, line.description, line.category, amount].join('|');
      here[key] = (here[key] || 0) + 1;
      lines[key] = line;
    });
    Object.keys(here).forEach(k => { lineCounts[k] = Math.max(lineCounts[k] || 0, here[k]); });
  });
  let spent = 0;
  const byCategory = {}, byMonth = {}, spendDays = {};
  let biggest = null;
  Object.keys(lineCounts).forEach(k => {
    const line = lines[k], times = lineCounts[k];
    spent += line.amount * times;
    byCategory[line.category] = (byCategory[line.category] || 0) + line.amount * times;
    const m = line.date.slice(5, 7);
    byMonth[m] = (byMonth[m] || 0) + line.amount * times;
    spendDays[line.date] = true;
    if (!biggest || line.amount > biggest.amount) biggest = line;
  });

  // ---- Income and money sent home
  const months = {};
  [SHEETS.SALARY].concat(archives('Salary')).forEach(name => {
    const sheet = ss.getSheetByName(name);
    if (!sheet || sheet.getLastRow() < SALARY_COLS.FIRST_ROW) return;
    sheet.getRange(SALARY_COLS.FIRST_ROW, 1, sheet.getLastRow() - SALARY_COLS.FIRST_ROW + 1, SALARY_COLS.REMIT_QAR).getValues().forEach(r => {
      const month = r[SALARY_COLS.MONTH - 1] instanceof Date ? ymd_(r[SALARY_COLS.MONTH - 1]).slice(0, 7) : String(r[SALARY_COLS.MONTH - 1] || '').slice(0, 7);
      if (month.slice(0, 4) !== y || months[month] || month > today.slice(0, 7)) return;
      const income = n(r[SALARY_COLS.TOTAL_INCOME - 1]), remit = n(r[SALARY_COLS.REMIT_QAR - 1]);
      if (income === null && remit === null) return;
      months[month] = { income: income || 0, remit: remit || 0 };
    });
  });
  const income = Object.keys(months).reduce((a, m) => a + months[m].income, 0);
  const remitted = Object.keys(months).reduce((a, m) => a + months[m].remit, 0);

  const round = v => Math.round(v * 100) / 100;
  const avg = list => list.length ? list.reduce((a, b) => a + b, 0) / list.length : null;
  return {
    year: Number(y),
    from: dayList.length ? dayList[0].date : null,
    to: dayList.length ? dayList[dayList.length - 1].date : null,
    days: {
      tracked: dayList.length,
      logged: logged.length,
      averageScore: avg(logged.map(d => d.r[DT.SCORE - 1])),
      greatDays: logged.filter(d => d.r[DT.STATUS - 1] === 'Excellent').length,
      bestStreak: dayList.reduce((b, d) => Math.max(b, n(d.r[DT.STREAK - 1]) || 0), 0),
      workouts: dayList.filter(d => d.r[DT.WORKOUT - 1] === 'Y').length,
      steps: steps.reduce((a, d) => a + d.r[DT.STEPS - 1], 0),
      stepDays: steps.length,
      bestStepsDay: bestSteps ? { date: bestSteps.date, steps: bestSteps.r[DT.STEPS - 1] } : null,
      averageSleep: avg(sleep.map(d => d.r[DT.SLEEP - 1])),
      weightFrom: weights.length ? { date: weights[0].date, kg: weights[0].r[DT.WEIGHT - 1] } : null,
      weightTo: weights.length ? { date: weights[weights.length - 1].date, kg: weights[weights.length - 1].r[DT.WEIGHT - 1] } : null,
      scoreByMonth: Object.keys(byMonthScore).sort().map(m => ({ month: m, score: avg(byMonthScore[m]) }))
    },
    money: {
      spent: round(spent),
      byCategory: Object.keys(byCategory).map(c => ({ category: c, amount: round(byCategory[c]) })).sort((a, b) => b.amount - a.amount),
      byMonth: Object.keys(byMonth).sort().map(m => ({ month: m, amount: round(byMonth[m]) })),
      biggest: biggest,
      noSpendDays: dayList.filter(d => !spendDays[d.date]).length,
      income: round(income),
      remitted: round(remitted),
      months: Object.keys(months).length
    }
  };
}
