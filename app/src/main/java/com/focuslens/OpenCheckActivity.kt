package com.focuslens

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shown each time Instagram is opened fresh. Two choices, no challenge:
 * "Open" lets you in until you leave the app; "Not now" sends you home and counts the choice.
 */
class OpenCheckActivity : ComponentActivity() {

    companion object {
        @Volatile var showing = false
    }

    private val pkg by lazy { intent.getStringExtra("pkg") ?: SectionDetector.INSTAGRAM }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showing = true
        window.setBackgroundDrawable(ColorDrawable(0xFFF7F5F0.toInt()))
        // Back counts as "Not now", so it can't be used to slip past the check.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = notNow()
        })
        setContent {
            MaterialTheme(
                typography = AppTypography,
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1F3A34),
                    background = Color(0xFFF7F5F0),
                    surface = Color(0xFFF7F5F0)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        OpenCheckBody(
                            count = FocusPrefs(this@OpenCheckActivity).notOpenedTotal(pkg),
                            onOpen = { open() },
                            onNotNow = { notNow() }
                        )
                    }
                }
            }
        }
    }

    private fun open() {
        FocusPrefs(this).setOpenApproved(true)
        finish()
    }

    private fun notNow() {
        FocusPrefs(this).addNotOpened(pkg)
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        finish()
    }

    override fun onStop() {
        showing = false
        if (!isFinishing) finish()
        super.onStop()
    }

    override fun onDestroy() {
        showing = false
        super.onDestroy()
    }
}

@Composable
private fun OpenCheckBody(count: Long, onOpen: () -> Unit, onNotNow: () -> Unit) {
    Text("📵", fontSize = 56.sp)
    Spacer(Modifier.height(16.dp))
    Text("Open Instagram?", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(20.dp))
    Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Open", fontSize = 16.sp) }
    Spacer(Modifier.height(10.dp))
    OutlinedButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) { Text("Not now", fontSize = 16.sp) }
    Spacer(Modifier.height(24.dp))
    Text("You chose not to open it $count times", fontSize = 14.sp, color = Color.Gray)
}
