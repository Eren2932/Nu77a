package club.nuva.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import club.nuva.app.data.remote.RealtimeClient
import club.nuva.app.ui.design.NuvaAvatar
import club.nuva.app.ui.theme.NuvaRadius
import club.nuva.app.ui.theme.NuvaSize
import club.nuva.app.ui.theme.NuvaSpace
import club.nuva.app.ui.theme.NuvaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import androidx.lifecycle.viewModelScope
import java.util.Calendar
import java.util.Locale

/**
 * Chats list.
 *
 * Public surface is unchanged so NuvaShell keeps compiling untouched:
 * ChatsViewModel(store), UiState(query, conversations, totalUnread),
 * state, onQueryChange, and ChatsScreen(viewModel, realtimeState,
 * onOpenChat, onNewChat, modifier, bottomInset).
 */
class ChatsViewModel(private val store: ChatDraftStore) : ViewModel() {

    data class UiState(
        val query: String = "",
        val conversations: List<ChatDraftStore.Conversation> = emptyList(),
        val totalUnread: Int = 0,
    )

    private val query = MutableStateFlow("")

    val state: StateFlow<UiState> = combine(store.conversations, query) { list, q ->
        val filtered = if (q.isBlank()) {
            list
        } else {
            val needle = q.trim().lowercase(Locale.getDefault())
            list.filter { conversation ->
                conversation.peer.displayName.lowercase(Locale.getDefault()).contains(needle) ||
                    conversation.peer.username.lowercase(Locale.getDefault()).contains(needle) ||
                    conversation.lastMessage?.text.orEmpty().lowercase(Locale.getDefault()).contains(needle)
            }
        }
        UiState(
            query = q,
            conversations = filtered.sortedByDescending { it.lastMessage?.sentAtMillis ?: 0L },
            totalUnread = list.sumOf { it.unread },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun onQueryChange(value: String) = query.update { value }
}

@Composable
fun ChatsScreen(
    viewModel: ChatsViewModel,
    realtimeState: RealtimeClient.State,
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val palette = NuvaTheme.palette
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize().background(palette.canvas)) {
        Column(modifier = Modifier.fillMaxSize()) {

            ChatsHeader(totalUnread = state.totalUnread)

            ConnectionStrip(realtimeState = realtimeState)

            SearchField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
            )

            if (state.conversations.isEmpty()) {
                EmptyChats(
                    searching = state.query.isNotBlank(),
                    onNewChat = onNewChat,
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(
                        top = NuvaSpace.sm,
                        bottom = bottomInset + NuvaSpace.huge,
                    ),
                ) {
                    items(
                        items = state.conversations,
                        key = { conversation -> conversation.id },
                    ) { conversation ->
                        ConversationRow(
                            conversation = conversation,
                            onClick = { onOpenChat(conversation.id) },
                        )
                    }
                }
            }
        }

        // Compose button, lifted above the tab bar.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = NuvaSpace.gutter, bottom = bottomInset + NuvaSpace.gutter)
                .size(NuvaSize.fab)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(palette.bubbleOutTop, palette.bubbleOutBottom)))
                .clickable(onClick = onNewChat),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "New chat",
                tint = palette.bubbleOutText,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

// ----------------------------------------------------------------- header

@Composable
private fun ChatsHeader(totalUnread: Int) {
    val palette = NuvaTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(palette.surface, palette.canvas)))
            .statusBarsPadding()
            .padding(horizontal = NuvaSpace.gutter, vertical = NuvaSpace.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Chats",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = palette.text,
            )
            if (totalUnread > 0) {
                Spacer(Modifier.width(NuvaSpace.md))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(NuvaRadius.chip))
                        .background(palette.accentWash)
                        .padding(horizontal = NuvaSpace.sm, vertical = NuvaSpace.hair),
                ) {
                    Text(
                        text = "$totalUnread new",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.accentInk,
                    )
                }
            }
        }
    }
}

/**
 * Connection strip.
 *
 * Only shows when something is wrong. A permanent "Online" banner is noise:
 * the useful signal is the moment the socket is not there.
 */
@Composable
private fun ConnectionStrip(realtimeState: RealtimeClient.State) {
    val palette = NuvaTheme.palette
    val visible = realtimeState != RealtimeClient.State.Online

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(180)) + fadeIn(tween(180)),
        exit = shrinkVertically(tween(140)) + fadeOut(tween(140)),
    ) {
        val label = when (realtimeState) {
            RealtimeClient.State.Idle -> "Offline"
            RealtimeClient.State.Connecting -> "Connecting\u2026"
            RealtimeClient.State.Reconnecting -> "Reconnecting\u2026"
            RealtimeClient.State.Online -> "Online"
        }
        val tone = when (realtimeState) {
            RealtimeClient.State.Idle -> palette.textMuted
            else -> palette.amber
        }
        val pulse = rememberInfiniteTransition(label = "conn")
        val alpha by pulse.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
            label = "conndot",
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NuvaSpace.gutter)
                .clip(RoundedCornerShape(NuvaRadius.tile))
                .background(palette.surfaceSunken)
                .padding(horizontal = NuvaSpace.md, vertical = NuvaSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(tone.copy(alpha = alpha)))
            Spacer(Modifier.width(NuvaSpace.sm))
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = palette.textMuted)
        }
    }
}

// ----------------------------------------------------------------- search

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    val palette = NuvaTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NuvaSpace.gutter, vertical = NuvaSpace.sm)
            .heightIn(min = NuvaSize.searchField)
            .clip(RoundedCornerShape(NuvaRadius.chip))
            .background(palette.surfaceSunken)
            .border(1.dp, palette.hairline, RoundedCornerShape(NuvaRadius.chip))
            .padding(horizontal = NuvaSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = palette.textFaint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(NuvaSpace.md))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = "Search chats",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textFaint,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = palette.text,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                ),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Clear",
                tint = palette.textMuted,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onValueChange("") },
            )
        }
    }
}

// -------------------------------------------------------------------- row

@Composable
private fun ConversationRow(
    conversation: ChatDraftStore.Conversation,
    onClick: () -> Unit,
) {
    val palette = NuvaTheme.palette
    val last = conversation.lastMessage
    val unread = conversation.unread

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = NuvaSpace.gutter, vertical = NuvaSpace.md)
            .heightIn(min = NuvaSize.chatRow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NuvaAvatar(
            name = conversation.peer.displayName,
            seed = conversation.peer.id,
            size = NuvaSize.avatarChat,
            imageUrl = conversation.peer.avatarUrl.takeIf { it.isNotBlank() },
            online = conversation.peer.online,
        )

        Spacer(Modifier.width(NuvaSpace.lg))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.peer.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.SemiBold,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (conversation.muted) {
                    Spacer(Modifier.width(NuvaSpace.xs))
                    Icon(
                        imageVector = Icons.Filled.NotificationsOff,
                        contentDescription = "Muted",
                        tint = palette.textFaint,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Spacer(Modifier.height(NuvaSpace.hair))

            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    conversation.peerTyping -> Text(
                        text = "typing\u2026",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.accent,
                        maxLines = 1,
                    )

                    last != null -> {
                        if (last.mine) {
                            DeliveryTicks(delivery = last.delivery)
                            Spacer(Modifier.width(NuvaSpace.xs))
                        }
                        Text(
                            text = last.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (unread > 0) palette.textMuted else palette.textFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    else -> Text(
                        text = "No messages yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textFaint,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.width(NuvaSpace.md))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = last?.let { relativeStamp(it.sentAtMillis) }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = if (unread > 0) palette.accent else palette.textFaint,
            )
            Spacer(Modifier.height(NuvaSpace.xs))
            if (unread > 0) {
                Box(
                    modifier = Modifier
                        .widthIn(min = 20.dp)
                        .clip(RoundedCornerShape(NuvaRadius.chip))
                        .background(
                            if (conversation.muted) {
                                SolidColor(palette.textFaint)
                            } else {
                                Brush.verticalGradient(listOf(palette.bubbleOutTop, palette.bubbleOutBottom))
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (unread > 99) "99+" else "$unread",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.bubbleOutText,
                    )
                }
            } else {
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = NuvaSpace.gutter + NuvaSize.avatarChat + NuvaSpace.lg)
            .height(1.dp)
            .background(palette.hairline.copy(alpha = 0.5f)),
    )
}

// ----------------------------------------------------------------- empty

@Composable
private fun EmptyChats(
    searching: Boolean,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = NuvaTheme.palette
    Box(
        modifier = modifier.fillMaxWidth().padding(NuvaSpace.huge),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(palette.surfaceSunken),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ChatBubbleOutline,
                    contentDescription = null,
                    tint = palette.textFaint,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.height(NuvaSpace.lg))
            Text(
                text = if (searching) "Nothing found" else "No chats yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
            )
            Spacer(Modifier.height(NuvaSpace.xs))
            Text(
                text = if (searching) "Try a different name." else "Start one and it will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textMuted,
            )
            if (!searching) {
                Spacer(Modifier.height(NuvaSpace.xl))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(NuvaRadius.chip))
                        .background(Brush.verticalGradient(listOf(palette.bubbleOutTop, palette.bubbleOutBottom)))
                        .clickable(onClick = onNewChat)
                        .padding(horizontal = NuvaSpace.xl, vertical = NuvaSpace.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = palette.bubbleOutText,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(NuvaSpace.sm))
                    Text(
                        text = "New chat",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.bubbleOutText,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ time

/** Today -> clock, this week -> weekday, older -> date. */
private fun relativeStamp(millis: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 ->
            String.format(Locale.US, "%02d:%02d", then.get(Calendar.HOUR_OF_DAY), then.get(Calendar.MINUTE))
        sameYear && dayDiff == 1 -> "Yesterday"
        sameYear && dayDiff in 2..6 -> String.format(Locale.US, "%1\$ta", then)
        sameYear -> String.format(Locale.US, "%1\$td.%1\$tm", then)
        else -> String.format(Locale.US, "%1\$td.%1\$tm.%1\$ty", then)
    }
}
