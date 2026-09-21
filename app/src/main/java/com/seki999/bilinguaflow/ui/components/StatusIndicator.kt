package com.seki999.bilinguaflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.seki999.bilinguaflow.R
import com.seki999.bilinguaflow.model.ListeningState

@Composable
fun StatusIndicator(state: ListeningState, modifier: Modifier = Modifier) {
    val (label, color) = when (state) {
        ListeningState.IDLE -> stringResource(R.string.status_idle) to MaterialTheme.colorScheme.onSurfaceVariant
        ListeningState.LISTENING -> stringResource(R.string.status_listening) to MaterialTheme.colorScheme.secondary
        ListeningState.PAUSED -> stringResource(R.string.status_paused) to MaterialTheme.colorScheme.tertiary
        ListeningState.STOPPED -> stringResource(R.string.status_stopped) to MaterialTheme.colorScheme.onSurfaceVariant
        ListeningState.ERROR -> stringResource(R.string.status_error) to MaterialTheme.colorScheme.error
    }

    val description = stringResource(R.string.cd_status_indicator)

    Row(
        modifier = modifier.semantics { contentDescription = "$description: $label" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(color)
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}
