package com.focuslens

import android.content.Context
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote

/** A curated focus playlist — real Spotify playlist/track URIs the user can swap in Settings. */
data class FocusPlaylist(val label: String, val emoji: String, val spotifyUri: String)

val FOCUS_PLAYLISTS = listOf(
    FocusPlaylist("Lo-fi focus", "🎧", "spotify:playlist:37i9dQZF1DWWQRwui0ExPn"),
    FocusPlaylist("Deep focus", "🧠", "spotify:playlist:37i9dQZF1DX3PFzdbtx1Us"),
    FocusPlaylist("Rain sounds", "🌧️", "spotify:playlist:37i9dQZF1DX4PP3DA4J0N8")
)

sealed class SpotifyState {
    object Disconnected : SpotifyState()
    object Connecting : SpotifyState()
    object Connected : SpotifyState()
    data class Failed(val reason: String) : SpotifyState()
}

/**
 * Thin wrapper over Spotify's App Remote SDK. Needs the Spotify app installed on the
 * device and a Client ID registered at developer.spotify.com (see build.gradle.kts).
 * Controls playback directly in the user's own Spotify app rather than streaming audio
 * itself — FocusLens never touches the user's Spotify login or library beyond that.
 */
class SpotifyConnector(private val context: Context) {
    private var remote: SpotifyAppRemote? = null

    fun connect(onState: (SpotifyState) -> Unit) {
        if (BuildConfig.SPOTIFY_CLIENT_ID.isBlank()) {
            onState(SpotifyState.Failed("No Spotify Client ID configured — see build.gradle.kts"))
            return
        }
        onState(SpotifyState.Connecting)
        val params = ConnectionParams.Builder(BuildConfig.SPOTIFY_CLIENT_ID)
            .setRedirectUri("focuslens://auth")
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                remote = appRemote
                onState(SpotifyState.Connected)
            }

            override fun onFailure(error: Throwable) {
                onState(SpotifyState.Failed(error.message ?: "Couldn't reach Spotify"))
            }
        })
    }

    fun play(playlist: FocusPlaylist) {
        remote?.playerApi?.play(playlist.spotifyUri)
    }

    fun pause() {
        remote?.playerApi?.pause()
    }

    fun disconnect() {
        remote?.let { SpotifyAppRemote.disconnect(it) }
        remote = null
    }
}
