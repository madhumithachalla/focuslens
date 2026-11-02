package com.focuslens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

private fun authErrorMessage(e: Exception): String {
    val code = (e as? FirebaseAuthException)?.errorCode
    return when (code) {
        "ERROR_EMAIL_ALREADY_IN_USE" -> "That email already has an account — try signing in instead."
        "ERROR_INVALID_EMAIL" -> "That doesn't look like a valid email address."
        "ERROR_WEAK_PASSWORD" -> "Password needs to be at least 6 characters."
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "Email or password is wrong."
        "ERROR_USER_NOT_FOUND" -> "No account with that email — try signing up instead."
        "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts — wait a bit and try again."
        "ERROR_OPERATION_NOT_ALLOWED" -> "Email sign-in isn't turned on for this project yet — in Firebase console: Authentication → Sign-in method → Email/Password → Enable."
        "ERROR_USER_DISABLED" -> "This account has been disabled."
        else -> if (e.message?.contains("CONFIGURATION_NOT_FOUND") == true)
            "Authentication hasn't been turned on for this Firebase project yet — in Firebase console: Authentication → click \"Get started\" (a one-time setup step, separate from enabling any specific provider). Until that's done, every sign-in method — Google and email both — fails with this same error."
        else e.message ?: "Something went wrong. Try again."
    }
}

/**
 * Credential Manager is the current (non-deprecated) Android API for "Sign in with Google" —
 * it replaces the old GoogleSignInClient SDK. Needs BuildConfig.GOOGLE_WEB_CLIENT_ID (the
 * "Web client ID" Firebase shows once Google sign-in is enabled for this project) and the
 * app's signing-certificate SHA-1 registered in the Firebase console, or it fails with a
 * DEVELOPER_ERROR-style exception.
 */
private suspend fun signInWithGoogle(context: android.content.Context, auth: FirebaseAuth, db: FirebaseFirestore) {
    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val result = CredentialManager.create(context).getCredential(context, request)
    val credential = result.credential
    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
        val authResult = auth.signInWithCredential(firebaseCredential).awaitResult()
        val signedInUser = authResult.user!!
        // merge: true so a returning user's doc isn't clobbered, just kept current. This is the
        // "auto-fetch" path: name, email and photo come straight from the Google account the
        // person just chose to sign in with — the email form below is the manual alternative.
        db.collection("users").document(signedInUser.uid).set(
            mapOf(
                "displayName" to signedInUser.displayName,
                "email" to signedInUser.email,
                "photoUrl" to signedInUser.photoUrl?.toString(),
                "source" to "google",
                "createdAt" to System.currentTimeMillis()
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).awaitResult()
    } else {
        throw IllegalStateException("Unexpected credential type from Google sign-in.")
    }
}

/**
 * Email sign-in via the same Firebase project as the web app (focuslens-d5379) — same
 * accounts, same Firestore `users` collection, so a future community/sharing/accountability
 * feature works identically on both. Doesn't sync Journal/Cycle/To-do data to the account
 * yet (still local-only, same as the web app) — this just establishes who's signed in.
 */
@Composable
fun AccountScreen(onSignedIn: () -> Unit = {}) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(auth.currentUser) }
    var mode by remember { mutableStateOf("signin") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun onGoogleClick() {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            error = "Google sign-in needs one more setup step (the Web client ID isn't set yet) — " +
                "use email sign-in below for now, or see the Guide tab's Account entry for what's missing."
            return
        }
        error = null
        loading = true
        scope.launch {
            try {
                signInWithGoogle(context, auth, db)
                user = auth.currentUser
                onSignedIn()
            } catch (e: GetCredentialException) {
                error = "Google sign-in was cancelled or unavailable."
            } catch (e: Exception) {
                error = authErrorMessage(e)
            } finally {
                loading = false
            }
        }
    }

    val current = user
    if (current != null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text("Account", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            // Exactly what this sign-in actually fetched/stored, not just a one-line summary —
            // name and email always come from Firebase Auth itself; the sign-in method and
            // photo (Google only) are read from the same Firestore doc written at sign-in time.
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("WHAT'S ON THIS ACCOUNT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(Modifier.height(10.dp))
                    AccountField("Name", current.displayName ?: "(not set)")
                    AccountField("Email", current.email ?: "(not set)")
                    AccountField("Signed in with", if (current.photoUrl != null) "Google" else "Email & password")
                    if (current.photoUrl != null) {
                        AccountField("Profile photo", "Fetched from Google — not shown here, no image loader in this app yet")
                    }
                    val createdAt = current.metadata?.creationTimestamp
                    AccountField("Account created", if (createdAt != null && createdAt > 0) formatPickedDate(createdAt) else "(unknown)")
                }
            }
            Spacer(Modifier.height(16.dp))
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Community posts, achievement sharing and accountability partners aren't wired up yet — " +
                            "this account will unlock them once they're built. Your Journal/Cycle/To-do data on " +
                            "this device stays local for now, not yet synced to this account.",
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { auth.signOut(); user = null }) {
                Text("Sign out")
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Account", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Sign in so your data can eventually follow you across devices, and so you can use community " +
                "features once they're live.",
            fontSize = 13.sp
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Choose how: \"Continue with Google\" auto-fetches your name, email and photo from the " +
                "Google account you pick — nothing else on that account is touched. Prefer not to share " +
                "it? Skip straight to email below and type everything in yourself.",
            fontSize = 12.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            enabled = !loading,
            onClick = ::onGoogleClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue with Google (auto-fetch)")
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text("  or enter manually  ", fontSize = 12.sp, color = Color.Gray)
            HorizontalDivider(modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == "signin", onClick = { mode = "signin"; error = null }, label = { Text("Sign in") })
            FilterChip(selected = mode == "signup", onClick = { mode = "signup"; error = null }, label = { Text("Sign up") })
        }
        Spacer(Modifier.height(16.dp))
        if (mode == "signup") {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 12.sp, color = Color(0xFFB3261E))
        }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !loading && email.isNotBlank() && password.isNotBlank(),
            onClick = {
                error = null
                loading = true
                scope.launch {
                    try {
                        if (mode == "signup") {
                            val result = auth.createUserWithEmailAndPassword(email.trim(), password).awaitResult()
                            val newUser = result.user!!
                            val displayName = name.trim().ifBlank { email.substringBefore("@") }
                            newUser.updateProfile(
                                UserProfileChangeRequest.Builder().setDisplayName(displayName).build()
                            ).awaitResult()
                            db.collection("users").document(newUser.uid).set(
                                mapOf(
                                    "displayName" to displayName,
                                    "email" to email.trim(),
                                    "source" to "manual",
                                    "createdAt" to System.currentTimeMillis()
                                )
                            ).awaitResult()
                            user = auth.currentUser
                            onSignedIn()
                        } else {
                            auth.signInWithEmailAndPassword(email.trim(), password).awaitResult()
                            user = auth.currentUser
                            onSignedIn()
                        }
                    } catch (e: Exception) {
                        error = authErrorMessage(e)
                    } finally {
                        loading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (mode == "signup") "Create account" else "Sign in")
        }
    }
}

@Composable
private fun AccountField(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 13.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.3f))
    }
}
