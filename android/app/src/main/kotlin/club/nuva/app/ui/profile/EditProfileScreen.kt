package club.nuva.app.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import club.nuva.app.di.ServiceLocator
import club.nuva.app.ui.design.NuvaAvatar
import club.nuva.app.ui.design.NuvaButton
import club.nuva.app.ui.design.NuvaCard
import club.nuva.app.ui.design.NuvaField
import club.nuva.app.ui.design.NuvaHeader
import club.nuva.app.ui.theme.NuvaSpace
import club.nuva.app.ui.theme.NuvaTheme

/**
 * Profile editor.
 *
 * The layout, validation and live avatar preview are final. Saving is the one
 * thing that is not, and the screen says so out loud instead of showing a Save
 * button that silently does nothing — the exact trap that made us distrust
 * "finished" screens in the last project.
 *
 * SPRINT 1 WIRING (three lines, nothing else changes):
 *   PATCH /v1/me with UpdateProfileRequestDto(displayName, bio, avatarUrl)
 *   -> AuthRepository.updateProfile(...) -> sessionStore.save(updated)
 * The DTO already exists in data/remote/Dto.kt, so the client half is ready.
 */
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = NuvaTheme.palette
    val session by ServiceLocator.authRepository.session.collectAsStateWithLifecycle()

    var displayName by rememberSaveable { mutableStateOf(session?.displayName.orEmpty()) }
    var bio by rememberSaveable { mutableStateOf("") }
    var avatarUrl by rememberSaveable { mutableStateOf("") }

    val displayNameError = when {
        displayName.isBlank() -> "A display name cannot be empty."
        displayName.length > 64 -> "64 characters maximum."
        else -> null
    }
    val bioError = if (bio.length > 280) "280 characters maximum." else null

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        NuvaHeader(title = "Edit profile", compact = true, onBack = onBack)

        Column(
            modifier = Modifier
                .padding(horizontal = NuvaSpace.gutter)
                .padding(bottom = NuvaSpace.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Live preview: initials change as you type, so the fallback avatar
            // is never a surprise after saving.
            NuvaAvatar(
                name = displayName.ifBlank { session?.username.orEmpty() },
                seed = session?.userId.orEmpty(),
                imageUrl = avatarUrl.ifBlank { null },
                size = 92.dp,
            )
            Spacer(Modifier.height(NuvaSpace.sm))
            Text(
                text = "@${session?.username.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
                color = p.textMuted,
            )
            Text(
                text = "The username is permanent on this server.",
                style = MaterialTheme.typography.labelSmall,
                color = p.textFaint,
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = NuvaSpace.gutter),
        ) {
            NuvaField(
                value = displayName,
                onValueChange = { displayName = it.take(64) },
                label = "Display name",
                placeholder = "How people see you",
                errorText = displayNameError,
                supporting = "${displayName.length}/64",
            )
            Spacer(Modifier.height(NuvaSpace.md))
            NuvaField(
                value = bio,
                onValueChange = { bio = it.take(280) },
                label = "About",
                placeholder = "One line about you",
                singleLine = false,
                errorText = bioError,
                supporting = "${bio.length}/280",
            )
            Spacer(Modifier.height(NuvaSpace.md))
            NuvaField(
                value = avatarUrl,
                onValueChange = { avatarUrl = it.trim() },
                label = "Avatar URL",
                placeholder = "https://…",
                mono = true,
                supporting = "Uploads land in sprint 3. A link works today.",
            )

            Spacer(Modifier.height(NuvaSpace.xl))

            NuvaCard(accented = false) {
                Column(Modifier.padding(NuvaSpace.lg)) {
                    Text(
                        text = "Saving is not wired yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = p.amber,
                    )
                    Spacer(Modifier.height(NuvaSpace.xs))
                    Text(
                        text = "The server has no PATCH /v1/me endpoint yet — it is the " +
                            "first task of sprint 1. This screen is finished and will " +
                            "start saving the moment that endpoint exists. Nothing you " +
                            "type here is stored in the meantime, on purpose: a local-only " +
                            "profile that silently disagrees with the server is worse than " +
                            "no profile at all.",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.textMuted,
                    )
                }
            }

            Spacer(Modifier.height(NuvaSpace.lg))

            NuvaButton(
                text = "Save changes",
                onClick = { /* sprint 1: repository.updateProfile(...) */ },
                enabled = false,
                icon = Icons.Filled.Info,
            )
            Spacer(Modifier.height(NuvaSpace.huge))
        }
    }
}
