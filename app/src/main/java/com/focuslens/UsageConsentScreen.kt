package com.focuslens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Asked once, before any usage is saved. Yes or no; both work, and the choice can change in Settings. */
@Composable
fun UsageConsentScreen(onAnswer: (Boolean) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Track your usage on this phone?", fontSize = 22.sp)
        Spacer(Modifier.height(16.dp))
        Text(PrivacyPrefs.CONSENT_TEXT, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Button(onClick = { onAnswer(true) }, modifier = Modifier.fillMaxWidth()) { Text("Yes") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { onAnswer(false) }, modifier = Modifier.fillMaxWidth()) { Text("No") }
    }
}
