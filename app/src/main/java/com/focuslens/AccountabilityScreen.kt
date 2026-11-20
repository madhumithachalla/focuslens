package com.focuslens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Alignment
import android.content.Intent
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
private val Dark = Color(0xFF0E0F13)
private val PanelColor = Color(0xFF1A1C22)
private val Chip = Color(0xFF272A33)
private val Accent = Color(0xFFA9A6F7)
private val Gold = Color(0xFFE0B25C)
private val Muted = Color(0xFF9AA0AB)
private val Coral = Color(0xFFD98C8C)

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(PanelColor, RoundedCornerShape(22.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier) {
    Column(modifier.background(Chip, RoundedCornerShape(16.dp)).padding(12.dp)) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun Avatar(name: String, uid: String) {
    Box(
        Modifier.size(56.dp).background(if (uid.hashCode() % 2 == 0) Accent else Gold, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(name.take(1).uppercase(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Dark)
    }
}

/** A short status line for a task, in minutes, not sentences. */
private fun taskHint(t: PactTask, mine: Boolean, now: Long): String = when {
    t.verified -> "Checked"
    t.status == "done" && mine -> "Waiting for a check"
    t.status == "done" -> "Tap to check"
    t.dueAt < now -> "Overdue"
    else -> "${(t.dueAt - now) / 60_000L} min left"
}

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
        Column(Modifier.fillMaxSize().background(Dark).padding(20.dp)) {
            Text("Accountability", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Sign in on the Account tab first.", fontSize = 14.sp, color = Muted)
        }
        return
    }

    val active = pacts.firstOrNull { it.status == "active" }
    val pending = pacts.firstOrNull { it.status == "pending" }
    // The active pact's tasks open by themselves.
    LaunchedEffect(active?.id) { openPact = active?.id }

    fun partnerOf(p: Pact): String = p.memberUids.firstOrNull { it != me.uid }?.let { p.names[it] } ?: "Partner"
    fun setStatus(id: String, status: String, errorPrefix: String) {
        scope.launch {
            try { db.collection("pacts").document(id).update("status", status).awaitResult() }
            catch (e: Exception) { message = "$errorPrefix: ${e.message}" }
        }
    }
    fun sendInvite() {
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
                    message = null
                }
            } catch (e: Exception) {
                message = "Could not send the invite: ${e.message}"
            }
        }
    }
    fun addTask(pactId: String) {
        scope.launch {
            try {
                db.collection("pacts").document(pactId).collection("tasks").add(
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
    fun tapTask(pactId: String, t: PactTask) {
        val mine = t.ownerUid == me.uid
        val ref = db.collection("pacts").document(pactId).collection("tasks").document(t.id)
        scope.launch {
            try {
                when {
                    mine && t.status == "open" -> ref.update("status", "done").awaitResult()
                    !mine && t.status == "done" && !t.verified -> ref.update("verified", true).awaitResult()
                }
            } catch (e: Exception) { message = "Could not update the task: ${e.message}" }
        }
    }

    val nowMs = System.currentTimeMillis()
    val checked = tasks.count { it.verified }
    val openCount = tasks.count { it.status == "open" }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Dark, background = Dark, surface = PanelColor,
            onSurface = Color.White, onBackground = Color.White, outline = Chip
        )
    ) {
        Column(
            Modifier.fillMaxSize().background(Dark).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Accountability", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            message?.let { Text(it, fontSize = 12.sp, color = Coral) }

            // Partner: the one big card.
            Panel {
                when {
                    active != null -> {
                        val name = partnerOf(active)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(name, active.memberUids.firstOrNull { it != me.uid } ?: "")
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Active", fontSize = 13.sp, color = Accent)
                            }
                            TextButton(onClick = { setStatus(active.id, "ended", "Could not end it") }) {
                                Text("End", color = Muted)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatBox("$openCount", "Open", Modifier.weight(1f))
                            StatBox("${tasks.count { it.status == "done" && !it.verified }}", "Waiting check", Modifier.weight(1f))
                            StatBox("$checked", "Checked", Modifier.weight(1f))
                        }
                    }
                    pending != null && pending.requesterUid == me.uid -> {
                        Text("Waiting for ${partnerOf(pending)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Invite sent", fontSize = 13.sp, color = Gold)
                        TextButton(onClick = { setStatus(pending.id, "ended", "Could not cancel") }) {
                            Text("Cancel", color = Muted)
                        }
                    }
                    pending != null -> {
                        Text("${partnerOf(pending)} invited you", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { setStatus(pending.id, "active", "Could not accept") }) { Text("Accept") }
                            TextButton(onClick = { setStatus(pending.id, "ended", "Could not decline") }) {
                                Text("Decline", color = Muted)
                            }
                        }
                    }
                    else -> {
                        Text("Find a partner", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = partnerCode,
                                onValueChange = { partnerCode = it.uppercase().take(8) },
                                placeholder = { Text("Their code") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(enabled = partnerCode.length == 8, onClick = { sendInvite() }) { Text("Invite") }
                        }
                    }
                }
            }

            // Your code: how people find you.
            Panel {
                Text("Your code", fontSize = 13.sp, color = Muted)
                Text(
                    myCode ?: "…",
                    fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, color = Color.White
                )
                OutlinedButton(
                    enabled = myCode != null,
                    onClick = {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Be my accountability partner on FocusLens. My code: $myCode")
                                },
                                "Share"
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                ) { Text("Share") }
            }

            // Tasks: only with an active partner.
            if (active != null) {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tasks", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                        Text("$checked of ${tasks.size} checked", fontSize = 13.sp, color = Muted)
                    }
                    val fraction = if (tasks.isEmpty()) 0f else checked.toFloat() / tasks.size
                    Box(Modifier.fillMaxWidth().height(6.dp).background(Chip, RoundedCornerShape(3.dp))) {
                        Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(Accent, RoundedCornerShape(3.dp)))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = taskTitle,
                            onValueChange = { taskTitle = it },
                            placeholder = { Text("Add a task, due in 1 hour") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(enabled = taskTitle.isNotBlank(), onClick = { addTask(active.id) }) { Text("+") }
                    }
                    tasks.forEach { t ->
                        val mine = t.ownerUid == me.uid
                        val tappable = (mine && t.status == "open") || (!mine && t.status == "done" && !t.verified)
                        val circle = when {
                            t.verified -> Accent
                            t.status == "done" -> Gold
                            else -> Chip
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(32.dp).background(circle, CircleShape)
                                    .then(if (tappable) Modifier.clickable { tapTask(active.id, t) } else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                if (t.status == "done" || t.verified) Text("✓", color = Dark, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, color = Color.White)
                                Text(taskHint(t, mine, nowMs), fontSize = 12.sp, color = Muted)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
