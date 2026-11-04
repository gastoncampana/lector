package io.github.gastoncampana.lector

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The app's icon: picks a voice for each language and the speed, letting each be heard, and points to Accessibility
 * settings until the shortcut is on.
 */
class SettingsActivity : ComponentActivity() {
    // Whether the shortcut is set: Android turns the service on exactly when a shortcut targets it.
    private var shortcut by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Speech.prepare(this)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)) {
                Surface(Modifier.fillMaxSize()) {
                    Screen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        shortcut = getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    override fun onStop() {
        super.onStop()
        Speech.stop()
    }

    /** Says the sample for [voice]'s language with it. */
    private fun sample(voice: Voice) {
        val text = getString(if (voice.language == Language.ENGLISH) R.string.sample_english else R.string.sample)
        Speech.speak(this, listOf(text to voice), speed(), onStart = {}, onEnd = {})
    }

    @Composable
    private fun Screen() {
        var spanish by remember { mutableStateOf(voice(Language.SPANISH)) }
        var english by remember { mutableStateOf(voice(Language.ENGLISH)) }
        // The voice the speed slider is heard with: the last one tapped.
        var heard by remember { mutableStateOf(spanish) }
        var speed by remember { mutableFloatStateOf(speed()) }
        val pick = { v: Voice ->
            if (v.language == Language.SPANISH) spanish = v else english = v
            heard = v
            setVoice(v)
            sample(v)
        }
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            if (!shortcut) ShortcutCard()
            Text(stringResource(R.string.voices_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Voices(R.string.voice_spanish, Language.SPANISH, spanish, pick)
            Voices(R.string.voice_english, Language.ENGLISH, english, pick)
            Text(stringResource(R.string.speed, speed), style = MaterialTheme.typography.titleMedium)
            Slider(
                value = speed,
                onValueChange = { speed = Math.round(it * 10) / 10f },
                onValueChangeFinished = {
                    setSpeed(speed)
                    sample(heard)
                },
                valueRange = 0.5f..2f,
                steps = 14,
            )
        }
    }

    @Composable
    private fun Voices(title: Int, language: Language, picked: Voice, pick: (Voice) -> Unit) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            Voice.entries.filter { it.language == language }.forEach { v ->
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(v == picked, role = Role.RadioButton) { pick(v) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = v == picked, onClick = null)
                    Text(v.name, Modifier.padding(start = 16.dp))
                }
            }
        }
    }

    @Composable
    private fun ShortcutCard() {
        val context = LocalContext.current
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.shortcut_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.shortcut_text))
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                    Text(stringResource(R.string.shortcut_button))
                }
            }
        }
    }
}
