package club.nuva.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import club.nuva.app.ui.design.NuvaAvatar
import club.nuva.app.ui.design.NuvaHeader
import club.nuva.app.ui.theme.NuvaMotion
import club.nuva.app.ui.theme.NuvaSpace
import club.nuva.app.ui.theme.NuvaTheme
import club.nuva.app.util.NuvaTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ChatViewModel(
    private val conversationId: String,
    private val store: ChatDraftStore,
) : ViewModel() {

    val conversation: StateFlow<ChatDraftStore.Conversation?> = store.conversations
        .map { list -> list.firstOrNull { it.id == conversationId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), store.conversation(conversationId))

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    init {
        // Opening a chat is the read receipt. No separate "mark read" gesture.
        store.markRead(conversationId)
    }

    fun onDraftChange(value: String) {
        _draft.value = value
    }

    fun send() {
        val text = _draft.value
        if (text.isBlank()) return
        // Clear first: the field must never hold a message that is already sent.
        _draft.value = ""
        store.send(conversationId, text)
    }

    fun retry(messageId: String) = store.retry(conversationId, messageId)
}

/**
 * The conversation screen.
 *
 * Shape signature: every bubble is 22dp round except the corner nearest its
 * author, which is 6dp. That single asymmetric corner replaces the tail
 * triangle everyone else draws, costs no extra layer, and never breaks when the
 * text wraps.
 *
 * Attachments and voice are wired as visible-but-honest: the buttons exist so
 * the layout is final, and they say which sprint brings them instead of doing
 * nothing when tapped.
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = NuvaTheme.palette
    val convo by viewModel.conversation.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var hint by remember { mutableStateOf<String?>(null) }

    val messages = convo?.messages ?: emptyList()

    // Follow the tail of the conversation, including while the peer types.
    LaunchedEffect(messages.size, convo?.peerTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex + 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        NuvaHeader(
            title = convo?.peer?.displayName ?: "Chat",
            subtitle = when {
                convo == null -> null
                convo?.peerTyping == true -> "typing…"
                convo?.peer?.online == true -> "online"
                else -> convo?.peer?.presence?.ifBlank { "@${convo?.peer?.username}" }
            },
            compact = true,
            onBack = onBack,
            leading = {
                NuvaAvatar(
                    name = convo?.peer?.displayName.orEmpty().ifEmpty { "?" },
                    seed = convo?.peer?.id.orEmpty(),
                    imageUrl = convo?.peer?.avatarUrl,
                    online = convo?.peer?.online == true,
                    size = 38.dp,
                )
            },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(p.hairline),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = NuvaSpace.md,
                end = NuvaSpace.md,
                top = NuvaSpace.md,
                bottom = NuvaSpace.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            itemsIndexed(items = messages, key = { _, item -> item.id }) { index, message ->
                val previous = messages.getOrNull(index - 1)
                val next = messages.getOrNull(index + 1)
                val newDay = previous == null ||
                    !NuvaTime.isSameDay(previous.sentAtMillis, message.sentAtMillis)

                if (newDay) {
                    DaySeparator(NuvaTime.dayLabel(message.sentAtMillis))
                }
                MessageBubble(
                    message = message,
                    // Group consecutive messages from the same author: only the
                    // last of a run carries the timestamp and the round tail.
                    lastInRun = next == null || next.authorId != message.authorId,
                    onRetry = { viewModel.retry(message.id) },
                )
            }
            item {
                AnimatedVisibility(
                    visible = convo?.peerTyping == true,
                    enter = fadeIn(tween(NuvaMotion.FAST)),
                    exit = fadeOut(tween(NuvaMotion.FAST)),
                ) {
                    TypingBubble()
                }
            }
        }

        AnimatedVisibility(
            visible = hint != null,
            enter = fadeIn(tween(NuvaMotion.FAST)),
            exit = fadeOut(tween(NuvaMotion.FAST)),
        ) {
            Text(
                text = hint.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = p.textMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { hint = null }
                    .padding(horizontal = NuvaSpace.gutter, vertical = NuvaSpace.xs),
            )
        }

        Composer(
            value = draft,
            onValueChange = viewModel::onDraftChange,
            onSend = viewModel::send,
            onAttach = { hint = "Photos and files arrive in sprint 3, together with voice." },
            onVoice = { hint = "Voice messages arrive in sprint 3. No length limit, that is the point." },
        )
    }
}

/** LazyColumn's indexed items, imported locally to keep the import list honest. */
private inline fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    items: List<ChatDraftStore.Message>,
    crossinline itemContent: @Composable (Int, ChatDraftStore.Message) -> Unit,
) = items(
    count = items.size,
    key = { index -> items[index].id },
) { index ->
    itemContent(index, items[index])
}

@Composable
private fun DaySeparator(label: String) {
    val p = NuvaTheme.palette
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NuvaSpace.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = p.textFaint,
            modifier = Modifier
                .clip(CircleShape)
                .background(p.surfaceAlt)
                .padding(horizontal = NuvaSpace.md, vertical = 5.dp),
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatDraftStore.Message,
    lastInRun: Boolean,
    onRetry: () -> Unit,
) {
    val p = NuvaTheme.palette
    val mine = message.mine
    val tail = 6.dp
    val round = 22.dp
    val shape = if (mine) {
        RoundedCornerShape(
            topStart = round,
            topEnd = round,
            bottomEnd = if (lastInRun) tail else round,
            bottomStart = round,
        )
    } else {
        RoundedCornerShape(
            topStart = round,
            topEnd = round,
            bottomEnd = round,
            bottomStart = if (lastInRun) tail else round,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(if (mine) p.bubbleOut else p.bubbleIn)
                .then(
                    if (mine) Modifier else Modifier.border(1.dp, p.hairline, shape),
                )
                .clickable(
                    enabled = message.delivery == ChatDraftStore.Delivery.Failed,
                    onClick = onRetry,
                )
                .padding(horizontal = NuvaSpace.md, vertical = NuvaSpace.sm),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (mine) p.bubbleOutText else p.bubbleInText,
            )
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = NuvaTime.clock(message.sentAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (mine) p.bubbleOutText.copy(alpha = 0.7f) else p.textFaint,
                )
                if (mine) {
                    Spacer(Modifier.width(5.dp))
                    DeliveryTicks(message.delivery, onLight = true)
                }
            }
            if (message.delivery == ChatDraftStore.Delivery.Failed) {
                Text(
                    text = "Not sent — tap to retry",
                    style = MaterialTheme.typography.labelSmall,
                    color = p.coral,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** One tick sent, two ticks delivered, filled ticks read. Failed is loud. */
@Composable
fun DeliveryTicks(
    delivery: ChatDraftStore.Delivery,
    onLight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val p = NuvaTheme.palette
    val muted = if (onLight) p.bubbleOutText.copy(alpha = 0.65f) else p.textFaint
    when (delivery) {
        ChatDraftStore.Delivery.Sending -> Icon(
            Icons.Filled.Schedule,
            contentDescription = "Sending",
            tint = muted,
            modifier = modifier.size(13.dp),
        )
        ChatDraftStore.Delivery.Sent -> Icon(
            Icons.Filled.Check,
            contentDescription = "Sent",
            tint = muted,
            modifier = modifier.size(14.dp),
        )
        ChatDraftStore.Delivery.Delivered -> Icon(
            Icons.Filled.DoneAll,
            contentDescription = "Delivered",
            tint = muted,
            modifier = modifier.size(14.dp),
        )
        ChatDraftStore.Delivery.Read -> Icon(
            Icons.Filled.DoneAll,
            contentDescription = "Read",
            tint = if (onLight) p.bubbleOutText else p.mint,
            modifier = modifier.size(14.dp),
        )
        ChatDraftStore.Delivery.Failed -> Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = "Failed",
            tint = p.coral,
            modifier = modifier.size(14.dp),
        )
    }
}

@Composable
private fun TypingBubble() {
    val p = NuvaTheme.palette
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 6.dp))
                .background(p.bubbleIn)
                .border(
                    1.dp,
                    p.hairline,
                    RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 6.dp),
                )
                .padding(horizontal = NuvaSpace.lg, vertical = NuvaSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .padding(end = if (index < 2) 4.dp else 0.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(p.textMuted.copy(alpha = 0.4f + 0.2f * index)),
                )
            }
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onVoice: () -> Unit,
) {
    val p = NuvaTheme.palette
    val canSend = value.isNotBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = NuvaSpace.md, vertical = NuvaSpace.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(p.surface)
                .border(1.dp, p.hairline, RoundedCornerShape(26.dp))
                .padding(start = NuvaSpace.sm, end = NuvaSpace.xs, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onAttach),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = "Attach",
                    tint = p.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = "Message",
                        style = MaterialTheme.typography.bodyLarge,
                        color = p.textFaint,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = LocalTextStyle.current.merge(
                        MaterialTheme.typography.bodyLarge.copy(color = p.text),
                    ),
                    cursorBrush = SolidColor(p.accent),
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onVoice),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Voice message",
                    tint = p.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(NuvaSpace.sm))
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(if (canSend) p.accent else p.surfaceAlt)
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (canSend) p.accentInk else p.textFaint,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}
