package club.nuva.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import club.nuva.app.BuildConfig
import club.nuva.app.data.remote.RealtimeClient
import club.nuva.app.ui.components.ErrorBanner
import club.nuva.app.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val realtimeState by viewModel.realtimeState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(state.session?.displayName?.ifBlank { "Nuva" } ?: "Nuva") },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = viewModel::logout) {
                        Icon(Icons.Filled.Logout, contentDescription = "Sign out")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    label = when (realtimeState) {
                        RealtimeClient.State.Online -> "Realtime online"
                        RealtimeClient.State.Connecting -> "Connecting..."
                        RealtimeClient.State.Reconnecting -> "Reconnecting..."
                        RealtimeClient.State.Idle -> "Offline"
                    },
                    online = realtimeState == RealtimeClient.State.Online,
                    connecting = realtimeState == RealtimeClient.State.Connecting ||
                        realtimeState == RealtimeClient.State.Reconnecting,
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(
                    label = "${state.onlineUsers} online",
                    online = state.onlineUsers > 0,
                )
            }

            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Account", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Username", state.session?.username.orEmpty())
                    InfoRow("User id", state.session?.userId.orEmpty())
                    InfoRow("App", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    InfoRow("Server", BuildConfig.API_BASE_URL)
                    InfoRow("API", state.serverVersion.ifBlank { "unknown" })
                }
            }

            state.errorMessage?.let { message ->
                ErrorBanner(message = message, onDismiss = viewModel::dismissError)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::sendEcho, modifier = Modifier.weight(1f)) {
                    Text("Send echo")
                }
                OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.weight(1f)) {
                    Text("Refresh")
                }
            }

            Text(
                "Realtime log",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.log) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
}
