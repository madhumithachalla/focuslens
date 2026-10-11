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
