// Curated content shared with the Android app's data (ArticlesScreen.kt /
// MediaSuggestionsScreen.kt / RewardSuggestions.kt) — kept in sync by hand, not generated,
// so every link and title here is a real, verifiable thing, same policy as the Android side.

export const LEARN_TOPICS = [
  { id: "ALL", label: "All" },
  { id: "FOCUS_PRODUCTIVITY", label: "Focus & Productivity" },
  { id: "SLEEP", label: "Sleep" },
  { id: "MENTAL_HEALTH", label: "Mental Health" },
  { id: "HABITS", label: "Habits & Behavior" },
  { id: "STUDY_SKILLS", label: "Study Skills" },
  { id: "RESEARCH", label: "Research & Papers" },
  { id: "SEX_ED", label: "Sex Education" },
  { id: "LGBTQ", label: "LGBTQ+ Support" },
  { id: "NEURODIVERGENCE", label: "Neurodivergence & ADHD" },
];

export const LEARN_ITEMS = [
  { id: "who_mental_health", title: "Mental health of adolescents (fact sheet)", source: "World Health Organization", url: "https://www.who.int/news-room/fact-sheets/detail/mental-health-of-adolescents", topic: "MENTAL_HEALTH", complexity: 1, kind: "Article" },
  { id: "nhs_every_mind", title: "Every Mind Matters", source: "NHS", url: "https://www.nhs.uk/every-mind-matters/", topic: "MENTAL_HEALTH", complexity: 1, kind: "Org hub" },
  { id: "mind_uk", title: "Information and support", source: "Mind (UK mental health charity)", url: "https://www.mind.org.uk/", topic: "MENTAL_HEALTH", complexity: 1, kind: "Org hub" },
  { id: "apa", title: "Stress, coping and everyday psychology", source: "American Psychological Association", url: "https://www.apa.org/", topic: "MENTAL_HEALTH", complexity: 2, kind: "Org hub" },
  { id: "digital_wellbeing", title: "Digital Wellbeing", source: "Google", url: "https://wellbeing.google/", topic: "FOCUS_PRODUCTIVITY", complexity: 1, kind: "Org hub" },
  { id: "headspace", title: "Mindfulness and focus", source: "Headspace", url: "https://www.headspace.com/", topic: "FOCUS_PRODUCTIVITY", complexity: 1, kind: "Org hub" },
  { id: "newsinhealth", title: "News in Health", source: "NIH (National Institutes of Health)", url: "https://newsinhealth.nih.gov/", topic: "FOCUS_PRODUCTIVITY", complexity: 2, kind: "Newsletter" },
  { id: "cdc_sleep", title: "Sleep and health", source: "CDC", url: "https://www.cdc.gov/sleep/", topic: "SLEEP", complexity: 1, kind: "Article" },
  { id: "fs_blog", title: "Brain Food", source: "Farnam Street", url: "https://fs.blog/", topic: "HABITS", complexity: 2, kind: "Newsletter" },
  { id: "james_clear", title: "3-2-1 Thursday", source: "James Clear", url: "https://jamesclear.com/", topic: "HABITS", complexity: 2, kind: "Newsletter" },
  { id: "marginalian", title: "The Marginalian", source: "Maria Popova", url: "https://www.themarginalian.org/", topic: "HABITS", complexity: 2, kind: "Newsletter" },
  { id: "khan_academy", title: "Free courses on anything", source: "Khan Academy", url: "https://www.khanacademy.org/", topic: "STUDY_SKILLS", complexity: 1, kind: "Org hub" },
  { id: "learning_how_to_learn", title: "Learning How to Learn (course)", source: "Coursera / UC San Diego", url: "https://www.coursera.org/learn/learning-how-to-learn", topic: "STUDY_SKILLS", complexity: 2, kind: "Course" },
  { id: "scholar", title: "Search real research papers", source: "Google Scholar", url: "https://scholar.google.com/", topic: "RESEARCH", complexity: 3, kind: "Research hub" },
  { id: "pubmed", title: "Biomedical and life-science research", source: "PubMed", url: "https://pubmed.ncbi.nlm.nih.gov/", topic: "RESEARCH", complexity: 3, kind: "Research hub" },
  { id: "jstor", title: "Academic journals and papers", source: "JSTOR", url: "https://www.jstor.org/", topic: "RESEARCH", complexity: 3, kind: "Research hub" },

  { id: "scarleteen", title: "Sex ed for real life", source: "Scarleteen", url: "https://www.scarleteen.com/", topic: "SEX_ED", complexity: 1, kind: "Org hub" },
  { id: "amaze", title: "Honest, age-appropriate sex ed", source: "AMAZE", url: "https://amaze.org/", topic: "SEX_ED", complexity: 1, kind: "Org hub" },
  { id: "planned_parenthood", title: "Sexual health info and services", source: "Planned Parenthood", url: "https://www.plannedparenthood.org/", topic: "SEX_ED", complexity: 2, kind: "Org hub" },
  { id: "nhs_sexual_health", title: "Sexual health", source: "NHS", url: "https://www.nhs.uk/live-well/sexual-health/", topic: "SEX_ED", complexity: 1, kind: "Org hub" },

  { id: "trevor_project", title: "Crisis support for LGBTQ+ young people", source: "The Trevor Project", url: "https://www.thetrevorproject.org/", topic: "LGBTQ", complexity: 1, kind: "Org hub" },
  { id: "qlife", title: "LGBTIQ+ peer support (Australia)", source: "QLife", url: "https://qlife.org.au/", topic: "LGBTQ", complexity: 1, kind: "Org hub" },
  { id: "pflag", title: "Support for LGBTQ+ people and families", source: "PFLAG", url: "https://pflag.org/", topic: "LGBTQ", complexity: 2, kind: "Org hub" },
  { id: "stonewall", title: "LGBTQ+ rights and resources", source: "Stonewall", url: "https://www.stonewall.org.uk/", topic: "LGBTQ", complexity: 2, kind: "Org hub" },

  { id: "asrs_screener", title: "Adult ADHD Self-Report Scale (ASRS v1.1)", source: "Harvard Medical School / WHO", url: "https://www.hcp.med.harvard.edu/ncs/asrs.php", topic: "NEURODIVERGENCE", complexity: 2, kind: "Professional screener" },
  { id: "chadd", title: "Support and info for ADHD, kids through adults", source: "CHADD", url: "https://chadd.org/", topic: "NEURODIVERGENCE", complexity: 1, kind: "Org hub" },
  { id: "adda", title: "Support specifically for adults with ADHD", source: "ADDA", url: "https://add.org/", topic: "NEURODIVERGENCE", complexity: 1, kind: "Org hub" },
  { id: "additude", title: "ADHD strategies and expert-reviewed articles", source: "ADDitude", url: "https://www.additudemag.com/", topic: "NEURODIVERGENCE", complexity: 2, kind: "Article" },
  { id: "cdc_adhd", title: "ADHD facts and info", source: "CDC", url: "https://www.cdc.gov/adhd/", topic: "NEURODIVERGENCE", complexity: 1, kind: "Org hub" },
  { id: "asan", title: "Autistic self-advocacy, run by autistic people", source: "Autistic Self Advocacy Network", url: "https://autisticadvocacy.org/", topic: "NEURODIVERGENCE", complexity: 1, kind: "Org hub" },
];

export const REFLECTION_PROMPTS = [
  "What's one idea from this you want to remember?",
  "What's the main claim, in your own words?",
  "What's one limitation or counterpoint worth keeping in mind?",
];

export const MEDIA_GENRES = [
  { id: "ALL", label: "All" },
  { id: "PRODUCTIVITY", label: "Productivity" },
  { id: "SCI_FI", label: "Sci-fi" },
  { id: "COMEDY", label: "Comedy" },
  { id: "DRAMA", label: "Drama" },
  { id: "DOCUMENTARY", label: "Documentary" },
  { id: "MEMOIR", label: "Memoir & Non-fiction" },
  { id: "FEEL_GOOD", label: "Feel-good" },
  { id: "FOCUS", label: "Focus" },
  { id: "CALM", label: "Calm" },
  { id: "ADHD", label: "ADHD-friendly" },
];

export const MEDIA_ITEMS = [
  { title: "Atomic Habits", creator: "James Clear", y: 2018, kind: "BOOK", genres: ["PRODUCTIVITY", "ADHD"], blurb: "Small, consistent changes compound — the book behind most modern habit-building advice." },
  { title: "Deep Work", creator: "Cal Newport", y: 2016, kind: "BOOK", genres: ["PRODUCTIVITY", "FOCUS"], blurb: "The case for long, undistracted stretches of focused work in a world built to fragment it." },
  { title: "Sapiens", creator: "Yuval Noah Harari", y: 2011, kind: "BOOK", genres: ["MEMOIR"], blurb: "A sweeping, readable history of how humans ended up running the planet." },
  { title: "Educated", creator: "Tara Westover", y: 2018, kind: "BOOK", genres: ["MEMOIR", "DRAMA"], blurb: "A memoir about growing up off-grid and the cost and value of an education found later." },
  { title: "Project Hail Mary", creator: "Andy Weir", y: 2021, kind: "BOOK", genres: ["SCI_FI"], blurb: "A lone astronaut, a dying sun, and a puzzle-box plot from the author of The Martian." },
  { title: "The Midnight Library", creator: "Matt Haig", y: 2020, kind: "BOOK", genres: ["FEEL_GOOD", "DRAMA"], blurb: "A quiet, hopeful novel about regret and the lives you didn't live." },
  { title: "Inside Out", creator: "Pixar, dir. Pete Docter", y: 2015, kind: "MOVIE", genres: ["FEEL_GOOD"], blurb: "Emotions as characters inside a kid's head — funnier and sadder than it sounds." },
  { title: "Soul", creator: "Pixar, dir. Pete Docter", y: 2020, kind: "MOVIE", genres: ["FEEL_GOOD", "DRAMA"], blurb: "What actually makes a life worth living, wrapped in a jazz musician's afterlife detour." },
  { title: "The Secret Life of Walter Mitty", creator: "dir. Ben Stiller", y: 2013, kind: "MOVIE", genres: ["FEEL_GOOD"], blurb: "A daydreamer finally goes and does the things he's only imagined." },
  { title: "Spotlight", creator: "dir. Tom McCarthy", y: 2015, kind: "MOVIE", genres: ["DRAMA"], blurb: "Journalists doing patient, careful, unglamorous work that actually matters. Best Picture, 2016." },
  { title: "Everything Everywhere All at Once", creator: "dir. the Daniels", y: 2022, kind: "MOVIE", genres: ["SCI_FI", "COMEDY"], blurb: "A multiverse kung-fu tax audit that somehow lands as one of the most moving films in years." },
  { title: "Ted Lasso", creator: "Apple TV+", y: 2020, kind: "SERIES", genres: ["COMEDY", "FEEL_GOOD"], blurb: "An American football coach leads an English football team, armed only with relentless kindness." },
  { title: "The Bear", creator: "FX / Hulu", y: 2022, kind: "SERIES", genres: ["DRAMA", "COMEDY"], blurb: "A fine-dining chef returns to run his late brother's sandwich shop. Loud, tender, stressful in a good way." },
  { title: "Abbott Elementary", creator: "ABC", y: 2021, kind: "SERIES", genres: ["COMEDY"], blurb: "A mockumentary about underfunded teachers who keep showing up anyway." },
  { title: "Brooklyn Nine-Nine", creator: "NBC / Fox", y: 2013, kind: "SERIES", genres: ["COMEDY"], blurb: "A precinct of very silly detectives. Easy to watch an episode and stop — or not." },
  { title: "Planet Earth II", creator: "BBC", y: 2016, kind: "SERIES", genres: ["DOCUMENTARY"], blurb: "Nature documentary filmmaking at its most jaw-dropping, narrated by David Attenborough." },
  { title: "Hidden Brain", creator: "Shankar Vedantam / NPR", y: 2015, kind: "PODCAST", genres: ["CALM", "FOCUS"], blurb: "Psychology and social science stories about why we do what we do. Unhurried, one idea per episode." },
  { title: "The Happiness Lab", creator: "Dr. Laurie Santos", y: 2019, kind: "PODCAST", genres: ["CALM", "FEEL_GOOD"], blurb: "A Yale professor on what research says actually makes people happier, and what doesn't." },
  { title: "Huberman Lab", creator: "Andrew Huberman", y: 2021, kind: "PODCAST", genres: ["FOCUS", "PRODUCTIVITY"], blurb: "Long, detailed neuroscience episodes on sleep, attention and habits. Treat the advice as ideas to check, not prescriptions." },
  { title: "Deep Questions", creator: "Cal Newport", y: 2020, kind: "PODCAST", genres: ["FOCUS", "PRODUCTIVITY"], blurb: "Practical Q&A on deep work, digital minimalism and organising a work life without constant busyness." },
  { title: "Ten Percent Happier", creator: "Dan Harris", kind: "PODCAST", genres: ["CALM"], blurb: "Meditation and mindfulness for sceptics, with teachers and researchers." },
  { title: "ADHD reWired", creator: "Eric Tivers", kind: "PODCAST", genres: ["ADHD"], blurb: "A coach with ADHD interviews guests and shares practical strategies for adults with ADHD." },
  { title: "Music for Airports", creator: "Brian Eno", y: 1978, kind: "MUSIC", genres: ["CALM", "FOCUS"], blurb: "The original ambient album. Slow, wordless and easy to leave on in the background." },
  { title: "Sleep", creator: "Max Richter", y: 2015, kind: "MUSIC", genres: ["CALM"], blurb: "An eight-hour piece for sleeping, with shorter excerpts that work for winding down." },
  { title: "Kind of Blue", creator: "Miles Davis", y: 1959, kind: "MUSIC", genres: ["FOCUS", "CALM"], blurb: "Relaxed modal jazz with no vocals, a long-time favourite for working to." },
  { title: "Goldberg Variations", creator: "J.S. Bach", kind: "MUSIC", genres: ["FOCUS", "CALM"], blurb: "Structured, repetitive classical keyboard music. Many people find instrumental music helps them settle into a task." },
  { title: "Dive", creator: "Tycho", y: 2011, kind: "MUSIC", genres: ["FOCUS", "ADHD"], blurb: "Warm, steady instrumental electronica with a gentle pulse. Wordless, so it won't compete with what you're reading or writing." },
  { title: "Substrata", creator: "Biosphere", y: 1997, kind: "MUSIC", genres: ["CALM"], blurb: "Cold, spacious ambient from Norway. Good for quiet, low-stimulation stretches." },
];

export const MEDIA_KINDS = [
  { id: "BOOK", label: "Books" },
  { id: "MOVIE", label: "Movies" },
  { id: "SERIES", label: "Series" },
  { id: "PODCAST", label: "Podcasts" },
  { id: "MUSIC", label: "Music" },
];

export const REWARD_IDEAS = [
  { category: "Move", text: "Step outside for 5 minutes, even just to the doorway." },
  { category: "Move", text: "Stretch your neck and shoulders for 30 seconds." },
  { category: "Move", text: "Do 10 slow squats or a short walk around the room." },
  { category: "Rest your eyes", text: "Look at something 20 feet away for 20 seconds." },
  { category: "Rest your eyes", text: "Close your eyes and breathe slowly for 1 minute." },
  { category: "Hydrate", text: "Drink a full glass of water." },
  { category: "Connect", text: "Send a kind text to someone you haven't talked to in a while." },
  { category: "Connect", text: "Call a friend or family member, even just for 2 minutes." },
  { category: "Connect", text: "Say one genuine compliment to someone nearby." },
  { category: "Create", text: "Doodle for 2 minutes, no pressure for it to look good." },
  { category: "Create", text: "Hum or sing a song you like." },
  { category: "Create", text: "Write down one idea you've been putting off." },
  { category: "Tidy", text: "Tidy one surface near you." },
  { category: "Tidy", text: "Reply to one message or email you've been avoiding." },
  { category: "Reflect", text: "Write down one thing you're grateful for today." },
  { category: "Reflect", text: "Write down what you want to do with the time you just got back." },
];

// Commonly-cited "cycle syncing" guidance — widely circulated wellness framing, not a
// clinically proven formula. Everyone's actual experience varies; treat as a starting
// suggestion to notice how you feel against, not a rule to follow regardless of how you
// actually feel that day.
export const PHASE_GUIDANCE = {
  Menstrual: {
    color: "var(--phase-menstrual)",
    text: "Energy is often lower for the first few days of a period. Often suggested for: rest, gentle movement, reflection, and lighter commitments where you can — but plenty of people feel fine, so go by how you actually feel.",
  },
  "Follicular phase": {
    color: "var(--phase-follicular)",
    text: "Energy commonly starts rising through this phase. Often suggested for: starting new projects, planning, brainstorming, and ramping up exercise intensity as you feel able to.",
  },
  "Fertile window": {
    color: "var(--phase-fertile)",
    text: "Energy and confidence often climb further here. Often suggested for: social plans, presentations, or harder conversations, if that matches how you're feeling.",
  },
  Ovulation: {
    color: "var(--phase-ovulation)",
    text: "Energy often peaks around now for many people. Often suggested for: your most demanding task or a big social event — but only if your own energy actually backs it up today.",
  },
  "Luteal phase": {
    color: "var(--phase-luteal)",
    text: "Energy and focus commonly ease off through this phase, sometimes more sharply near the end. Often suggested for: detail work, wrapping up tasks, gentler exercise, and extra rest.",
  },
};

// Pre-filled structure, not a question — tapping one fills the textarea with this scaffold.
// Still plain text underneath: saving/reading an entry never treats a template differently
// from a blank page.
export const JOURNAL_TEMPLATES = [
  { emoji: "🙏", title: "Gratitude list", scaffold: "Three things I'm grateful for today:\n1. \n2. \n3. \n" },
  { emoji: "✅", title: "Daily reflection", scaffold: "What went well today:\n\nWhat didn't go as planned:\n\nOne thing to try tomorrow:\n" },
  { emoji: "🧩", title: "Thought record (CBT)", scaffold: "Situation:\n\nWhat I thought in the moment:\n\nHow strongly I felt it (1–10):\n\nEvidence for the thought:\n\nEvidence against it:\n\nA more balanced way to see it:\n" },
  { emoji: "🌊", title: "Brain dump (ADHD)", scaffold: "Everything in my head, no order, no judging:\n\n\nPick ONE thing from above to do next:\n" },
  { emoji: "🏛️", title: "Stoic evening review", scaffold: "What did I do well today?\n\nWhere did I fall short of who I want to be?\n\nWhat was in my control, and what wasn't?\n\nWhat will I do differently tomorrow?\n" },
  { emoji: "🌓", title: "Shadow work", scaffold: "Something that really irritated me lately:\n\nWhat might it be touching in me?\n\nA quality I judge in others that I recognise in myself:\n\nHow could I treat that part of me with kindness?\n" },
  { emoji: "🌙", title: "Dream log", scaffold: "What I remember of the dream:\n\nHow it felt:\n\nAnything from yesterday it might connect to:\n" },
  { emoji: "🌆", title: "Evening wind-down", scaffold: "What's still on my mind:\n\nWhat can wait until tomorrow:\n\nOne thing that felt good today:\n\nTomorrow's first small step:\n" },
  { emoji: "⏱️", title: "5-minute micro-journal", scaffold: "Mood in one word:\n\nOne win:\n\nOne worry:\n\nOne kind thing I can do for myself:\n" },
  { emoji: "🔎", title: "Sensory & focus check-in", scaffold: "What pulled my attention today (apps, people, noise)?\n\nWhat helped me concentrate?\n\nAny sensory triggers (light, sound, texture, crowds)?\n\nOne adjustment for tomorrow:\n" },
];

// Tracking is opt-in per type — nobody is forced into any one of these; pick whichever apply.
export const TRACKER_TYPES = [
  { id: "energy", label: "Energy", hint: "1 low – 5 high" },
  { id: "mood", label: "Mood", hint: "1 low – 5 great" },
  { id: "sleep", label: "Sleep quality", hint: "1 poor – 5 great" },
  { id: "water", label: "Water intake", hint: "1 very little – 5 plenty" },
];

export const MOODS = [
  { emoji: "😔", id: "low", score: 1 },
  { emoji: "😐", id: "flat", score: 2 },
  { emoji: "🙂", id: "okay", score: 3 },
  { emoji: "😊", id: "good", score: 4 },
  { emoji: "🤩", id: "great", score: 5 },
];

export const MISS_REASONS = [
  { id: "TIRED", label: "Tired" },
  { id: "DISTRACTED", label: "Distracted" },
  { id: "TOO_BIG", label: "Felt too big" },
  { id: "FORGOT", label: "Forgot" },
  { id: "OTHER", label: "Other" },
];

export const MISS_SUGGESTIONS = {
  TIRED: "Tired came up most this week — try scheduling your hardest task earlier in the day, before you're worn down.",
  DISTRACTED: "Distraction was the top reason this week — consider raising Focus limits on the apps pulling you away (in the Android app).",
  TOO_BIG: "Tasks feeling too big came up most — try splitting your next few into smaller first steps.",
  FORGOT: "Forgetting was the top reason this week — a reminder habit (sticky note, phone alarm) might help here.",
  OTHER: "Something else got in the way most this week — worth a look at what's actually competing for your time.",
};

// What's live in this web app today vs. what's still being built, kept honestly up to
// date turn by turn rather than promising ahead of what actually works.
export const ROADMAP = {
  current: [
    { title: "Journal", brief: "Daily prompts or free writing, one entry per day, with a mood tag and a real streak (celebrates at 3/7/14/30/60/100 days)." },
    { title: "Menstrual & energy rhythm estimator", brief: "Log a period start or a general energy day — today or any past date. After 2+ logged starts: an exponentially weighted estimate of your next cycle (up to your last 12, recent ones count more), a regularity score, current phase (menstrual/follicular/fertile window/ovulation/luteal) with its own accessible color, an estimated fertile window, and commonly-cited \"what people often do in this phase\" guidance. An estimate from your own data and widely-circulated wellness framing, not a clinical prediction, diagnosis, or contraception method. No watch-sync here (needs native OS access) — that's in the Android app." },
    { title: "To-Do + weekly reflection", brief: "Add, complete, or miss tasks with a reason. A weekly summary surfaces your top miss-reason and a matching suggestion." },
    { title: "Calendar scheduling", brief: "Give a task a date/time and add it to Google Calendar (one tap) or download it as a .ics file for Apple Calendar, Outlook, or any other calendar app — no account or API key needed." },
    { title: "Learn", brief: "Newsletters, articles and research hubs across focus, sleep, mental health, habits, study skills, sex education, LGBTQ+ support, and neurodivergence/ADHD (including a real symptom screener designed by Harvard Medical School and the WHO), gated by a short self-reflection prompt before \"mark as read\" sticks." },
    { title: "Suggested for you", brief: "On the Mental health page: a simple rule-based pick from Learn based on your own recent mood trend or top missed-task reason — plainly labeled as rules, not AI or a diagnosis." },
    { title: "Account sign-in + cross-device sync", brief: "Email or Google sign-in via Firebase. Journal/Cycle/To-do/Birthdays data now follows your account — sign in on another browser or device and it pulls down automatically (newest edit wins if you changed both offline)." },
    { title: "Focus timer + Eisenhower matrix", brief: "Pomodoro (25/5), 52/17 and 90-minute sprint blocks with breaks, a sound + notification when a block ends, and a count of the minutes you've actually focused today and this week. Keeps running if you switch pages." },
    { title: "Sleep", brief: "Bedtime planner built on 90-minute sleep cycles, a nightly sleep log, a 7-day sleep-debt figure against your own target, and a body-clock (lark / in-between / owl) tip. Based on what you enter — no phone or watch sensors on the web." },
    { title: "Wellness", brief: "Habit library and tracker with a 7-day grid and streaks, an activity log with step goal, a nutrition log and your own plan, vision-board pillars, a water counter with your own daily target, a self-written medication / supplement checklist, life pillars, and a 5-4-3-2-1 grounding exercise plus box breathing for when your mind is racing." },
    { title: "Screeners", brief: "PHQ-9, GAD-7 and the WHO ASRS v1.1 Part A in their standard published wording, with saved results over time and crisis-support numbers always one tap away. Screening aids only, not a diagnosis." },
    { title: "Journal (10 modes)", brief: "Ten structured templates (gratitude, CBT thought record, ADHD brain dump, Stoic review, shadow work, dream log, wind-down, 5-minute micro-journal and more), your own saved prompts, search across all entries, and a gentle nudge to calming exercises and helplines when your mood is low." },
    { title: "Progress", brief: "Level and XP worked out from what you really log, daily quests, a weekly Doomscroll Serpent boss beaten with focus minutes, badges, and a milestone card you can save as an image." },
    { title: "Help & accessibility", brief: "Helplines by region, a plain guide to getting a proper assessment, a printable self-report summary for appointments, plus dark/OLED theme, high contrast, larger text and reduced motion." },
    { title: "Community chat", brief: "Sign in to post in General or the Quiet focus room, like and comment, and report anything harmful. Shows your display name only, never your email. Live updates; posts can't be edited, and you can delete your own." },
    { title: "Accountability partners + dares", brief: "Swap invite codes with one friend, accept the pact, then each pledge tasks with a due time. Your partner verifies what you finish; a missed task gets a light dare from a fixed list, which you complete and send proof for. Your partner sees only the tasks in the pact." },
    { title: "Medications", brief: "Keep what you take and when: your own list from your labels, a daily checklist, reminders while the app is open, and a private text update you can send to a family member through your own messaging app. Stays on this device unless you opt in to sync. Never suggests doses or products. Reading a label with the camera is Android-only." },
    { title: "Intercept (question gate)", brief: "Before a blocked app or site opens you answer questions. Math and pattern questions are generated, with word, science, general-knowledge and wellbeing questions too, plus your own. They get harder as the day goes on and as you answer well. Practise and tune it here; the live gate runs in the Android app and the browser extension." },
    { title: "Browser blocker extension", brief: "A real Chrome/Edge extension that blocks the sites you choose, with schedules, focus mode, strict mode, emergency passes and the same question gate. Download it from the Intercept page; everything stays on your device." },
    { title: "Focus quality", brief: "While a focus block runs, FocusLens counts how often you left the tab and for how long, and scores the block. Optionally it asks a quick question when you come back." },
    { title: "Settings", brief: "Accent colour, density, corner style and font, timer lengths, intercept rules, sleep and health targets, which pages show in the menu, data export/import/delete, and a full reset." },
    { title: "Glossary", brief: "Searchable plain-language definitions of the sleep, focus, cycle and screener terms used in the app, each with its primary source." },
    { title: "Explore + My stack", brief: "A curated, filterable list of real books, movies, series, podcasts and music, with focus, calm and ADHD-friendly tags. Pin items to My stack and they stay at the top and follow your account. Private to you, never shared; not a live catalog." },
    { title: "Screen time (from your Android app)", brief: "Sign in and see the last 7 days of phone usage, today's top apps and your weekly average. It only shows data the Android app has synced to your account, and shows an empty state if there is none." },
    { title: "Reward suggestions", brief: "A small real-world activity suggestion, shuffled on request." },
    { title: "Mental health trend", brief: "A 14-day mood/energy trend built from your own Journal and Cycle entries — a mirror on your own pattern, not a diagnosis." },
  ],
  upcoming: [
    { title: "Android screen-time in your account", brief: "Now available: the Android app can sync daily usage to your account (opt-in, off by default, under Sync screen time to my account), and the Screen time page on the web shows it. Remaining work: a category breakdown and Focus Score on the web." },
    { title: "Achievement sharing", brief: "Share a card of a milestone (a streak, a Focus Score) with other app users. Needs the same shared-read rules as community chat, plus an explicit opt-in so nothing is shared by default." },
    { title: "Native iOS app", brief: "On hold until there's a Mac + Apple Developer account. Blocking features would need a redesign around Apple's narrower Screen Time framework — some current Android capabilities (Reels/Shorts-only blocking, Chrome domain detection) have no iOS equivalent at all." },
  ],
  androidOnly: [
    { title: "Screen time, usage reports and Focus Score", brief: "Per-app and hourly usage, category breakdown and your Focus Score come from Android's usage-access data. A website can't read what other apps you use, so these live in the Android app only." },
    { title: "Wearable heart rate & HRV", brief: "Live pulse and heart-rate variability need a watch or band connected through Health Connect on Android. The web app does not show simulated vitals." },
    { title: "App & feature blocking", brief: "Per-app daily limits, hard 6am locks, Reels/Shorts-only blocking, Chrome social-media domain blocking, Hyper Focus, Study Mode device-wide lock. These need OS-level permissions no website in any browser can ever be granted — they stay native-app-only by design, not by oversight." },
  ],
};

// ---------------------------------------------------------------- Glossary ("What is what")
// Plain-language definitions with the primary source each one rests on. Wellness framing,
// not clinical guidance.
export const GLOSSARY = [
  { term: "Sleep cycle (ultradian rhythm)", group: "Sleep", def: "Sleep moves through light sleep, deep sleep and REM in repeating loops of roughly 90 minutes (anywhere from ~70 to ~120 between people and nights). The bedtime calculator uses 90 as an average, so treat its times as a starting point.", cite: "Kleitman, N. (1963). Sleep and Wakefulness. University of Chicago Press." },
  { term: "REM sleep", group: "Sleep", def: "Rapid-eye-movement sleep: vivid dreaming, a brain that looks nearly awake, and a body that is temporarily paralysed. Linked to memory and emotional processing. Longer REM periods come later in the night.", cite: "Carskadon, M.A. & Dement, W.C. (2011). Normal human sleep: an overview. In Principles and Practice of Sleep Medicine (5th ed.)." },
  { term: "Slow-wave (deep) sleep, N3", group: "Sleep", def: "The deepest non-REM stage, dominated by slow delta waves. Most of it happens in the first half of the night. Hardest stage to wake from.", cite: "Carskadon & Dement (2011), as above." },
  { term: "Sleep latency", group: "Sleep", def: "How long it takes you to fall asleep after lying down. The bedtime calculator assumes about 14 minutes; yours may differ.", cite: "Carskadon & Dement (2011), as above." },
  { term: "Sleep inertia", group: "Sleep", def: "The groggy, impaired stretch right after waking, especially when woken from deep sleep. It usually fades within 15-30 minutes but can last longer after short or disrupted sleep.", cite: "Tassi, P. & Muzet, A. (2000). Sleep inertia. Sleep Medicine Reviews, 4(4), 341-353." },
  { term: "Sleep debt", group: "Sleep", def: "The gap between the sleep you need and the sleep you get, added up over days. Here it is the minutes short of your nightly target across the last 7 logged nights. Repeated short nights build up measurable attention lapses even when you feel fine.", cite: "Van Dongen, H.P.A. et al. (2003). The cumulative cost of additional wakefulness. Sleep, 26(2), 117-126." },
  { term: "Chronotype", group: "Sleep", def: "Your natural lean toward mornings or evenings (\"lark\" to \"owl\"). Partly genetic and shifts with age. The three options here are a simplification of a spectrum.", cite: "Horne, J.A. & Ostberg, O. (1976). A self-assessment questionnaire to determine morningness-eveningness. International Journal of Chronobiology, 4, 97-110." },
  { term: "Circadian rhythm", group: "Sleep", def: "The roughly 24-hour internal clock that times sleepiness, alertness, body temperature and hormones. Morning light is its strongest reset signal.", cite: "Borbely, A.A. (1982). A two process model of sleep regulation. Human Neurobiology, 1, 195-204." },
  { term: "Pomodoro technique", group: "Focus", def: "Work in fixed blocks (traditionally 25 minutes) separated by short breaks. The value is the fixed start/stop boundary, which lowers the effort of beginning.", cite: "Cirillo, F. (2006). The Pomodoro Technique." },
  { term: "Time blindness", group: "Focus", def: "Difficulty sensing how much time has passed or how long a task will take, common with ADHD. External timers and visible clocks compensate for it.", cite: "Barkley, R.A. (1997). Behavioral inhibition, sustained attention, and executive functions. Psychological Bulletin, 121(1), 65-94." },
  { term: "Heart rate variability (HRV)", group: "Body", def: "The small beat-to-beat variation in your heart rate. Often used as a rough marker of recovery and stress. Only a wearable or chest strap can measure it; this web app does not.", cite: "Task Force of the ESC and NASPE (1996). Heart rate variability: standards of measurement. Circulation, 93(5), 1043-1065." },
  { term: "Fertile window", group: "Cycle", def: "In a typical cycle, the roughly six days ending on the day of ovulation. The Tracking page estimates it from your own logged starts; an estimate is not a contraception method.", cite: "Wilcox, A.J., Weinberg, C.R. & Baird, D.D. (1995). Timing of sexual intercourse in relation to ovulation. New England Journal of Medicine, 333, 1517-1521." },
  { term: "PHQ-9", group: "Screeners", def: "A nine-question self-report questionnaire about low mood over the last two weeks. A screening aid that suggests when to talk to a clinician. It cannot diagnose depression.", cite: "Kroenke, K., Spitzer, R.L. & Williams, J.B.W. (2001). The PHQ-9. Journal of General Internal Medicine, 16(9), 606-613." },
  { term: "GAD-7", group: "Screeners", def: "A seven-question self-report questionnaire about anxiety over the last two weeks. Screening only, not a diagnosis.", cite: "Spitzer, R.L. et al. (2006). A brief measure for assessing generalized anxiety disorder: the GAD-7. Archives of Internal Medicine, 166(10), 1092-1097." },
  { term: "ASRS v1.1", group: "Screeners", def: "The World Health Organization's adult ADHD self-report scale. Part A (six questions) is the short screener; four or more flagged answers suggests it is worth discussing with a clinician. Screening only.", cite: "Kessler, R.C. et al. (2005). The World Health Organization Adult ADHD Self-Report Scale (ASRS). Psychological Medicine, 35(2), 245-256." },
  { term: "RMSSD (heart rate variability)", group: "Body", def: "Root mean square of successive differences between heartbeats, in milliseconds. It reflects beat-to-beat variation and is a common short-term HRV measure. It varies a lot between people, so compare yourself with your own baseline, not with others.", cite: "Task Force of the ESC and NASPE (1996). Heart rate variability: standards of measurement. Circulation, 93(5), 1043-1065." },
  { term: "5-4-3-2-1 grounding", group: "Calm", def: "Name 5 things you see, 4 you feel, 3 you hear, 2 you smell, 1 you taste. Pulls attention from a worry loop back to the present senses. A common coping skill in CBT-style programmes.", cite: "Widely used in cognitive-behavioural and trauma-informed practice; no single primary source." },
  { term: "Box breathing", group: "Calm", def: "Breathe in 4 seconds, hold 4, out 4, hold 4. Slow, even breathing is a simple way to settle a racing pulse.", cite: "Slow-paced breathing review: Zaccaro, A. et al. (2018). How breath-control can change your life. Frontiers in Human Neuroscience, 12, 353." },
];

// ---------------------------------------------------------------- Screeners
// Questionnaires reproduced in their standard published form. Scores are self-report screening
// results, never a diagnosis; the UI says so wherever a result is shown.
const FREQ_4 = ["Not at all", "Several days", "More than half the days", "Nearly every day"];

export const SCREENERS = {
  PHQ9: {
    title: "PHQ-9 (low mood)",
    intro: "Over the last 2 weeks, how often have you been bothered by any of the following problems?",
    options: FREQ_4,
    questions: [
      "Little interest or pleasure in doing things",
      "Feeling down, depressed, or hopeless",
      "Trouble falling or staying asleep, or sleeping too much",
      "Feeling tired or having little energy",
      "Poor appetite or overeating",
      "Feeling bad about yourself, or that you are a failure or have let yourself or your family down",
      "Trouble concentrating on things, such as reading the newspaper or watching television",
      "Moving or speaking so slowly that other people could have noticed, or the opposite: being so fidgety or restless that you have been moving around a lot more than usual",
      "Thoughts that you would be better off dead, or of hurting yourself in some way",
    ],
    safetyItem: 8, // any answer above "Not at all" shows the support panel
    bands: [
      [0, 4, "Minimal"], [5, 9, "Mild"], [10, 14, "Moderate"], [15, 19, "Moderately severe"], [20, 27, "Severe"],
    ],
    max: 27,
  },
  GAD7: {
    title: "GAD-7 (anxiety)",
    intro: "Over the last 2 weeks, how often have you been bothered by the following problems?",
    options: FREQ_4,
    questions: [
      "Feeling nervous, anxious, or on edge",
      "Not being able to stop or control worrying",
      "Worrying too much about different things",
      "Trouble relaxing",
      "Being so restless that it is hard to sit still",
      "Becoming easily annoyed or irritable",
      "Feeling afraid as if something awful might happen",
    ],
    bands: [[0, 4, "Minimal"], [5, 9, "Mild"], [10, 14, "Moderate"], [15, 21, "Severe"]],
    max: 21,
  },
  ASRS: {
    title: "ASRS v1.1 Part A (ADHD)",
    intro: "Please answer the questions, rating yourself on each criterion using the scale. Think about how you have felt and conducted yourself over the past 6 months.",
    options: ["Never", "Rarely", "Sometimes", "Often", "Very often"],
    questions: [
      "How often do you have trouble wrapping up the final details of a project, once the challenging parts have been done?",
      "How often do you have difficulty getting things in order when you have to do a task that requires organization?",
      "How often do you have problems remembering appointments or obligations?",
      "When you have a task that requires a lot of thought, how often do you avoid or delay getting started?",
      "How often do you fidget or squirm with your hands or feet when you have to sit down for a long time?",
      "How often do you feel overly active and compelled to do things, like you were driven by a motor?",
    ],
    // A question counts as flagged at "Sometimes" or above for the first three, "Often" or above for the last three.
    flagAt: [2, 2, 2, 3, 3, 3],
    flagThreshold: 4,
    max: 6,
  },
};

export const CRISIS_RESOURCES = [
  { region: "Australia", text: "Lifeline 13 11 14 (24/7) or Beyond Blue 1300 22 4636" },
  { region: "United States", text: "Call or text 988 (Suicide & Crisis Lifeline)" },
  { region: "United Kingdom & Ireland", text: "Samaritans 116 123 (free, 24/7)" },
  { region: "Elsewhere", text: "findahelpline.com lists free local services by country. In immediate danger, call your local emergency number." },
];

// ---------------------------------------------------------------- Habit library, vision board, exercises
export const HABIT_LIBRARY = [
  { group: "Deep work", items: ["25-minute focus block before checking my phone", "Plan tomorrow's top 3 tasks", "Phone out of reach while I work"] },
  { group: "Mindfulness", items: ["5 minutes of breathing or meditation", "Write one line in my journal", "Notice 3 good things today"] },
  { group: "ADHD routines", items: ["Put keys / wallet / phone in the same spot", "2-minute tidy of one surface", "Body-double or co-work session", "Check the calendar after breakfast"] },
  { group: "Digital detox", items: ["No screens for the last 30 minutes before bed", "Phone on grayscale after 9pm", "One meal without a screen"] },
  { group: "Physical health", items: ["10-minute walk", "Stretch for 5 minutes", "Eat something with protein at breakfast", "Take a break to look 20 feet away every 20 minutes"] },
];

export const VISION_AREAS = ["Career", "Health", "Mindset", "Wealth", "Relationships", "Learning", "Fun"];

export const EXERCISES = {
  grounding: { title: "5-4-3-2-1 grounding", steps: [
    "Name 5 things you can see.",
    "Name 4 things you can feel (feet on the floor, fabric, air on your skin).",
    "Name 3 things you can hear.",
    "Name 2 things you can smell.",
    "Name 1 thing you can taste.",
  ] },
  eyes: { title: "20-20-20 eye rest", steps: [
    "Look away from your screen at something about 20 feet (6 m) away.",
    "Keep looking there for 20 seconds. Let your eyes soften.",
    "Blink slowly 10 times to re-wet your eyes.",
    "Roll your eyes in a slow circle once each way, then return to your task.",
  ] },
  neck: { title: "Neck & shoulder release", steps: [
    "Sit tall. Drop your shoulders away from your ears.",
    "Slowly tilt your right ear toward your right shoulder. Hold for 15 seconds, breathing out.",
    "Slowly tilt your left ear toward your left shoulder. Hold for 15 seconds.",
    "Roll your shoulders back 5 times, then forward 5 times.",
    "Gently look over each shoulder, 10 seconds a side. Stop if anything hurts.",
  ] },
};

// ---------------------------------------------------------------- Support directory
// Public services only. Always confirm opening hours and eligibility with the service itself.
export const HELPLINES = [
  { region: "Australia", name: "Lifeline", detail: "24/7 crisis support", contact: "13 11 14", url: "https://www.lifeline.org.au" },
  { region: "Australia", name: "Beyond Blue", detail: "Anxiety, depression and general support", contact: "1300 22 4636", url: "https://www.beyondblue.org.au" },
  { region: "Australia", name: "headspace", detail: "Support for ages 12-25", contact: "eheadspace online and phone", url: "https://headspace.org.au" },
  { region: "Australia", name: "healthdirect service finder", detail: "Find a GP, psychologist or clinic near you", contact: "1800 022 222", url: "https://www.healthdirect.gov.au" },
  { region: "United States", name: "988 Suicide & Crisis Lifeline", detail: "24/7 call or text", contact: "988", url: "https://988lifeline.org" },
  { region: "United States", name: "SAMHSA treatment locator", detail: "Find mental-health and substance-use services", contact: "1-800-662-4357", url: "https://findtreatment.gov" },
  { region: "United Kingdom & Ireland", name: "Samaritans", detail: "Free, 24/7 listening", contact: "116 123", url: "https://www.samaritans.org" },
  { region: "United Kingdom & Ireland", name: "NHS: find a mental health service", detail: "Self-referral and GP routes", contact: "NHS 111 (UK)", url: "https://www.nhs.uk/service-search/mental-health" },
  { region: "Anywhere", name: "Find a Helpline", detail: "Free local helplines listed by country", contact: "Website", url: "https://findahelpline.com" },
];

export const CARE_GUIDE = [
  { q: "Not sure what you're going through?", a: "That's common. You don't need a label to ask for help. Start with your GP or a counsellor and describe what you notice: how long, how often, what gets in the way. Notes from your Journal, Sleep and Screeners pages can help you say it." },
  { q: "Who can actually assess me?", a: "A registered GP, psychologist, psychiatrist or other licensed mental-health professional. Check registration with your country's professional register before booking. Online quizzes (including the ones in this app) can't diagnose." },
  { q: "What should I bring to a first appointment?", a: "Use \"Print a summary\" on the Screeners page to bring your recent scores, mood and sleep. It is your own self-report, a conversation starter rather than a result." },
  { q: "Is this an emergency?", a: "If you may act on thoughts of harming yourself or someone else, or you are in danger, call your local emergency number now. A helpline is also fine if you're unsure." },
];

// ---------------------------------------------------------------- Journal prompt library
// Prompts are questions to answer; templates (JOURNAL_TEMPLATES) are structures to fill in.
// Users can add their own templates on the Journal page.
export const JOURNAL_CATEGORIES = [
  { id: "reflect", emoji: "🪞", label: "Reflection", prompts: [
    "What is one small thing that brought me genuine ease today?",
    "What did I spend the most energy on today, and was it worth it?",
    "What would I do differently if I could replay today?",
    "What am I avoiding, and what is the smallest first step?",
    "What did I learn about myself this week?",
  ] },
  { id: "gratitude", emoji: "🙏", label: "Gratitude", prompts: [
    "Who made my day easier recently, and what could I say to thank them?",
    "What is something ordinary I'd miss if it were gone?",
    "What went right today that I didn't plan for?",
    "Which part of my body helped me today, and how?",
  ] },
  { id: "feelings", emoji: "🌊", label: "Feelings & thoughts", prompts: [
    "What am I feeling right now, and where do I feel it in my body?",
    "What thought keeps repeating, and what's the evidence for and against it?",
    "If a friend told me this worry, what would I say to them?",
    "What would 'good enough' look like for this situation?",
    "What is in my control here, and what isn't?",
  ] },
  { id: "values", emoji: "🏛️", label: "Values & Stoic", prompts: [
    "What kind of person did I want to be today, and how close did I get?",
    "What obstacle today could become a chance to practise patience?",
    "What do I want to be remembered for, in one sentence?",
    "Which of my values did I honour today, and which did I neglect?",
  ] },
  { id: "creative", emoji: "🎨", label: "Creativity", prompts: [
    "Describe a place you've never been, using only senses.",
    "Write the first paragraph of a story that starts with an unexpected phone call.",
    "What would I make if nobody would ever see it?",
    "List 10 ways to solve the problem I'm stuck on, including silly ones.",
  ] },
  { id: "people", emoji: "🤝", label: "Relationships", prompts: [
    "Who do I want to be more present with, and what's one way to do that this week?",
    "What do I need from the people close to me that I haven't asked for?",
    "Is there a conversation I've been putting off? What's the kindest honest version of it?",
    "How do I want to show up for someone who's struggling?",
  ] },
  { id: "work", emoji: "🎓", label: "Work & study", prompts: [
    "What is the one thing that would make today a success?",
    "Where did my attention go today, and what pulled it?",
    "What am I learning that I want to explain to someone else?",
    "What's blocking me on my biggest task, and who or what could help?",
    "What can I stop doing, delegate or shrink?",
  ] },
  { id: "body", emoji: "💪", label: "Body & health", prompts: [
    "How is my energy right now, 1 to 5, and what's helping or hurting it?",
    "What did I eat, drink and move today, and how did I feel after?",
    "What does my body need most this week?",
    "What small health habit am I proud of?",
  ] },
  { id: "sleep", emoji: "🌙", label: "Sleep & dreams", prompts: [
    "How did I sleep, and what might have helped or hurt it?",
    "What's on my mind that I should put on paper before bed?",
    "Describe the most vivid image from last night's dream.",
    "What would make tonight's wind-down easier?",
  ] },
  { id: "money", emoji: "💰", label: "Money", prompts: [
    "What did I spend on today that I'm glad about, and what do I regret?",
    "What does 'enough' mean to me?",
    "What's one money habit I'd like to build this month?",
  ] },
  { id: "goals", emoji: "🧭", label: "Goals & future", prompts: [
    "Where do I want to be in a year, and what's the first step for this month?",
    "What would I attempt if I knew I'd learn a lot even if it failed?",
    "What's one thing I've outgrown?",
    "What does a really good ordinary day look like for me?",
  ] },
  { id: "adhd", emoji: "⚡", label: "ADHD & focus", prompts: [
    "What helped me start today, and what made starting hard?",
    "Which sensory things (light, noise, texture) affected my focus today?",
    "What's one task I can make 2 minutes long to get going?",
    "What did I hyperfocus on, and was it what I intended?",
    "What systems (lists, alarms, body-doubling) are working, and which should I drop?",
  ] },
  { id: "kind", emoji: "💚", label: "Self-compassion", prompts: [
    "What would I say to comfort a friend who felt the way I do today?",
    "What did I do well today that I'm not giving myself credit for?",
    "What can I forgive myself for this week?",
    "What do I need to hear right now?",
  ] },
  { id: "hard", emoji: "🕊️", label: "Hard times", prompts: [
    "What is the hardest part of this moment, in plain words?",
    "Who or what has helped me through something difficult before?",
    "What is one small, gentle thing I can do for myself in the next hour?",
    "Is this something I should tell someone, or ask for help with?",
  ] },
];

// Research journal: pick something you're curious about, then write what you find in your own
// words. Starter questions only; follow the sources in Learn or your library.
export const RESEARCH_TEMPLATE = "Question I'm exploring:\n\nWhat I already think I know:\n\nSources I'll check (books, papers, people):\n\nWhat I found (in my own words):\n\nWhat surprised me or didn't fit:\n\nMy next question:\n";

export const RESEARCH_TOPICS = [
  { topic: "Sleep and memory", questions: ["How does sleep help consolidate what I learn?", "What happens to attention after a short night?", "Does timing of sleep matter as much as length?"] },
  { topic: "Building habits", questions: ["How long do habits really take to form?", "What makes a habit easier to start: cues, rewards, or friction?", "How do I restart after missing days?"] },
  { topic: "ADHD and time blindness", questions: ["What strategies help with estimating time?", "How do external timers and body-doubling work?", "What's the difference between a screener and a diagnosis?"] },
  { topic: "Screens and attention", questions: ["How does short-form video affect focus?", "What does research say about notifications and task switching?", "What limits actually stick?"] },
  { topic: "Stress and the body", questions: ["What is heart rate variability and what does it reflect?", "How does slow breathing change stress responses?", "Which recovery habits have the best evidence?"] },
  { topic: "Exercise and mood", questions: ["How much movement is linked to better mood?", "Does the type of exercise matter?", "What's the smallest dose worth doing?"] },
  { topic: "Procrastination", questions: ["Why do I delay tasks I care about?", "What's the role of emotion rather than time management?", "Which tactics help me begin?"] },
  { topic: "The menstrual cycle and energy", questions: ["How much do energy and mood vary across phases, and how much does that differ by person?", "What can and can't cycle tracking tell me?", "When should I mention cycle changes to a doctor?"] },
  { topic: "Learning how to learn", questions: ["What do spaced repetition and retrieval practice do?", "How long can I focus on hard material?", "How do I check I really understand something?"] },
];
