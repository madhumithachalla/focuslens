package com.focuslens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Waits for a Firebase task without an extra library. */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

private fun randomCode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return (1..8).map { chars.random() }.joinToString("")
}

/** One pact, as the rules describe it: exactly two people, starts pending, then active or ended. */
private data class Pact(val id: String, val memberUids: List<String>, val names: Map<String, String>, val requesterUid: String, val status: String)

private data class PactTask(val id: String, val ownerUid: String, val title: String, val dueAt: Long, val status: String, val verified: Boolean)

/** The Accountability pact: pair up with one other person, share tasks, and check each other's. */
@Composable
fun AccountabilityScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val me = auth.currentUser
    var myCode by remember { mutableStateOf<String?>(null) }
    var myName by remember { mutableStateOf("") }
    var pacts by remember { mutableStateOf<List<Pact>>(emptyList()) }
    var openPact by remember { mutableStateOf<String?>(null) }
    var tasks by remember { mutableStateOf<List<PactTask>>(emptyList()) }
    var partnerCode by remember { mutableStateOf("") }
    var taskTitle by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    // My invite code: created once, the first time the screen opens.
    LaunchedEffect(me?.uid) {
        val user = me ?: return@LaunchedEffect
        val uid = user.uid
        try {
            val snap = db.collection("profiles").document(uid).get().awaitResult()
            if (snap.exists()) {
                myCode = snap.getString("inviteCode")
                myName = snap.getString("displayName") ?: ""
            } else {
                val name = user.displayName?.takeIf { it.isNotBlank() } ?: "FocusLens user"
                val code = randomCode()
                db.collection("profiles").document(uid).set(
                    mapOf("displayName" to name, "inviteCode" to code, "updatedAt" to FieldValue.serverTimestamp())
                ).awaitResult()
                myCode = code
                myName = name
            }
        } catch (e: Exception) {
            message = "Could not load your invite code: ${e.message}"
        }
    }

    // My pacts, live.
    DisposableEffect(me?.uid) {
        val uid = me?.uid
        var reg: ListenerRegistration? = null
        if (uid != null) {
            reg = db.collection("pacts").whereArrayContains("memberUids", uid)
                .addSnapshotListener { snap: QuerySnapshot?, err ->
                    if (err != null) { message = "Could not read pacts: ${err.message}"; return@addSnapshotListener }
                    pacts = snap?.documents.orEmpty().mapNotNull { d: DocumentSnapshot ->
                        @Suppress("UNCHECKED_CAST")
                        Pact(
                            id = d.id,
                            memberUids = (d.get("memberUids") as? List<*>)?.map { it.toString() }.orEmpty(),
                            names = (d.get("names") as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() }.orEmpty(),
                            requesterUid = d.getString("requesterUid") ?: "",
                            status = d.getString("status") ?: "pending"
                        )
                    }
                }
        }
        onDispose { reg?.remove() }
    }

    // Tasks of the pact that is open, live.
    DisposableEffect(openPact) {
        var reg: ListenerRegistration? = null
        val id = openPact
        if (id != null) {
            reg = db.collection("pacts").document(id).collection("tasks")
                .addSnapshotListener { snap: QuerySnapshot?, err ->
                    if (err != null) { message = "Could not read tasks: ${err.message}"; return@addSnapshotListener }
                    tasks = snap?.documents.orEmpty().map { d: DocumentSnapshot ->
                        PactTask(
                            id = d.id,
                            ownerUid = d.getString("ownerUid") ?: "",
                            title = d.getString("title") ?: "",
                            dueAt = d.getLong("dueAt") ?: 0L,
                            status = d.getString("status") ?: "open",
                            verified = d.getBoolean("verified") ?: false
                        )
                    }.sortedBy { it.dueAt }
                }
        }
        onDispose { reg?.remove() }
    }

    if (me == null) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("Accountability pact", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Sign in first (Account page). A pact needs your account, so the other person can find you.", fontSize = 13.sp, color = Color.Gray)
        }
        return
    }

    val open = pacts.firstOrNull { it.id == openPact }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Accountability pact", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Pair up with one person. You each set tasks with a time, mark them done, and the other person checks them.",
            fontSize = 13.sp, color = Color.Gray
        )
        message?.let { Spacer(Modifier.height(6.dp)); Text(it, fontSize = 12.sp, color = Color(0xFFB3261E)) }
        Spacer(Modifier.height(12.dp))

        // Invite code: this is how someone finds you. Nobody can search for you by email.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Your invite code", fontWeight = FontWeight.Medium)
                Text(myCode ?: "Loading…", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Give this code to the person you want as your partner. They enter it under Find a partner. " +
                        "You can also send it to them by message.",
                    fontSize = 12.sp, color = Color.Gray
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Find a partner", fontWeight = FontWeight.Medium)
        Text(
            "Type their invite code. Contacts can't be matched automatically, because the people they know may not use FocusLens yet.",
            fontSize = 12.sp, color = Color.Gray
        )
        OutlinedTextField(
            value = partnerCode, onValueChange = { partnerCode = it.uppercase().take(8) },
            label = { Text("Their invite code") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Button(
            enabled = partnerCode.length == 8,
            onClick = {
                scope.launch {
                    try {
                        val found = db.collection("profiles").whereEqualTo("inviteCode", partnerCode).get().awaitResult()
                        val doc = found.documents.firstOrNull()
                        if (doc == null || doc.id == me.uid) {
                            message = "No one has that code. Check it with them."
                        } else {
                            val theirName = doc.getString("displayName") ?: "Partner"
                            db.collection("pacts").add(
                                mapOf(
                                    "memberUids" to listOf(me.uid, doc.id),
                                    "names" to mapOf(me.uid to myName.ifBlank { "Me" }, doc.id to theirName),
                                    "requesterUid" to me.uid,
                                    "status" to "pending",
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                            ).awaitResult()
                            partnerCode = ""
                            message = "Invite sent. It starts once $theirName accepts."
                        }
                    } catch (e: Exception) {
                        message = "Could not send the invite: ${e.message}"
                    }
                }
            }
        ) { Text("Send invite") }

        Spacer(Modifier.height(16.dp))
        Text("Your pacts", fontWeight = FontWeight.Medium)
        if (pacts.isEmpty()) Text("No pacts yet.", fontSize = 13.sp, color = Color.Gray)
        pacts.forEach { p ->
            val partner = p.memberUids.firstOrNull { it != me.uid }?.let { p.names[it] } ?: "Partner"
            val statusText = when {
                p.status == "pending" && p.requesterUid == me.uid -> "Waiting for $partner to accept"
                p.status == "pending" -> "$partner invited you"
                p.status == "active" -> "Active with $partner"
                else -> "Ended"
            }
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(partner, fontWeight = FontWeight.Medium)
                    Text(statusText, fontSize = 12.sp, color = Color.Gray)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (p.status == "pending" && p.requesterUid != me.uid) {
                            Button(onClick = {
                                scope.launch {
                                    try { db.collection("pacts").document(p.id).update("status", "active").awaitResult() }
                                    catch (e: Exception) { message = "Could not accept: ${e.message}" }
                                }
                            }) { Text("Accept") }
                        }
                        if (p.status == "active") {
                            OutlinedButton(onClick = { openPact = if (openPact == p.id) null else p.id }) {
                                Text(if (openPact == p.id) "Close" else "Open tasks")
                            }
                        }
                        if (p.status == "pending" || p.status == "active") {
                            TextButton(onClick = {
                                scope.launch {
                                    try { db.collection("pacts").document(p.id).update("status", "ended").awaitResult() }
                                    catch (e: Exception) { message = "Could not end it: ${e.message}" }
                                }
                            }) { Text("End pact") }
                        }
                    }
                }
            }
        }

        if (open != null && open.status == "active") {
            Spacer(Modifier.height(12.dp))
            Text("Tasks", fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = taskTitle, onValueChange = { taskTitle = it },
                label = { Text("What will you do? (due in 1 hour)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Button(
                enabled = taskTitle.isNotBlank(),
                onClick = {
                    scope.launch {
                        try {
                            db.collection("pacts").document(open.id).collection("tasks").add(
                                mapOf(
                                    "ownerUid" to me.uid,
                                    "title" to taskTitle.trim(),
                                    "dueAt" to System.currentTimeMillis() + 60L * 60 * 1000,
                                    "status" to "open",
                                    "verified" to false,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                            ).awaitResult()
                            taskTitle = ""
                        } catch (e: Exception) { message = "Could not add the task: ${e.message}" }
                    }
                }
            ) { Text("Add task") }
            tasks.forEach { t ->
                val mine = t.ownerUid == me.uid
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(t.title, fontWeight = FontWeight.Medium)
                        Text(
                            when {
                                t.status == "open" -> if (mine) "Open. Mark it done when finished." else "Open"
                                !t.verified && !mine -> "Done. Check it to confirm."
                                t.verified -> "Done and checked"
                                else -> "Done, waiting for a check"
                            },
                            fontSize = 12.sp, color = Color.Gray
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (mine && t.status == "open") {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        try { db.collection("pacts").document(open.id).collection("tasks").document(t.id).update("status", "done").awaitResult() }
                                        catch (e: Exception) { message = "Could not mark done: ${e.message}" }
                                    }
                                }) { Text("Mark done") }
                            }
                            if (!mine && t.status == "done" && !t.verified) {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        try { db.collection("pacts").document(open.id).collection("tasks").document(t.id).update("verified", true).awaitResult() }
                                        catch (e: Exception) { message = "Could not check it: ${e.message}" }
                                    }
                                }) { Text("Confirm done") }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
