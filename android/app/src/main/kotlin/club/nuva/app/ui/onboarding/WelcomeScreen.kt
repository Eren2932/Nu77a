package club.nuva.app.ui.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import club.nuva.app.ui.design.NuvaButton
import club.nuva.app.ui.theme.NuvaMotion
import club.nuva.app.ui.theme.NuvaSize
import club.nuva.app.ui.theme.NuvaSpace
import club.nuva.app.ui.theme.NuvaTheme

/**
 * The first thing a new install shows.
 *
 * Four cards, swipe or tap through, one primary button. Deliberately built out
 * of the plainest APIs in Compose — a page index, a Crossfade and a drag
 * detector — instead of HorizontalPager: this screen must work on the widest
 * possible range of Compose versions, because it is the one screen that runs
 * before the user has any reason to forgive us.
 *
 * The copy rule: every line here has to be true of the build you are holding.
 * "Military-grade encryption" on a client that has no message encryption yet
 * is how you teach a user that your interface lies. So the encryption card
 * says what actually ships today and what does not.
 */
@Composable
fun WelcomeScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = NuvaTheme.palette
    var page by rememberSaveable { mutableStateOf(0) }
    val pages = welcomePages()
    val last = page == pages.lastIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(p.canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(pages.size) {
                var travelled = 0f
                detectHorizontalDragGestures(
                    onDragStart = { travelled = 0f },
                    onDragEnd = {
                        // 48px of intent, so a vertical scroll never flips a card.
                        if (travelled <= -48f && page < pages.lastIndex) page += 1
                        if (travelled >= 48f && page > 0) page -= 1
                    },
                    onHorizontalDrag = { _, amount -> travelled += amount },
                )
            },
    ) {
        Spacer(Modifier.height(NuvaSpace.huge))

        Crossfade(
            targetState = page,
            animationSpec = tween(NuvaMotion.NORMAL),
            label = "welcome-page",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { index ->
            val card = pages[index]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = NuvaSpace.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .clip(CircleShape)
                        .background(p.accentWash),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = card.icon,
                        contentDescription = null,
                        tint = p.accent,
                        modifier = Modifier.size(56.dp),
                    )
                }

                Spacer(Modifier.height(NuvaSpace.huge))

                Text(
                    text = card.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = p.text,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(NuvaSpace.md))

                Text(
                    text = card.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = p.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = NuvaSpace.xl),
            horizontalArrangement = Arrangement.Center,
        ) {
            pages.forEachIndexed { index, _ ->
                Dot(active = index == page, accent = p.accent, idle = p.hairline)
                if (index != pages.lastIndex) Spacer(Modifier.width(NuvaSpace.sm))
            }
        }

        NuvaButton(
            text = if (last) "Get started" else "Next",
            onClick = { if (last) onDone() else page += 1 },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NuvaSpace.xl),
        )

        Spacer(Modifier.height(NuvaSpace.md))

        // Skip stays available on every card, including the last one, where it
        // reads as the honest "I have read enough" rather than a dead area.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NuvaSize.touchMin),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (last) " " else "Skip",
                style = MaterialTheme.typography.labelMedium,
                color = p.textFaint,
                modifier = Modifier
                    .clip(CircleShape)
                    .padding(horizontal = NuvaSpace.lg, vertical = NuvaSpace.sm),
            )
        }

        Spacer(Modifier.height(NuvaSpace.lg))
    }
}

@Composable
private fun Dot(active: Boolean, accent: Color, idle: Color) {
    val width by animateDpAsState(
        targetValue = if (active) 18.dp else 6.dp,
        animationSpec = tween(NuvaMotion.NORMAL),
        label = "dot",
    )
    Box(
        modifier = Modifier
            .width(width)
            .height(6.dp)
            .clip(CircleShape)
            .background(if (active) accent else idle),
    )
}

private data class WelcomeCard(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

/**
 * Four claims, in the order that matters to someone who has never heard of us:
 * whose machine is this, who am I here, where is my data, and what is NOT
 * done yet.
 */
private fun welcomePages(): List<WelcomeCard> = listOf(
    WelcomeCard(
        icon = Icons.Filled.Dns,
        title = "Your server",
        body = "Nuva talks to a server you pick — yours, or one run by someone " +
            "you actually trust. There is no global directory and no company " +
            "sitting in the middle of your conversations.",
    ),
    WelcomeCard(
        icon = Icons.Filled.Person,
        title = "No phone number",
        body = "You are a username here, not a SIM card. Nothing in Nuva asks " +
            "for your contacts list, and nothing uploads it.",
    ),
    WelcomeCard(
        icon = Icons.Filled.Devices,
        title = "History lives here",
        body = "Messages are stored on this device, in this app. Uninstall it " +
            "and the history is gone — that is the trade we chose over " +
            "keeping a copy of your life on someone else's disk.",
    ),
    WelcomeCard(
        icon = Icons.Filled.Shield,
        title = "What is not done yet",
        body = "Traffic to your server is protected by TLS today. " +
            "Message-level encryption and multi-device sync are being built, " +
            "and this screen will say so the day they actually ship.",
    ),
)
