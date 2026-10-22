// Firebase via the CDN-hosted modular SDK (not npm) — this app has no build step by
// design, so it can deploy straight to GitHub Pages with nothing to compile. Functionally
// identical to the npm package, just loaded as a plain ES module.
//
// Loaded lazily via dynamic import(), not a static top-level import: a static import of a
// CDN URL makes the whole module graph fail to instantiate if that one request is blocked or
// slow (ad blocker, firewall, flaky network) — which took down the ENTIRE app (every tab, not
// just sign-in) with a blank page and no error. Account features are optional; the rest of the
// app must not depend on Firebase's CDN being reachable.
const firebaseConfig = {
  apiKey: "AIzaSyBha4K2rRyuboD2C0uG9aJ0QAREXR-oEs0",
  authDomain: "focuslens-8805c.firebaseapp.com",
  projectId: "focuslens-8805c",
  storageBucket: "focuslens-8805c.firebasestorage.app",
  messagingSenderId: "637478613261",
  appId: "1:637478613261:web:49e130e823a9c7e0d68044",
};

let sdk = null; // { app, auth, db, fns: {...} } once loaded, or a rejected promise's error
let loadPromise = null;

function loadSdk() {
  if (!loadPromise) {
    loadPromise = Promise.all([
      import("https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js"),
      import("https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js"),
      import("https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js"),
    ]).then(([appMod, authMod, storeMod]) => {
      const app = appMod.initializeApp(firebaseConfig);
      const auth = authMod.getAuth(app);
      const db = storeMod.getFirestore(app);
      sdk = { app, auth, db, appMod, authMod, storeMod };
      return sdk;
    });
  }
  return loadPromise;
}

const UNAVAILABLE = "Account features need an internet connection to Google's servers — check your connection and try again.";

export function watchAuth(callback) {
  let unsub = () => {};
  loadSdk().then(({ auth, authMod }) => {
    unsub = authMod.onAuthStateChanged(auth, callback);
  }).catch(() => callback(null)); // treat "Firebase unreachable" as "signed out", not a crash
  return () => unsub();
}

export async function signUp(email, password, displayName) {
  const { auth, db, authMod, storeMod } = await loadSdk();
  const cred = await authMod.createUserWithEmailAndPassword(auth, email, password);
  await authMod.updateProfile(cred.user, { displayName });
  await storeMod.setDoc(storeMod.doc(db, "users", cred.user.uid), {
    displayName,
    email,
    createdAt: Date.now(),
  });
  return cred.user;
}

export async function signIn(email, password) {
  const { auth, authMod } = await loadSdk();
  const cred = await authMod.signInWithEmailAndPassword(auth, email, password);
  return cred.user;
}

// Redirect (not popup) flow: popups are routinely blocked once this PWA is installed to a
// homescreen/standalone window, which has no normal browser chrome to host one. Needs the
// Google provider enabled, and this page's domain added under Authentication > Settings >
// Authorized domains, in the Firebase console.
export async function signInWithGoogle() {
  const { auth, authMod } = await loadSdk();
  await authMod.signInWithRedirect(auth, new authMod.GoogleAuthProvider());
}

// Call once on page load: resolves the user coming back from signInWithGoogle's redirect.
// Resolves to null on an ordinary page load with no pending redirect, or if Firebase's CDN
// couldn't be reached at all.
export async function handleGoogleRedirectResult() {
  let sdkRef;
  try {
    sdkRef = await loadSdk();
  } catch {
    return null;
  }
  const { auth, db, authMod, storeMod } = sdkRef;
  const result = await authMod.getRedirectResult(auth);
  if (!result) return null;
  const { user } = result;
  await storeMod.setDoc(storeMod.doc(db, "users", user.uid), {
    displayName: user.displayName,
    email: user.email,
    createdAt: Date.now(),
  }, { merge: true });
  return user;
}

export async function signOutUser() {
  const { auth, authMod } = await loadSdk();
  await authMod.signOut(auth);
}

export async function getUserProfile(uid) {
  const { db, storeMod } = await loadSdk();
  const snap = await storeMod.getDoc(storeMod.doc(db, "users", uid));
  return snap.exists() ? snap.data() : null;
}

// Journal/Cycle/To-do/etc. — the whole local app-state blob, mirrored one document per user so
// it follows the account across browsers/devices. Needs a Firestore rule letting a signed-in
// user read/write only their own users/{uid}/data/state document (see firestore.rules.md).
export async function pushAppState(uid, state) {
  const { db, storeMod } = await loadSdk();
  await storeMod.setDoc(storeMod.doc(db, "users", uid, "data", "state"), state);
}

export async function pullAppState(uid) {
  const { db, storeMod } = await loadSdk();
  const snap = await storeMod.getDoc(storeMod.doc(db, "users", uid, "data", "state"));
  return snap.exists() ? snap.data() : null;
}

export function authErrorMessage(err) {
  if (err instanceof TypeError || /fetch|network|Failed to fetch/i.test(err?.message || "")) {
    return UNAVAILABLE;
  }
  const map = {
    "auth/email-already-in-use": "That email already has an account — try signing in instead.",
    "auth/invalid-email": "That doesn't look like a valid email address.",
    "auth/weak-password": "Password needs to be at least 6 characters.",
    "auth/wrong-password": "Wrong password.",
    "auth/invalid-credential": "Email or password is wrong.",
    "auth/user-not-found": "No account with that email — try signing up instead.",
    "auth/too-many-requests": "Too many attempts — wait a bit and try again.",
    "auth/operation-not-allowed": "Email sign-in isn't turned on for this project yet — in Firebase console: Authentication → Sign-in method → Email/Password → Enable.",
    "auth/unauthorized-domain": "This site isn't yet on the allowed list for sign-in — in Firebase console: Authentication → Settings → Authorized domains → add this domain.",
    "auth/popup-blocked": "The sign-in window was blocked — allow pop-ups for this site and try again.",
    "auth/user-disabled": "This account has been disabled.",
    "auth/configuration-not-found": "Authentication hasn't been turned on for this Firebase project yet — in Firebase console: Authentication → click \"Get started\" (this is a one-time setup step, separate from enabling any specific provider). Until that's done, every sign-in method — Google and email both — will fail with this same error.",
  };
  return map[err?.code] || err?.message || "Something went wrong. Try again.";
}

// ---------------------------------------------------------------- Community + accountability
// All of this talks to Firestore collections guarded by firestore.rules (profiles, posts,
// pacts). The rules, not this code, are what keep people out of each other's private data —
// see the comments in firestore.rules for exactly what each collection allows.

const CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no 0/O/1/I/L: easy to read out loud

function randomInviteCode() {
  const bytes = new Uint8Array(8);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => CODE_ALPHABET[b % CODE_ALPHABET.length]).join("");
}

// Returns this user's public profile ({displayName, inviteCode}), creating it on first use.
// Calls are shared per uid: signing in re-renders the page twice in quick succession, and two
// racing first-time creates would each pick a different invite code (the rules, correctly,
// refuse to let an invite code change once set).
const profilePromises = new Map();

export function ensureProfile(user) {
  if (!profilePromises.has(user.uid)) {
    const p = createProfileIfMissing(user).catch((e) => { profilePromises.delete(user.uid); throw e; });
    profilePromises.set(user.uid, p);
  }
  return profilePromises.get(user.uid);
}

async function createProfileIfMissing(user) {
  const { db, storeMod: s } = await loadSdk();
  const ref = s.doc(db, "profiles", user.uid);
  const snap = await s.getDoc(ref);
  if (snap.exists()) return snap.data();
  const fallback = (user.displayName || (user.email || "").split("@")[0] || "FocusLens user").slice(0, 40);
  const profile = { displayName: fallback, inviteCode: randomInviteCode(), updatedAt: s.serverTimestamp() };
  try {
    await s.setDoc(ref, profile);
  } catch (e) {
    // Another device created it first: use theirs rather than fail.
    const again = await s.getDoc(ref);
    if (again.exists()) return again.data();
    throw e;
  }
  return { displayName: fallback, inviteCode: profile.inviteCode };
}

export async function renameProfile(uid, displayName) {
  const { db, storeMod: s } = await loadSdk();
  await s.updateDoc(s.doc(db, "profiles", uid), { displayName, updatedAt: s.serverTimestamp() });
}

export async function findProfileByCode(code) {
  const { db, storeMod: s } = await loadSdk();
  const snap = await s.getDocs(s.query(s.collection(db, "profiles"), s.where("inviteCode", "==", code)));
  if (snap.empty) return null;
  const d = snap.docs[0];
  return { uid: d.id, displayName: d.data().displayName };
}

// Live feed. Ordered by time only and filtered by room in the app, so no composite Firestore
// index has to be created by hand. Returns an unsubscribe function.
export function watchPosts(onData, onError) {
  let unsub = () => {};
  loadSdk().then(({ db, storeMod: s }) => {
    const q = s.query(s.collection(db, "posts"), s.orderBy("createdAt", "desc"), s.limit(100));
    unsub = s.onSnapshot(q, (snap) => onData(snap.docs.map((d) => ({ id: d.id, ...d.data() }))), onError);
  }).catch(onError);
  return () => unsub();
}

export async function createPost(user, authorName, text, room) {
  const { db, storeMod: s } = await loadSdk();
  await s.addDoc(s.collection(db, "posts"), { authorUid: user.uid, authorName, text, room, createdAt: s.serverTimestamp() });
}

export async function deletePost(postId) {
  const { db, storeMod: s } = await loadSdk();
  await s.deleteDoc(s.doc(db, "posts", postId));
}

export async function listLikes(postId) {
  const { db, storeMod: s } = await loadSdk();
  const snap = await s.getDocs(s.collection(db, "posts", postId, "likes"));
  return snap.docs.map((d) => d.id);
}

export async function setLike(postId, uid, liked) {
  const { db, storeMod: s } = await loadSdk();
  const ref = s.doc(db, "posts", postId, "likes", uid);
  if (liked) await s.setDoc(ref, { createdAt: s.serverTimestamp() });
  else await s.deleteDoc(ref);
}

export async function listComments(postId) {
  const { db, storeMod: s } = await loadSdk();
  const snap = await s.getDocs(s.query(s.collection(db, "posts", postId, "comments"), s.orderBy("createdAt", "asc"), s.limit(50)));
  return snap.docs.map((d) => ({ id: d.id, ...d.data() }));
}

export async function addComment(postId, user, authorName, text) {
  const { db, storeMod: s } = await loadSdk();
  await s.addDoc(s.collection(db, "posts", postId, "comments"), { authorUid: user.uid, authorName, text, createdAt: s.serverTimestamp() });
}

export async function deleteComment(postId, commentId) {
  const { db, storeMod: s } = await loadSdk();
  await s.deleteDoc(s.doc(db, "posts", postId, "comments", commentId));
}

export async function reportPost(user, postId, reason) {
  const { db, storeMod: s } = await loadSdk();
  await s.addDoc(s.collection(db, "reports"), { reporterUid: user.uid, postId, reason, createdAt: s.serverTimestamp() });
}

// ---- Pacts ----

export function watchPacts(uid, onData, onError) {
  let unsub = () => {};
  loadSdk().then(({ db, storeMod: s }) => {
    const q = s.query(s.collection(db, "pacts"), s.where("memberUids", "array-contains", uid));
    unsub = s.onSnapshot(q, (snap) => onData(snap.docs.map((d) => ({ id: d.id, ...d.data() }))), onError);
  }).catch(onError);
  return () => unsub();
}

export async function requestPact(me, myName, partner) {
  const { db, storeMod: s } = await loadSdk();
  await s.addDoc(s.collection(db, "pacts"), {
    memberUids: [me.uid, partner.uid],
    names: { [me.uid]: myName, [partner.uid]: partner.displayName },
    requesterUid: me.uid,
    status: "pending",
    createdAt: s.serverTimestamp(),
  });
}

export async function setPactStatus(pactId, status) {
  const { db, storeMod: s } = await loadSdk();
  await s.updateDoc(s.doc(db, "pacts", pactId), { status });
}

export function watchPactTasks(pactId, onData, onError) {
  let unsub = () => {};
  loadSdk().then(({ db, storeMod: s }) => {
    const q = s.query(s.collection(db, "pacts", pactId, "tasks"), s.orderBy("dueAt", "desc"), s.limit(50));
    unsub = s.onSnapshot(q, (snap) => onData(snap.docs.map((d) => ({ id: d.id, ...d.data() }))), onError);
  }).catch(onError);
  return () => unsub();
}

export async function addPactTask(pactId, uid, title, dueAtMs) {
  const { db, storeMod: s } = await loadSdk();
  await s.addDoc(s.collection(db, "pacts", pactId, "tasks"), {
    ownerUid: uid, title, dueAt: dueAtMs, status: "open", verified: false, createdAt: s.serverTimestamp(),
  });
}

export async function updatePactTask(pactId, taskId, fields) {
  const { db, storeMod: s } = await loadSdk();
  await s.updateDoc(s.doc(db, "pacts", pactId, "tasks", taskId), fields);
}

export async function deletePactTask(pactId, taskId) {
  const { db, storeMod: s } = await loadSdk();
  await s.deleteDoc(s.doc(db, "pacts", pactId, "tasks", taskId));
}
