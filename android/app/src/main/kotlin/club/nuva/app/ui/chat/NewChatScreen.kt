package club.nuva.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import club.nuva.app.ui.design.NuvaCard
import club.nuva.app.ui.design.NuvaHeader
import club.nuva.app.ui.design.NuvaRow
import club.nuva.app.ui.design.NuvaSectionLabel
import club.nuva.app.ui.theme.NuvaSpace
import club.nuva.app.ui.theme.NuvaTheme

/**
 * People.
 *
 * 2.5 listed a directory of invented strangers. There is no directory API yet,
 * so this screen no longer pretends there is one: it shows the contacts you
 * have written down, and the search field doubles as the way to add one.
 *
 * Type a username that is not in the list and the first row becomes "Start
 * chat with @name". One field, two jobs, no separate "add contact" screen to
 * design and maintain — and the moment the server exposes a real directory,
 * the same field starts returning remote matches above the local ones.
 */
@Composable
fun NewChatScreen(
    store: ChatDraftStore,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val p = NuvaTheme.palette
    val people by store.people.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    val typed = query.trim().removePrefix("@").lowercase()
    val matches = remember(people, typed) {
        if (typed.isEmpty()) {
            people
        } else {
            people.filter {
                it.username.contains(typed) || it.displayName.lowercase().contains(typed)
            }
        }
    }
    val exactExists = people.any { it.username == typed }
    val canCreate = typed.isNotEmpty() && !exactExists && typed.all { it.isLetterOrDigit() || it == '_' || it == '.' }

    Column(modifier = modifier.fillMaxSize()) {
        NuvaHeader(
            title = "People",
            subtitle = "Contacts on this device",
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = NuvaSpace.gutter,
                end = NuvaSpace.gutter,
                bottom = bottomInset + NuvaSpace.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(NuvaSpace.sm),
        ) {
            item {
                club.nuva.app.ui.design.NuvaField(
                    value = query,
                    onValueChange = { query = it },
                    label = "Find or add",
                    placeholder = "username",
                    leadingIcon = Icons.Filled.PersonSearch,
                    supporting = "Letters, digits, dot and underscore",
                )
            }

            if (canCreate) {
                item {
                    NuvaCard(
                        accented = true,
                        onClick = {
                            val person = store.createContact(typed)
                            query = ""
                            onOpenConversation(store.openWith(person))
                        },
                    ) {
                        Column(modifier = Modifier.padding(NuvaSpace.lg)) {
                            Text(
                                text = "Start chat with @$typed",
                                style = MaterialTheme.typography.titleSmall,
                                color = p.text,
                            )
                            Spacer(Modifier.height(NuvaSpace.xs))
                            Text(
                                text = "Saved on this device. Messages reach them once " +
                                    "your server is connected in sprint 1.",
                                style = MaterialTheme.typography.bodySmall,
                                color = p.textMuted,
                            )
                        }
                    }
                }
            }

            if (matches.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(NuvaSpace.sm))
                    NuvaSectionLabel(text = if (typed.isEmpty()) "All contacts" else "Matches")
                }

                items(matches, key = { it.id }) { person ->
                    NuvaRow(
                        title = person.displayName,
                        subtitle = "@" + person.username,
                        icon = Icons.Filled.Person,
                        onClick = { onOpenConversation(store.openWith(person)) },
                    )
                }
            }

            if (people.isEmpty() && !canCreate) {
                item {
                    Spacer(Modifier.height(NuvaSpace.xxl))
                    Text(
                        text = "No contacts yet.\nType a username above to add the first one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.textMuted,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
