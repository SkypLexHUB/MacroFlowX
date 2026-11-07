package com.macroflow.automation.ui.common

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.macroflow.automation.R
import com.macroflow.automation.ui.theme.ErrorRed
import com.macroflow.automation.ui.theme.SuccessGreen

/** Explains why the accessibility service is needed, shows its status and opens the system screen. */
@Composable
fun AccessibilityCard(enabled: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AppCard(modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.acc_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(if (enabled) R.string.acc_status_on else R.string.acc_status_off),
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) SuccessGreen else ErrorRed,
            )
        }
        Text(
            text = stringResource(R.string.acc_why),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!enabled) {
            Text(
                text = stringResource(R.string.acc_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        OutlinedButton(
            onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Text(stringResource(R.string.acc_open))
        }
    }
}
