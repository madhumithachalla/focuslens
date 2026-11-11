package com.focuslens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Menu page: check for and install the latest build, and see what changed in each build. */
@Composable
fun AppUpdatesScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("focus", android.content.Context.MODE_PRIVATE) }
    var keptVersion by remember { mutableStateOf(prefs.getInt("backup_kept_version", -1)) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("App updates", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        if (keptVersion >= 0) {
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFEDEAE0))) {
                Column(Modifier.padding(16.dp)) {
                    Text("A copy of your data is kept on this phone", fontWeight = FontWeight.Medium)
                    Text(
                        "It was saved from an earlier version of FocusLens before it was updated. " +
                            "It is not restored automatically yet. Keep it, or delete it to free space.",
                        fontSize = 12.sp, color = Color.Gray
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        java.io.File(context.filesDir, "backups").deleteRecursively()
                        prefs.edit().remove("backup_kept_version").apply()
                        keptVersion = -1
                    }) { Text("Delete the copy") }
                }
            }
        }
        Text("Your app version: ${BuildConfig.BUILD_NUMBER}", fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        UpdateCard()
        Spacer(Modifier.height(20.dp))
        ApkCleanupCard()
        Spacer(Modifier.height(20.dp))
        Text("What changed", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        APP_CHANGELOG.sortedByDescending { it.build }.forEach { note ->
            Spacer(Modifier.height(10.dp))
            Text("App version ${note.build}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            note.points.forEach { Text("• $it", fontSize = 13.sp) }
        }
    }
}
