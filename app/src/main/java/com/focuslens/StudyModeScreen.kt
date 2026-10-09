package com.focuslens

import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private enum class AudioChoice { SYSTEM_DEFAULT, SPOTIFY_PICK, LOCAL_MUSIC, SILENCE }

/**
 * Setup screen styled after Minimalist Focus Launcher: black background, plain text rows,
 * no icons. Combines an audio choice with the existing Hyper Focus device-wide block, so
 * "studying" actually means something (everything else is locked out), not just a playlist.
 *
 * The in-session screen is a minimal on-screen ambiance (dark background, a slow breathing
 * dot, a timer) — not a camera-based AR virtual background. Building an actual photorealistic
 * study-room backdrop would need real image/video assets this project doesn't have; this is
 * the honest, buildable version of "feels like a quiet place to study."
 */
@Composable
fun StudyModeScreen() {
    val context = LocalContext.current
    val prefs = remember { FocusPrefs(context) }

    var minutes by remember { mutableIntStateOf(45) }
    var audioChoice by remember { mutableStateOf(AudioChoice.SYSTEM_DEFAULT) }
    var spotifyPlaylist by remember { mutableStateOf(FOCUS_PLAYLISTS.first()) }
    var showSpotifyPicker by remember { mutableStateOf(false) }
    var sessionActive by remember { mutableStateOf(prefs.isHyperFocusActive()) }
    var confirmEnd by remember { mutableStateOf(false) }

    val connector = remember { SpotifyConnector(context) }
    var spotifyState by remember { mutableStateOf<SpotifyState>(SpotifyState.Disconnected) }

    if (sessionActive) {
        InSessionScreen(
            resetAt = prefs.hyperFocusUntil(),
            onEnded = { sessionActive = false },
            onEndEarly = { confirmEnd = true }
        )
        if (confirmEnd) {
            AlertDialog(
                onDismissRequest = { confirmEnd = false },
                title = { Text("End study session?") },
                text = { Text("This also turns off the device-wide block early.") },
                confirmButton = {
                    Button(onClick = {
                        prefs.endHyperFocus()
                        connector.pause()
                        sessionActive = false
                        confirmEnd = false
                    }) { Text("End it") }
                },
                dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep going") } }
            )
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text("STUDY MODE", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Blocks everything else on the phone for the session, with calming audio alongside it.",
            color = Color(0xFF9A9A9A),
            fontSize = 13.sp
        )
        Spacer(Modifier.height(28.dp))

        Text("DURATION", color = Color(0xFF9A9A9A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(25, 45, 60, 90).forEach { m ->
                MinimalRow(label = "${m}m", selected = minutes == m, onClick = { minutes = m }, compact = true)
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("SOUND", color = Color(0xFF9A9A9A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        MinimalRow(
            label = "System default — Lo-fi focus",
            selected = audioChoice == AudioChoice.SYSTEM_DEFAULT,
            onClick = { audioChoice = AudioChoice.SYSTEM_DEFAULT }
        )
        MinimalRow(
            label = if (audioChoice == AudioChoice.SPOTIFY_PICK) "Spotify — ${spotifyPlaylist.label}" else "Choose a Spotify playlist",
            selected = audioChoice == AudioChoice.SPOTIFY_PICK,
            onClick = { audioChoice = AudioChoice.SPOTIFY_PICK; showSpotifyPicker = true }
        )
        MinimalRow(
            label = "Local music (opens your music app)",
            selected = audioChoice == AudioChoice.LOCAL_MUSIC,
            onClick = { audioChoice = AudioChoice.LOCAL_MUSIC }
        )
        MinimalRow(
            label = "Silence — no audio",
            selected = audioChoice == AudioChoice.SILENCE,
            onClick = { audioChoice = AudioChoice.SILENCE }
        )

        if (showSpotifyPicker && audioChoice == AudioChoice.SPOTIFY_PICK) {
            Spacer(Modifier.height(4.dp))
            Column(Modifier.padding(start = 16.dp)) {
                FOCUS_PLAYLISTS.forEach { p ->
                    MinimalRow(
                        label = p.label,
                        selected = spotifyPlaylist == p,
                        onClick = { spotifyPlaylist = p; showSpotifyPicker = false },
                        compact = true
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                prefs.startHyperFocus(minutes)
                when (audioChoice) {
                    AudioChoice.SYSTEM_DEFAULT -> connector.connect { state ->
                        spotifyState = state
                        if (state is SpotifyState.Connected) connector.play(FOCUS_PLAYLISTS.first())
                    }
                    AudioChoice.SPOTIFY_PICK -> connector.connect { state ->
                        spotifyState = state
                        if (state is SpotifyState.Connected) connector.play(spotifyPlaylist)
                    }
                    AudioChoice.LOCAL_MUSIC -> {
                        try {
                            context.startActivity(Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_APP_MUSIC)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (e: Exception) { /* no music app found — session still starts */ }
                    }
                    AudioChoice.SILENCE -> {}
                }
                sessionActive = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Start studying", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MinimalRow(label: String, selected: Boolean, onClick: () -> Unit, compact: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (selected) Color.White else Color(0xFFB0B0B0),
            fontSize = if (compact) 14.sp else 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text("✓", color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
private fun InSessionScreen(resetAt: Long, onEnded: () -> Unit, onEndEarly: () -> Unit) {
    var remainingMs by remember { mutableLongStateOf((resetAt - System.currentTimeMillis()).coerceAtLeast(0L)) }

    LaunchedEffect(Unit) {
        while (remainingMs > 0) {
            delay(1000)
            remainingMs = (resetAt - System.currentTimeMillis()).coerceAtLeast(0L)
        }
        onEnded()
    }

    val transition = rememberInfiniteTransition(label = "breathe")
    val scale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(animation = tween(4000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "scale"
    )

    val mins = remainingMs / 60_000L
    val secs = (remainingMs / 1000L) % 60L

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(120.dp)
                .scale(scale)
                .background(Color(0xFF2A2A2A), CircleShape)
        )
        Spacer(Modifier.height(32.dp))
        Text("Studying", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(String.format("%02d:%02d", mins, secs), color = Color(0xFF9A9A9A), fontSize = 15.sp)
        Spacer(Modifier.height(40.dp))
        TextButton(onClick = onEndEarly) {
            Text("End session", color = Color(0xFF9A9A9A))
        }
    }
}
