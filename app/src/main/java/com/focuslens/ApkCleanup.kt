package com.focuslens

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** Installer files (.apk) that are taking up space: in Downloads, and the update FocusLens downloaded. */
object ApkCleanup {
    data class ApkFile(val uri: Uri, val name: String, val bytes: Long)

    /** The update FocusLens downloads into its cache. Only this app can delete it, so no prompt is needed. */
    fun ownUpdateFile(context: Context): File = File(context.cacheDir, "update/focuslens-update.apk")

    /** Lists .apk files in Downloads. Android hides other apps' files unless storage access is granted. */
    fun findInDownloads(context: Context): List<ApkFile> {
        val out = mutableListOf<ApkFile>()
        val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.SIZE)
        try {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                arrayOf("%.apk"),
                null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                while (c.moveToNext()) {
                    val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(idCol).toString())
                    out += ApkFile(uri, c.getString(nameCol) ?: "file.apk", c.getLong(sizeCol))
                }
            }
        } catch (e: Exception) {
            // Storage access not granted: show nothing rather than crash.
        }
        return out.sortedByDescending { it.bytes }
    }

    /** Deletes the Downloads files Android allows: directly on Android 8-10, via the system consent sheet on 11+. */
    fun deleteDirect(context: Context, files: List<ApkFile>): Int {
        var deleted = 0
        files.forEach { f ->
            try {
                if (context.contentResolver.delete(f.uri, null, null) > 0) deleted++
            } catch (e: Exception) {
                // Not allowed for this file; skip it.
            }
        }
        return deleted
    }
}

/** A card with the APK files taking up space, and a button to delete them. */
@Composable
fun ApkCleanupCard() {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<ApkCleanup.ApkFile>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val ownFile = remember { ApkCleanup.ownUpdateFile(context) }

    // Android 11+ shows the system "allow delete" sheet; the result tells us what was removed.
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        message = if (result.resultCode == android.app.Activity.RESULT_OK) "Deleted." else "Nothing was deleted."
        files = ApkCleanup.findInDownloads(context)
    }

    Column(Modifier.fillMaxWidth()) {
        Text("Installer files (APK)", fontSize = 15.sp)
        Text(
            "Old app installers take up space after you install them. Only .apk files are listed. Nothing else is touched.",
            fontSize = 12.sp, color = Color.Gray
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { files = ApkCleanup.findInDownloads(context); message = null }) {
                Text("Find APK files")
            }
        }
        val list = files
        if (list != null) {
            val ownSize = if (ownFile.exists()) ownFile.length() else 0L
            val total = list.sumOf { it.bytes } + ownSize
            Spacer(Modifier.height(6.dp))
            if (list.isEmpty() && ownSize == 0L) {
                Text("No APK files found.", fontSize = 12.sp, color = Color.Gray)
            } else {
                val extra = if (ownSize > 0) " + FocusLens update file" else ""
                Text("${list.size} file(s) in Downloads$extra · ${formatBytes(total)}", fontSize = 12.sp)
                list.take(8).forEach { f ->
                    Text("${f.name} · ${formatBytes(f.bytes)}", fontSize = 11.sp, color = Color.Gray)
                }
                Spacer(Modifier.height(6.dp))
                Button(onClick = {
                    if (ownSize > 0) ownFile.delete()
                    if (list.isEmpty()) {
                        message = "Deleted the FocusLens update file."
                        files = ApkCleanup.findInDownloads(context)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val pi = MediaStore.createDeleteRequest(
                                context.contentResolver, list.map { it.uri }
                            )
                            consentLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                        } catch (e: Exception) {
                            message = "Could not start the delete: ${e.message}"
                        }
                    } else {
                        val n = ApkCleanup.deleteDirect(context, list)
                        message = "Deleted $n of ${list.size} file(s)."
                        files = ApkCleanup.findInDownloads(context)
                    }
                }) { Text("Delete APK files") }
            }
        }
        message?.let { Spacer(Modifier.height(4.dp)); Text(it, fontSize = 12.sp, color = Color.Gray) }
    }
}

private fun formatBytes(b: Long): String = when {
    b >= 1_000_000_000L -> "%.1f GB".format(b / 1_000_000_000.0)
    b >= 1_000_000L -> "%.1f MB".format(b / 1_000_000.0)
    b >= 1_000L -> "%.0f KB".format(b / 1_000.0)
    else -> "$b B"
}
