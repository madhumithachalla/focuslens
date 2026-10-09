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
    { title: "Cycle & Energy", brief: "Log a period start or a general energy day. After 2+ logged starts: a rolling-average estimate of your next cycle, current phase (follicular/fertile window/ovulation/luteal), and an estimated fertile window, using the standard ~14-day luteal-phase rule of thumb. An estimate from your own data, not a clinical prediction, diagnosis, or contraception method." },
    { title: "To-Do + weekly reflection", brief: "Add, complete, or miss tasks with a reason. A weekly summary surfaces your top miss-reason and a matching suggestion." },
    { title: "Calendar scheduling", brief: "Give a task a date/time and add it to Google Calendar (one tap) or download it as a .ics file for Apple Calendar, Outlook, or any other calendar app — no account or API key needed." },
    { title: "Learn", brief: "Newsletters, articles and research hubs across focus, sleep, mental health, habits, study skills, sex education and LGBTQ+ support, gated by a short self-reflection prompt before \"mark as read\" sticks." },
    { title: "Suggested for you", brief: "On the Mental health page: a simple rule-based pick from Learn based on your own recent mood trend or top missed-task reason — plainly labeled as rules, not AI or a diagnosis." },
    { title: "Account sign-in", brief: "Email sign-up/sign-in via Firebase. Your Journal/Cycle/To-do data doesn't sync to it yet (still local to this browser) — that's next." },
    { title: "Explore", brief: "A curated, filterable list of real books, movies and series — not a live catalog." },
    { title: "Reward suggestions", brief: "A small real-world activity suggestion, shuffled on request." },
    { title: "Mental health trend", brief: "A 14-day mood/energy trend built from your own Journal and Cycle entries — a mirror on your own pattern, not a diagnosis." },
  ],
  upcoming: [
    { title: "Cross-device data sync", brief: "Once signed in, Journal/Cycle/To-do data and screen-time reported by your Android app follow your account across devices instead of staying on one browser." },
    { title: "Community chat", brief: "A shared space for FocusLens users to post and talk." },
    { title: "Achievement sharing", brief: "Share a card of a milestone (a streak, a Focus Score) with other app users." },
    { title: "Accountability partners + dares", brief: "Pick a friend to hold you accountable on tasks; if a task isn't done, a dare gets assigned." },
    { title: "Native iOS app", brief: "On hold until there's a Mac + Apple Developer account. Blocking features would need a redesign around Apple's narrower Screen Time framework — some current Android capabilities (Reels/Shorts-only blocking, Chrome domain detection) have no iOS equivalent at all." },
  ],
  androidOnly: [
    { title: "App & feature blocking", brief: "Per-app daily limits, hard 6am locks, Reels/Shorts-only blocking, Chrome social-media domain blocking, Hyper Focus, Study Mode device-wide lock. These need OS-level permissions no website in any browser can ever be granted — they stay native-app-only by design, not by oversight." },
  ],
};
