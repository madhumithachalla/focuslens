import test from 'node:test';
import assert from 'node:assert/strict';
import {
  normalizeDomain, isValidDomain, matchEntry, isWindowActive, shouldBlockNow, getLockInfo, validateSchedule,
  buildRules, effectiveDomains, checkAnswer, checkQuiz, isQuizReady, sanitizeSettings, defaultSettings,
  passesLeft, recordBlock, recordGaveIn, summarizeStats, pruneStats, originalUrlFromHash, PRESET_PACKS,
} from '../logic.js';

const at = (y, m, d, h, min) => new Date(y, m - 1, d, h, min); // local time
// 2025-01-06 is a Monday
const MON = (h, m) => at(2025, 1, 6, h, m);

test('normalizeDomain', () => {
  assert.equal(normalizeDomain('https://www.Example.com/path?q=1'), 'example.com');
  assert.equal(normalizeDomain('  m.Facebook.com:8080/x '), 'm.facebook.com');
  assert.equal(normalizeDomain('example.com.'), 'example.com');
  assert.equal(normalizeDomain('localhost'), 'localhost');
  assert.equal(normalizeDomain('127.0.0.1:3000'), '127.0.0.1');
  assert.equal(normalizeDomain('bücher.de'), 'xn--bcher-kva.de');
  for (const bad of ['', 'nodot', 'a b.com', 'http://', '-bad.com', 'x.c', 'a..com', null, 42, '999.1.1.1']) assert.equal(normalizeDomain(bad), null, String(bad));
  assert.ok(isValidDomain('a-b.co.uk'));
});

test('matchEntry prefers most specific, covers subdomains only', () => {
  const list = ['google.com', 'news.google.com'];
  assert.equal(matchEntry('news.google.com', list), 'news.google.com');
  assert.equal(matchEntry('x.news.google.com', list), 'news.google.com');
  assert.equal(matchEntry('mail.google.com', list), 'google.com');
  assert.equal(matchEntry('notgoogle.com', list), null);
});

test('isWindowActive same-day window', () => {
  const s = { days: [1], start: '07:30', end: '10:30' };
  assert.equal(isWindowActive(s, MON(7, 29)), false);
  assert.equal(isWindowActive(s, MON(7, 30)), true);
  assert.equal(isWindowActive(s, MON(10, 29)), true);
  assert.equal(isWindowActive(s, MON(10, 30)), false);
  assert.equal(isWindowActive(s, at(2025, 1, 7, 8, 0)), false); // Tuesday
});

test('isWindowActive crossing midnight', () => {
  const s = { days: [5], start: '22:00', end: '06:00' }; // Friday night
  const fri = (h, m) => at(2025, 1, 10, h, m);
  const sat = (h, m) => at(2025, 1, 11, h, m);
  assert.equal(isWindowActive(s, fri(21, 59)), false);
  assert.equal(isWindowActive(s, fri(22, 0)), true);
  assert.equal(isWindowActive(s, sat(5, 59)), true);
  assert.equal(isWindowActive(s, sat(6, 0)), false);
  assert.equal(isWindowActive(s, fri(5, 0)), false); // Friday early morning belongs to Thursday's window
  assert.equal(isWindowActive(s, sat(23, 0)), false);
  assert.equal(isWindowActive({ days: [1], start: '10:00', end: '10:00' }, MON(10, 0)), false);
});

test('shouldBlockNow: schedules, focus, pause', () => {
  const base = { ...defaultSettings(), blocklist: ['a.com'] };
  assert.equal(shouldBlockNow(base, MON(3, 0)), true); // no schedules = always
  const sch = { ...base, schedules: [{ id: '1', name: 'Morning', days: [1], start: '07:30', end: '10:30', enabled: true }] };
  assert.equal(shouldBlockNow(sch, MON(8, 0)), true);
  assert.equal(shouldBlockNow(sch, MON(12, 0)), false);
  assert.equal(shouldBlockNow({ ...sch, focusUntil: MON(12, 30).getTime() }, MON(12, 0)), true);
  assert.equal(shouldBlockNow({ ...sch, focusUntil: MON(11, 0).getTime() }, MON(12, 0)), false);
  assert.equal(shouldBlockNow({ ...sch, enabled: false }, MON(8, 0)), false);
  const allOff = { ...sch, schedules: [{ ...sch.schedules[0], enabled: false }] };
  assert.equal(shouldBlockNow(allOff, MON(12, 0)), true);
});

test('strict lock', () => {
  const s = { ...defaultSettings(), strict: true, schedules: [{ id: '1', name: 'Morning', days: [1], start: '07:30', end: '10:30', enabled: true }] };
  assert.equal(getLockInfo(s, MON(8, 0)).locked, true);
  assert.match(getLockInfo(s, MON(8, 0)).reason, /Morning/);
  assert.equal(getLockInfo(s, MON(11, 0)).locked, false);
  assert.equal(getLockInfo({ ...s, strict: false }, MON(8, 0)).locked, false);
  assert.equal(getLockInfo({ ...s, focusUntil: MON(9, 0).getTime() }, MON(11, 0)).locked, false);
  assert.equal(getLockInfo({ ...s, focusUntil: MON(12, 0).getTime() }, MON(11, 0)).locked, true);
});

test('validateSchedule', () => {
  assert.deepEqual(validateSchedule({ name: 'x', days: [1], start: '07:30', end: '10:30' }), []);
  assert.ok(validateSchedule({ name: '', days: [1], start: '07:30', end: '10:30' }).length);
  assert.ok(validateSchedule({ name: 'x', days: [], start: '07:30', end: '10:30' }).length);
  assert.ok(validateSchedule({ name: 'x', days: [1], start: '25:00', end: '10:30' }).length);
  assert.ok(validateSchedule({ name: 'x', days: [1], start: '08:00', end: '08:00' }).length);
});

test('buildRules', () => {
  const url = 'chrome-extension://abc/blocked.html';
  const rules = buildRules(['a.com', 'b.com'], url);
  assert.equal(rules.length, 2);
  assert.deepEqual(rules.map((r) => r.id), [1, 2]);
  assert.deepEqual(rules[0].condition.requestDomains, ['a.com']);
  assert.deepEqual(rules[0].condition.resourceTypes, ['main_frame']);
  assert.equal(rules[0].action.redirect.regexSubstitution, url + '#\\1');
  const allowed = buildRules(['a.com', 'b.com'], url, { 'a.com': 2000 }, 1000);
  assert.deepEqual(allowed.map((r) => r.condition.requestDomains[0]), ['b.com']);
  const expired = buildRules(['a.com'], url, { 'a.com': 500 }, 1000);
  assert.equal(expired.length, 1);
  assert.deepEqual(buildRules([], url), []);
});

test('effectiveDomains merges packs, dedupes, normalises', () => {
  const s = sanitizeSettings({ blocklist: ['https://www.YouTube.com/x', 'foo.org', 'foo.org', 'junk'], packs: { video: true } });
  const d = effectiveDomains(s);
  assert.equal(d.filter((x) => x === 'youtube.com').length, 1);
  assert.ok(d.includes('foo.org') && d.includes('netflix.com'));
  assert.ok(!d.includes('junk'));
  for (const p of Object.values(PRESET_PACKS)) for (const x of p.domains) assert.equal(normalizeDomain(x), x);
});

test('quiz checking', () => {
  const short = { type: 'short', q: 'Capital of France?', answer: 'Paris|Paris, France' };
  assert.equal(checkAnswer(short, '  paris '), true);
  assert.equal(checkAnswer(short, 'PARIS!'), true);
  assert.equal(checkAnswer(short, 'Paris France'), true);
  assert.equal(checkAnswer(short, 'Lyon'), false);
  assert.equal(checkAnswer(short, ''), false);
  const mc = { type: 'mc', q: '2+2?', choices: ['3', '4', '5', ''], answer: 1 };
  assert.equal(checkAnswer(mc, '1'), true);
  assert.equal(checkAnswer(mc, 1), true);
  assert.equal(checkAnswer(mc, '0'), false);
  assert.equal(checkAnswer(mc, ''), false);
  assert.equal(checkAnswer({ type: 'mc', q: 'x', choices: ['a', 'b'], answer: 5 }, '5'), false);
  const qs = [short, mc, { ...short, answer: 'x' }];
  assert.deepEqual(checkQuiz(qs, ['paris', '1', 'x']), { correct: [true, true, true], allCorrect: true });
  assert.equal(checkQuiz(qs, ['paris', '2', 'x']).allCorrect, false);
  assert.equal(isQuizReady({ enabled: true, questions: qs }), true);
  assert.equal(isQuizReady({ enabled: true, questions: qs.slice(0, 2) }), false);
  assert.equal(isQuizReady({ enabled: false, questions: qs }), false);
});

test('sanitizeSettings handles junk and import', () => {
  const s = sanitizeSettings({ delaySec: 7, passesPerDay: -1, schedules: [{ name: '', days: [] }, { name: 'ok', days: [1, 1, 9], start: '09:00', end: '10:00' }], blocklist: 'x' });
  assert.equal(s.delaySec, 5);
  assert.equal(s.passesPerDay, 3);
  assert.equal(s.schedules.length, 1);
  assert.deepEqual(s.schedules[0].days, [1]);
  assert.deepEqual(s.blocklist, []);
  assert.equal(s.quiz.questions.length, 3);
  assert.deepEqual(sanitizeSettings(sanitizeSettings(JSON.parse(JSON.stringify(s)))).delaySec, 5);
  assert.equal(sanitizeSettings(null).enabled, true);
});

test('passes per day, strict disables', () => {
  const s = { ...defaultSettings(), passesPerDay: 3 };
  const d = MON(9, 0);
  assert.equal(passesLeft(s, { date: '', used: 0 }, d), 3);
  assert.equal(passesLeft(s, { date: '2025-01-06', used: 2 }, d), 1);
  assert.equal(passesLeft(s, { date: '2025-01-05', used: 3 }, d), 3);
  assert.equal(passesLeft(s, { date: '2025-01-06', used: 5 }, d), 0);
  assert.equal(passesLeft({ ...s, strict: true }, { date: '', used: 0 }, d), 0);
});

test('stats: record, prune to 30 days, summarize', () => {
  let st = { days: {} };
  st = recordBlock(st, 'a.com', at(2024, 12, 1, 9, 0));
  st = recordBlock(st, 'a.com', MON(9, 0));
  st = recordBlock(st, 'b.com', MON(9, 5));
  st = recordGaveIn(st, MON(9, 6));
  assert.equal(st.days['2024-12-01'], undefined); // older than 30 days, pruned on write
  const sum = summarizeStats(st, MON(10, 0));
  assert.equal(sum.today.blocked, 2);
  assert.equal(sum.today.gaveIn, 1);
  assert.equal(sum.blocked30, 2);
  assert.deepEqual(sum.topSites[0], ['a.com', 1]);
  assert.equal(sum.perDay.length, 30);
  assert.equal(sum.perDay[29].blocked, 2);
  assert.equal(Object.keys(pruneStats({ days: { '2020-01-01': {}, '2025-01-05': {} } }, MON(9, 0)).days).length, 1);
});

test('originalUrlFromHash', () => {
  assert.equal(originalUrlFromHash('#https://a.com/x?y=1&z=2#frag'), 'https://a.com/x?y=1&z=2#frag');
  assert.equal(originalUrlFromHash('#javascript:alert(1)'), null);
  assert.equal(originalUrlFromHash(''), null);
});

// ---- adaptive question gate ----
import { readFileSync } from 'node:fs';
import { isGateReady, attemptsBefore } from '../logic.js';
import { QuizSession, baseLevelForAttempts } from '../quiz-engine.js';

test('extension quiz engine is identical to the web app copy', () => {
  const a = readFileSync(new URL('../quiz-engine.js', import.meta.url), 'utf8');
  let b;
  try { b = readFileSync(new URL('../../docs/quiz-engine.js', import.meta.url), 'utf8'); } catch { return; } // zip/standalone: nothing to compare
  assert.equal(a, b);
});

test('gate is ready only when enabled with at least one category', () => {
  assert.equal(isGateReady({ enabled: true, categories: ['math'], needed: 2 }), true);
  assert.equal(isGateReady({ enabled: true, categories: [], needed: 2 }), false);
  assert.equal(isGateReady({ enabled: false, categories: ['math'], needed: 2 }), false);
  assert.equal(isGateReady(undefined), false);
});

test('attempts before this one never go negative and feed the starting level', () => {
  assert.equal(attemptsBefore({ days: {} }), 0);
  const day = new Date();
  const key = `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`;
  const n = attemptsBefore({ days: { [key]: { blocked: 5 } } }, day);
  assert.ok(n === 4 || n === 0);
  const s = new QuizSession({ categories: ['math'], attemptsToday: 6 });
  assert.equal(s.base, baseLevelForAttempts(6));
});
