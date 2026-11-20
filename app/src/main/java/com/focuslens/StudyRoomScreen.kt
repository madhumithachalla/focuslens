package com.focuslens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Ten random backdrops. A room gets one at random when it is made. */
private val backdrops: List<Pair<Long, Long>> = listOf(
    0xFF1F3A34L to 0xFF5B8C7AL,
    0xFF2B2A4CL to 0xFF7A78C4L,
    0xFF3A2A1FL to 0xFFC4935AL,
    0xFF1D3344L to 0xFF5F9CC4L,
    0xFF3B2233L to 0xFFB06A9AL,
    0xFF233B2AL to 0xFF9CC4A8L,
    0xFF2E2E2EL to 0xFF8A8A8AL,
    0xFF3D2B2BL to 0xFFC47A7AL,
    0xFF1E2B3DL to 0xFF7AA0C4L,
    0xFF302A3DL to 0xFFB8A6D9L
)

private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
}

private fun newCode(): String = (1..6).map { CODE_CHARS.random() }.joinToString("")

private fun roomsRef() = FirebaseFirestore.getInstance().collection("studyRooms")

/** Makes a room with you in it, with a random backdrop. Returns its id. */
private suspend fun createRoom(uid: String, type: String): String {
    val ref = roomsRef().document()
    ref.set(
        mapOf(
            "type" to type,
            "code" to newCode(),
            "ownerUid" to uid,
            "backdrop" to backdrops.indices.random(),
            "memberUids" to listOf(uid),
            "open" to true,
            "timerEnd" to 0L,
            "createdAt" to FieldValue.serverTimestamp()
        )
    ).awaitTask()
    return ref.id
}

/** Adds you to a room, if it still has space. Runs as a transaction so two people can't overfill it. */
private suspend fun joinById(roomId: String, uid: String): Boolean {
    val db = FirebaseFirestore.getInstance()
    return db.runTransaction { tx: Transaction ->
        val ref = roomsRef().document(roomId)
        val snap = tx.get(ref)
        val members = (snap.get("memberUids") as? List<*>)?.map { it.toString() } ?: emptyList()
        val cap = if (snap.getString("type") == "one") 2 else 8
        if (uid in members || members.size >= cap || snap.getBoolean("open") != true) {
            false
        } else {
            val next = members + uid
            tx.update(ref, mapOf("memberUids" to next, "open" to (next.size < cap)))
            true
        }
    }.awaitTask()
}

/** Joins a random open room of this type, or returns null if none has space. */
private suspend fun joinRandom(uid: String, type: String): String? {
    val rooms = roomsRef()
        .whereEqualTo("type", type)
        .whereEqualTo("open", true)
        .limit(10)
        .get().awaitTask()
    for (doc in rooms.documents.shuffled()) {
        if (doc.getString("ownerUid") == uid) continue
        if (joinById(doc.id, uid)) return doc.id
    }
    return null
}

private suspend fun joinByCode(code: String, uid: String): String? {
    val found = roomsRef().whereEqualTo("code", code.trim().uppercase()).limit(1).get().awaitTask()
    val id = found.documents.firstOrNull()?.id ?: return null
    return if (joinById(id, uid)) id else null
}

private suspend fun leaveRoom(roomId: String, uid: String) {
    roomsRef().document(roomId).update(
        mapOf("memberUids" to FieldValue.arrayRemove(uid), "open" to true)
    ).awaitTask()
}

private fun avatarColor(uid: String): Color {
    val palette = listOf(0xFFA9A6F7L, 0xFFE0B25CL, 0xFF7FA8C9L, 0xFFD98C8CL, 0xFF9CC4A8L, 0xFFB8A6D9L)
    return Color(palette[(uid.hashCode() and Int.MAX_VALUE) % palette.size])
}

/**
 * Study rooms: one-on-one or group. You join at random or with a code, and invite people with
 * the code. Members show as coloured dots, with no names. There is no chat.
 */
@Composable
fun StudyRoomScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var roomId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(Color(0xFF0E0F13)).padding(20.dp)) {
        if (uid == null) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sign in to join a study room", color = Color.White, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onClose) { Text("Close") }
            }
            return@Box
        }

        val current = roomId
        if (current == null) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.height(24.dp))
                Text("Study room", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    busy = true; message = null
                    scope.launch {
                        try {
                            roomId = joinRandom(uid, "one") ?: createRoom(uid, "one")
                        } catch (e: Exception) { message = "Could not connect. Check your internet." }
                        busy = false
                    }
                }) { Text("One on one") }
                Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    busy = true; message = null
                    scope.launch {
                        try {
                            roomId = joinRandom(uid, "group") ?: createRoom(uid, "group")
                        } catch (e: Exception) { message = "Could not connect. Check your internet." }
                        busy = false
                    }
                }) { Text("Group room") }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.take(6) },
                        label = { Text("Invite code") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = !busy && code.length == 6, onClick = {
                        busy = true; message = null
                        scope.launch {
                            try {
                                val id = joinByCode(code, uid)
                                if (id == null) message = "No room with that code, or it is full." else roomId = id
                            } catch (e: Exception) { message = "Could not connect. Check your internet." }
                            busy = false
                        }
                    }) { Text("Join") }
                }
                message?.let { Text(it, color = Color(0xFFD98C8C), fontSize = 13.sp) }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        } else {
            StudyRoomView(
                roomId = current,
                uid = uid,
                onInvite = { c ->
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Join my FocusLens study room. Code: $c")
                            },
                            "Invite"
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                onLeave = {
                    scope.launch {
                        try { leaveRoom(current, uid) } catch (e: Exception) { }
                        roomId = null
                    }
                }
            )
        }
    }
}

@Composable
private fun StudyRoomView(roomId: String, uid: String, onInvite: (String) -> Unit, onLeave: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var members by remember { mutableStateOf<List<String>>(emptyList()) }
    var backdrop by remember { mutableStateOf(0) }
    var timerEnd by remember { mutableLongStateOf(0L) }
    var isOne by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    DisposableEffect(roomId) {
        val reg: ListenerRegistration = roomsRef().document(roomId).addSnapshotListener { snap, _ ->
            val d = snap?.data ?: return@addSnapshotListener
            code = d["code"] as? String ?: ""
            members = (d["memberUids"] as? List<*>)?.map { it.toString() } ?: emptyList()
            backdrop = (d["backdrop"] as? Number)?.toInt() ?: 0
            timerEnd = (d["timerEnd"] as? Number)?.toLong() ?: 0L
            isOne = d["type"] == "one"
        }
        onDispose { reg.remove() }
    }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(1_000L) }
    }

    val (c1, c2) = backdrops[backdrop.coerceIn(0, backdrops.size - 1)]
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(c1), Color(c2))))
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(20.dp))
            Text(if (isOne) "One on one" else "Group room", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("${members.size} of ${if (isOne) 2 else 8} here · code $code", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                members.forEach { m ->
                    Box(Modifier.size(36.dp).background(avatarColor(m), CircleShape))
                }
            }

            Spacer(Modifier.weight(1f))
            val left = timerEnd - now
            Text(
                if (left > 0) String.format("%02d:%02d", left / 60_000L, (left / 1_000L) % 60L) else "Ready",
                color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = left <= 0,
                onClick = {
                    scope.launch {
                        try {
                            roomsRef().document(roomId).update("timerEnd", System.currentTimeMillis() + 25 * 60_000L).awaitTask()
                        } catch (e: Exception) { }
                    }
                }
            ) { Text("Start 25 min focus") }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(modifier = Modifier.weight(1f), onClick = { onInvite(code) }) { Text("Invite") }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onLeave) { Text("Leave") }
            }
        }
    }
}
