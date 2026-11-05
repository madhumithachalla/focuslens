package com.focuslens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val VERSION_URL = "https://madhumithachalla.github.io/focuslens/version.txt"
const val APK_DOWNLOAD_URL = "https://madhumithachalla.github.io/focuslens/focuslens-debug.apk"

sealed class UpdateCheck {
    object UpToDate : UpdateCheck()
    data class UpdateAvailable(val latestBuild: Int) : UpdateCheck()
    object Unknown : UpdateCheck() // no network, no BUILD_NUMBER baked into this build, etc.
}

/**
 * Compares this build's BuildConfig.BUILD_NUMBER (baked in by the CI workflow from
 * github.run_number — see build.gradle.kts and .github/workflows/build-apk.yml) against
 * docs/version.txt, published alongside the APK on the same build. Never silently installs
 * anything — just tells the caller whether to show a link to APK_DOWNLOAD_URL. Network-only;
 * fails closed (Unknown) on anything going wrong rather than falsely claiming either state.
 */
fun checkForUpdate(): UpdateCheck {
    val installed = BuildConfig.BUILD_NUMBER
    if (installed <= 0) return UpdateCheck.Unknown
    return try {
        val conn = URL(VERSION_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.requestMethod = "GET"
        val code = conn.responseCode
        if (code != 200) return UpdateCheck.Unknown
        val latest = conn.inputStream.bufferedReader().readText().trim().toIntOrNull()
            ?: return UpdateCheck.Unknown
        if (latest > installed) UpdateCheck.UpdateAvailable(latest) else UpdateCheck.UpToDate
    } catch (e: Exception) {
        UpdateCheck.Unknown
    }
}

/** Shown in Settings. Checks once automatically on open; "Check again" re-runs it on demand.
 *  Only ever opens the website's download page in the browser — this app never downloads or
 *  installs anything on its own. */
@Composable
fun UpdateCard() {
    val context = LocalContext.current
    var state by remember { mutableStateOf<UpdateCheck?>(null) }
    var checking by remember { mutableStateOf(true) }

    suspend fun run() {
        checking = true
        state = withContext(Dispatchers.IO) { checkForUpdate() }
        checking = false
    }

    LaunchedEffect(Unit) { run() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state is UpdateCheck.UpdateAvailable) Color(0xFFE3EFE8) else CardDefaults.cardColors().containerColor
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("App update", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                when (val s = state) {
                    null -> "Checking…"
                    is UpdateCheck.UpdateAvailable -> "A newer build (#${s.latestBuild}) is on the website — this install is build #${BuildConfig.BUILD_NUMBER}."
                    UpdateCheck.UpToDate -> "You're on the latest build (#${BuildConfig.BUILD_NUMBER})."
                    UpdateCheck.Unknown -> if (BuildConfig.BUILD_NUMBER <= 0)
                        "This build doesn't know its own build number — can't check for updates (only CI-built APKs can)."
                    else "Couldn't check just now — no connection, or the site's unreachable."
                },
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state is UpdateCheck.UpdateAvailable) {
                    Button(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APK_DOWNLOAD_URL)))
                    }) { Text("Download update") }
                }
                OutlinedButton(enabled = !checking, onClick = { scope.launch { run() } }) {
                    Text(if (checking) "Checking…" else "Check again")
                }
            }
        }
    }
}
