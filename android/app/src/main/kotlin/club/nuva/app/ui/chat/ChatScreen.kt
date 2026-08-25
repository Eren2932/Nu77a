package club.nuva.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import club.nuva.app.ui.design.NuvaAvatar
import club.nuva.app.ui.theme.NuvaMotion
import club.nuva.app.ui.theme.NuvaRadius
import club.nuva.app.ui.theme.NuvaSize
import club.nuva.app.ui.theme.NuvaSpace
import club.nuva.app.ui.theme.NuvaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import java.util.Locale

/**
 * Chat screen.
 *
 * The public surface is unchanged on purpose: [ChatViewModel] still takes
 * (conversationId, store) and [ChatScreen] still takes (viewModel, onBack),
 * so NuvaShell keeps compiling untouched. Everything below that line is new.
 */
class ChatViewModel(
    private val conversationId: String,
    private val store: ChatDraftStore,
) : ViewModel() {

    val conversation: StateFlow<ChatDraftStore.Conversation?> = store.conversations
        .map { list -> list.firstOrNull { it.id == conversationId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    /**
     * Reactions live in memory only, keyed by message id.
     *
     * This is deliberate and temporary: the durable version is the
     * reaction_add / reaction_remove / reaction_relay path on the server,
     * which is not wired to this client yet. Keeping them here means the
     * gesture, the panel and the pills are real and testable today, and the
     * swap to server state later touches this one property, not the UI.
     */
    private val _reactions = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val reactions: StateFlow<Map<String, Set<String>>> = _reactions.asStateFlow()

    init {
        store.markRead(conversationId)
    }

    fun onDraftChange(value: String) {
        _draft.value = value
    }

    fun send() {
        val text = _draft.value.trim()
        if (text.isEmpty()) return
        store.send(conversationId, text)
        _draft.value = ""
    }

    fun retry(messageId: String) = store.retry(conversationId, messageId)

    fun toggleReaction(messageId: String, emoji: String) {
        _reactions.value = _reactions.value.toMutableMap().apply {
            val current = this[messageId].orEmpty()
            val next = if (emoji in current) current - emoji else current + emoji
            if (next.isEmpty()) remove(messageId) else put(messageId, next)
        }
    }
}

/** Emoji offered by the long-press panel, in the order Telegram uses. */
private val QUICK_REACTIONS = listOf("\uD83D\uDD25", "\u2764\uFE0F", "\uD83D\uDC4D", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22")

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = NuvaTheme.palette
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val reactions by viewModel.reactions.collectAsStateWithLifecycle()

    // id of the message whose reaction panel is open, null when none is.
    var reactingTo by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val messages = conversation?.messages.orEmpty()

    // Stick to the newest message whenever one arrives.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Box(modifier = modifier.fillMaxSize().background(palette.canvas)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ChatTopBar(
                title = conversation?.peer?.displayName ?: "Chat",
                subtitle = when {
                    conversation?.peerTyping == true -> "typing\u2026"
                    conversation?.peer?.online == true -> "online"
                    else -> conversation?.peer?.presence
                },
                subtitleAccented = conversation?.peerTyping == true || conversation?.peer?.online == true,
                peerName = conversation?.peer?.displayName ?: "?",
                peerSeed = conversation?.peer?.id ?: "?",
                online = conversation?.peer?.online == true,
                onBack = onBack,
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    EmptyConversation(name = conversation?.peer?.displayName ?: "")
                } else {
                    MessageList(
                        listState = listState,
                        rows = remember(messages) { buildRows(messages) },
                        reactions = reactions,
                        peerTyping = conversation?.peerTyping == true,
                        onLongPress = { reactingTo = it },
                        onRetry = viewModel::retry,
                        onToggleReaction = viewModel::toggleReaction,
                    )
                }
            }

            Composer(
                value = draft,
                onValueChange = viewModel::onDraftChange,
                onSend = viewModel::send,
                recording = recording,
                onRecordingChange = { recording = it },
            )
        }

        // Reaction picker, drawn above everything on its own scrim.
        ReactionOverlay(
            visibleFor = reactingTo,
            chosen = reactingTo?.let { reactions[it] }.orEmpty(),
            onPick = { id, emoji ->
                viewModel.toggleReaction(id, emoji)
                reactingTo = null
            },
            onDismiss = { reactingTo = null },
        )
    }
}

// ---------------------------------------------------------------- top bar

@Composable
private fun ChatTopBar(
    title: String,
    subtitle: String?,
    subtitleAccented: Boolean,
    peerName: String,
    peerSeed: String,
    online: Boolean,
    onBack: () -> Unit,
) {
    val palette = NuvaTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(palette.surface, palette.surfaceAlt))
            )
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NuvaSize.topBar)
                .padding(horizontal = NuvaSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircle(icon = Icons.AutoMirrored.Filled.ArrowBack, description = "Back", onClick = onBack)
            Spacer(Modifier.width(NuvaSpace.xs))
            NuvaAvatar(name = peerName, seed = peerSeed, size = NuvaSize.avatarTopBar, online = online)
            Spacer(Modifier.width(NuvaSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                    maxLines = 1,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (subtitleAccented) palette.accent else palette.textMuted,
                        maxLines = 1,
                    )
                }
            }
            IconCircle(icon = Icons.Filled.MoreVert, description = "More", onClick = {})
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(palette.hairline))
    }
}

@Composable
private fun IconCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val palette = NuvaTheme.palette
    Box(
        modifier = Modifier
            .size(NuvaSize.iconButton)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = palette.textMuted, modifier = Modifier.size(22.dp))
    }
}

// ------------------------------------------------------------ message list

/** A rendered row: either a floating day pill or an actual message. */
private sealed interface Row2 {
    data class Day(val label: String, val key: String) : Row2
    data class Msg(
        val message: ChatDraftStore.Message,
        val firstOfGroup: Boolean,
        val lastOfGroup: Boolean,
    ) : Row2
}

/**
 * Turns a flat message list into rows, newest first (the list is reversed),
 * grouping consecutive messages from the same author so only the last one
 * in a run carries a tail and a timestamp.
 */
private fun buildRows(messages: List<ChatDraftStore.Message>): List<Row2> {
    val ordered = messages.sortedBy { it.sentAtMillis }
    val out = mutableListOf<Row2>()
    var lastDay: String? = null
    ordered.forEachIndexed { index, message ->
        val day = dayKey(message.sentAtMillis)
        if (day != lastDay) {
            out += Row2.Day(label = dayLabel(message.sentAtMillis), key = day)
            lastDay = day
        }
        val prev = ordered.getOrNull(index - 1)
        val next = ordered.getOrNull(index + 1)
        val sameAsPrev = prev != null && prev.authorId == message.authorId && dayKey(prev.sentAtMillis) == day
        val sameAsNext = next != null && next.authorId == message.authorId && dayKey(next.sentAtMillis) == day
        out += Row2.Msg(message, firstOfGroup = !sameAsPrev, lastOfGroup = !sameAsNext)
    }
    return out.asReversed()
}

@Composable
private fun MessageList(
    listState: androidx.compose.foundation.lazy.LazyListState,
    rows: List<Row2>,
    reactions: Map<String, Set<String>>,
    peerTyping: Boolean,
    onLongPress: (String) -> Unit,
    onRetry: (String) -> Unit,
    onToggleReaction: (String, String) -> Unit,
) {
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = NuvaSpace.gutter,
            end = NuvaSpace.gutter,
            top = NuvaSpace.lg,
            bottom = NuvaSpace.lg,
        ),
    ) {
        if (peerTyping) {
            item(key = "typing") { TypingBubble() }
        }
        items(items = rows, key = { row -> if (row is Row2.Day) "day-${row.key}" else (row as Row2.Msg).message.id }) { row ->
            when (row) {
                is Row2.Day -> DaySeparator(row.label)
                is Row2.Msg -> MessageBubble(
                    message = row.message,
                    firstOfGroup = row.firstOfGroup,
                    lastOfGroup = row.lastOfGroup,
                    reactions = reactions[row.message.id].orEmpty(),
                    onLongPress = { onLongPress(row.message.id) },
                    onRetry = { onRetry(row.message.id) },
                    onToggleReaction = { emoji -> onToggleReaction(row.message.id, emoji) },
                )
            }
        }
    }
}

@Composable
private fun DaySeparator(label: String) {
    val palette = NuvaTheme.palette
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = NuvaSpace.md), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted,
            modifier = Modifier
                .clip(RoundedCornerShape(NuvaRadius.chip))
                .background(palette.surfaceRaised)
                .padding(horizontal = NuvaSpace.md, vertical = NuvaSpace.xs),
        )
    }
}

// ---------------------------------------------------------------- bubbles

@Composable
private fun MessageBubble(
    message: ChatDraftStore.Message,
    firstOfGroup: Boolean,
    lastOfGroup: Boolean,
    reactions: Set<String>,
    onLongPress: () -> Unit,
    onRetry: () -> Unit,
    onToggleReaction: (String) -> Unit,
) {
    val palette = NuvaTheme.palette
    val mine = message.mine
    val haptics = LocalHapticFeedback.current

    // Entry animation: every bubble rises a little and fades in once.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(message.id) {
        appear.animateTo(1f, tween(NuvaMotion.BUBBLE_IN))
    }

    val shape = RoundedCornerShape(
        topStart = NuvaRadius.bubble,
        topEnd = NuvaRadius.bubble,
        bottomStart = if (mine || !lastOfGroup) NuvaRadius.bubble else NuvaRadius.bubbleTail,
        bottomEnd = if (!mine || !lastOfGroup) NuvaRadius.bubble else NuvaRadius.bubbleTail,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (firstOfGroup) NuvaSpace.md else NuvaSpace.hair)
            .alpha(appear.value),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                .then(
                    if (mine) {
                        Modifier.background(
                            Brush.verticalGradient(listOf(palette.bubbleOutTop, palette.bubbleOutBottom))
                        )
                    } else {
                        Modifier.background(palette.bubbleIn).border(1.dp, palette.hairline, shape)
                    }
                )
                .pointerInput(message.id) {
                    detectTapGestures(
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        },
                        onTap = { if (message.delivery == ChatDraftStore.Delivery.Failed) onRetry() },
                    )
                }
                .padding(horizontal = NuvaSpace.lg, vertical = NuvaSpace.md),
        ) {
            Column {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (mine) palette.bubbleOutText else palette.bubbleInText,
                )
                Spacer(Modifier.height(NuvaSpace.xs))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.End)) {
                    Text(
                        text = clockLabel(message.sentAtMillis),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = if (mine) palette.bubbleOutText.copy(alpha = 0.7f) else palette.textFaint,
                    )
                    if (mine) {
                        Spacer(Modifier.width(NuvaSpace.xs))
                        DeliveryTicks(delivery = message.delivery, onLight = true)
                    }
                }
            }
        }

        if (reactions.isNotEmpty()) {
            ReactionPills(reactions = reactions, mine = mine, onToggle = onToggleReaction)
        }
    }
}

@Composable
private fun ReactionPills(reactions: Set<String>, mine: Boolean, onToggle: (String) -> Unit) {
    val palette = NuvaTheme.palette
    Row(
        modifier = Modifier.padding(top = NuvaSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(NuvaSpace.xs),
    ) {
        reactions.forEach { emoji ->
            val scale = remember { Animatable(0.6f) }
            LaunchedEffect(emoji) { scale.animateTo(1f, tween(NuvaMotion.MICRO)) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .scale(scale.value)
                    .clip(RoundedCornerShape(NuvaRadius.chip))
                    .background(palette.accentWash)
                    .border(1.dp, palette.accentSoft, RoundedCornerShape(NuvaRadius.chip))
                    .clickable { onToggle(emoji) }
                    .padding(horizontal = NuvaSpace.sm, vertical = NuvaSpace.hair),
            ) {
                Text(text = emoji, fontSize = 13.sp)
                Spacer(Modifier.width(NuvaSpace.xs))
                Text(text = "1", style = MaterialTheme.typography.labelSmall, color = palette.accentInk)
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    val palette = NuvaTheme.palette
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = Modifier
            .padding(vertical = NuvaSpace.sm)
            .clip(RoundedCornerShape(NuvaRadius.bubble))
            .background(palette.bubbleIn)
            .padding(horizontal = NuvaSpace.lg, vertical = NuvaSpace.md),
        horizontalArrangement = Arrangement.spacedBy(NuvaSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 160, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(palette.textMuted.copy(alpha = alpha)))
        }
    }
}

// ------------------------------------------------------------ reaction UI

@Composable
private fun ReactionOverlay(
    visibleFor: String?,
    chosen: Set<String>,
    onPick: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = NuvaTheme.palette
    AnimatedVisibility(
        visible = visibleFor != null,
        enter = fadeIn(tween(NuvaMotion.OVERLAY)),
        exit = fadeOut(tween(NuvaMotion.FAST)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.scrim)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visibleFor != null,
                enter = scaleIn(tween(NuvaMotion.OVERLAY), initialScale = 0.8f) + fadeIn(),
                exit = scaleOut(tween(NuvaMotion.FAST)) + fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(NuvaRadius.sheet))
                        .background(palette.surfaceRaised)
                        .border(1.dp, palette.hairline, RoundedCornerShape(NuvaRadius.sheet))
                        .padding(horizontal = NuvaSpace.lg, vertical = NuvaSpace.md),
                    horizontalArrangement = Arrangement.spacedBy(NuvaSpace.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QUICK_REACTIONS.forEach { emoji ->
                        val picked = emoji in chosen
                        val scale by animateFloatAsState(if (picked) 1.25f else 1f, tween(NuvaMotion.MICRO), label = "pick")
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .background(if (picked) palette.accentWash else Color.Transparent)
                                .clickable { visibleFor?.let { onPick(it, emoji) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = emoji, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------- composer

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    recording: Boolean,
    onRecordingChange: (Boolean) -> Unit,
) {
    val palette = NuvaTheme.palette
    val haptics = LocalHapticFeedback.current
    val canSend = value.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(palette.hairline))

        if (recording) {
            RecordingBar(onCancel = { onRecordingChange(false) })
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NuvaSpace.md, vertical = NuvaSpace.sm),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = NuvaSize.composerMin)
                        .clip(RoundedCornerShape(NuvaRadius.sheet))
                        .background(palette.surfaceSunken)
                        .border(1.dp, palette.hairline, RoundedCornerShape(NuvaRadius.sheet))
                        .padding(horizontal = NuvaSpace.lg, vertical = NuvaSpace.md),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Message",
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.textFaint,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = LocalTextStyle.current.copy(
                            color = palette.text,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        ),
                        cursorBrush = SolidColor(palette.accent),
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.width(NuvaSpace.sm))

                val buttonScale by animateFloatAsState(if (canSend) 1f else 0.92f, tween(NuvaMotion.MICRO), label = "send")
                Box(
                    modifier = Modifier
                        .size(NuvaSize.composerMin)
                        .scale(buttonScale)
                        .clip(CircleShape)
                        .background(
                            if (canSend) {
                                Brush.verticalGradient(listOf(palette.bubbleOutTop, palette.bubbleOutBottom))
                            } else {
                                SolidColor(palette.surfaceSunken)
                            }
                        )
                        .pointerInput(canSend) {
                            detectTapGestures(
                                onTap = { if (canSend) onSend() },
                                onLongPress = {
                                    if (!canSend) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onRecordingChange(true)
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (canSend) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic,
                        contentDescription = if (canSend) "Send" else "Hold to record",
                        tint = if (canSend) palette.bubbleOutText else palette.textMuted,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

private fun Modifier.heightIn(min = NuvaSize.composerMin): Modifier = this.then(Modifier.padding(0.dp))

/**
 * Recording strip.
 *
 * The gesture, the timer and the live waveform are real. Actual audio capture
 * is not wired yet: it needs MediaRecorder plus a runtime RECORD_AUDIO grant
 * and the POST /v1/media upload, which is the next slice. Nothing here
 * pretends a file was produced.
 */
@Composable
private fun RecordingBar(onCancel: () -> Unit) {
    val palette = NuvaTheme.palette
    var elapsed by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsed += 1
        }
    }
    val blink = rememberInfiniteTransition(label = "rec")
    val dotAlpha by blink.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "recdot",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NuvaSpace.lg, vertical = NuvaSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(palette.danger.copy(alpha = dotAlpha)))
        Spacer(Modifier.width(NuvaSpace.md))
        Text(
            text = String.format(Locale.US, "%d:%02d", elapsed / 60, elapsed % 60),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.text,
        )
        Spacer(Modifier.width(NuvaSpace.lg))
        LiveWaveform(modifier = Modifier.weight(1f).height(28.dp))
        Spacer(Modifier.width(NuvaSpace.lg))
        Text(
            text = "Cancel",
            style = MaterialTheme.typography.labelLarge,
            color = palette.danger,
            modifier = Modifier.clickable(onClick = onCancel),
        )
    }
}

@Composable
private fun LiveWaveform(modifier: Modifier = Modifier) {
    val palette = NuvaTheme.palette
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "phase",
    )
    Canvas(modifier = modifier) {
        val bars = 28
        val gap = size.width / bars
        repeat(bars) { index ->
            val wobble = kotlin.math.abs(kotlin.math.sin((index / 3f) + phase * 6.28f))
            val barHeight = size.height * (0.2f + 0.8f * wobble)
            val x = index * gap + gap / 2f
            drawLine(
                color = if (index % 4 == 0) palette.waveActive else palette.waveIdle,
                start = Offset(x, (size.height - barHeight) / 2f),
                end = Offset(x, (size.height + barHeight) / 2f),
                strokeWidth = gap * 0.45f,
            )
        }
    }
}

// ----------------------------------------------------------------- pieces

@Composable
private fun EmptyConversation(name: String) {
    val palette = NuvaTheme.palette
    Box(modifier = Modifier.fillMaxSize().padding(NuvaSpace.huge), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No messages yet",
                style = MaterialTheme.typography.titleMedium,
                color = palette.text,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(NuvaSpace.sm))
            Text(
                text = if (name.isBlank()) "Say hello." else "Say hello to $name.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textMuted,
            )
        }
    }
}

/** Kept public with its original signature: other screens render it too. */
@Composable
fun DeliveryTicks(
    delivery: ChatDraftStore.Delivery,
    onLight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val palette = NuvaTheme.palette
    val tint = when (delivery) {
        ChatDraftStore.Delivery.Failed -> palette.danger
        ChatDraftStore.Delivery.Read -> if (onLight) palette.mint else palette.accent
        else -> if (onLight) palette.bubbleOutText.copy(alpha = 0.7f) else palette.textFaint
    }
    val icon = when (delivery) {
        ChatDraftStore.Delivery.Sending -> Icons.Filled.Schedule
        ChatDraftStore.Delivery.Sent -> Icons.Filled.Check
        ChatDraftStore.Delivery.Delivered, ChatDraftStore.Delivery.Read -> Icons.Filled.DoneAll
        ChatDraftStore.Delivery.Failed -> Icons.Filled.ErrorOutline
    }
    Icon(imageVector = icon, contentDescription = delivery.name, tint = tint, modifier = modifier.size(14.dp))
}

// ------------------------------------------------------------------ time

private fun dayKey(millis: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
}

private fun dayLabel(millis: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "Today"
        sameYear && dayDiff == 1 -> "Yesterday"
        sameYear -> String.format(Locale.US, "%1\$td %1\$tB", then)
        else -> String.format(Locale.US, "%1\$td %1\$tB %1\$tY", then)
    }
}

private fun clockLabel(millis: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}
