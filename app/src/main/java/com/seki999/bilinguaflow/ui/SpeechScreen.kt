package com.seki999.bilinguaflow.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seki999.bilinguaflow.R
import com.seki999.bilinguaflow.model.ListeningState
import com.seki999.bilinguaflow.ui.components.ControlButtons
import com.seki999.bilinguaflow.ui.components.LanguageSelector
import com.seki999.bilinguaflow.ui.components.LiveRecognitionCard
import com.seki999.bilinguaflow.ui.components.StatusIndicator
import com.seki999.bilinguaflow.ui.components.TranscriptCard
import com.seki999.bilinguaflow.viewmodel.SpeechUiEvent
import com.seki999.bilinguaflow.viewmodel.SpeechViewModel
import kotlinx.coroutines.launch

@Composable
fun SpeechScreen(viewModel: SpeechViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val micPermissionRequiredMessage = stringResource(R.string.msg_mic_permission_required)
    val micPermissionPermanentlyDeniedMessage = stringResource(R.string.msg_mic_permission_permanently_denied)
    val transcriptCopiedMessage = stringResource(R.string.msg_transcript_copied)
    val autoStoppedMessage = stringResource(R.string.msg_auto_stopped_inactivity)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDeniedMessage = null
            permanentlyDenied = false
            pendingAction?.invoke()
        } else {
            val activity = context as? Activity
            val showRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
            } ?: true
            permanentlyDenied = !showRationale
            permissionDeniedMessage = if (permanentlyDenied) {
                micPermissionPermanentlyDeniedMessage
            } else {
                micPermissionRequiredMessage
            }
        }
        pendingAction = null
    }

    fun withMicPermission(action: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            permissionDeniedMessage = null
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Keep the screen on only while actively listening; restore normal timeout otherwise.
    DisposableEffect(uiState.listeningState) {
        view.keepScreenOn = uiState.listeningState == ListeningState.LISTENING
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SpeechUiEvent.AutoStoppedInactivity -> snackbarHostState.showSnackbar(autoStoppedMessage)
                is SpeechUiEvent.FatalError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)

            Spacer(Modifier.height(16.dp))
            LanguageSelector(
                selected = uiState.selectedLanguage,
                enabled = uiState.listeningState != ListeningState.LISTENING,
                onLanguageSelected = viewModel::onLanguageSelected
            )

            Spacer(Modifier.height(16.dp))
            StatusIndicator(state = uiState.listeningState)

            Spacer(Modifier.height(16.dp))
            LiveRecognitionCard(partialText = uiState.partialText)

            Spacer(Modifier.height(16.dp))
            TranscriptCard(
                transcript = uiState.transcript,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.height(12.dp))
            permissionDeniedMessage?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                if (permanentlyDenied) {
                    TextButton(onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    }) {
                        Text(stringResource(R.string.btn_open_app_settings))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            ControlButtons(
                listeningState = uiState.listeningState,
                onStart = { withMicPermission(viewModel::onStartClicked) },
                onPause = viewModel::onPauseClicked,
                onResume = { withMicPermission(viewModel::onResumeClicked) },
                onStop = viewModel::onStopClicked,
                onClear = viewModel::onClearClicked,
                onCopyAll = {
                    clipboardManager.setText(AnnotatedString(uiState.transcript))
                    scope.launch { snackbarHostState.showSnackbar(transcriptCopiedMessage) }
                }
            )
        }
    }
}
