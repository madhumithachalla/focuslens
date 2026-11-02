package com.focuslens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FocusPlaylistsScreen() {
    val context = LocalContext.current
    val connector = remember { SpotifyConnector(context) }
    var state by remember { mutableStateOf<SpotifyState>(SpotifyState.Disconnected) }
    var nowPlaying by remember { mutableStateOf<FocusPlaylist?>(null) }

    DisposableEffect(Unit) {
        onDispose { connector.disconnect() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Focus playlists", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Plays in your own Spotify app — FocusLens just tells it what to queue.",
            fontSize = 13.sp
        )
        Spacer(Modifier.height(20.dp))

        when (val s = state) {
            is SpotifyState.Disconnected -> {
                Button(onClick = { connector.connect { state = it } }) {
                    Text("Connect Spotify")
                }
            }
            is SpotifyState.Connecting -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Connecting…")
                }
            }
            is SpotifyState.Failed -> {
                Text("Couldn't connect: ${s.reason}", fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { connector.connect { state = it } }) {
                    Text("Try again")
                }
            }
            is SpotifyState.Connected -> {
                FOCUS_PLAYLISTS.forEach { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(playlist.emoji, fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(playlist.label, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Button(onClick = {
                            connector.play(playlist)
                            nowPlaying = playlist
                        }) {
                            Text(if (nowPlaying == playlist) "Playing" else "Play")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { connector.pause(); nowPlaying = null }) {
                    Text("Pause")
                }
            }
        }
    }
}
