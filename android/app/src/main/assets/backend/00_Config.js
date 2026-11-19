/**
 * LIFE & FINANCE COMMAND CENTER
 * Shared configuration: sheet names, layout constants and the theme.
 *
 * Every other file reads from here. Changing a sheet name or a column order
 * is a one-line edit in this file rather than a hunt through eight builders.
 */

const SHEETS = {
  DASHBOARD: 'Dashboard',
  DAILY: 'Daily Tracker',
  FINANCE: 'Finance Tracker',
  SALARY: 'Salary & Savings',
  WEEKLY: 'Weekly Summary',
  MONTHLY: 'Monthly Summary',
  SETTINGS: 'Settings',
  RECURRING: 'Recurring',
  FOOD_LOG: 'Food Log',
  MEAL_PLAN: 'Meal Plan',
  MY_FOODS: 'My Foods',
  PEOPLE: 'People',
  STUDY_SUBJECTS: 'Study Subjects',
  STUDY_TOPICS: 'Study Topics',
  STUDY_LOG: 'Study Log',
  STUDY_GOALS: 'Study Goals',
  SHOPPING: 'Shopping',
  SHOPPING_TRIPS: 'Shopping Trips',
  SET_ASIDE: 'Set Aside',
  SPLIT: 'Salary Split',
  SAVINGS_GOALS: 'Savings Goals',
  PLANNER: 'Planner',
  OUTFITS: 'Outfits',
  WARDROBE: 'Wardrobe',
  LIFE_GOALS: 'Life Goals',
  GOAL_STEPS: 'Goal Steps',
  RENEWALS: 'Renewals',
  LOANS: 'Loans',
  MY_INPUTS: 'My Inputs',
  INPUT_LOG: 'Input Log',
  PARENTS: 'Parents',
  PARENT_MEDICINES: 'Parent Medicines',
  PARENT_DATES: 'Parent Dates',
  TAKE_HOME: 'Take Home',
  PARENT_HEALTH: 'Parent Health'
};

/** Palette lifted from the source workbook so the rebuild looks familiar. */
const THEME = {
  HEADER_BG: '#1F3864',
  HEADER_TEXT: '#FFFFFF',
  SUBHEADER_BG: '#2F5597',
  TABLE_HEAD_BG: '#2F5597',
  INPUT_BG: '#FFFDE7',        // yellow = you type here
  CALC_BG: '#F2F5FA',         // grey/blue = formula, don't touch
  BORDER: '#BFC9DA',
  GOOD: '#1E7B34',
  GOOD_BG: '#D9EAD3',
  BAD: '#C0392B',
  BAD_BG: '#F4CCCC',
  WARN: '#B7791F',
  WARN_BG: '#FFF2CC',
  MUTED: '#5A6B85',
  CARD_BORDER: '#D4DCE8'
};

/**
 * Settings cell addresses.
 *
 * Formulas across the workbook point at these by name. The PDF hardcoded
 * things like "Settings B17" into its help text; naming them here means a row
 * inserted on Settings doesn't silently repoint every formula in the file.
 */
const CFG = {
  START_DATE:      'B6',
  END_DATE:        'B7',
  RATE:            'B8',
  DAILY_LIMIT:     'B9',
  MONTHLY_LIMIT:   'B10',
  WATER_TARGET:    'B11',
  SLEEP_TARGET:    'B12',
  STEPS_TARGET:    'B13',
  SCREEN_LIMIT:    'B14',
  SALARY:          'B15',
  SAVINGS_GOAL:    'B16',
  GREAT_DAY:       'B17',
  REMIT_AS_SAVING: 'B18',
  // The Currency block (11_Currency.gs) sits lower down, clear of the derived section.
  MAIN_CURRENCY:   'B28',
  HOME_CURRENCY:   'B29',
  // The Nutrition block (14_Food.gs), below the currency block.
  CALORIE_TARGET:  'B34',
  PROTEIN_TARGET:  'B35',
  CARB_TARGET:     'B36',
  FAT_TARGET:      'B37',
  FOOD_BUDGET:     'B38',
  // The Study block (18_Study.gs), below the setup status line.
  STUDY_DAILY_TARGET:  'B46',
  STUDY_WEEKLY_TARGET: 'B47',
  // The Body block (19_Body.gs): your details in, suggested targets out.
  SEX:                'B52',
  BIRTH_YEAR:         'B53',
  HEIGHT_CM:          'B54',
  BODY_WEIGHT:        'B55',
  ACTIVITY:           'B56',
  BODY_GOAL:          'B57',
  PROTEIN_PER_KG:     'B58',
  FOLLOW_WEIGHT:      'B59',
  CURRENT_WEIGHT:     'B60',
  BMR:                'B61',
  TDEE:               'B62',
  SUGGESTED_CALORIES: 'B63',
  // The Salary Split block (21_Split.gs): a share of income for each bucket.
  SPLIT_ESSENTIALS:   'B68',
  SPLIT_EMERGENCY:    'B69',
  SPLIT_SAVINGS:      'B70',
  SPLIT_FAMILY:       'B71',
  SPLIT_LIFESTYLE:    'B72',
  EMERGENCY_GOAL:     'B73',
  // The monthly spend limit: from the split (Y) or the custom amount below.
  LIMIT_FROM_SPLIT:   'B75',
  CUSTOM_LIMIT:       'B76',

  // Your habits (HABITS block, row 78 — see 29_Habits.gs): name, on (Y/N), icon.
  HABIT1_NAME: 'B80', HABIT2_NAME: 'B81', HABIT3_NAME: 'B82', HABIT4_NAME: 'B83', HABIT5_NAME: 'B84',
  HABIT1_ON:   'C80', HABIT2_ON:   'C81', HABIT3_ON:   'C82', HABIT4_ON:   'C83', HABIT5_ON:   'C84',
  HABIT1_ICON: 'D80', HABIT2_ICON: 'D81', HABIT3_ICON: 'D82', HABIT4_ICON: 'D83', HABIT5_ICON: 'D84',
  WATER_ON: 'C85', SLEEP_ON: 'C86', STEPS_ON: 'C87', WEIGHT_ON: 'C88', SCREEN_ON: 'C89',
  SCORE_OUT_OF: 'B90'
};

/** Absolute references, for use inside formula strings. */
function cfg(key) {
  return "'" + SHEETS.SETTINGS + "'!$" + CFG[key].charAt(0) + '$' + CFG[key].slice(1);
}

/** Expense categories — drives the dropdown, the dashboard breakdown and charts. */
const CATEGORIES = [
  'Groceries', 'Food & Dining', 'Transport', 'Rent & Utilities',
  'Mobile & Internet', 'Health & Pharmacy', 'Personal Care', 'Household',
  'Clothing', 'Remittance', 'Entertainment', 'Gifts & Family',
  'Education', 'Travel', 'Other'
];

const PAYMENT_METHODS = [
  'Cash', 'Debit Card', 'Credit Card', 'Apple Pay / Google Pay',
  'Bank Transfer', 'Other'
];

/**
 * The five yes/no habits, in column order on the Daily Tracker.
 *
 * Water, sleep and steps are scored from their numeric columns against the
 * Settings targets, so they are NOT in this list — but they do count toward
 * the daily score. See SCORE_DENOMINATOR.
 */
const YN_HABITS = [
  { key: 'office',    label: 'In Office\nby 9:00 AM', target: 'Every working day' },
  { key: 'breakfast', label: 'Breakfast\nDone',       target: 'Every day' },
  { key: 'lunch',     label: 'Lunch\nTaken',          target: 'Every day' },
  { key: 'protein',   label: 'Protein in\nLunch',     target: 'Every day' },
  { key: 'workout',   label: 'Workout\nDone',         target: 'Every day' }
];

/**
 * Daily score denominator: 5 Y/N habits + water + sleep.
 *
 * Steps is reported as a success rate on the summaries but deliberately does
 * NOT feed the score, matching the source workbook. Set this to 8 and add the
 * steps term in buildDailyTracker to include it.
 */
const SCORE_DENOMINATOR = 7;

/** Daily Tracker column layout (1-indexed). */
const DT = {
  DATE: 1, DAY: 2,
  OFFICE: 3, BREAKFAST: 4, LUNCH: 5, PROTEIN: 6, WORKOUT: 7,
  WATER: 8, SLEEP: 9, STEPS: 10, WEIGHT: 11, SCREEN: 12,
  NOTES: 13,
  HABITS_MET: 14, SCORE: 15, STATUS: 16, STREAK: 17,
  HEADER_ROW: 4,
  FIRST_ROW: 5
};

/** Finance Tracker column layout (1-indexed). */
const FT = {
  DATE: 1, DESC: 2, CATEGORY: 3, METHOD: 4,
  QAR: 5, INR: 6, PCT: 7, NOTES: 8,
  HEADER_ROW: 4,
  FIRST_ROW: 5,
  MAX_ROWS: 1000            // ledger capacity; one row per purchase
};

/** Day-by-day budget block, to the right of the ledger. */
const BUDGET = {
  DATE: 10, SPENT_QAR: 11, SPENT_INR: 12, VS_LIMIT: 13, MTD: 14, LEFT: 15,
  HEADER_ROW: 4,
  FIRST_ROW: 5
};

const SALARY_COLS = {
  MONTH: 1, SALARY: 2, OTHER: 3, TOTAL_INCOME: 4, EXPENSES: 5,
  REMIT_QAR: 6, REMIT_INR: 7, NET_SAVINGS: 8, RATE: 9,
  GOAL: 10, CUMULATIVE: 11, STATUS: 12,
  HEADER_ROW: 4,
  FIRST_ROW: 5
};

/** How many months the Salary & Savings sheet plans ahead. */
const SALARY_MONTHS = 12;

/** Rows reserved for weekly summary (a 47-day period is 7 weeks; allow a year). */
const WEEK_ROWS = 54;
