package com.example.comfood.wear

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.comfood.wear.R
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                WearVoiceCaptureScreen()
            }
        }
    }
}

@Composable
private fun WearVoiceCaptureScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Tap and say what you ate") }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()

        if (!spoken.isNullOrBlank()) {
            status = "Sending \"$spoken\" to your phone..."
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val id = UUID.randomUUID().toString()
                    val createdAt = System.currentTimeMillis()
                    val payload = JSONObject().apply {
                        put("id", id)
                        put("transcript", spoken)
                        put("createdAt", createdAt)
                        put("sourceDevice", "watch")
                    }.toString().toByteArray()

                    val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
                    nodes.forEach { node ->
                        Tasks.await(
                            Wearable.getMessageClient(context)
                                .sendMessage(node.id, PENDING_MEAL_MESSAGE_PATH, payload)
                        )
                    }

                    val request = PutDataMapRequest.create("$PENDING_MEAL_PATH_PREFIX/$id").apply {
                        dataMap.putString("id", id)
                        dataMap.putString("transcript", spoken)
                        dataMap.putLong("createdAt", createdAt)
                        dataMap.putString("sourceDevice", "watch")
                    }.asPutDataRequest().setUrgent()
                    Tasks.await(Wearable.getDataClient(context).putDataItem(request))
                }.onSuccess {
                    status = "Sent to phone. Check the pending card."
                }.onFailure {
                    status = "Could not send yet. Make sure the phone is nearby."
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                context.getString(R.string.watch_title),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                context.getString(R.string.watch_subtitle),
                color = Color(0xFFF0A060),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = { launchSpeechInput(context, speechLauncher) },
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Mic", textAlign = TextAlign.Center)
                    Spacer(Modifier.size(4.dp))
                    Text(context.getString(R.string.watch_record), textAlign = TextAlign.Center)
                }
            }
            Text(
                if (status == "Tap and say what you ate") context.getString(R.string.watch_idle) else status,
                color = Color(0xFFE2E8F0),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun launchSpeechInput(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe your meal")
    }
    try {
        launcher.launch(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Speech recognition is not available.", Toast.LENGTH_SHORT).show()
    }
}

private const val PENDING_MEAL_PATH_PREFIX = "/pending_meal"
private const val PENDING_MEAL_MESSAGE_PATH = "/pending_meal_message"
