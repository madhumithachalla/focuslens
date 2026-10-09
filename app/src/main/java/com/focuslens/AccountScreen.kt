package com.focuslens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
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
        else -> e.message ?: "Something went wrong. Try again."
    }
}

/**
 * Email sign-in via the same Firebase project as the web app (focuslens-8805c) — same
 * accounts, same Firestore `users` collection, so a future community/sharing/accountability
 * feature works identically on both. Doesn't sync Journal/Cycle/To-do data to the account
 * yet (still local-only, same as the web app) — this just establishes who's signed in.
 */
@Composable
fun AccountScreen() {
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

    val current = user
    if (current != null) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Account", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Signed in as ${current.displayName ?: current.email}", fontSize = 15.sp, fontWeight = FontWeight.Medium)
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

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Account", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Sign in so your data can eventually follow you across devices, and so you can use community " +
                "features once they're live.",
            fontSize = 13.sp
        )
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
                                    "createdAt" to System.currentTimeMillis()
                                )
                            ).awaitResult()
                            user = auth.currentUser
                        } else {
                            auth.signInWithEmailAndPassword(email.trim(), password).awaitResult()
                            user = auth.currentUser
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
