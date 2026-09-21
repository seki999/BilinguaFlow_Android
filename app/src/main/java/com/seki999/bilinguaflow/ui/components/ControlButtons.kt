package com.seki999.bilinguaflow.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seki999.bilinguaflow.R
import com.seki999.bilinguaflow.model.ListeningState

@Composable
fun ControlButtons(
    listeningState: ListeningState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onCopyAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = listeningState == ListeningState.LISTENING
    val isPaused = listeningState == ListeningState.PAUSED
    val isIdleLike = listeningState == ListeningState.IDLE ||
        listeningState == ListeningState.STOPPED ||
        listeningState == ListeningState.ERROR

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onStart,
            enabled = isIdleLike,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.btn_start))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = if (isPaused) onResume else onPause,
                enabled = isListening || isPaused,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isPaused) stringResource(R.string.btn_resume) else stringResource(R.string.btn_pause))
            }
            OutlinedButton(
                onClick = onStop,
                enabled = isListening || isPaused,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.btn_stop))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.btn_clear))
            }
            OutlinedButton(
                onClick = onCopyAll,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.btn_copy_all))
            }
        }
    }
}
