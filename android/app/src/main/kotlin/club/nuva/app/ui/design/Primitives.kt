package club.nuva.app.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import club.nuva.app.ui.theme.NuvaMonoStyle
import club.nuva.app.ui.theme.NuvaMotion
import club.nuva.app.ui.theme.NuvaSpace
import club.nuva.app.ui.theme.NuvaTheme
import kotlin.math.abs

/**
 * The Nuva component vocabulary.
 *
 * Every screen is assembled out of these. If a screen needs a raw
 * `Button`/`Card`/`Text` with hand-written colours, that is a signal the
 * vocabulary is missing a word — add it here instead of styling in place.
 */

// ---------------------------------------------------------------------------
// Page chrome
// ---------------------------------------------------------------------------

/** The app canvas. A single soft aurora wash at the top, nothing behind text. */
@Composable
fun NuvaCanvas(
    modifier: Modifier = Modifier,
    glow: Boolean = true,
    content: @Composable () -> Unit,
) {
    val p = NuvaTheme.palette
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(p.canvas),
    ) {
        if (glow) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(p.glow, Color.Transparent),
                        ),
                    ),
            )
        }
        content()
    }
}

/**
 * Nuva's header. Deliberately not `TopAppBar`: no elevation, no tonal
 * container, a large tight title, and an optional subtitle that carries the
 * one fact the user needs (who they are talking to, which server they trust).
 */
@Composable
fun NuvaHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    compact: Boolean = false,
    onBack: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
) {
    val p = NuvaTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (onBack != null) NuvaSpace.xs else NuvaSpace.gutter,
                end = NuvaSpace.sm,
                top = if (compact) NuvaSpace.sm else NuvaSpace.lg,
                bottom = if (compact) NuvaSpace.sm else NuvaSpace.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = p.text,
                )
            }
        }
        if (leading != null) {
            leading()
            Spacer(Modifier.width(NuvaSpace.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (compact) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.headlineMedium
                },
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = p.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (actions != null) {
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}

/** Group label above a block of rows. Small, wide-tracked, muted. */
@Composable
fun NuvaSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = NuvaTheme.palette.textFaint,
        modifier = modifier.padding(
            start = NuvaSpace.gutter,
            end = NuvaSpace.gutter,
            top = NuvaSpace.xl,
            bottom = NuvaSpace.sm,
        ),
    )
}

// ---------------------------------------------------------------------------
// Containers
// ---------------------------------------------------------------------------

@Composable
fun NuvaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    accented: Boolean = false,
    content: @Composable () -> Unit,
) {
    val p = NuvaTheme.palette
    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(if (accented) p.accent.copy(alpha = 0.10f) else p.surface)
        .border(
            width = 1.dp,
            color = if (accented) p.accent.copy(alpha = 0.35f) else p.hairline,
            shape = shape,
        )
    Box(
        modifier = modifier
            .then(base)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        content()
    }
}

/** A tappable row: icon, title, optional value, chevron. The settings atom. */
@Composable
fun NuvaRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    tint: Color? = null,
    showChevron: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val p = NuvaTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = NuvaSpace.lg, vertical = NuvaSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background((tint ?: p.accent).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint ?: p.accent,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(NuvaSpace.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = tint ?: p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(NuvaSpace.sm))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = p.textMuted,
                maxLines = 1,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(NuvaSpace.sm))
            trailing()
        } else if (showChevron && onClick != null) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = p.textFaint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun NuvaHairline(modifier: Modifier = Modifier, inset: Dp = NuvaSpace.lg) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(1.dp)
            .background(NuvaTheme.palette.hairline),
    )
}

/** Read-only value the user may need to copy or read out loud. */
@Composable
fun NuvaMonoValue(text: String, modifier: Modifier = Modifier) {
    val p = NuvaTheme.palette
    Text(
        text = text,
        style = NuvaMonoStyle,
        color = p.textMuted,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(p.surfaceAlt)
            .padding(horizontal = NuvaSpace.sm, vertical = NuvaSpace.xs),
    )
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

@Composable
fun NuvaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    icon: ImageVector? = null,
) {
    val p = NuvaTheme.palette
    val active = enabled && !busy
    val container by animateColorAsState(
        targetValue = if (active) p.accent else p.surfaceAlt,
        animationSpec = tween(NuvaMotion.NORMAL),
        label = "btn-bg",
    )
    val content = if (active) p.accentInk else p.textFaint

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = p.textMuted,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(NuvaSpace.sm))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                )
            }
        }
    }
}

@Composable
fun NuvaGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    icon: ImageVector? = null,
) {
    val p = NuvaTheme.palette
    val tint = if (danger) p.coral else p.text
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(CircleShape)
            .border(1.dp, if (danger) p.coral.copy(alpha = 0.45f) else p.hairline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(NuvaSpace.sm))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = tint)
        }
    }
}

/** Two-state pill switcher. Used for Sign in / Create account. */
@Composable
fun NuvaSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = NuvaTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(p.surfaceAlt)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(
                targetValue = if (selected) p.surface else Color.Transparent,
                animationSpec = tween(NuvaMotion.FAST),
                label = "seg-bg",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable { onSelect(index) }
                    .padding(vertical = NuvaSpace.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) p.text else p.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun NuvaSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    val p = NuvaTheme.palette
    NuvaRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        showChevron = false,
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = p.accentInk,
                    checkedTrackColor = p.accent,
                    uncheckedThumbColor = p.textMuted,
                    uncheckedTrackColor = p.surfaceAlt,
                    uncheckedBorderColor = p.hairline,
                ),
            )
        },
    )
}

// ---------------------------------------------------------------------------
// Input
// ---------------------------------------------------------------------------

@Composable
fun NuvaField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    mono: Boolean = false,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    leadingIcon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val p = NuvaTheme.palette
    val isError = errorText != null
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            isError = isError,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it, color = p.textFaint) } },
            leadingIcon = leadingIcon?.let {
                { Icon(it, contentDescription = null, tint = p.textMuted, modifier = Modifier.size(19.dp)) }
            },
            trailingIcon = trailing,
            visualTransformation = if (isPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            ),
            textStyle = if (mono) NuvaMonoStyle.copy(color = p.text) else MaterialTheme.typography.bodyLarge,
            shape = MaterialTheme.shapes.medium,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = p.surface,
                unfocusedContainerColor = p.surface,
                disabledContainerColor = p.surfaceAlt,
                errorContainerColor = p.surface,
                focusedIndicatorColor = p.accent,
                unfocusedIndicatorColor = p.hairline,
                disabledIndicatorColor = p.hairline,
                errorIndicatorColor = p.coral,
                focusedLabelColor = p.accent,
                unfocusedLabelColor = p.textMuted,
                errorLabelColor = p.coral,
                cursorColor = p.accent,
                focusedTextColor = p.text,
                unfocusedTextColor = p.text,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        val helper = errorText ?: supporting
        if (helper != null) {
            Text(
                text = helper,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) p.coral else p.textMuted,
                modifier = Modifier.padding(start = NuvaSpace.md, top = NuvaSpace.xs),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Identity
// ---------------------------------------------------------------------------

/**
 * Avatar. Falls back to initials on a colour derived from the id, so every
 * user is visually distinct before a single image is ever uploaded.
 */
@Composable
fun NuvaAvatar(
    name: String,
    modifier: Modifier = Modifier,
    seed: String = name,
    size: Dp = 46.dp,
    imageUrl: String? = null,
    online: Boolean = false,
) {
    val p = NuvaTheme.palette
    val ring = avatarColor(seed, p.isDark)
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(ring.copy(alpha = if (p.isDark) 0.24f else 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!imageUrl.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(
                    text = initialsOf(name),
                    style = MaterialTheme.typography.titleMedium,
                    color = ring,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (online) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size / 3.6f)
                    .clip(CircleShape)
                    .background(p.canvas),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(size / 5.5f)
                        .clip(CircleShape)
                        .background(p.mint),
                )
            }
        }
    }
}

private val avatarSeeds = listOf(
    Color(0xFF8B7CFF), Color(0xFF3FD9B3), Color(0xFFFFB454),
    Color(0xFFFF7A9C), Color(0xFF4FB8FF), Color(0xFFB07CFF),
    Color(0xFF6FD36F), Color(0xFFFF8F5C),
)

fun avatarColor(seed: String, dark: Boolean): Color {
    val base = avatarSeeds[abs(seed.hashCode()) % avatarSeeds.size]
    return if (dark) base else base.copy(alpha = 1f)
}

fun initialsOf(name: String): String {
    val parts = name.trim().split(' ', '_', '-', '.').filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

// ---------------------------------------------------------------------------
// Status and states
// ---------------------------------------------------------------------------

@Composable
fun NuvaDot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size).clip(CircleShape).background(color))
}

/** Nothing-here state. Always says what to DO, never just "no data". */
@Composable
fun NuvaEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val p = NuvaTheme.palette
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NuvaSpace.huge, vertical = NuvaSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(p.surfaceAlt),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = p.textFaint, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(NuvaSpace.lg))
        Text(title, style = MaterialTheme.typography.titleLarge, color = p.text)
        Spacer(Modifier.height(NuvaSpace.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = p.textMuted,
            modifier = Modifier.alpha(0.95f),
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(NuvaSpace.xl))
            NuvaButton(text = actionText, onClick = onAction, modifier = Modifier.width(220.dp))
        }
    }
}

/** Placeholder block for content that is loading. Shape only, no fake text. */
@Composable
fun NuvaSkeleton(
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    shape: Shape = RoundedCornerShape(7.dp),
) {
    val p = NuvaTheme.palette
    val alpha by animateFloatAsState(
        targetValue = 0.6f,
        animationSpec = tween(NuvaMotion.SLOW),
        label = "skeleton",
    )
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(p.surfaceAlt.copy(alpha = alpha)),
    )
}

/** Inline chip: a dot plus a word. Used for connection state everywhere. */
@Composable
fun NuvaStatusChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val p = NuvaTheme.palette
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(p.surfaceAlt)
            .padding(horizontal = NuvaSpace.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NuvaDot(color)
        Spacer(Modifier.width(NuvaSpace.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = 0.85f),
        )
    }
}
