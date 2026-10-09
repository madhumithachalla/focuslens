// Firebase via the CDN-hosted modular SDK (not npm) — this app has no build step by
// design, so it can deploy straight to GitHub Pages with nothing to compile. Functionally
// identical to the npm package, just loaded as a plain ES module.
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js";
import {
  getAuth, createUserWithEmailAndPassword, signInWithEmailAndPassword,
  signOut, onAuthStateChanged, updateProfile,
  GoogleAuthProvider, signInWithRedirect, getRedirectResult,
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js";
import {
  getFirestore, doc, setDoc, getDoc,
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js";

const firebaseConfig = {
  apiKey: "AIzaSyBha4K2rRyuboD2C0uG9aJ0QAREXR-oEs0",
  authDomain: "focuslens-8805c.firebaseapp.com",
  projectId: "focuslens-8805c",
  storageBucket: "focuslens-8805c.firebasestorage.app",
  messagingSenderId: "637478613261",
  appId: "1:637478613261:web:49e130e823a9c7e0d68044",
};

export const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);

export function watchAuth(callback) {
  return onAuthStateChanged(auth, callback);
}

export async function signUp(email, password, displayName) {
  const cred = await createUserWithEmailAndPassword(auth, email, password);
  await updateProfile(cred.user, { displayName });
  await setDoc(doc(db, "users", cred.user.uid), {
    displayName,
    email,
    createdAt: Date.now(),
  });
  return cred.user;
}

export async function signIn(email, password) {
  const cred = await signInWithEmailAndPassword(auth, email, password);
  return cred.user;
}

// Redirect (not popup) flow: popups are routinely blocked once this PWA is installed to a
// homescreen/standalone window, which has no normal browser chrome to host one. Needs the
// Google provider enabled, and this page's domain added under Authentication > Settings >
// Authorized domains, in the Firebase console.
export async function signInWithGoogle() {
  await signInWithRedirect(auth, new GoogleAuthProvider());
}

// Call once on page load: resolves the user coming back from signInWithGoogle's redirect.
// Resolves to null on an ordinary page load with no pending redirect.
export async function handleGoogleRedirectResult() {
  const result = await getRedirectResult(auth);
  if (!result) return null;
  const { user } = result;
  await setDoc(doc(db, "users", user.uid), {
    displayName: user.displayName,
    email: user.email,
    createdAt: Date.now(),
  }, { merge: true });
  return user;
}

export async function signOutUser() {
  await signOut(auth);
}

export async function getUserProfile(uid) {
  const snap = await getDoc(doc(db, "users", uid));
  return snap.exists() ? snap.data() : null;
}

export function authErrorMessage(err) {
  const map = {
    "auth/email-already-in-use": "That email already has an account — try signing in instead.",
    "auth/invalid-email": "That doesn't look like a valid email address.",
    "auth/weak-password": "Password needs to be at least 6 characters.",
    "auth/wrong-password": "Wrong password.",
    "auth/invalid-credential": "Email or password is wrong.",
    "auth/user-not-found": "No account with that email — try signing up instead.",
    "auth/too-many-requests": "Too many attempts — wait a bit and try again.",
  };
  return map[err?.code] || "Something went wrong. Try again.";
}
