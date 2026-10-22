// Adaptive question engine for the FocusLens "intercept": the question you answer before a blocked
// app or site opens. No imports and no DOM so it can be reused as-is by the web app, the browser
// extension, and ported line for line to Kotlin. Math and logic questions are generated (answers
// are computed, never typed), so they can't be wrong; the other categories are a small written bank.
//
// Difficulty runs from level 1 (easy) to 5 (hard) and rises two ways: within a session, two
// correct answers in a row move you up a level and a miss moves you down; across the day, every
// unlock attempt starts you one level higher than the last pair (see baseLevelForAttempts), so
// repeatedly reaching for a blocked app gets steadily harder to talk yourself into.

export const CATEGORIES = [
  { id: "math", label: "Math" },
  { id: "logic", label: "Logic patterns" },
  { id: "language", label: "Words" },
  { id: "science", label: "Science" },
  { id: "general", label: "General knowledge" },
  { id: "wellbeing", label: "Focus & wellbeing" },
];

export const MAX_LEVEL = 5;

// Small deterministic RNG (mulberry32) so tests and replays are reproducible.
export function makeRng(seed = Date.now()) {
  let a = seed >>> 0;
  return function rng() {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const int = (rng, lo, hi) => lo + Math.floor(rng() * (hi - lo + 1));
const pick = (rng, arr) => arr[Math.floor(rng() * arr.length)];

function shuffle(rng, arr) {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

/** Four unique numeric choices: the answer plus plausible near misses. */
function numericChoices(rng, answer, spread) {
  const set = new Set([answer]);
  let guard = 0;
  while (set.size < 4 && guard++ < 50) {
    const delta = int(rng, 1, spread) * (rng() < 0.5 ? -1 : 1);
    const v = answer + delta;
    if (v !== answer) set.add(v);
  }
  let pad = 1;
  while (set.size < 4) set.add(answer + spread + pad++);
  const choices = shuffle(rng, [...set]);
  return { choices: choices.map(String), answerIndex: choices.indexOf(answer) };
}

// ---------------------------------------------------------------- Generated: math

function mathQuestion(rng, level) {
  let prompt, answer, explain;
  if (level === 1) {
    const a = int(rng, 3, 19), b = int(rng, 2, 9);
    prompt = `${a} + ${b}`; answer = a + b; explain = `${a} plus ${b} is ${answer}.`;
  } else if (level === 2) {
    const a = int(rng, 24, 89), b = int(rng, 11, 48);
    if (rng() < 0.5) { prompt = `${a} + ${b}`; answer = a + b; }
    else { prompt = `${a + b} − ${b}`; answer = a; }
    explain = `Work it out column by column: ${answer}.`;
  } else if (level === 3) {
    const a = int(rng, 6, 15), b = int(rng, 4, 14);
    prompt = `${a} × ${b}`; answer = a * b; explain = `${a} times ${b} is ${answer}.`;
  } else if (level === 4) {
    const a = int(rng, 3, 12), b = int(rng, 3, 12), c = int(rng, 2, 9);
    prompt = `${a} + ${b} × ${c}`; answer = a + b * c;
    explain = `Multiply first: ${b} × ${c} = ${b * c}, then add ${a} to get ${answer}.`;
  } else {
    const kind = int(rng, 0, 2);
    if (kind === 0) {
      const pct = pick(rng, [5, 10, 15, 20, 25, 30, 40, 60, 75]);
      const base = pick(rng, [40, 80, 120, 160, 200, 240, 360, 480]);
      prompt = `${pct}% of ${base}`; answer = (pct * base) / 100;
      explain = `${pct}% of ${base} = ${pct} × ${base} ÷ 100 = ${answer}.`;
    } else if (kind === 1) {
      const x = int(rng, 3, 15), m = int(rng, 2, 9), k = int(rng, 4, 30);
      prompt = `If ${m}x + ${k} = ${m * x + k}, what is x`; answer = x;
      explain = `Subtract ${k}, then divide by ${m}: x = ${x}.`;
    } else {
      const a = int(rng, 12, 25), b = int(rng, 6, 14);
      prompt = `${a}² − ${b}²`; answer = a * a - b * b;
      explain = `${a * a} − ${b * b} = ${answer}.`;
    }
  }
  const spread = level <= 2 ? 6 : level === 3 ? 12 : 20;
  const { choices, answerIndex } = numericChoices(rng, answer, spread);
  return { category: "math", level, prompt: /^If /.test(prompt) ? `${prompt}?` : `What is ${prompt}?`, choices, answerIndex, explain };
}

// ---------------------------------------------------------------- Generated: number patterns

function logicQuestion(rng, level) {
  let seq, answer, explain;
  if (level === 1) {
    const start = int(rng, 1, 20), step = int(rng, 2, 7);
    seq = [0, 1, 2, 3].map((i) => start + i * step); answer = start + 4 * step; explain = `The sequence goes up by ${step} each time.`;
  } else if (level === 2) {
    const a = int(rng, 2, 9), b = int(rng, 11, 19), s1 = int(rng, 2, 4), s2 = int(rng, 3, 6);
    seq = [a, b, a + s1, b + s2, a + 2 * s1]; answer = b + 2 * s2;
    explain = `Two interleaved lines: one adds ${s1}, the other adds ${s2}.`;
  } else if (level === 3) {
    const start = int(rng, 2, 5), r = int(rng, 2, 3);
    seq = [0, 1, 2, 3].map((i) => start * Math.pow(r, i)); answer = start * Math.pow(r, 4); explain = `Each number is ${r} times the one before.`;
  } else if (level === 4) {
    const a = int(rng, 1, 4), b = int(rng, 2, 6);
    seq = [a, b]; for (let i = 2; i < 6; i++) seq.push(seq[i - 1] + seq[i - 2]);
    answer = seq[5] + seq[4]; seq = seq.slice(0, 6);
    explain = "Each number is the sum of the two before it.";
  } else {
    const k = int(rng, 1, 4);
    seq = [1, 2, 3, 4, 5].map((n) => n * n + k * n); answer = 36 + 6 * k;
    explain = `The n-th term is n² + ${k}n, so the 6th is ${answer}.`;
  }
  const { choices, answerIndex } = numericChoices(rng, answer, level < 3 ? 6 : 18);
  return { category: "logic", level, prompt: `What comes next? ${seq.join(", ")}, …`, choices, answerIndex, explain };
}

// ---------------------------------------------------------------- Written bank
// Each entry: [prompt, correct, [three wrong answers], explanation]. Only facts that are stable.

const BANK = {
  language: {
    1: [
      ["Which word means the opposite of 'hot'?", "Cold", ["Warm", "Soft", "Fast"], "Cold is the opposite of hot."],
      ["Which word is spelled correctly?", "Because", ["Becuase", "Becouse", "Beacause"], "Because: b-e-c-a-u-s-e."],
      ["Which word means the same as 'big'?", "Large", ["Tiny", "Quiet", "Sharp"], "Large and big are synonyms."],
    ],
    2: [
      ["Which word is spelled correctly?", "Definitely", ["Definately", "Definitly", "Defanitely"], "Definitely: de-fin-ite-ly."],
      ["'Brief' is closest in meaning to:", "Short", ["Loud", "Careful", "Heavy"], "Brief means short in time or length."],
      ["Complete the pair: hand is to glove as foot is to:", "Sock", ["Shoe lace", "Toe", "Leg"], "A glove covers a hand; a sock covers a foot."],
    ],
    3: [
      ["'Meticulous' most nearly means:", "Very careful about detail", ["Easily angered", "Very tired", "Fond of travel"], "Meticulous describes extreme care with details."],
      ["Choose the correct word: 'The new policy will ___ everyone in the office.'", "affect", ["effect", "affact", "efect"], "'Affect' is the verb; 'effect' is usually the noun."],
      ["The opposite of 'scarce' is:", "Plentiful", ["Rare", "Costly", "Hidden"], "Scarce means in short supply; plentiful means the reverse."],
    ],
    4: [
      ["'Ephemeral' most nearly means:", "Lasting a very short time", ["Extremely large", "Hard to believe", "Easy to see"], "Ephemeral things are short-lived."],
      ["Which sentence uses 'its' correctly?", "The dog wagged its tail.", ["The dog wagged it's tail.", "Its a sunny day today.", "The book lost it's cover."], "'Its' is possessive; 'it's' means 'it is' or 'it has'."],
      ["'Ubiquitous' most nearly means:", "Found everywhere", ["Hard to pronounce", "Brightly coloured", "Very old"], "Ubiquitous describes something present everywhere."],
    ],
    5: [
      ["'Equivocal' most nearly means:", "Open to more than one interpretation", ["Perfectly balanced", "Clearly false", "Strongly opposed"], "An equivocal statement is ambiguous."],
      ["Which word is a synonym for 'laconic'?", "Brief in speech", ["Talkative", "Sad", "Hasty"], "A laconic person uses few words."],
      ["'Perfunctory' describes an action done:", "With minimal effort or interest", ["With great enthusiasm", "In secret", "At the wrong time"], "Perfunctory means routine and superficial."],
    ],
  },
  science: {
    1: [
      ["Which planet do we live on?", "Earth", ["Mars", "Venus", "Jupiter"], "We live on Earth."],
      ["Water freezes into ice at what temperature (Celsius)?", "0°C", ["10°C", "50°C", "100°C"], "Pure water freezes at 0 °C at normal pressure."],
      ["Which gas do humans need to breathe in to live?", "Oxygen", ["Helium", "Methane", "Argon"], "Our cells use oxygen for energy."],
    ],
    2: [
      ["What is the closest star to Earth?", "The Sun", ["Proxima Centauri", "Sirius", "The North Star"], "The Sun is about 150 million km away."],
      ["Plants take in which gas for photosynthesis?", "Carbon dioxide", ["Nitrogen", "Hydrogen", "Oxygen"], "Plants use carbon dioxide and light to make sugars."],
      ["How many bones are in a typical adult human body?", "206", ["106", "306", "412"], "Adults have 206 bones."],
    ],
    3: [
      ["What is the chemical symbol for sodium?", "Na", ["So", "Sd", "S"], "Na comes from the Latin 'natrium'."],
      ["Which organ produces insulin?", "Pancreas", ["Liver", "Kidney", "Spleen"], "Insulin is made in the pancreas."],
      ["Approximately how long does light from the Sun take to reach Earth?", "About 8 minutes", ["About 8 seconds", "About 8 hours", "About 1 day"], "Roughly 8 minutes 20 seconds."],
    ],
    4: [
      ["What is the most abundant gas in Earth's atmosphere?", "Nitrogen", ["Oxygen", "Carbon dioxide", "Argon"], "About 78% is nitrogen."],
      ["Which particle carries a negative electric charge?", "Electron", ["Proton", "Neutron", "Photon"], "Electrons are negatively charged."],
      ["What does DNA stand for?", "Deoxyribonucleic acid", ["Dinitrogen acid", "Dioxyribose nucleic anion", "Deoxyribose nitrate acid"], "DNA is deoxyribonucleic acid."],
    ],
    5: [
      ["Which neurotransmitter is most associated with reward and motivation?", "Dopamine", ["Insulin", "Cortisol", "Melatonin"], "Dopamine is central to reward and motivation circuits."],
      ["The powerhouse of the cell, producing most of its ATP, is the:", "Mitochondrion", ["Ribosome", "Golgi apparatus", "Lysosome"], "Mitochondria make most of the cell's ATP."],
      ["Which hormone is released mainly at night and signals sleep timing?", "Melatonin", ["Adrenaline", "Thyroxine", "Oxytocin"], "Melatonin rises in the evening in darkness."],
    ],
  },
  general: {
    1: [
      ["How many days are in a week?", "7", ["5", "6", "10"], "There are seven days in a week."],
      ["Which is the largest ocean on Earth?", "Pacific", ["Atlantic", "Indian", "Arctic"], "The Pacific Ocean is the largest."],
      ["How many minutes are in an hour?", "60", ["30", "100", "120"], "60 minutes make an hour."],
    ],
    2: [
      ["What is the capital of Australia?", "Canberra", ["Sydney", "Melbourne", "Perth"], "Canberra is the capital."],
      ["How many continents are there?", "7", ["5", "6", "8"], "By the common convention there are seven."],
      ["Which language has the most native speakers?", "Mandarin Chinese", ["English", "Spanish", "Hindi"], "Mandarin has the most native speakers."],
    ],
    3: [
      ["In which year did the first Moon landing happen?", "1969", ["1959", "1972", "1981"], "Apollo 11 landed in July 1969."],
      ["Which element has the atomic number 1?", "Hydrogen", ["Helium", "Oxygen", "Carbon"], "Hydrogen is element number 1."],
      ["What is the longest river in Africa?", "The Nile", ["The Congo", "The Niger", "The Zambezi"], "The Nile is generally cited as Africa's longest."],
    ],
    4: [
      ["Who wrote the play 'Hamlet'?", "William Shakespeare", ["Christopher Marlowe", "Charles Dickens", "Oscar Wilde"], "Shakespeare wrote Hamlet around 1600."],
      ["What is the smallest prime number?", "2", ["0", "1", "3"], "2 is the smallest prime and the only even prime."],
      ["Which country has the largest land area?", "Russia", ["Canada", "China", "United States"], "Russia is the largest by area."],
    ],
    5: [
      ["The Rosetta Stone helped scholars decipher which writing system?", "Egyptian hieroglyphs", ["Cuneiform", "Linear B", "Mayan glyphs"], "It carried the same decree in three scripts."],
      ["What is the approximate value of pi to two decimal places?", "3.14", ["3.12", "3.41", "2.71"], "Pi ≈ 3.14159."],
      ["Which of these is a prime number?", "89", ["87", "91", "93"], "89 is prime; 87 = 3×29, 91 = 7×13, 93 = 3×31."],
    ],
  },
  wellbeing: {
    1: [
      ["How many hours of sleep do most adults need each night?", "About 7–9", ["About 3–4", "About 11–12", "Only 5"], "Most adults do best with roughly 7–9 hours."],
      ["Which helps your eyes after long screen use?", "Looking at something far away", ["Staring closer at the screen", "Turning brightness to the max", "Rubbing them hard"], "The 20-20-20 habit rests focusing muscles."],
      ["What is a good first step when you feel overwhelmed?", "Take a few slow breaths", ["Check more notifications", "Skip a meal", "Stay up late"], "Slow breathing can settle the body."],
    ],
    2: [
      ["The Pomodoro technique traditionally uses focus blocks of:", "25 minutes", ["5 minutes", "2 hours", "10 seconds"], "25 minutes, then a short break."],
      ["Looking away from a screen every 20 minutes for 20 seconds at something 20 feet away is called the:", "20-20-20 rule", ["5-5-5 rule", "Rule of 3", "Half-time rule"], "It's a common eye-strain habit."],
      ["Which is generally better before bed?", "Dim lights and fewer screens", ["Bright screens", "A large coffee", "A hard workout"], "Light and stimulation delay sleepiness."],
    ],
    3: [
      ["About how long is one full sleep cycle?", "About 90 minutes", ["About 15 minutes", "About 3 hours", "About 6 hours"], "A typical cycle is roughly 90 minutes."],
      ["Task switching usually makes work:", "Slower and more error-prone", ["Faster", "No different", "Easier to remember"], "Switching has a cost in time and accuracy."],
      ["Which is a sign of 'sleep debt'?", "Needing caffeine to feel alert most days", ["Waking naturally before the alarm", "Feeling calm at night", "Dreaming vividly once"], "Chronic short sleep builds up."],
    ],
    4: [
      ["What does HRV measure?", "Variation in time between heartbeats", ["Blood sugar", "Lung capacity", "Muscle strength"], "Heart rate variability is the variation between beats."],
      ["'Implementation intentions' are plans written as:", "If situation X, then I will do Y", ["Maybe I will someday", "Always do everything", "Never do Z"], "If-then plans make follow-through easier."],
      ["Which tends to boost focus for the next few hours?", "A short walk", ["Skipping water", "More tabs open", "Scrolling feeds"], "Brief movement can improve attention."],
    ],
    5: [
      ["Which best describes 'time blindness'?", "Difficulty sensing how much time has passed", ["Being unable to see clocks", "Sleeping too long", "Always arriving early"], "Common in ADHD; external timers help."],
      ["Why can a fixed start time make starting a task easier?", "It removes the decision about when to begin", ["It makes the task shorter", "It hides deadlines", "It skips planning"], "Fewer decisions means less friction."],
      ["A screening questionnaire like the PHQ-9 can:", "Suggest whether to talk to a clinician", ["Diagnose depression", "Replace therapy", "Predict the future"], "Screeners guide next steps; they don't diagnose."],
    ],
  },
};

function bankQuestion(rng, category, level) {
  const pool = BANK[category][level] || BANK[category][1];
  const [prompt, correct, wrong, explain] = pick(rng, pool);
  const all = shuffle(rng, [correct, ...wrong]);
  return { category, level, prompt, choices: all, answerIndex: all.indexOf(correct), explain };
}

// ---------------------------------------------------------------- Public API

/** One question in `category` at `level` (1-5). */
export function generateQuestion({ category, level = 1, rng = Math.random }) {
  const lv = Math.max(1, Math.min(MAX_LEVEL, Math.round(level)));
  const q = category === "math" ? mathQuestion(rng, lv) : category === "logic" ? logicQuestion(rng, lv) : bankQuestion(rng, category, lv);
  return { id: `${category}-${lv}-${Math.floor(rng() * 1e9)}`, ...q };
}

/** Where today's gate starts: every two unlock attempts raise the starting level by one. */
export function baseLevelForAttempts(attemptsToday) {
  return Math.min(MAX_LEVEL, 1 + Math.floor(Math.max(0, attemptsToday) / 2));
}

/**
 * A gate session: the user must answer `needed` questions correctly. Categories come from the
 * user's enabled list (and rotate so it isn't the same type every time). Two right in a row moves
 * difficulty up one level, a wrong answer moves it down one (never below the day's base level).
 */
export class QuizSession {
  constructor({ categories, attemptsToday = 0, needed = 3, rng = Math.random, customQuestions = [] } = {}) {
    this.categories = categories && categories.length ? categories : CATEGORIES.map((c) => c.id);
    this.base = baseLevelForAttempts(attemptsToday);
    this.level = this.base;
    this.needed = needed;
    this.correct = 0;
    this.asked = 0;
    this.streak = 0;
    this.rng = rng;
    this.custom = customQuestions;
    this._last = null;
  }

  get done() { return this.correct >= this.needed; }

  next() {
    // Your own questions mix in, one in four, when you've written some.
    if (this.custom.length && this.rng() < 0.25) {
      const c = pick(this.rng, this.custom);
      this._last = { id: `custom-${this.asked}`, category: "custom", level: this.level, ...c };
      return this._last;
    }
    const cat = this.categories[this.asked % this.categories.length];
    this._last = generateQuestion({ category: cat, level: this.level, rng: this.rng });
    return this._last;
  }

  answer(index) {
    const q = this._last;
    const right = q && index === q.answerIndex;
    this.asked++;
    if (right) {
      this.correct++;
      this.streak++;
      if (this.streak >= 2) { this.level = Math.min(MAX_LEVEL, this.level + 1); this.streak = 0; }
    } else {
      this.streak = 0;
      this.level = Math.max(this.base, this.level - 1);
    }
    return { right: !!right, explain: q ? q.explain : "", correctIndex: q ? q.answerIndex : -1, done: this.done };
  }
}
