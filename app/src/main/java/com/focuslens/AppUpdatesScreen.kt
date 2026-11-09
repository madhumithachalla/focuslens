package com.focuslens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Menu page: check for and install the latest build, and see what changed in each build. */
@Composable
fun AppUpdatesScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("App updates", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("You have build #${BuildConfig.BUILD_NUMBER}.", fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        UpdateCard()
        Spacer(Modifier.height(20.dp))
        Text("What changed", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        APP_CHANGELOG.sortedByDescending { it.build }.forEach { note ->
            Spacer(Modifier.height(10.dp))
            Text("Build #${note.build}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            note.points.forEach { Text("• $it", fontSize = 13.sp) }
        }
    }
}
