package com.focuslens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The one celebration moment used everywhere a real milestone is actually reached. */
@Composable
fun CelebrationDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("Nice") } },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🎉", fontSize = 40.sp)
                Spacer(Modifier.height(6.dp))
                Text(title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        },
        text = { Text(body, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    )
}
