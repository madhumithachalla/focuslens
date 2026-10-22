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
];

export const MEDIA_ITEMS = [
  { title: "Atomic Habits", creator: "James Clear", y: 2018, kind: "BOOK", genres: ["PRODUCTIVITY"], blurb: "Small, consistent changes compound — the book behind most modern habit-building advice." },
  { title: "Deep Work", creator: "Cal Newport", y: 2016, kind: "BOOK", genres: ["PRODUCTIVITY"], blurb: "The case for long, undistracted stretches of focused work in a world built to fragment it." },
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
];

export const MEDIA_KINDS = [
  { id: "BOOK", label: "Books" },
  { id: "MOVIE", label: "Movies" },
  { id: "SERIES", label: "Series" },
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
  { emoji: "🧩", title: "Thought record", scaffold: "Situation:\n\nWhat I thought in the moment:\n\nHow strongly I felt it (1–10):\n\nA more balanced way to see it:\n" },
  { emoji: "🌊", title: "Brain dump", scaffold: "Just getting everything out, no particular order:\n\n" },
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
    { title: "Cycle & Energy", brief: "Log a period start or a general energy day — today or any past date. After 2+ logged starts: a rolling-average estimate of your next cycle, current phase (follicular/fertile window/ovulation/luteal) with its own accessible color, an estimated fertile window, and commonly-cited \"what people often do in this phase\" guidance. An estimate from your own data and widely-circulated wellness framing, not a clinical prediction, diagnosis, or contraception method. No watch-sync here (needs native OS access) — that's in the Android app." },
    { title: "To-Do + weekly reflection", brief: "Add, complete, or miss tasks with a reason. A weekly summary surfaces your top miss-reason and a matching suggestion." },
    { title: "Calendar scheduling", brief: "Give a task a date/time and add it to Google Calendar (one tap) or download it as a .ics file for Apple Calendar, Outlook, or any other calendar app — no account or API key needed." },
    { title: "Learn", brief: "Newsletters, articles and research hubs across focus, sleep, mental health, habits, study skills, sex education, LGBTQ+ support, and neurodivergence/ADHD (including a real symptom screener designed by Harvard Medical School and the WHO), gated by a short self-reflection prompt before \"mark as read\" sticks." },
    { title: "Suggested for you", brief: "On the Mental health page: a simple rule-based pick from Learn based on your own recent mood trend or top missed-task reason — plainly labeled as rules, not AI or a diagnosis." },
    { title: "Account sign-in + cross-device sync", brief: "Email or Google sign-in via Firebase. Journal/Cycle/To-do/Birthdays data now follows your account — sign in on another browser or device and it pulls down automatically (newest edit wins if you changed both offline)." },
    { title: "Explore", brief: "A curated, filterable list of real books, movies and series — not a live catalog." },
    { title: "Reward suggestions", brief: "A small real-world activity suggestion, shuffled on request." },
    { title: "Mental health trend", brief: "A 14-day mood/energy trend built from your own Journal and Cycle entries — a mirror on your own pattern, not a diagnosis." },
  ],
  upcoming: [
    { title: "Android screen-time in your account", brief: "Accounts are live on both platforms (same Firebase project, same sign-in). The web sync only covers Journal/Cycle/To-do/Birthdays, which live in the web app. Screen-time usage is collected by the Android app and still stays on-device — making it follow your account means adding a Firestore write to the Android app itself (under users/{uid}/data, which the existing security rules already cover), not a web change." },
    { title: "Community chat", brief: "A shared space for FocusLens users to post and talk. Sign-in is already in place; still to build: a posts collection, Firestore rules that allow signed-in users to read others' posts (today the rules only allow your own data), and moderation/reporting." },
    { title: "Achievement sharing", brief: "Share a card of a milestone (a streak, a Focus Score) with other app users. Needs the same shared-read rules as community chat, plus an explicit opt-in so nothing is shared by default." },
    { title: "Accountability partners + dares", brief: "Pick a friend to hold you accountable on tasks; if a task isn't done, a dare gets assigned. Needs a way to find and invite another account, a mutual-consent link between two users, and rules that let a partner see only the tasks you share with them." },
    { title: "Native iOS app", brief: "On hold until there's a Mac + Apple Developer account. Blocking features would need a redesign around Apple's narrower Screen Time framework — some current Android capabilities (Reels/Shorts-only blocking, Chrome domain detection) have no iOS equivalent at all." },
  ],
  androidOnly: [
    { title: "App & feature blocking", brief: "Per-app daily limits, hard 6am locks, Reels/Shorts-only blocking, Chrome social-media domain blocking, Hyper Focus, Study Mode device-wide lock. These need OS-level permissions no website in any browser can ever be granted — they stay native-app-only by design, not by oversight." },
  ],
};
