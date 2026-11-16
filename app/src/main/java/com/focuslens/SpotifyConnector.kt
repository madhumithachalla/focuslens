package com.focuslens

import android.content.Context
import android.util.Log
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

    companion object {
        /** The live connection, so a break or a block can pause playback from anywhere. */
        @Volatile private var live: SpotifyAppRemote? = null

        /** Pauses Spotify if FocusLens is connected to it. Does nothing otherwise. */
        fun pauseIfConnected() {
            try {
                live?.playerApi?.pause()
            } catch (e: Exception) {
                Log.e("FocusLens", "Spotify pause failed: ${e.message}", e)
            }
        }
    }

    fun connect(onState: (SpotifyState) -> Unit) {
        if (!FocusPrefs(context).spotifyOn()) {
            onState(SpotifyState.Failed("Spotify is off. Turn it on in Settings → Connections if you want to use it."))
            return
        }
        if (BuildConfig.SPOTIFY_CLIENT_ID.isBlank()) {
            onState(SpotifyState.Failed(
                "Spotify isn't set up for this build yet. One-time fix: create an app at " +
                    "developer.spotify.com/dashboard to get a Client ID, then add it as a GitHub " +
                    "repo secret named SPOTIFY_CLIENT_ID (Settings > Secrets and variables > Actions) " +
                    "and rebuild — the next CI build picks it up automatically."
            ))
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
                live = appRemote
                onState(SpotifyState.Connected)
            }

            override fun onFailure(error: Throwable) {
                Log.e("FocusLens", "Spotify connect failed: ${error.message}", error)
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
        live = null
    }
}
