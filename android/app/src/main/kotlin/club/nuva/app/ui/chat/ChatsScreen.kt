package club.nuva.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import club.nuva.app.data.remote.RealtimeClient
import club.nuva.app.ui.design.NuvaAvatar
import club.nuva.app.ui.design.NuvaEmptyState
import club.nuva.app.ui.design.NuvaField
import club.nuva.app.ui.design.NuvaHeader
import club.nuva.app.ui.design.NuvaStatusChip
import club.nuva.app.ui.theme.NuvaSpace
import club.nuva.app.ui.theme.NuvaTheme
import club.nuva.app.util.NuvaTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class ChatsViewModel(private val store: ChatDraftStore) : ViewModel() {

    data class UiState(
        val query: String = "",
        val conversations: List<ChatDraftStore.Conversation> = emptyList(),
        val totalUnread: Int = 0,
    )

    private val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<UiState> = combine(store.conversations, query) { list, q ->
        val filtered = if (q.isBlank()) {
            list
        } else {
            list.filter { convo ->
                convo.peer.displayName.contains(q, ignoreCase = true) ||
                    convo.peer.username.contains(q, ignoreCase = true) ||
                    convo.lastMessage?.text?.contains(q, ignoreCase = true) == true
            }
        }
        UiState(query = q, conversations = filtered, totalUnread = list.sumOf { it.unread })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun onQueryChange(value: String) = query.update { value }
}

/**
 * The chat list — the screen the app opens on and the one users judge it by.
 *
 * Layout decisions worth keeping:
 *  - the realtime state lives in the header, not buried in settings. If the
 *    socket is down the user must see it before they wonder why nothing arrives.
 *  - search is always visible rather than hidden behind a magnifier icon: one
 *    less tap on the action people repeat most.
 *  - the last message line shows who spoke ("You: ...") so a read receipt is
 *    not the only clue about whose turn it is.
 */
@Composable
fun ChatsScreen(
    viewModel: ChatsViewModel,
    realtimeState: RealtimeClient.State,
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val p = NuvaTheme.palette
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            NuvaHeader(
                title = "Nuva",
                subtitle = if (state.totalUnread > 0) "${state.totalUnread} unread" else "Your words. Your rules.",
                actions = {
                    NuvaStatusChip(
                        label = when (realtimeState) {
                            RealtimeClient.State.Online -> "live"
                            RealtimeClient.State.Connecting -> "connecting"
                            RealtimeClient.State.Reconnecting -> "reconnecting"
                            RealtimeClient.State.Idle -> "offline"
                        },
                        color = when (realtimeState) {
                            RealtimeClient.State.Online -> p.mint
                            RealtimeClient.State.Idle -> p.textFaint
                            else -> p.amber
                        },
                    )
                },
            )

            NuvaField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = "Search",
                placeholder = "Name or message",
                leadingIcon = Icons.Filled.Search,
                modifier = Modifier.padding(horizontal = NuvaSpace.gutter),
            )

            Spacer(Modifier.height(NuvaSpace.md))

            if (state.conversations.isEmpty()) {
                NuvaEmptyState(
                    icon = Icons.Filled.ChatBubbleOutline,
                    title = if (state.query.isBlank()) "No conversations yet" else "Nothing found",
                    message = if (state.query.isBlank()) {
                        "Start with someone on this server. Nobody outside it can see that you did."
                    } else {
                        "No chat or message matches “${state.query}”."
                    },
                    actionText = if (state.query.isBlank()) "Start a chat" else null,
                    onAction = if (state.query.isBlank()) onNewChat else null,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.conversations, key = { it.id }) { convo ->
                        ConversationRow(convo = convo, onClick = { onOpenChat(convo.id) })
                    }
                    item { Spacer(Modifier.height(bottomInset + NuvaSpace.huge)) }
                }
            }
        }

        // Compose action. Sits above the tab bar, thumb-reachable.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = NuvaSpace.gutter, bottom = bottomInset + NuvaSpace.lg)
                .size(58.dp)
                .clip(CircleShape)
                .background(p.accent)
                .clickable(onClick = onNewChat),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "New chat",
                tint = p.accentInk,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun ConversationRow(
    convo: ChatDraftStore.Conversation,
    onClick: () -> Unit,
) {
    val p = NuvaTheme.palette
    val last = convo.lastMessage
    val unread = convo.unread > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = NuvaSpace.gutter, vertical = NuvaSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NuvaAvatar(
            name = convo.peer.displayName,
            seed = convo.peer.id,
            imageUrl = convo.peer.avatarUrl,
            online = convo.peer.online,
            size = 50.dp,
        )
        Spacer(Modifier.width(NuvaSpace.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = convo.peer.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (convo.muted) {
                    Spacer(Modifier.width(NuvaSpace.xs))
                    Icon(
                        Icons.Filled.NotificationsOff,
                        contentDescription = "Muted",
                        tint = p.textFaint,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    convo.peerTyping -> "typing…"
                    last == null -> "No messages yet"
                    last.mine -> "You: ${last.text}"
                    else -> last.text
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    convo.peerTyping -> p.mint
                    unread -> p.text
                    else -> p.textMuted
                },
                fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(NuvaSpace.sm))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = NuvaTime.listStamp(last?.sentAtMillis ?: 0L),
                style = MaterialTheme.typography.labelSmall,
                color = if (unread) p.accent else p.textFaint,
            )
            Spacer(Modifier.height(6.dp))
            if (unread) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(p.accent)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = convo.unread.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = p.accentInk,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else if (last?.mine == true) {
                DeliveryTicks(last.delivery)
            }
        }
    }
}
