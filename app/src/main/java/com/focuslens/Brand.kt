package com.focuslens

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** A small FocusLens mark, so every blocking screen says which app is doing the blocking. */
@Composable
fun FocusLensMark(modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Color(0x66000000), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(painterResource(R.drawable.ic_focuslens_symbol), contentDescription = null, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Image(painterResource(R.drawable.ic_focuslens_wordmark), contentDescription = "FocusLens", modifier = Modifier.height(10.dp))
    }
}

/** Like setContent, but the FocusLens mark sits over the top of every blocking screen. */
fun ComponentActivity.setBrandedContent(content: @Composable () -> Unit) {
    setContent {
        Box(Modifier.fillMaxSize()) {
            content()
            FocusLensMark(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 2.dp))
        }
    }
}

/** Whether notifications are allowed for this app (always true before Android 13). */
internal fun notificationsAllowedHere(context: Context): Boolean =
    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
