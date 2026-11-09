package com.focuslens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
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
import androidx.compose.runtime.rememberCoroutineScope
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
import java.io.File
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
 * github.run_number) against docs/version.txt, published alongside the APK on every CI build.
 * Fails closed (Unknown) on anything going wrong rather than falsely claiming either state.
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

/** Downloads the latest APK into the app's private cache. Runs on IO; throws on failure. */
fun downloadUpdateApk(context: Context): File {
    val dir = File(context.cacheDir, "update").apply { mkdirs() }
    val target = File(dir, "focuslens-update.apk")
    val conn = URL(APK_DOWNLOAD_URL).openConnection() as HttpURLConnection
    conn.connectTimeout = 15_000
    conn.readTimeout = 60_000
    conn.instanceFollowRedirects = true
    if (conn.responseCode != 200) throw IllegalStateException("Download failed (HTTP ${conn.responseCode})")
    conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
    if (target.length() < 1_000_000L) throw IllegalStateException("Download looks incomplete")
    return target
}

/**
 * Opens Android's own installer for the downloaded APK. Android asks the person to confirm,
 * and on first use asks them to allow installs from FocusLens. Nothing installs silently.
 */
fun launchInstaller(context: Context, apk: File) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
    context.startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/** Update card: checks on open, downloads the new APK in the app, then opens Android's installer. */
@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateCheck?>(null) }
    var checking by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun run() {
        checking = true
        state = withContext(Dispatchers.IO) { checkForUpdate() }
        checking = false
    }

    LaunchedEffect(Unit) { run() }

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
                    is UpdateCheck.UpdateAvailable -> "Version ${s.latestBuild} is ready. You have version ${BuildConfig.BUILD_NUMBER}."
                    UpdateCheck.UpToDate -> "You have the latest version (${BuildConfig.BUILD_NUMBER})."
                    UpdateCheck.Unknown -> if (BuildConfig.BUILD_NUMBER <= 0)
                        "This app version can't check for updates (only the published versions can)."
                    else "Couldn't check just now. Connect to the internet and tap Check again."
                },
                fontSize = 12.sp,
                color = Color.Gray
            )
            message?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state is UpdateCheck.UpdateAvailable) {
                    Button(enabled = !busy, onClick = {
                        busy = true
                        message = "Downloading the update…"
                        scope.launch {
                            try {
                                val apk = withContext(Dispatchers.IO) { downloadUpdateApk(context) }
                                message = null
                                launchInstaller(context, apk)
                            } catch (e: Exception) {
                                message = "Update failed: ${e.message ?: "unknown error"}. Try again."
                            }
                            busy = false
                        }
                    }) { Text(if (busy) "Downloading…" else "Install update") }
                }
                OutlinedButton(enabled = !checking, onClick = { scope.launch { run() } }) {
                    Text(if (checking) "Checking…" else "Check again")
                }
            }
        }
    }
}
