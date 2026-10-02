package com.crank.music.feature.recognition

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.crank.music.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun RecognitionScreen(
    onPlaySong: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onBackClick: () -> Unit = {},
    recognitionViewModel: RecognitionViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = recognitionViewModel.repository

    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var recognizedSong by remember { mutableStateOf<Song?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Single shared implementation used by both the permission-grant callback and
    // the main button. These were previously two copies of ~25 identical lines,
    // which is how the error handling would have drifted apart between them.
    val beginRecognition: () -> Unit = {
        startAudioRecording(
            scope = scope,
            onStart = {
                isRecording = true
                errorMessage = null
                recognizedSong = null
            },
            onComplete = { audioBytes ->
                isRecording = false
                isProcessing = true
                scope.launch(Dispatchers.IO) {
                    val result = repository.recognizeSong(audioBytes)
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        when (result) {
                            is RecognitionResult.Match -> recognizedSong = result.song
                            RecognitionResult.NoMatch -> errorMessage =
                                "No match found. Try again closer to the music."
                            RecognitionResult.NotConfigured -> errorMessage =
                                "Song recognition isn't set up in this build. " +
                                    "Add AUDD_API_TOKEN to local.properties."
                            is RecognitionResult.ServiceError -> errorMessage =
                                "Recognition service rejected the request " +
                                    "(HTTP ${result.statusCode}). Try again later."
                            is RecognitionResult.NetworkError -> errorMessage =
                                "Couldn't reach the recognition service. " +
                                    "Check your connection and try again."
                        }
                    }
                }
            },
            onError = { err ->
                isRecording = false
                isProcessing = false
                errorMessage = err
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            beginRecognition()
        } else {
            errorMessage = "Microphone permission is required to recognize music."
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Song Recognition",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tap to identify songs playing around you",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                )
            }

            IconButton(
                onClick = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        beginRecognition()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = !isRecording && !isProcessing,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRecording) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer
                    )
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.GraphicEq else Icons.Default.Mic,
                        contentDescription = "Recognize",
                        tint = if (isRecording) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = when {
                isRecording -> "Listening for music..."
                isProcessing -> "Identifying track..."
                else -> "Tap icon to start"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        errorMessage?.let { err ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        recognizedSong?.let { song ->
            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (song.artworkUrl.isNotBlank()) {
                        AsyncImage(
                            model = song.artworkUrl,
                            contentDescription = song.title,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = song.artistName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { onPlaySong(song) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play")
                        }

                        OutlinedButton(
                            onClick = { onAddToQueue(song) }
                        ) {
                            Icon(Icons.Default.Queue, contentDescription = "Add to Queue")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add to Queue")
                        }
                    }
                }
            }
        }
    }
}

@Suppress("MissingPermission")
private fun startAudioRecording(
    scope: CoroutineScope,
    onStart: () -> Unit,
    onComplete: (ByteArray) -> Unit,
    onError: (String) -> Unit
) {
    onStart()
    scope.launch(Dispatchers.IO) {
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        if (bufferSize <= 0) {
            withContext(Dispatchers.Main) { onError("Audio recorder initialization failed") }
            return@launch
        }

        // Held outside the try so the finally block can release it on every exit path. An
        // AudioRecord that is never released keeps the microphone open for the process's
        // lifetime, so a mid-read failure (or the early "not initialized" return) must not leak it.
        var recorder: AudioRecord? = null
        try {
            val activeRecorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            recorder = activeRecorder

            if (activeRecorder.state != AudioRecord.STATE_INITIALIZED) {
                withContext(Dispatchers.Main) { onError("Microphone unavailable") }
                return@launch
            }

            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(bufferSize)
            activeRecorder.startRecording()

            val recordTimeMs = 5000L
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < recordTimeMs) {
                val read = activeRecorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    outputStream.write(buffer, 0, read)
                }
            }

            activeRecorder.stop()
            activeRecorder.release()
            // Released on the success path; clear the reference so the finally block does not
            // release it a second time.
            recorder = null

            val capturedBytes = outputStream.toByteArray()
            withContext(Dispatchers.Main) {
                onComplete(capturedBytes)
            }
        } catch (e: Exception) {
            Log.e("CRANK_INTEGRATION", e.message ?: "Audio recording failed", e)
            withContext(Dispatchers.Main) {
                onError(e.message ?: "Recording error")
            }
        } finally {
            recorder?.release()
        }
    }
}
